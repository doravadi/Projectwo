
import java.time.LocalDateTime;
import java.util.Objects;
import java.io.Serializable;


public final class Presentment implements Comparable<Presentment>, Serializable {

    private static final long serialVersionUID = 1L;

    private final String presentmentId;
    private final CardNumber cardNumber;
    private final Money amount;
    private final LocalDateTime timestamp;
    private final String merchantId;
    private final String mccCode;
    private final PresentmentType type;
    private final String originalAuthId; 
    private final String batchId;
    private final String merchantReference;
    private final PresentmentStatus status;

    
    private Presentment(Builder builder) {
        this.presentmentId = Objects.requireNonNull(builder.presentmentId, "Presentment ID cannot be null");
        this.cardNumber = Objects.requireNonNull(builder.cardNumber, "Card number cannot be null");
        this.amount = Objects.requireNonNull(builder.amount, "Amount cannot be null");
        this.timestamp = Objects.requireNonNull(builder.timestamp, "Timestamp cannot be null");
        this.merchantId = Objects.requireNonNull(builder.merchantId, "Merchant ID cannot be null");
        this.mccCode = Objects.requireNonNull(builder.mccCode, "MCC code cannot be null");
        this.type = Objects.requireNonNull(builder.type, "Presentment type cannot be null");
        this.batchId = Objects.requireNonNull(builder.batchId, "Batch ID cannot be null");
        this.merchantReference = builder.merchantReference; 
        this.originalAuthId = builder.originalAuthId; 
        this.status = Objects.requireNonNull(builder.status, "Status cannot be null");

        
        validatePresentment();
    }

    public static Builder builder() {
        return new Builder();
    }

    
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

    
    public double calculateCompatibilityScore(Auth auth) {
        if (!canBeMatched() || !auth.canBeMatched()) {
            return 0.0;
        }

        double score = 0.0;

        
        if (!this.cardNumber.equals(auth.getCardNumber())) {
            return 0.0;
        }

        
        double amountScore = calculateAmountCompatibility(auth.getAmount());
        score += amountScore * 0.4; 

        
        double timeScore = calculateTimeCompatibility(auth.getTimestamp());
        score += timeScore * 0.3; 

        
        double merchantScore = this.merchantId.equals(auth.getMerchantId()) ? 100.0 : 0.0;
        score += merchantScore * 0.2; 

        
        double mccScore = this.mccCode.equals(auth.getMccCode()) ? 100.0 : 0.0;
        score += mccScore * 0.1; 

        return Math.min(100.0, score);
    }

    
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
        private PresentmentStatus status = PresentmentStatus.PENDING; 

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
        
        Money absAmount = this.amount.isNegative() ?
                Money.of(this.amount.getAmount().negate(), this.amount.getCurrency()) : this.amount;

        if (absAmount.equals(authAmount)) {
            return 100.0; 
        }

        
        if (absAmount.getAmount().compareTo(authAmount.getAmount()) <= 0) {
            double ratio = absAmount.getAmount().divide(authAmount.getAmount(), Money.MONEY_CONTEXT).doubleValue();

            if (ratio >= 0.95) return 90.0;  
            if (ratio >= 0.80) return 75.0;  
            if (ratio >= 0.50) return 60.0;  
            if (ratio >= 0.20) return 40.0;  

            return 20.0; 
        }

        
        double excessRatio = absAmount.getAmount().divide(authAmount.getAmount(), Money.MONEY_CONTEXT).doubleValue();

        if (excessRatio <= 1.05) return 70.0;  
        if (excessRatio <= 1.15) return 40.0;  

        return 0.0; 
    }

    private double calculateTimeCompatibility(LocalDateTime authTimestamp) {
        
        if (this.timestamp.isBefore(authTimestamp)) {
            
            long hoursBefore = java.time.Duration.between(this.timestamp, authTimestamp).toHours();

            if (hoursBefore <= 1) return 60.0;   
            if (hoursBefore <= 24) return 30.0;  

            return 10.0; 
        }

        
        long hoursAfter = java.time.Duration.between(authTimestamp, this.timestamp).toHours();

        if (hoursAfter <= 2) return 100.0;     
        if (hoursAfter <= 24) return 90.0;     
        if (hoursAfter <= 72) return 80.0;     
        if (hoursAfter <= 168) return 60.0;    

        return 30.0; 
    }

    
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
        
        return other.timestamp.compareTo(this.timestamp);
    }

    @Override
    public String toString() {
        return String.format("Presentment[id=%s, card=%s, amount=%s, type=%s, status=%s, timestamp=%s]",
                presentmentId, cardNumber, amount, type, status, timestamp);
    }
}