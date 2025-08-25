
import java.time.LocalDateTime;
import java.util.Objects;
import java.io.Serializable;


public final class Auth implements Comparable<Auth>, Serializable {

    private static final long serialVersionUID = 1L;

    private final String authId;
    private final CardNumber cardNumber;
    private final Money amount;
    private final LocalDateTime timestamp;
    private final String merchantId;
    private final String mccCode;
    private final AuthStatus status;
    private final LocalDateTime expiryTime;
    private final String responseCode;
    private final String authCode;


    private Auth(Builder builder) {
        this.authId = Objects.requireNonNull(builder.authId, "Auth ID cannot be null");
        this.cardNumber = Objects.requireNonNull(builder.cardNumber, "Card number cannot be null");
        this.amount = Objects.requireNonNull(builder.amount, "Amount cannot be null");
        this.timestamp = Objects.requireNonNull(builder.timestamp, "Timestamp cannot be null");
        this.merchantId = Objects.requireNonNull(builder.merchantId, "Merchant ID cannot be null");
        this.mccCode = Objects.requireNonNull(builder.mccCode, "MCC code cannot be null");
        this.status = Objects.requireNonNull(builder.status, "Status cannot be null");
        this.expiryTime = Objects.requireNonNull(builder.expiryTime, "Expiry time cannot be null");
        this.responseCode = builder.responseCode;
        this.authCode = builder.authCode;


        validateAuth();
    }

    public static Builder builder() {
        return new Builder();
    }


    public static Auth approved(String authId, CardNumber cardNumber, Money amount,
                                LocalDateTime timestamp, String merchantId, String mccCode,
                                String authCode) {
        return builder()
                .authId(authId)
                .cardNumber(cardNumber)
                .amount(amount)
                .timestamp(timestamp)
                .merchantId(merchantId)
                .mccCode(mccCode)
                .status(AuthStatus.APPROVED)
                .expiryTime(timestamp.plusDays(7))
                .responseCode("00")
                .authCode(authCode)
                .build();
    }

    public static Auth declined(String authId, CardNumber cardNumber, Money amount,
                                LocalDateTime timestamp, String merchantId, String mccCode,
                                String responseCode) {
        return builder()
                .authId(authId)
                .cardNumber(cardNumber)
                .amount(amount)
                .timestamp(timestamp)
                .merchantId(merchantId)
                .mccCode(mccCode)
                .status(AuthStatus.DECLINED)
                .expiryTime(timestamp.plusHours(1))
                .responseCode(responseCode)
                .build();
    }


    public String getAuthId() {
        return authId;
    }

    public CardNumber getCardNumber() {
        return cardNumber;
    }

    public Money getAmount() {
        return amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getMccCode() {
        return mccCode;
    }

    public AuthStatus getStatus() {
        return status;
    }

    public LocalDateTime getExpiryTime() {
        return expiryTime;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public String getAuthCode() {
        return authCode;
    }


    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryTime);
    }

    public boolean isApproved() {
        return status == AuthStatus.APPROVED;
    }

    public boolean isDeclined() {
        return status == AuthStatus.DECLINED;
    }

    public boolean isPending() {
        return status == AuthStatus.PENDING;
    }

    public boolean canBeMatched() {
        return isApproved() && !isExpired();
    }


    public double calculateMatchingScore(Presentment presentment) {
        if (!canBeMatched()) {
            return 0.0;
        }

        double score = 0.0;


        if (!this.cardNumber.equals(presentment.getCardNumber())) {
            return 0.0;
        }


        double amountScore = calculateAmountScore(presentment.getAmount());
        score += amountScore * 0.4;


        double timeScore = calculateTimeScore(presentment.getTimestamp());
        score += timeScore * 0.3;


        double merchantScore = calculateMerchantScore(presentment.getMerchantId());
        score += merchantScore * 0.2;


        double mccScore = calculateMccScore(presentment.getMccCode());
        score += mccScore * 0.1;

        return Math.min(100.0, score);
    }


    public static class Builder {
        private String authId;
        private CardNumber cardNumber;
        private Money amount;
        private LocalDateTime timestamp;
        private String merchantId;
        private String mccCode;
        private AuthStatus status;
        private LocalDateTime expiryTime;
        private String responseCode;
        private String authCode;

        public Builder authId(String authId) {
            this.authId = authId;
            return this;
        }

        public Builder cardNumber(CardNumber cardNumber) {
            this.cardNumber = cardNumber;
            return this;
        }

        public Builder amount(Money amount) {
            this.amount = amount;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder merchantId(String merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public Builder mccCode(String mccCode) {
            this.mccCode = mccCode;
            return this;
        }

        public Builder status(AuthStatus status) {
            this.status = status;
            return this;
        }

        public Builder expiryTime(LocalDateTime expiryTime) {
            this.expiryTime = expiryTime;
            return this;
        }

        public Builder responseCode(String responseCode) {
            this.responseCode = responseCode;
            return this;
        }

        public Builder authCode(String authCode) {
            this.authCode = authCode;
            return this;
        }

        public Auth build() {
            return new Auth(this);
        }
    }


    private void validateAuth() {
        if (authId.trim().isEmpty()) {
            throw new IllegalArgumentException("Auth ID cannot be empty");
        }

        if (amount.isNegative()) {
            throw new IllegalArgumentException("Auth amount cannot be negative");
        }

        if (expiryTime.isBefore(timestamp)) {
            throw new IllegalArgumentException("Expiry time cannot be before auth timestamp");
        }

        if (mccCode.length() != 4 || !mccCode.matches("\\d{4}")) {
            throw new IllegalArgumentException("MCC code must be 4 digits: " + mccCode);
        }

        if (status == AuthStatus.APPROVED && (authCode == null || authCode.trim().isEmpty())) {
            throw new IllegalArgumentException("Approved auth must have auth code");
        }
    }

    private double calculateAmountScore(Money presentmentAmount) {
        if (this.amount.equals(presentmentAmount)) {
            return 100.0;
        }


        double thisAmountValue = this.amount.getAmount().doubleValue();
        double presentmentAmountValue = presentmentAmount.getAmount().doubleValue();

        if (thisAmountValue == 0) {
            return presentmentAmountValue == 0 ? 100.0 : 0.0;
        }

        double percentageDiff = Math.abs(thisAmountValue - presentmentAmountValue) / thisAmountValue * 100;


        if (percentageDiff <= 1) return 95.0;
        if (percentageDiff <= 5) return 80.0;
        if (percentageDiff <= 10) return 60.0;
        if (percentageDiff <= 20) return 30.0;

        return 0.0;
    }

    private double calculateTimeScore(LocalDateTime presentmentTime) {
        long hoursDifference = Math.abs(java.time.Duration.between(this.timestamp, presentmentTime).toHours());

        if (hoursDifference <= 1) return 100.0;
        if (hoursDifference <= 24) return 80.0;
        if (hoursDifference <= 72) return 60.0;
        if (hoursDifference <= 168) return 30.0;

        return 10.0;
    }

    private double calculateMerchantScore(String presentmentMerchantId) {
        if (this.merchantId.equals(presentmentMerchantId)) {
            return 100.0;
        }


        return 0.0;
    }

    private double calculateMccScore(String presentmentMccCode) {
        if (this.mccCode.equals(presentmentMccCode)) {
            return 100.0;
        }


        return 0.0;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Auth auth = (Auth) obj;
        return Objects.equals(authId, auth.authId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authId);
    }

    @Override
    public int compareTo(Auth other) {

        return other.timestamp.compareTo(this.timestamp);
    }

    @Override
    public String toString() {
        return String.format("Auth[id=%s, card=%s, amount=%s, status=%s, timestamp=%s]",
                authId, cardNumber, amount, status, timestamp);
    }
}