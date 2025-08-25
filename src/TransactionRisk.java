import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.EnumSet;
import java.util.Set;


public final class TransactionRisk implements Comparable<TransactionRisk> {

    private final String transactionId;
    private final String cardNumber;
    private final BigDecimal amount;
    private final String merchantName;
    private final String merchantCity;
    private final String merchantCountry;
    private final LocalDateTime transactionTime;
    private final TransactionType type;


    private final int riskScore;
    private final RiskLevel riskLevel;
    private final EnumSet<RiskFactor> riskFactors;
    private final String riskReason;

    public enum TransactionType {
        PURCHASE, WITHDRAWAL, REFUND, ONLINE, CONTACTLESS
    }

    public enum RiskLevel {
        LOW(0, 30, "Düşük Risk"),
        MEDIUM(31, 60, "Orta Risk"),
        HIGH(61, 80, "Yüksek Risk"),
        CRITICAL(81, 100, "Kritik Risk");

        private final int minScore;
        private final int maxScore;
        private final String description;

        RiskLevel(int minScore, int maxScore, String description) {
            this.minScore = minScore;
            this.maxScore = maxScore;
            this.description = description;
        }

        public static RiskLevel fromScore(int score) {
            for (RiskLevel level : values()) {
                if (score >= level.minScore && score <= level.maxScore) {
                    return level;
                }
            }
            return LOW;
        }

        public int getMinScore() {
            return minScore;
        }

        public int getMaxScore() {
            return maxScore;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum RiskFactor {
        VELOCITY_ANOMALY("Çok hızlı ardışık işlemler"),
        LOCATION_JUMP("Coğrafi konum atlaması"),
        AMOUNT_ANOMALY("Alışılmadık tutar"),
        TIME_ANOMALY("Alışılmadık saat"),
        MERCHANT_RISK("Riskli iş yeri"),
        DUPLICATE_TRANSACTION("Tekrarlanan işlem"),
        INTERNATIONAL("Uluslararası işlem"),
        HIGH_FREQUENCY("Yüksek frekans"),
        WEEKEND_ACTIVITY("Hafta sonu aktivitesi"),
        NIGHT_ACTIVITY("Gece aktivitesi");

        private final String description;

        RiskFactor(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public TransactionRisk(String transactionId, String cardNumber, BigDecimal amount,
                           String merchantName, String merchantCity, String merchantCountry,
                           LocalDateTime transactionTime, TransactionType type,
                           int riskScore, EnumSet<RiskFactor> riskFactors, String riskReason) {

        this.transactionId = Objects.requireNonNull(transactionId, "Transaction ID cannot be null");
        this.cardNumber = Objects.requireNonNull(cardNumber, "Card number cannot be null");
        this.amount = Objects.requireNonNull(amount, "Amount cannot be null");
        this.merchantName = Objects.requireNonNull(merchantName, "Merchant name cannot be null");
        this.merchantCity = Objects.requireNonNull(merchantCity, "Merchant city cannot be null");
        this.merchantCountry = Objects.requireNonNull(merchantCountry, "Merchant country cannot be null");
        this.transactionTime = Objects.requireNonNull(transactionTime, "Transaction time cannot be null");
        this.type = Objects.requireNonNull(type, "Transaction type cannot be null");
        this.riskScore = Math.max(0, Math.min(100, riskScore));
        this.riskLevel = RiskLevel.fromScore(this.riskScore);
        this.riskFactors = riskFactors != null ? EnumSet.copyOf(riskFactors) : EnumSet.noneOf(RiskFactor.class);
        this.riskReason = Objects.requireNonNull(riskReason, "Risk reason cannot be null");

        validateTransaction();
    }


    public static TransactionRisk lowRisk(String transactionId, String cardNumber, BigDecimal amount,
                                          String merchantName, String merchantCity, String merchantCountry,
                                          LocalDateTime transactionTime, TransactionType type) {
        return new TransactionRisk(transactionId, cardNumber, amount, merchantName, merchantCity,
                merchantCountry, transactionTime, type, 15,
                EnumSet.noneOf(RiskFactor.class), "Normal transaction pattern");
    }

    public static TransactionRisk suspicious(String transactionId, String cardNumber, BigDecimal amount,
                                             String merchantName, String merchantCity, String merchantCountry,
                                             LocalDateTime transactionTime, TransactionType type,
                                             EnumSet<RiskFactor> factors, String reason) {
        int score = calculateRiskScore(factors, amount);
        return new TransactionRisk(transactionId, cardNumber, amount, merchantName, merchantCity,
                merchantCountry, transactionTime, type, score, factors, reason);
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

    public LocalDateTime getTransactionTime() {
        return transactionTime;
    }

    public TransactionType getType() {
        return type;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public EnumSet<RiskFactor> getRiskFactors() {
        return EnumSet.copyOf(riskFactors);
    }

    public String getRiskReason() {
        return riskReason;
    }


    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL;
    }

    public boolean requiresManualReview() {
        return riskLevel == RiskLevel.CRITICAL ||
                riskFactors.contains(RiskFactor.LOCATION_JUMP) ||
                riskFactors.contains(RiskFactor.DUPLICATE_TRANSACTION);
    }

    public boolean shouldBlock() {
        return riskScore >= 90 ||
                (riskFactors.size() >= 4 && riskLevel == RiskLevel.CRITICAL);
    }

    public String getLocation() {
        return merchantCity + ", " + merchantCountry;
    }

    public String getMaskedCardNumber() {
        if (cardNumber.length() < 4) {
            return cardNumber;
        }
        return "****" + cardNumber.substring(cardNumber.length() - 4);
    }

    public boolean isInternational() {
        return riskFactors.contains(RiskFactor.INTERNATIONAL);
    }

    public boolean isNightTransaction() {
        int hour = transactionTime.getHour();
        return hour < 6 || hour > 22;
    }

    public boolean isWeekendTransaction() {
        return transactionTime.getDayOfWeek().getValue() >= 6;
    }


    private static int calculateRiskScore(EnumSet<RiskFactor> factors, BigDecimal amount) {
        int baseScore = 10;

        for (RiskFactor factor : factors) {
            baseScore += switch (factor) {
                case VELOCITY_ANOMALY -> 25;
                case LOCATION_JUMP -> 35;
                case AMOUNT_ANOMALY -> 20;
                case DUPLICATE_TRANSACTION -> 30;
                case TIME_ANOMALY -> 15;
                case MERCHANT_RISK -> 25;
                case INTERNATIONAL -> 10;
                case HIGH_FREQUENCY -> 20;
                case WEEKEND_ACTIVITY -> 5;
                case NIGHT_ACTIVITY -> 10;
            };
        }


        if (amount.compareTo(new BigDecimal("10000")) > 0) {
            baseScore += 15;
        } else if (amount.compareTo(new BigDecimal("100")) < 0) {
            baseScore -= 5;
        }

        return Math.max(0, Math.min(100, baseScore));
    }


    public RiskAnalysis getDetailedAnalysis() {
        return new RiskAnalysis(
                riskScore,
                riskLevel,
                riskFactors.size(),
                isNightTransaction(),
                isWeekendTransaction(),
                isInternational(),
                amount.compareTo(new BigDecimal("5000")) > 0,
                calculateUrgency()
        );
    }

    private Urgency calculateUrgency() {
        if (shouldBlock()) return Urgency.IMMEDIATE;
        if (requiresManualReview()) return Urgency.HIGH;
        if (isHighRisk()) return Urgency.MEDIUM;
        return Urgency.LOW;
    }

    private void validateTransaction() {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Transaction amount cannot be negative");
        }

        if (transactionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID cannot be empty");
        }

        if (cardNumber.length() < 4) {
            throw new IllegalArgumentException("Card number too short");
        }
    }


    public enum Urgency {
        LOW, MEDIUM, HIGH, IMMEDIATE
    }

    public static final class RiskAnalysis {
        private final int riskScore;
        private final RiskLevel riskLevel;
        private final int factorCount;
        private final boolean nightTransaction;
        private final boolean weekendTransaction;
        private final boolean international;
        private final boolean highAmount;
        private final Urgency urgency;

        public RiskAnalysis(int riskScore, RiskLevel riskLevel, int factorCount,
                            boolean nightTransaction, boolean weekendTransaction,
                            boolean international, boolean highAmount, Urgency urgency) {
            this.riskScore = riskScore;
            this.riskLevel = riskLevel;
            this.factorCount = factorCount;
            this.nightTransaction = nightTransaction;
            this.weekendTransaction = weekendTransaction;
            this.international = international;
            this.highAmount = highAmount;
            this.urgency = urgency;
        }


        public int getRiskScore() {
            return riskScore;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public int getFactorCount() {
            return factorCount;
        }

        public boolean isNightTransaction() {
            return nightTransaction;
        }

        public boolean isWeekendTransaction() {
            return weekendTransaction;
        }

        public boolean isInternational() {
            return international;
        }

        public boolean isHighAmount() {
            return highAmount;
        }

        public Urgency getUrgency() {
            return urgency;
        }

        @Override
        public String toString() {
            return String.format("RiskAnalysis{score=%d, level=%s, factors=%d, urgency=%s}",
                    riskScore, riskLevel, factorCount, urgency);
        }
    }

    @Override
    public int compareTo(TransactionRisk other) {

        int scoreCompare = Integer.compare(other.riskScore, this.riskScore);
        if (scoreCompare != 0) return scoreCompare;


        int timeCompare = other.transactionTime.compareTo(this.transactionTime);
        if (timeCompare != 0) return timeCompare;


        return other.amount.compareTo(this.amount);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        TransactionRisk that = (TransactionRisk) obj;
        return Objects.equals(transactionId, that.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId);
    }

    @Override
    public String toString() {
        return String.format("TransactionRisk{id=%s, card=%s, amount=%s, score=%d (%s), factors=%d}",
                transactionId, getMaskedCardNumber(), amount, riskScore,
                riskLevel, riskFactors.size());
    }
}