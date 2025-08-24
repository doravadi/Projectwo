import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;


public final class FraudAlert implements Comparable<FraudAlert> {

    private final String alertId;
    private final String transactionId;
    private final String cardNumber;
    private final LocalDateTime alertTime;
    private final AlertSeverity severity;
    private final AlertStatus status;

    
    private final BigDecimal transactionAmount;
    private final String merchantName;
    private final String location;
    private final Set<AlertReason> reasons;
    private final int compositeRiskScore;

    
    private final Set<RecommendedAction> recommendedActions;
    private final String investigationNotes;
    private final Priority investigationPriority;

    
    private final Map<String, Object> metadata;

    public enum AlertSeverity {
        LOW(1, "Düşük", "Monitoring only"),
        MEDIUM(2, "Orta", "Review recommended"),
        HIGH(3, "Yüksek", "Immediate review required"),
        CRITICAL(4, "Kritik", "Block and investigate");

        private final int level;
        private final String displayName;
        private final String description;

        AlertSeverity(int level, String displayName, String description) {
            this.level = level;
            this.displayName = displayName;
            this.description = description;
        }

        public int getLevel() { return level; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public enum AlertStatus {
        NEW, INVESTIGATING, RESOLVED_FRAUD, RESOLVED_LEGITIMATE, FALSE_POSITIVE
    }

    public enum AlertReason {
        VELOCITY_ANOMALY("Hızlı ardışık işlemler"),
        LOCATION_IMPOSSIBLE_SPEED("İmkansız seyahat hızı"),
        LOCATION_RAPID_CITIES("Hızlı şehir değişimi"),
        AMOUNT_ANOMALY("Alışılmadık tutar"),
        DUPLICATE_TRANSACTION("Çift işlem tespit"),
        BLOOM_FILTER_HIT("Daha önce görülen işlem pattern"),
        TIME_ANOMALY("Alışılmadık işlem saati"),
        INTERNATIONAL_TRAVEL("Ani uluslararası işlem"),
        MERCHANT_RISK("Riskli iş yeri"),
        HIGH_FREQUENCY("Yüksek frekanslı işlemler"),
        PATTERN_MISMATCH("Spending pattern uyumsuzluğu");

        private final String description;

        AlertReason(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    public enum RecommendedAction {
        MONITOR("Monitor future transactions"),
        REVIEW_MANUAL("Manual review required"),
        CONTACT_CUSTOMER("Contact customer for verification"),
        BLOCK_CARD("Block card immediately"),
        DECLINE_TRANSACTION("Decline current transaction"),
        FLAG_MERCHANT("Flag merchant for review"),
        INVESTIGATE_PATTERN("Investigate spending pattern"),
        UPDATE_RISK_PROFILE("Update customer risk profile");

        private final String description;

        RecommendedAction(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    public enum Priority {
        LOW(1), MEDIUM(2), HIGH(3), URGENT(4);

        private final int level;
        Priority(int level) { this.level = level; }
        public int getLevel() { return level; }
    }

    public FraudAlert(String alertId, String transactionId, String cardNumber,
                      AlertSeverity severity, BigDecimal transactionAmount,
                      String merchantName, String location, Set<AlertReason> reasons,
                      int compositeRiskScore, Set<RecommendedAction> recommendedActions,
                      String investigationNotes, Map<String, Object> metadata) {

        this.alertId = Objects.requireNonNull(alertId, "Alert ID cannot be null");
        this.transactionId = Objects.requireNonNull(transactionId, "Transaction ID cannot be null");
        this.cardNumber = Objects.requireNonNull(cardNumber, "Card number cannot be null");
        this.alertTime = LocalDateTime.now();
        this.severity = Objects.requireNonNull(severity, "Severity cannot be null");
        this.status = AlertStatus.NEW;

        this.transactionAmount = Objects.requireNonNull(transactionAmount, "Transaction amount cannot be null");
        this.merchantName = Objects.requireNonNull(merchantName, "Merchant name cannot be null");
        this.location = Objects.requireNonNull(location, "Location cannot be null");
        this.reasons = EnumSet.copyOf(Objects.requireNonNull(reasons, "Reasons cannot be null"));
        this.compositeRiskScore = Math.max(0, Math.min(100, compositeRiskScore));

        this.recommendedActions = EnumSet.copyOf(Objects.requireNonNull(recommendedActions, "Recommended actions cannot be null"));
        this.investigationNotes = Objects.requireNonNull(investigationNotes, "Investigation notes cannot be null");
        this.investigationPriority = calculatePriority();
        this.metadata = new HashMap<>(Objects.requireNonNull(metadata, "Metadata cannot be null"));
    }

    
    public static FraudAlert createLowRiskAlert(String transactionId, String cardNumber,
                                                BigDecimal amount, String merchant, String location) {
        String alertId = "ALERT_" + System.currentTimeMillis() + "_" + Math.abs(transactionId.hashCode());

        return new FraudAlert(alertId, transactionId, cardNumber, AlertSeverity.LOW,
                amount, merchant, location, EnumSet.of(AlertReason.PATTERN_MISMATCH),
                25, EnumSet.of(RecommendedAction.MONITOR),
                "Low risk transaction - monitoring", new HashMap<>());
    }

    public static FraudAlert createFromRiskAnalysis(TransactionRisk transactionRisk,
                                                    LocationTracker.LocationAnalysis locationAnalysis) {

        String alertId = "ALERT_" + System.currentTimeMillis() + "_" +
                Math.abs(transactionRisk.getTransactionId().hashCode());

        
        Set<AlertReason> combinedReasons = new HashSet<>();
        Set<RecommendedAction> combinedActions = new HashSet<>();

        
        for (TransactionRisk.RiskFactor factor : transactionRisk.getRiskFactors()) {
            switch (factor) {
                case VELOCITY_ANOMALY -> combinedReasons.add(AlertReason.VELOCITY_ANOMALY);
                case LOCATION_JUMP -> combinedReasons.add(AlertReason.LOCATION_IMPOSSIBLE_SPEED);
                case AMOUNT_ANOMALY -> combinedReasons.add(AlertReason.AMOUNT_ANOMALY);
                case DUPLICATE_TRANSACTION -> combinedReasons.add(AlertReason.DUPLICATE_TRANSACTION);
                case TIME_ANOMALY -> combinedReasons.add(AlertReason.TIME_ANOMALY);
                case MERCHANT_RISK -> combinedReasons.add(AlertReason.MERCHANT_RISK);
                case INTERNATIONAL -> combinedReasons.add(AlertReason.INTERNATIONAL_TRAVEL);
                case HIGH_FREQUENCY -> combinedReasons.add(AlertReason.HIGH_FREQUENCY);
            }
        }

        
        if (locationAnalysis != null) {
            for (LocationTracker.LocationAnomaly anomaly : locationAnalysis.getAnomalies()) {
                switch (anomaly) {
                    case IMPOSSIBLE_SPEED -> combinedReasons.add(AlertReason.LOCATION_IMPOSSIBLE_SPEED);
                    case RAPID_CITY_CHANGES -> combinedReasons.add(AlertReason.LOCATION_RAPID_CITIES);
                    case INTERNATIONAL_TRAVEL -> combinedReasons.add(AlertReason.INTERNATIONAL_TRAVEL);
                    case REPEATED_LOCATION -> combinedReasons.add(AlertReason.PATTERN_MISMATCH);
                }
            }
        }

        
        int transactionScore = transactionRisk.getRiskScore();
        int locationScore = locationAnalysis != null ? locationAnalysis.getRiskScore() : 0;
        int compositeScore = Math.min(100, (int)(transactionScore * 0.6 + locationScore * 0.4));

        
        AlertSeverity severity = determineSeverity(compositeScore, combinedReasons);

        
        combinedActions.addAll(determineActions(severity, combinedReasons, transactionRisk));

        
        String notes = buildInvestigationNotes(transactionRisk, locationAnalysis, compositeScore);

        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("transactionRiskScore", transactionScore);
        metadata.put("locationRiskScore", locationScore);
        metadata.put("hasLocationAnalysis", locationAnalysis != null);
        if (locationAnalysis != null) {
            metadata.put("distanceKm", locationAnalysis.getDistanceKm());
            metadata.put("speedKmh", locationAnalysis.getSpeedKmh());
        }

        return new FraudAlert(alertId, transactionRisk.getTransactionId(),
                transactionRisk.getCardNumber(), severity,
                transactionRisk.getAmount(), transactionRisk.getMerchantName(),
                transactionRisk.getLocation(), combinedReasons, compositeScore,
                combinedActions, notes, metadata);
    }

    
    public String getAlertId() { return alertId; }
    public String getTransactionId() { return transactionId; }
    public String getCardNumber() { return cardNumber; }
    public LocalDateTime getAlertTime() { return alertTime; }
    public AlertSeverity getSeverity() { return severity; }
    public AlertStatus getStatus() { return status; }
    public BigDecimal getTransactionAmount() { return transactionAmount; }
    public String getMerchantName() { return merchantName; }
    public String getLocation() { return location; }
    public Set<AlertReason> getReasons() { return EnumSet.copyOf(reasons); }
    public int getCompositeRiskScore() { return compositeRiskScore; }
    public Set<RecommendedAction> getRecommendedActions() { return EnumSet.copyOf(recommendedActions); }
    public String getInvestigationNotes() { return investigationNotes; }
    public Priority getInvestigationPriority() { return investigationPriority; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }

    
    public boolean requiresImmediateAction() {
        return severity == AlertSeverity.CRITICAL ||
                recommendedActions.contains(RecommendedAction.BLOCK_CARD);
    }

    public boolean shouldBlockTransaction() {
        return recommendedActions.contains(RecommendedAction.DECLINE_TRANSACTION) ||
                recommendedActions.contains(RecommendedAction.BLOCK_CARD);
    }

    public boolean requiresCustomerContact() {
        return recommendedActions.contains(RecommendedAction.CONTACT_CUSTOMER);
    }

    public String getMaskedCardNumber() {
        if (cardNumber.length() < 4) return cardNumber;
        return "****" + cardNumber.substring(cardNumber.length() - 4);
    }

    public int getAgeMinutes() {
        return (int) java.time.Duration.between(alertTime, LocalDateTime.now()).toMinutes();
    }

    public boolean isStale(int maxAgeMinutes) {
        return getAgeMinutes() > maxAgeMinutes;
    }

    
    private static AlertSeverity determineSeverity(int compositeScore, Set<AlertReason> reasons) {
        if (compositeScore >= 80) return AlertSeverity.CRITICAL;
        if (compositeScore >= 60) return AlertSeverity.HIGH;
        if (compositeScore >= 40) return AlertSeverity.MEDIUM;
        return AlertSeverity.LOW;
    }

    private static Set<RecommendedAction> determineActions(AlertSeverity severity,
                                                           Set<AlertReason> reasons,
                                                           TransactionRisk transactionRisk) {
        Set<RecommendedAction> actions = new HashSet<>();

        switch (severity) {
            case CRITICAL -> {
                actions.add(RecommendedAction.BLOCK_CARD);
                actions.add(RecommendedAction.CONTACT_CUSTOMER);
                actions.add(RecommendedAction.INVESTIGATE_PATTERN);
            }
            case HIGH -> {
                actions.add(RecommendedAction.DECLINE_TRANSACTION);
                actions.add(RecommendedAction.REVIEW_MANUAL);
                actions.add(RecommendedAction.CONTACT_CUSTOMER);
            }
            case MEDIUM -> {
                actions.add(RecommendedAction.REVIEW_MANUAL);
                actions.add(RecommendedAction.UPDATE_RISK_PROFILE);
            }
            case LOW -> {
                actions.add(RecommendedAction.MONITOR);
            }
        }

        
        if (reasons.contains(AlertReason.MERCHANT_RISK)) {
            actions.add(RecommendedAction.FLAG_MERCHANT);
        }

        if (reasons.contains(AlertReason.DUPLICATE_TRANSACTION)) {
            actions.add(RecommendedAction.DECLINE_TRANSACTION);
        }

        return actions;
    }

    private static String buildInvestigationNotes(TransactionRisk transactionRisk,
                                                  LocationTracker.LocationAnalysis locationAnalysis,
                                                  int compositeScore) {
        StringBuilder notes = new StringBuilder();
        notes.append("Composite Risk Score: ").append(compositeScore).append(". ");
        notes.append("Transaction Risk: ").append(transactionRisk.getRiskScore()).append(" (")
                .append(transactionRisk.getRiskLevel()).append("). ");

        if (locationAnalysis != null) {
            notes.append("Location Risk: ").append(locationAnalysis.getRiskScore()).append(". ");
            if (locationAnalysis.getDistanceKm() > 0) {
                notes.append(String.format("Distance: %.1fkm, Speed: %.1fkm/h. ",
                        locationAnalysis.getDistanceKm(), locationAnalysis.getSpeedKmh()));
            }
        }

        if (!transactionRisk.getRiskFactors().isEmpty()) {
            notes.append("Risk Factors: ").append(transactionRisk.getRiskFactors().size()).append(". ");
        }

        return notes.toString();
    }

    private Priority calculatePriority() {
        return switch (severity) {
            case CRITICAL -> Priority.URGENT;
            case HIGH -> Priority.HIGH;
            case MEDIUM -> Priority.MEDIUM;
            case LOW -> Priority.LOW;
        };
    }

    @Override
    public int compareTo(FraudAlert other) {
        
        int severityCompare = Integer.compare(other.severity.getLevel(), this.severity.getLevel());
        if (severityCompare != 0) return severityCompare;

        
        int scoreCompare = Integer.compare(other.compositeRiskScore, this.compositeRiskScore);
        if (scoreCompare != 0) return scoreCompare;

        
        return other.alertTime.compareTo(this.alertTime);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        FraudAlert that = (FraudAlert) obj;
        return Objects.equals(alertId, that.alertId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alertId);
    }

    @Override
    public String toString() {
        return String.format("FraudAlert{id=%s, severity=%s, score=%d, reasons=%d, card=%s}",
                alertId.substring(alertId.length() - 8),
                severity, compositeRiskScore, reasons.size(), getMaskedCardNumber());
    }
}