// Presentment.java - Settlement/Presentment value object
import java.time.LocalDateTime;
import java.util.Objects;
import java.io.Serializable;

/**
 * Immutable presentment (settlement) transaction value object
 * Represents a merchant's request for payment settlement
 */
public final class Presentment implements Comparable<Presentment>, Serializable {

    private static final long serialVersionUID = 1L;

    private final String presentmentId;
    private final CardNumber cardNumber;
    private final Money amount;
    private final LocalDateTime timestamp;
    private final String merchantId;
    private final String mccCode;
    private final PresentmentType type;
    private final String originalAuthId; // Optional - may be null if not provided
    private final String batchId;
    private final String merchantReference;
    private final PresentmentStatus status;

    // Private constructor - use builder
    private Presentment(Builder builder) {
        this.presentmentId = Objects.requireNonNull(builder.presentmentId, "Presentment ID cannot be null");
        this.cardNumber = Objects.requireNonNull(builder.cardNumber, "Card number cannot be null");
        this.amount = Objects.requireNonNull(builder.amount, "Amount cannot be null");
        this.timestamp = Objects.requireNonNull(builder.timestamp, "Timestamp cannot be null");
        this.merchantId = Objects.requireNonNull(builder.merchantId, "Merchant ID cannot be null");
        this.mccCode = Objects.requireNonNull(builder.mccCode, "MCC code cannot be null");
        this.type = Objects.requireNonNull(builder.type, "Presentment type cannot be null");
        this.batchId = Objects.requireNonNull(builder.batchId, "Batch ID cannot be null");
        this.merchantReference = builder.merchantReference; // Can be null
        this.originalAuthId = builder.originalAuthId; // Can be null
        this.status = Objects.requireNonNull(builder.status, "Status cannot be null");

        // Business validation
        validatePresentment();
    }

    public static Builder builder() {
        return new Builder();
    }

    // Factory methods for common scenarios
    public static Presentment sale(String presentmentId, CardNumber cardNumber, Money amount,
                                   LocalDateTime timestamp, String merchantId, String mccCode,
                                   String batchId, String merchantReference) {
        return builder()
                .presentmentId(presentmentId)
                .cardNumber(cardNumber)
                .amount(amount)
                .timestamp(timestamp)
                .merchantId(merchantId)
                .mccCode(mccCode)
                .type(PresentmentType.SALE)
                .batchId(batchId)
                .merchantReference(merchantReference)
                .status(PresentmentStatus.PENDING)
                .build();
    }

    public static Presentment refund(String presentmentId, CardNumber cardNumber, Money amount,
                                     LocalDateTime timestamp, String merchantId, String mccCode,
                                     String batchId, String originalPresentmentId) {
        return builder()
                .presentmentId(presentmentId)
                .cardNumber(cardNumber)
                .amount(amount)
                .timestamp(timestamp)
                .merchantId(merchantId)
                .mccCode(mccCode)
                .type(PresentmentType.REFUND)
                .batchId(batchId)
                .merchantReference(originalPresentmentId)
                .status(PresentmentStatus.PENDING)
                .build();
    }

    // Getters
    public String getPresentmentId() { return presentmentId; }
    public CardNumber getCardNumber() { return cardNumber; }
    public Money getAmount() { return amount; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getMerchantId() { return merchantId; }
    public String getMccCode() { return mccCode; }
    public PresentmentType getType() { return type; }
    public String getOriginalAuthId() { return originalAuthId; }
    public String getBatchId() { return batchId; }
    public String getMerchantReference() { return merchantReference; }
    public PresentmentStatus getStatus() { return status; }

    // Business logic methods
    public boolean isPending() {
        return status == PresentmentStatus.PENDING;
    }

    public boolean isSettled() {
        return status == PresentmentStatus.SETTLED;
    }

    public boolean isRejected() {
        return status == PresentmentStatus.REJECTED;
    }

    public boolean canBeMatched() {
        return isPending() && (type == PresentmentType.SALE || type == PresentmentType.PARTIAL_CAPTURE);
    }

    public boolean isRefund() {
        return type == PresentmentType.REFUND;
    }

    public boolean isSale() {
        return type == PresentmentType.SALE;
    }

    /**
     * Calculate matching compatibility with an authorization
     * Returns compatibility score 0-100
     */
    public double calculateCompatibilityScore(Auth auth) {
        if (!canBeMatched() || !auth.canBeMatched()) {
            return 0.0;
        }

        double score = 0.0;

        // Card number must match exactly
        if (!this.cardNumber.equals(auth.getCardNumber())) {
            return 0.0;
        }

        // Amount compatibility
        double amountScore = calculateAmountCompatibility(auth.getAmount());
        score += amountScore * 0.4; // 40% weight

        // Time compatibility (presentment should be after auth)
        double timeScore = calculateTimeCompatibility(auth.getTimestamp());
        score += timeScore * 0.3; // 30% weight

        // Merchant matching
        double merchantScore = this.merchantId.equals(auth.getMerchantId()) ? 100.0 : 0.0;
        score += merchantScore * 0.2; // 20% weight

        // MCC matching
        double mccScore = this.mccCode.equals(auth.getMccCode()) ? 100.0 : 0.0;
        score += mccScore * 0.1; // 10% weight

        return Math.min(100.0, score);
    }

    /**
     * Create a copy with updated status
     */
    public Presentment withStatus(PresentmentStatus newStatus) {
        return builder()
                .presentmentId(this.presentmentId)
                .cardNumber(this.cardNumber)
                .amount(this.amount)
                .timestamp(this.timestamp)
                .merchantId(this.merchantId)
                .mccCode(this.mccCode)
                .type(this.type)
                .originalAuthId(this.originalAuthId)
                .batchId(this.batchId)
                .merchantReference(this.merchantReference)
                .status(newStatus)
                .build();
    }

    // Builder pattern
    public static class Builder {
        private String presentmentId;
        private CardNumber cardNumber;
        private Money amount;
        private LocalDateTime timestamp;
        private String merchantId;
        private String mccCode;
        private PresentmentType type;
        private String originalAuthId;
        private String batchId;
        private String merchantReference;
        private PresentmentStatus status = PresentmentStatus.PENDING; // Default

        public Builder presentmentId(String presentmentId) { this.presentmentId = presentmentId; return this; }
        public Builder cardNumber(CardNumber cardNumber) { this.cardNumber = cardNumber; return this; }
        public Builder amount(Money amount) { this.amount = amount; return this; }
        public Builder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
        public Builder merchantId(String merchantId) { this.merchantId = merchantId; return this; }
        public Builder mccCode(String mccCode) { this.mccCode = mccCode; return this; }
        public Builder type(PresentmentType type) { this.type = type; return this; }
        public Builder originalAuthId(String originalAuthId) { this.originalAuthId = originalAuthId; return this; }
        public Builder batchId(String batchId) { this.batchId = batchId; return this; }
        public Builder merchantReference(String merchantReference) { this.merchantReference = merchantReference; return this; }
        public Builder status(PresentmentStatus status) { this.status = status; return this; }

        public Presentment build() {
            return new Presentment(this);
        }
    }

    // Private helper methods
    private void validatePresentment() {
        if (presentmentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Presentment ID cannot be empty");
        }

        if (amount.isNegative() && type != PresentmentType.REFUND) {
            throw new IllegalArgumentException("Non-refund presentment amount cannot be negative");
        }

        if (amount.isZero()) {
            throw new IllegalArgumentException("Presentment amount cannot be zero");
        }

        if (mccCode.length() != 4 || !mccCode.matches("\\d{4}")) {
            throw new IllegalArgumentException("MCC code must be 4 digits: " + mccCode);
        }

        if (type == PresentmentType.REFUND && amount.isPositive()) {
            throw new IllegalArgumentException("Refund presentment amount should be negative");
        }
    }

    private double calculateAmountCompatibility(Money authAmount) {
        // For presentments, we expect amount <= auth amount (partial captures allowed)
        Money absAmount = this.amount.isNegative() ?
                Money.of(this.amount.getAmount().negate(), this.amount.getCurrency()) : this.amount;

        if (absAmount.equals(authAmount)) {
            return 100.0; // Perfect match
        }

        // Check if presentment amount is less than or equal to auth amount (partial capture)
        if (absAmount.getAmount().compareTo(authAmount.getAmount()) <= 0) {
            double ratio = absAmount.getAmount().divide(authAmount.getAmount(), Money.MONEY_CONTEXT).doubleValue();

            if (ratio >= 0.95) return 90.0;  // 95-100% of auth amount
            if (ratio >= 0.80) return 75.0;  // 80-95% of auth amount
            if (ratio >= 0.50) return 60.0;  // 50-80% of auth amount
            if (ratio >= 0.20) return 40.0;  // 20-50% of auth amount

            return 20.0; // Less than 20% but still valid partial
        }

        // Presentment amount > auth amount (potentially problematic)
        double excessRatio = absAmount.getAmount().divide(authAmount.getAmount(), Money.MONEY_CONTEXT).doubleValue();

        if (excessRatio <= 1.05) return 70.0;  // Up to 5% over (tips, etc.)
        if (excessRatio <= 1.15) return 40.0;  // Up to 15% over (questionable)

        return 0.0; // Too much over auth amount
    }

    private double calculateTimeCompatibility(LocalDateTime authTimestamp) {
        // Presentment should typically come after authorization
        if (this.timestamp.isBefore(authTimestamp)) {
            // Presentment before auth is unusual but possible (offline processing)
            long hoursBefore = java.time.Duration.between(this.timestamp, authTimestamp).toHours();

            if (hoursBefore <= 1) return 60.0;   // Within 1 hour before
            if (hoursBefore <= 24) return 30.0;  // Within 24 hours before

            return 10.0; // More than 24 hours before auth
        }

        // Normal case: presentment after auth
        long hoursAfter = java.time.Duration.between(authTimestamp, this.timestamp).toHours();

        if (hoursAfter <= 2) return 100.0;     // Within 2 hours
        if (hoursAfter <= 24) return 90.0;     // Same day
        if (hoursAfter <= 72) return 80.0;     // Within 3 days
        if (hoursAfter <= 168) return 60.0;    // Within 1 week

        return 30.0; // Over 1 week (getting old but still valid)
    }

    // Object contract methods
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Presentment that = (Presentment) obj;
        return Objects.equals(presentmentId, that.presentmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(presentmentId);
    }

    @Override
    public int compareTo(Presentment other) {
        // Sort by timestamp (newest first)
        return other.timestamp.compareTo(this.timestamp);
    }

    @Override
    public String toString() {
        return String.format("Presentment[id=%s, card=%s, amount=%s, type=%s, status=%s, timestamp=%s]",
                presentmentId, cardNumber, amount, type, status, timestamp);
    }
}