import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Payment Allocation Service - Ana API sınıfı
 *
 * Farklı tahsis stratejilerini yönetir, karşılaştırır ve optimize eder.
 * Thread-safe tasarım ile çoklu hesap desteği.
 */
public final class PaymentAllocationService {

    private final Map<String, List<DebtBucket>> accountBuckets;
    private final Map<String, PaymentAllocation> allocationHistory;
    private final PaymentOptimizer optimizer;
    private final AtomicLong allocationIdCounter;

    public PaymentAllocationService() {
        this.accountBuckets = new ConcurrentHashMap<>();
        this.allocationHistory = new ConcurrentHashMap<>();
        this.optimizer = PaymentOptimizer.createDefault();
        this.allocationIdCounter = new AtomicLong(1);
    }

    public PaymentAllocationService(PaymentOptimizer optimizer) {
        this.accountBuckets = new ConcurrentHashMap<>();
        this.allocationHistory = new ConcurrentHashMap<>();
        this.optimizer = Objects.requireNonNull(optimizer, "Optimizer cannot be null");
        this.allocationIdCounter = new AtomicLong(1);
    }

    /**
     * Hesap için debt bucket'ları ayarlar
     */
    public void setAccountBuckets(String accountId, List<DebtBucket> buckets) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(buckets, "Buckets cannot be null");

        // Deep copy
        List<DebtBucket> bucketCopy = new ArrayList<>(buckets);
        accountBuckets.put(accountId, bucketCopy);
    }

    /**
     * Hesaba yeni bucket ekler
     */
    public void addDebtBucket(String accountId, DebtBucket bucket) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(bucket, "Bucket cannot be null");

        accountBuckets.computeIfAbsent(accountId, k -> new ArrayList<>()).add(bucket);
    }

    /**
     * Belirli strateji ile ödeme tahsisi
     */
    public PaymentAllocation allocatePayment(String accountId, BigDecimal paymentAmount,
                                             PaymentAllocation.AllocationStrategy strategyType) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(paymentAmount, "Payment amount cannot be null");
        Objects.requireNonNull(strategyType, "Strategy type cannot be null");

        List<DebtBucket> buckets = getAccountBuckets(accountId);
        String allocationId = generateAllocationId();

        AllocationStrategy strategy = AllocationStrategy.createStrategy(strategyType);

        if (!strategy.isApplicable(buckets, paymentAmount)) {
            throw new IllegalArgumentException("Strategy " + strategyType +
                    " is not applicable for this scenario");
        }

        PaymentAllocation allocation = strategy.allocate(buckets, paymentAmount, allocationId);

        // Post-allocation validation
        validateAllocation(buckets, allocation);

        // Update bucket states
        updateBucketsAfterAllocation(accountId, allocation);

        // Store in history
        allocationHistory.put(allocationId, allocation);

        return allocation;
    }

    /**
     * Tüm stratejileri karşılaştır ve en iyi sonucu döndür
     */
    public StrategyComparison compareAllStrategies(String accountId, BigDecimal paymentAmount) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(paymentAmount, "Payment amount cannot be null");

        List<DebtBucket> buckets = getAccountBuckets(accountId);
        Map<PaymentAllocation.AllocationStrategy, PaymentAllocation> results = new EnumMap<>(PaymentAllocation.AllocationStrategy.class);
        Map<PaymentAllocation.AllocationStrategy, Exception> errors = new EnumMap<>(PaymentAllocation.AllocationStrategy.class);

        for (PaymentAllocation.AllocationStrategy strategyType : PaymentAllocation.AllocationStrategy.values()) {
            try {
                AllocationStrategy strategy = AllocationStrategy.createStrategy(strategyType);
                if (strategy.isApplicable(buckets, paymentAmount)) {
                    String testAllocationId = "TEST_" + generateAllocationId();
                    PaymentAllocation result = strategy.allocate(buckets, paymentAmount, testAllocationId);
                    results.put(strategyType, result);
                }
            } catch (Exception e) {
                errors.put(strategyType, e);
            }
        }

        return new StrategyComparison(results, errors, findBestAllocation(results));
    }

    /**
     * DP vs Bank Rule karşılaştırması (ana business case)
     */
    public AllocationComparison compareDPvsBankRule(String accountId, BigDecimal paymentAmount) {
        List<DebtBucket> buckets = getAccountBuckets(accountId);

        // Bank Rule allocation
        AllocationStrategy bankRule = AllocationStrategy.createStrategy(PaymentAllocation.AllocationStrategy.BANK_RULE);
        PaymentAllocation bankRuleResult = bankRule.allocate(buckets, paymentAmount,
                "BANK_" + generateAllocationId());

        // DP Optimal allocation
        AllocationStrategy dpOptimal = AllocationStrategy.createStrategy(PaymentAllocation.AllocationStrategy.DP_OPTIMAL);
        PaymentAllocation dpResult = dpOptimal.allocate(buckets, paymentAmount,
                "DP_" + generateAllocationId());

        return new AllocationComparison(bankRuleResult, dpResult);
    }

    /**
     * Allocation'ı uygular ve bucket state'lerini günceller
     */
    public void applyAllocation(String accountId, String allocationId) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(allocationId, "Allocation ID cannot be null");

        PaymentAllocation allocation = allocationHistory.get(allocationId);
        if (allocation == null) {
            throw new IllegalArgumentException("Allocation not found: " + allocationId);
        }

        updateBucketsAfterAllocation(accountId, allocation);
    }

    /**
     * Hesap bucket'larını döndürür
     */
    public List<DebtBucket> getAccountBuckets(String accountId) {
        List<DebtBucket> buckets = accountBuckets.get(accountId);
        if (buckets == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        return new ArrayList<>(buckets); // Defensive copy
    }

    /**
     * Allocation geçmişini döndürür
     */
    public List<PaymentAllocation> getAllocationHistory(String accountId) {
        return allocationHistory.values().stream()
                .filter(allocation -> allocation.getAllocationId().contains(accountId))
                .sorted(Comparator.comparing(PaymentAllocation::getAllocationTime).reversed())
                .toList();
    }

    /**
     * Sistem istatistikleri
     */
    public Map<String, Object> getSystemStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeAccounts", accountBuckets.size());
        stats.put("totalAllocations", allocationHistory.size());
        stats.put("totalBuckets", accountBuckets.values().stream()
                .mapToInt(List::size).sum());

        // Strategy usage statistics
        Map<PaymentAllocation.AllocationStrategy, Long> strategyUsage = new EnumMap<>(PaymentAllocation.AllocationStrategy.class);
        for (PaymentAllocation allocation : allocationHistory.values()) {
            strategyUsage.merge(allocation.getStrategy(), 1L, Long::sum);
        }
        stats.put("strategyUsage", strategyUsage);

        return stats;
    }

    // Helper methods
    private void validateAllocation(List<DebtBucket> buckets, PaymentAllocation allocation) {
        // Total amount validation
        BigDecimal totalAllocated = allocation.getTotalAllocatedAmount();
        if (totalAllocated.compareTo(allocation.getTotalPaymentAmount()) > 0) {
            throw AllocationInvariantBroken.totalMismatch(allocation.getTotalPaymentAmount(),
                    totalAllocated, allocation.getBucketAllocations());
        }

        // Bucket capacity validation
        for (Map.Entry<String, BigDecimal> entry : allocation.getBucketAllocations().entrySet()) {
            String bucketId = entry.getKey();
            BigDecimal allocatedAmount = entry.getValue();

            DebtBucket bucket = buckets.stream()
                    .filter(b -> b.getBucketId().equals(bucketId))
                    .findFirst()
                    .orElse(null);

            if (bucket == null) {
                continue; // Skip validation for unknown buckets
            }

            // Capacity check
            if (allocatedAmount.compareTo(bucket.getCurrentBalance()) > 0) {
                throw AllocationInvariantBroken.allocationOverflow(bucketId,
                        bucket.getCurrentBalance(), allocatedAmount);
            }

            // Minimum payment check (for non-zero allocations)
            if (allocatedAmount.compareTo(BigDecimal.ZERO) > 0 &&
                    allocatedAmount.compareTo(bucket.getMinimumPayment()) < 0 &&
                    allocatedAmount.compareTo(bucket.getCurrentBalance()) < 0) {

                // Allow full payment even if below minimum
                throw AllocationInvariantBroken.minimumPaymentViolation(bucketId,
                        bucket.getMinimumPayment(), allocatedAmount);
            }
        }
    }

    private void updateBucketsAfterAllocation(String accountId, PaymentAllocation allocation) {
        List<DebtBucket> currentBuckets = accountBuckets.get(accountId);
        if (currentBuckets == null) return;

        List<DebtBucket> updatedBuckets = new ArrayList<>();

        for (DebtBucket bucket : currentBuckets) {
            BigDecimal allocatedAmount = allocation.getAllocatedAmount(bucket.getBucketId());

            if (allocatedAmount.compareTo(BigDecimal.ZERO) > 0) {
                DebtBucket updatedBucket = bucket.withPayment(allocatedAmount);

                // Post-update validation
                if (updatedBucket.getCurrentBalance().compareTo(BigDecimal.ZERO) < 0) {
                    throw AllocationInvariantBroken.negativeBalance(bucket.getBucketId(),
                            BigDecimal.ZERO, updatedBucket.getCurrentBalance(), allocatedAmount);
                }

                updatedBuckets.add(updatedBucket);
            } else {
                updatedBuckets.add(bucket);
            }
        }

        accountBuckets.put(accountId, updatedBuckets);
    }

    private PaymentAllocation.AllocationStrategy findBestAllocation(
            Map<PaymentAllocation.AllocationStrategy, PaymentAllocation> results) {

        return results.entrySet().stream()
                .max(Comparator.comparing(entry -> entry.getValue().getTotalInterestSaved()))
                .map(Map.Entry::getKey)
                .orElse(PaymentAllocation.AllocationStrategy.BANK_RULE);
    }

    private String generateAllocationId() {
        return "ALLOC_" + allocationIdCounter.getAndIncrement() + "_" +
                System.currentTimeMillis();
    }

    // Nested result classes
    public static final class StrategyComparison {
        private final Map<PaymentAllocation.AllocationStrategy, PaymentAllocation> results;
        private final Map<PaymentAllocation.AllocationStrategy, Exception> errors;
        private final PaymentAllocation.AllocationStrategy bestStrategy;

        public StrategyComparison(Map<PaymentAllocation.AllocationStrategy, PaymentAllocation> results,
                                  Map<PaymentAllocation.AllocationStrategy, Exception> errors,
                                  PaymentAllocation.AllocationStrategy bestStrategy) {
            this.results = new EnumMap<>(results);
            this.errors = new EnumMap<>(errors);
            this.bestStrategy = bestStrategy;
        }

        public Map<PaymentAllocation.AllocationStrategy, PaymentAllocation> getResults() {
            return new EnumMap<>(results);
        }
        public Map<PaymentAllocation.AllocationStrategy, Exception> getErrors() {
            return new EnumMap<>(errors);
        }
        public PaymentAllocation.AllocationStrategy getBestStrategy() { return bestStrategy; }

        public PaymentAllocation getBestAllocation() {
            return results.get(bestStrategy);
        }

        @Override
        public String toString() {
            return String.format("StrategyComparison{results=%d, errors=%d, best=%s}",
                    results.size(), errors.size(), bestStrategy);
        }
    }

    public static final class AllocationComparison {
        private final PaymentAllocation bankRuleAllocation;
        private final PaymentAllocation dpOptimalAllocation;
        private final BigDecimal interestSavingsDifference;

        public AllocationComparison(PaymentAllocation bankRuleAllocation,
                                    PaymentAllocation dpOptimalAllocation) {
            this.bankRuleAllocation = bankRuleAllocation;
            this.dpOptimalAllocation = dpOptimalAllocation;
            this.interestSavingsDifference = dpOptimalAllocation.getTotalInterestSaved()
                    .subtract(bankRuleAllocation.getTotalInterestSaved());
        }

        public PaymentAllocation getBankRuleAllocation() { return bankRuleAllocation; }
        public PaymentAllocation getDpOptimalAllocation() { return dpOptimalAllocation; }
        public BigDecimal getInterestSavingsDifference() { return interestSavingsDifference; }

        public boolean isDpBetter() {
            return interestSavingsDifference.compareTo(BigDecimal.ZERO) > 0;
        }

        public double getImprovementPercentage() {
            if (bankRuleAllocation.getTotalInterestSaved().compareTo(BigDecimal.ZERO) == 0) {
                return 0.0;
            }
            return interestSavingsDifference.divide(bankRuleAllocation.getTotalInterestSaved(),
                            4, BigDecimal.ROUND_HALF_UP)
                    .multiply(new BigDecimal("100")).doubleValue();
        }

        @Override
        public String toString() {
            return String.format("Comparison{dpBetter=%s, saving=%.2f TL, improvement=%.2f%%}",
                    isDpBetter(), interestSavingsDifference.doubleValue(),
                    getImprovementPercentage());
        }
    }

    @Override
    public String toString() {
        return String.format("PaymentAllocationService{accounts=%d, allocations=%d}",
                accountBuckets.size(), allocationHistory.size());
    }
}