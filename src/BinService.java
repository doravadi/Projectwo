
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class BinService {

    private final BinRangeRepository repository;
    private final Map<String, List<BinRange>> bankCache;
    private final Map<String, List<BinRange>> countryCache;
    private volatile boolean cacheValid = false;

    public BinService(BinRangeRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
        this.bankCache = new ConcurrentHashMap<>();
        this.countryCache = new ConcurrentHashMap<>();
    }


    public PaymentRouting routePayment(CardNumber cardNumber) throws DomainException {
        Objects.requireNonNull(cardNumber, "Card number cannot be null");

        long bin = cardNumber.getBin();

        try {
            Optional<BinRange> range = repository.findRangeForBin(bin);

            if (range.isPresent()) {
                BinRange binRange = range.get();
                return PaymentRouting.builder()
                        .bankName(binRange.getBankName())
                        .country(binRange.getCountry())
                        .cardType(binRange.getCardType())
                        .domesticTransaction(isDomesticTransaction(binRange))
                        .riskLevel(calculateRiskLevel(binRange))
                        .build();
            } else {
                throw new UnknownBinException("No routing found for BIN: " + bin);
            }

        } catch (InfrastructureException e) {
            throw new BinRoutingException("Failed to route payment for BIN: " + bin, e);
        }
    }


    public Map<CardNumber, PaymentRouting> routePayments(List<CardNumber> cardNumbers) throws DomainException {
        Objects.requireNonNull(cardNumbers, "Card numbers cannot be null");

        if (cardNumbers.isEmpty()) {
            return Collections.emptyMap();
        }

        try {

            List<Long> bins = cardNumbers.stream()
                    .map(CardNumber::getBin)
                    .distinct()
                    .collect(Collectors.toList());


            Map<Long, BinRange> binRanges = repository.findRangesForBins(bins);


            Map<CardNumber, PaymentRouting> results = new HashMap<>();

            for (CardNumber cardNumber : cardNumbers) {
                long bin = cardNumber.getBin();
                BinRange range = binRanges.get(bin);

                if (range != null) {
                    PaymentRouting routing = PaymentRouting.builder()
                            .bankName(range.getBankName())
                            .country(range.getCountry())
                            .cardType(range.getCardType())
                            .domesticTransaction(isDomesticTransaction(range))
                            .riskLevel(calculateRiskLevel(range))
                            .build();

                    results.put(cardNumber, routing);
                } else {

                    logMissingBin(bin);
                }
            }

            return results;

        } catch (InfrastructureException e) {
            throw new BinRoutingException("Failed to route payments in batch", e);
        }
    }


    public void addBinRange(BinRange range) throws DomainException {
        Objects.requireNonNull(range, "BIN range cannot be null");

        validateBinRange(range);

        try {
            repository.addRange(range);
            invalidateCache();

        } catch (OverlappingRangeException e) {
            throw new BinRangeConflictException(
                    String.format("BIN range %s conflicts with existing range %s",
                            range, e.getExistingRange()), e);
        } catch (InfrastructureException e) {
            throw new BinManagementException("Failed to add BIN range: " + range, e);
        }
    }


    public List<BinRange> findRangesByBank(String bankName) throws DomainException {
        Objects.requireNonNull(bankName, "Bank name cannot be null");

        if (bankName.trim().isEmpty()) {
            throw new IllegalArgumentException("Bank name cannot be empty");
        }

        try {
            ensureCacheValid();
            return bankCache.getOrDefault(bankName, Collections.emptyList());

        } catch (InfrastructureException e) {
            throw new BinQueryException("Failed to find ranges for bank: " + bankName, e);
        }
    }


    public List<BinRange> findRangesByCountry(String country) throws DomainException {
        Objects.requireNonNull(country, "Country cannot be null");

        if (country.trim().isEmpty()) {
            throw new IllegalArgumentException("Country cannot be empty");
        }

        try {
            ensureCacheValid();
            return countryCache.getOrDefault(country, Collections.emptyList());

        } catch (InfrastructureException e) {
            throw new BinQueryException("Failed to find ranges for country: " + country, e);
        }
    }


    public void clearCache() {
        invalidateCache();
    }


    private void validateBinRange(BinRange range) throws DomainException {
        if (range.getStartBin() < 100000L) {
            throw new InvalidBinRangeException("BIN start too small: " + range.getStartBin());
        }

        if (range.getEndBin() > 999999999999L) {
            throw new InvalidBinRangeException("BIN end too large: " + range.getEndBin());
        }

        if (range.getBankName().trim().isEmpty()) {
            throw new InvalidBinRangeException("Bank name cannot be empty");
        }

        if (range.getCountry().length() != 2) {
            throw new InvalidBinRangeException("Country must be 2-letter code: " + range.getCountry());
        }
    }

    private boolean isDomesticTransaction(BinRange range) {

        return "TR".equals(range.getCountry());
    }

    private RiskLevel calculateRiskLevel(BinRange range) {

        if ("US".equals(range.getCountry()) || "EU".equals(range.getCountry())) {
            return RiskLevel.LOW;
        } else if ("TR".equals(range.getCountry())) {
            return RiskLevel.MEDIUM;
        } else {
            return RiskLevel.HIGH;
        }
    }

    private void ensureCacheValid() throws InfrastructureException {
        if (!cacheValid) {
            refreshCache();
        }
    }

    private synchronized void refreshCache() throws InfrastructureException {
        if (cacheValid) return;

        List<BinRange> allRanges = repository.findAll();


        Map<String, List<BinRange>> newBankCache = allRanges.stream()
                .collect(Collectors.groupingBy(BinRange::getBankName));


        Map<String, List<BinRange>> newCountryCache = allRanges.stream()
                .collect(Collectors.groupingBy(BinRange::getCountry));


        bankCache.clear();
        bankCache.putAll(newBankCache);
        countryCache.clear();
        countryCache.putAll(newCountryCache);

        cacheValid = true;
    }

    private void invalidateCache() {
        cacheValid = false;
        bankCache.clear();
        countryCache.clear();
    }

    private void addRangeSkipConflicts(BinRange range, List<BinRange> successful, List<BinRangeConflict> conflicts) {
        try {
            repository.addRange(range);
            successful.add(range);
        } catch (OverlappingRangeException e) {
            conflicts.add(new BinRangeConflict(range, e.getExistingRange(), "Skipped due to overlap"));
        }
    }

    private void addRangeMergeConflicts(BinRange range, List<BinRange> successful, List<BinRangeConflict> conflicts) {

        try {
            repository.addRange(range);
            successful.add(range);
        } catch (OverlappingRangeException e) {
            conflicts.add(new BinRangeConflict(range, e.getExistingRange(), "Merge not implemented"));
        }
    }

    private void logMissingBin(long bin) {

        System.err.println("Warning: No BIN range found for BIN: " + bin);
    }

    private void validateRangeConsistency(List<BinRange> ranges, ValidationReport.Builder builder) {


    }

    private void validateBankConsistency(List<BinRange> ranges, ValidationReport.Builder builder) {


    }

    private void validateCountryConsistency(List<BinRange> ranges, ValidationReport.Builder builder) {


    }

    private BinRange findLargestRange(List<BinRange> ranges) {
        return ranges.stream()
                .max(Comparator.comparing(BinRange::getRangeSize))
                .orElse(null);
    }

    private BinRange findSmallestRange(List<BinRange> ranges) {
        return ranges.stream()
                .min(Comparator.comparing(BinRange::getRangeSize))
                .orElse(null);
    }
}