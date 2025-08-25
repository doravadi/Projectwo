import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


public final class FraudDetectionService {


    private final BloomFilter transactionBloomFilter;
    private final LocationTracker locationTracker;
    private final Map<String, RiskWindow> cardRiskWindows;


    private final Map<String, FraudAlert> alertHistory;
    private final AtomicLong alertCounter;


    private final FraudDetectionConfig config;


    private final AtomicLong totalTransactionsProcessed;
    private final AtomicLong totalAlertsGenerated;
    private final Map<FraudAlert.AlertSeverity, AtomicLong> alertCountsBySeverity;

    public FraudDetectionService(FraudDetectionConfig config) {
        this.config = Objects.requireNonNull(config, "Config cannot be null");


        this.transactionBloomFilter = BloomFilter.createForFraudDetection();
        this.locationTracker = LocationTracker.createDefault();
        this.cardRiskWindows = new ConcurrentHashMap<>();


        this.alertHistory = new ConcurrentHashMap<>();
        this.alertCounter = new AtomicLong(1);


        this.totalTransactionsProcessed = new AtomicLong(0);
        this.totalAlertsGenerated = new AtomicLong(0);
        this.alertCountsBySeverity = new ConcurrentHashMap<>();

        for (FraudAlert.AlertSeverity severity : FraudAlert.AlertSeverity.values()) {
            alertCountsBySeverity.put(severity, new AtomicLong(0));
        }
    }

    public static FraudDetectionService createDefault() {
        return new FraudDetectionService(FraudDetectionConfig.createDefault());
    }

    public static FraudDetectionService createHighSecurity() {
        return new FraudDetectionService(FraudDetectionConfig.createHighSecurity());
    }


    public FraudAnalysisResult analyzeTransaction(String transactionId, String cardNumber,
                                                  BigDecimal amount, String merchantName,
                                                  String merchantCity, String merchantCountry,
                                                  double latitude, double longitude,
                                                  LocalDateTime transactionTime,
                                                  TransactionRisk.TransactionType transactionType) {

        Objects.requireNonNull(transactionId, "Transaction ID cannot be null");
        Objects.requireNonNull(cardNumber, "Card number cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");

        totalTransactionsProcessed.incrementAndGet();

        try {

            String transactionKey = createTransactionKey(cardNumber, merchantName, amount, transactionTime);
            boolean possibleDuplicate = transactionBloomFilter.mightContain(transactionKey);


            String location = merchantCity + ", " + merchantCountry;
            LocationTracker.LocationAnalysis locationAnalysis = locationTracker.recordLocation(
                    transactionId, cardNumber, latitude, longitude, merchantCity,
                    merchantCountry, transactionTime);


            RiskWindow cardWindow = getOrCreateRiskWindow(cardNumber);
            RiskWindow.WindowAnalysis windowAnalysis = cardWindow.addTransaction(
                    transactionId, cardNumber, amount, merchantName, location, transactionTime);


            TransactionRisk transactionRisk = assessTransactionRisk(
                    transactionId, cardNumber, amount, merchantName, location,
                    transactionTime, transactionType, possibleDuplicate,
                    locationAnalysis, windowAnalysis);


            FraudAlert fraudAlert = null;
            if (shouldGenerateAlert(transactionRisk, locationAnalysis, windowAnalysis, possibleDuplicate)) {
                fraudAlert = generateFraudAlert(transactionRisk, locationAnalysis,
                        windowAnalysis, possibleDuplicate);


                alertHistory.put(fraudAlert.getAlertId(), fraudAlert);
                totalAlertsGenerated.incrementAndGet();
                alertCountsBySeverity.get(fraudAlert.getSeverity()).incrementAndGet();
            }


            transactionBloomFilter.add(transactionKey);

            return new FraudAnalysisResult(transactionRisk, locationAnalysis, windowAnalysis,
                    fraudAlert, possibleDuplicate, getRecommendation(fraudAlert));

        } catch (Exception e) {

            System.err.println("Error analyzing transaction " + transactionId + ": " + e.getMessage());
            return createErrorResult(transactionId, cardNumber, amount);
        }
    }


    public List<FraudAnalysisResult> analyzeTransactionBatch(List<TransactionData> transactions) {
        Objects.requireNonNull(transactions, "Transactions cannot be null");

        List<FraudAnalysisResult> results = new ArrayList<>();

        for (TransactionData txn : transactions) {
            try {
                FraudAnalysisResult result = analyzeTransaction(
                        txn.getTransactionId(), txn.getCardNumber(), txn.getAmount(),
                        txn.getMerchantName(), txn.getMerchantCity(), txn.getMerchantCountry(),
                        txn.getLatitude(), txn.getLongitude(), txn.getTransactionTime(),
                        txn.getTransactionType());
                results.add(result);
            } catch (Exception e) {
                System.err.println("Error in batch analysis for transaction " +
                        txn.getTransactionId() + ": " + e.getMessage());

            }
        }

        return results;
    }


    public List<FraudAlert> getRecentAlerts(int maxCount) {
        return alertHistory.values().stream()
                .sorted(Comparator.comparing(FraudAlert::getAlertTime).reversed())
                .limit(maxCount)
                .toList();
    }


    public List<FraudAlert> getCardAlerts(String cardNumber, int maxCount) {
        Objects.requireNonNull(cardNumber, "Card number cannot be null");

        return alertHistory.values().stream()
                .filter(alert -> alert.getCardNumber().equals(cardNumber))
                .sorted(Comparator.comparing(FraudAlert::getAlertTime).reversed())
                .limit(maxCount)
                .toList();
    }


    public List<FraudAlert> getHighSeverityAlerts() {
        return alertHistory.values().stream()
                .filter(alert -> alert.getSeverity() == FraudAlert.AlertSeverity.HIGH ||
                        alert.getSeverity() == FraudAlert.AlertSeverity.CRITICAL)
                .sorted(Comparator.comparing(FraudAlert::getCompositeRiskScore).reversed())
                .toList();
    }


    public FraudDetectionStatistics getStatistics() {
        Map<FraudAlert.AlertSeverity, Long> alertCounts = new HashMap<>();
        for (Map.Entry<FraudAlert.AlertSeverity, AtomicLong> entry : alertCountsBySeverity.entrySet()) {
            alertCounts.put(entry.getKey(), entry.getValue().get());
        }

        BloomFilter.BloomFilterStatistics bloomStats = transactionBloomFilter.getStatistics();
        LocationTracker.TrackerStatistics locationStats = locationTracker.getStatistics();

        return new FraudDetectionStatistics(
                totalTransactionsProcessed.get(),
                totalAlertsGenerated.get(),
                alertCounts,
                cardRiskWindows.size(),
                bloomStats,
                locationStats
        );
    }


    public void updateConfig(FraudDetectionConfig newConfig) {
        Objects.requireNonNull(newConfig, "Config cannot be null");


        System.err.println("Warning: Config update requires service restart for thread safety");
    }


    public void clearAllData() {
        transactionBloomFilter.clear();
        cardRiskWindows.clear();
        alertHistory.clear();
        totalTransactionsProcessed.set(0);
        totalAlertsGenerated.set(0);

        for (AtomicLong counter : alertCountsBySeverity.values()) {
            counter.set(0);
        }
    }


    private String createTransactionKey(String cardNumber, String merchantName,
                                        BigDecimal amount, LocalDateTime timestamp) {

        String timeKey = timestamp.withSecond(0).withNano(0).toString();
        return String.format("%s_%s_%.2f_%s", cardNumber, merchantName, amount.doubleValue(), timeKey);
    }

    private RiskWindow getOrCreateRiskWindow(String cardNumber) {
        return cardRiskWindows.computeIfAbsent(cardNumber, k -> RiskWindow.createVelocityWindow());
    }

    private TransactionRisk assessTransactionRisk(String transactionId, String cardNumber,
                                                  BigDecimal amount, String merchantName, String location,
                                                  LocalDateTime transactionTime, TransactionRisk.TransactionType type,
                                                  boolean possibleDuplicate,
                                                  LocationTracker.LocationAnalysis locationAnalysis,
                                                  RiskWindow.WindowAnalysis windowAnalysis) {


        EnumSet<TransactionRisk.RiskFactor> riskFactors = EnumSet.noneOf(TransactionRisk.RiskFactor.class);


        if (possibleDuplicate) {
            riskFactors.add(TransactionRisk.RiskFactor.DUPLICATE_TRANSACTION);
        }


        if (locationAnalysis.hasSuspiciousActivity()) {
            if (locationAnalysis.getAnomalies().contains(LocationTracker.LocationAnomaly.IMPOSSIBLE_SPEED)) {
                riskFactors.add(TransactionRisk.RiskFactor.LOCATION_JUMP);
            }
            if (locationAnalysis.getAnomalies().contains(LocationTracker.LocationAnomaly.RAPID_CITY_CHANGES)) {
                riskFactors.add(TransactionRisk.RiskFactor.VELOCITY_ANOMALY);
            }
            if (locationAnalysis.getAnomalies().contains(LocationTracker.LocationAnomaly.INTERNATIONAL_TRAVEL)) {
                riskFactors.add(TransactionRisk.RiskFactor.INTERNATIONAL);
            }
        }


        if (windowAnalysis.hasRiskPatterns()) {
            if (windowAnalysis.getRiskPattern().hasPattern(RiskWindow.RiskPattern.PatternType.HIGH_VELOCITY)) {
                riskFactors.add(TransactionRisk.RiskFactor.HIGH_FREQUENCY);
            }
            if (windowAnalysis.getRiskPattern().hasPattern(RiskWindow.RiskPattern.PatternType.NIGHT_ACTIVITY)) {
                riskFactors.add(TransactionRisk.RiskFactor.NIGHT_ACTIVITY);
            }
        }


        if (amount.compareTo(config.getHighAmountThreshold()) > 0) {
            riskFactors.add(TransactionRisk.RiskFactor.AMOUNT_ANOMALY);
        }


        if (transactionTime.getHour() < 6 || transactionTime.getHour() > 22) {
            riskFactors.add(TransactionRisk.RiskFactor.TIME_ANOMALY);
        }

        if (transactionTime.getDayOfWeek().getValue() >= 6) {
            riskFactors.add(TransactionRisk.RiskFactor.WEEKEND_ACTIVITY);
        }


        String riskReason = buildRiskReason(riskFactors, locationAnalysis, windowAnalysis, possibleDuplicate);

        return TransactionRisk.suspicious(transactionId, cardNumber, amount, merchantName,
                location.split(",")[0], location.split(",")[1].trim(),
                transactionTime, type, riskFactors, riskReason);
    }

    private boolean shouldGenerateAlert(TransactionRisk transactionRisk,
                                        LocationTracker.LocationAnalysis locationAnalysis,
                                        RiskWindow.WindowAnalysis windowAnalysis,
                                        boolean possibleDuplicate) {


        if (transactionRisk.getRiskScore() >= config.getCriticalRiskThreshold()) {
            return true;
        }


        if (locationAnalysis.isHighRisk()) {
            return true;
        }


        if (windowAnalysis.isHighRisk()) {
            return true;
        }


        if (possibleDuplicate && transactionRisk.getRiskScore() >= config.getDuplicateAlertThreshold()) {
            return true;
        }


        if (transactionRisk.getRiskScore() >= config.getModerateRiskThreshold() &&
                (locationAnalysis.hasSuspiciousActivity() || windowAnalysis.hasRiskPatterns())) {
            return true;
        }

        return false;
    }

    private FraudAlert generateFraudAlert(TransactionRisk transactionRisk,
                                          LocationTracker.LocationAnalysis locationAnalysis,
                                          RiskWindow.WindowAnalysis windowAnalysis,
                                          boolean possibleDuplicate) {

        FraudAlert alert = FraudAlert.createFromRiskAnalysis(transactionRisk, locationAnalysis);


        Map<String, Object> metadata = new HashMap<>(alert.getMetadata());
        metadata.put("windowTransactionCount", windowAnalysis.getTransactionCount());
        metadata.put("windowTotalAmount", windowAnalysis.getTotalAmount());
        metadata.put("windowRiskScore", windowAnalysis.getRiskPattern().getRiskScore());
        metadata.put("possibleDuplicate", possibleDuplicate);
        metadata.put("bloomFilterUtilization", transactionBloomFilter.getMemoryUtilization());

        return alert;
    }

    private ProcessingRecommendation getRecommendation(FraudAlert alert) {
        if (alert == null) {
            return ProcessingRecommendation.ALLOW;
        }

        return switch (alert.getSeverity()) {
            case CRITICAL -> ProcessingRecommendation.BLOCK;
            case HIGH -> ProcessingRecommendation.REVIEW;
            case MEDIUM -> ProcessingRecommendation.MONITOR;
            case LOW -> ProcessingRecommendation.ALLOW;
        };
    }

    private String buildRiskReason(EnumSet<TransactionRisk.RiskFactor> riskFactors,
                                   LocationTracker.LocationAnalysis locationAnalysis,
                                   RiskWindow.WindowAnalysis windowAnalysis,
                                   boolean possibleDuplicate) {

        StringBuilder reason = new StringBuilder();

        if (possibleDuplicate) {
            reason.append("Possible duplicate transaction. ");
        }

        if (locationAnalysis.hasSuspiciousActivity()) {
            reason.append("Geographic anomalies detected. ");
        }

        if (windowAnalysis.hasRiskPatterns()) {
            reason.append("Suspicious transaction patterns. ");
        }

        if (riskFactors.size() > 3) {
            reason.append("Multiple risk factors present. ");
        }

        if (reason.length() == 0) {
            reason.append("Standard risk assessment completed.");
        }

        return reason.toString().trim();
    }

    private FraudAnalysisResult createErrorResult(String transactionId, String cardNumber, BigDecimal amount) {

        TransactionRisk errorRisk = TransactionRisk.lowRisk(transactionId, cardNumber, amount,
                "UNKNOWN", "UNKNOWN", "UNKNOWN",
                LocalDateTime.now(), TransactionRisk.TransactionType.PURCHASE);

        return new FraudAnalysisResult(errorRisk, null, null, null, false, ProcessingRecommendation.REVIEW);
    }


    public enum ProcessingRecommendation {
        ALLOW, MONITOR, REVIEW, BLOCK
    }

    public static final class TransactionData {
        private final String transactionId;
        private final String cardNumber;
        private final BigDecimal amount;
        private final String merchantName;
        private final String merchantCity;
        private final String merchantCountry;
        private final double latitude;
        private final double longitude;
        private final LocalDateTime transactionTime;
        private final TransactionRisk.TransactionType transactionType;

        public TransactionData(String transactionId, String cardNumber, BigDecimal amount,
                               String merchantName, String merchantCity, String merchantCountry,
                               double latitude, double longitude, LocalDateTime transactionTime,
                               TransactionRisk.TransactionType transactionType) {
            this.transactionId = transactionId;
            this.cardNumber = cardNumber;
            this.amount = amount;
            this.merchantName = merchantName;
            this.merchantCity = merchantCity;
            this.merchantCountry = merchantCountry;
            this.latitude = latitude;
            this.longitude = longitude;
            this.transactionTime = transactionTime;
            this.transactionType = transactionType;
        }


        public String getTransactionId() {
            return transactionId;
        }

        public String getCardNumber() {
            return cardNumber;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public String getMerchantName() {
            return merchantName;
        }

        public String getMerchantCity() {
            return merchantCity;
        }

        public String getMerchantCountry() {
            return merchantCountry;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public LocalDateTime getTransactionTime() {
            return transactionTime;
        }

        public TransactionRisk.TransactionType getTransactionType() {
            return transactionType;
        }
    }

    public static final class FraudAnalysisResult {
        private final TransactionRisk transactionRisk;
        private final LocationTracker.LocationAnalysis locationAnalysis;
        private final RiskWindow.WindowAnalysis windowAnalysis;
        private final FraudAlert fraudAlert;
        private final boolean possibleDuplicate;
        private final ProcessingRecommendation recommendation;

        public FraudAnalysisResult(TransactionRisk transactionRisk,
                                   LocationTracker.LocationAnalysis locationAnalysis,
                                   RiskWindow.WindowAnalysis windowAnalysis,
                                   FraudAlert fraudAlert, boolean possibleDuplicate,
                                   ProcessingRecommendation recommendation) {
            this.transactionRisk = transactionRisk;
            this.locationAnalysis = locationAnalysis;
            this.windowAnalysis = windowAnalysis;
            this.fraudAlert = fraudAlert;
            this.possibleDuplicate = possibleDuplicate;
            this.recommendation = recommendation;
        }


        public TransactionRisk getTransactionRisk() {
            return transactionRisk;
        }

        public LocationTracker.LocationAnalysis getLocationAnalysis() {
            return locationAnalysis;
        }

        public RiskWindow.WindowAnalysis getWindowAnalysis() {
            return windowAnalysis;
        }

        public FraudAlert getFraudAlert() {
            return fraudAlert;
        }

        public boolean isPossibleDuplicate() {
            return possibleDuplicate;
        }

        public ProcessingRecommendation getRecommendation() {
            return recommendation;
        }

        public boolean hasAlert() {
            return fraudAlert != null;
        }

        public boolean shouldBlock() {
            return recommendation == ProcessingRecommendation.BLOCK;
        }

        public boolean shouldReview() {
            return recommendation == ProcessingRecommendation.REVIEW;
        }

        @Override
        public String toString() {
            return String.format("FraudAnalysisResult{risk=%d, alert=%s, recommendation=%s}",
                    transactionRisk.getRiskScore(),
                    fraudAlert != null ? fraudAlert.getSeverity() : "NONE",
                    recommendation);
        }
    }

    public static final class FraudDetectionStatistics {
        private final long totalTransactionsProcessed;
        private final long totalAlertsGenerated;
        private final Map<FraudAlert.AlertSeverity, Long> alertCounts;
        private final int activeCardWindows;
        private final BloomFilter.BloomFilterStatistics bloomFilterStats;
        private final LocationTracker.TrackerStatistics locationStats;

        public FraudDetectionStatistics(long totalTransactionsProcessed, long totalAlertsGenerated,
                                        Map<FraudAlert.AlertSeverity, Long> alertCounts, int activeCardWindows,
                                        BloomFilter.BloomFilterStatistics bloomFilterStats,
                                        LocationTracker.TrackerStatistics locationStats) {
            this.totalTransactionsProcessed = totalTransactionsProcessed;
            this.totalAlertsGenerated = totalAlertsGenerated;
            this.alertCounts = new HashMap<>(alertCounts);
            this.activeCardWindows = activeCardWindows;
            this.bloomFilterStats = bloomFilterStats;
            this.locationStats = locationStats;
        }


        public long getTotalTransactionsProcessed() {
            return totalTransactionsProcessed;
        }

        public long getTotalAlertsGenerated() {
            return totalAlertsGenerated;
        }

        public Map<FraudAlert.AlertSeverity, Long> getAlertCounts() {
            return new HashMap<>(alertCounts);
        }

        public int getActiveCardWindows() {
            return activeCardWindows;
        }

        public BloomFilter.BloomFilterStatistics getBloomFilterStats() {
            return bloomFilterStats;
        }

        public LocationTracker.TrackerStatistics getLocationStats() {
            return locationStats;
        }

        public double getAlertRate() {
            return totalTransactionsProcessed > 0 ?
                    (double) totalAlertsGenerated / totalTransactionsProcessed * 100.0 : 0.0;
        }

        @Override
        public String toString() {
            return String.format("FraudStats{transactions=%d, alerts=%d (%.2f%%), cards=%d}",
                    totalTransactionsProcessed, totalAlertsGenerated,
                    getAlertRate(), activeCardWindows);
        }
    }

    @Override
    public String toString() {
        return String.format("FraudDetectionService{transactions=%d, alerts=%d, cards=%d}",
                totalTransactionsProcessed.get(), totalAlertsGenerated.get(),
                cardRiskWindows.size());
    }
}