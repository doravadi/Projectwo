
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;


public final class TransactionEntity {

    private final String transactionId;
    private final String cardNumber;
    private final String accountId;
    private final String customerId;
    private final BigDecimal amount;
    private final String currency;
    private final String merchantName;
    private final String merchantCategory; // MCC category as string
    private final String merchantCity;
    private final String merchantCountry;
    private final LocalDateTime transactionDateTime;
    private final TransactionType transactionType;
    private final TransactionStatus status;
    private final String authorizationCode;
    private final String channel; // ATM, POS, ONLINE, MOBILE
    private final String responseCode; // 00, 01, 05 vb.
    private final String description;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private TransactionEntity(Builder builder) {

        this.transactionId = validateRequired(builder.transactionId, "Transaction ID");
        this.cardNumber = validateRequired(builder.cardNumber, "Card number");
        this.accountId = validateRequired(builder.accountId, "Account ID");
        this.customerId = validateRequired(builder.customerId, "Customer ID");
        this.amount = validateAmount(builder.amount);
        this.currency = validateCurrency(builder.currency);
        this.transactionDateTime = Objects.requireNonNull(builder.transactionDateTime, "Transaction date time cannot be null");
        this.transactionType = Objects.requireNonNull(builder.transactionType, "Transaction type cannot be null");
        this.status = Objects.requireNonNull(builder.status, "Status cannot be null");

        this.merchantName = builder.merchantName;
        this.merchantCategory = builder.merchantCategory;
        this.merchantCity = builder.merchantCity;
        this.merchantCountry = builder.merchantCountry;
        this.authorizationCode = builder.authorizationCode;
        this.channel = builder.channel;
        this.responseCode = builder.responseCode;
        this.description = builder.description;
        this.createdAt = builder.createdAt != null ? builder.createdAt : LocalDateTime.now();
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : this.createdAt;

        validateBusinessRules();
    }

    private String validateRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
        return value.trim();
    }

    private BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amount);
        }
        return amount;
    }

    private String validateCurrency(String currency) {
        if (currency == null || currency.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency cannot be null or empty");
        }
        if (currency.length() != 3) {
            throw new IllegalArgumentException("Currency must be 3 characters: " + currency);
        }
        return currency.toUpperCase();
    }

    private void validateBusinessRules() {

        if (cardNumber.length() < 13 || cardNumber.length() > 19) {
            throw new IllegalArgumentException("Invalid card number length");
        }

        if (transactionDateTime.isAfter(LocalDateTime.now().plusMinutes(5))) { // 5 min tolerance for clock differences
            throw new IllegalArgumentException("Transaction date time cannot be in future");
        }

        if (status == TransactionStatus.APPROVED && responseCode != null &&
                !responseCode.equals("00") && !responseCode.equals("000")) {

        }
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public String getMerchantCategory() {
        return merchantCategory;
    }

    public String getMerchantCity() {
        return merchantCity;
    }

    public String getMerchantCountry() {
        return merchantCountry;
    }

    public LocalDateTime getTransactionDateTime() {
        return transactionDateTime;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public String getChannel() {
        return channel;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isApproved() {
        return status == TransactionStatus.APPROVED;
    }

    public boolean isDeclined() {
        return status == TransactionStatus.DECLINED;
    }

    public boolean isPending() {
        return status == TransactionStatus.PENDING || status == TransactionStatus.PROCESSING;
    }

    public boolean isReversed() {
        return status == TransactionStatus.REVERSED;
    }

    public boolean isCashAdvance() {
        return transactionType == TransactionType.CASH_ADVANCE;
    }

    public boolean isPurchase() {
        return transactionType == TransactionType.PURCHASE;
    }

    public boolean isRefund() {
        return transactionType == TransactionType.REFUND;
    }

    public boolean isForeignTransaction() {
        return merchantCountry != null && !"TR".equals(merchantCountry.toUpperCase());
    }

    public String getMaskedCardNumber() {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }

    public static TransactionEntity approvedPurchase(String transactionId, String cardNumber,
                                                     String accountId, String customerId,
                                                     BigDecimal amount, String currency,
                                                     String merchantName) {
        return builder()
                .transactionId(transactionId)
                .cardNumber(cardNumber)
                .accountId(accountId)
                .customerId(customerId)
                .amount(amount)
                .currency(currency)
                .merchantName(merchantName)
                .transactionType(TransactionType.PURCHASE)
                .status(TransactionStatus.APPROVED)
                .transactionDateTime(LocalDateTime.now())
                .responseCode("00")
                .build();
    }

    public static TransactionEntity declinedTransaction(String transactionId, String cardNumber,
                                                        String accountId, String customerId,
                                                        BigDecimal amount, String currency,
                                                        String responseCode, String description) {
        return builder()
                .transactionId(transactionId)
                .cardNumber(cardNumber)
                .accountId(accountId)
                .customerId(customerId)
                .amount(amount)
                .currency(currency)
                .transactionType(TransactionType.PURCHASE)
                .status(TransactionStatus.DECLINED)
                .transactionDateTime(LocalDateTime.now())
                .responseCode(responseCode)
                .description(description)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String transactionId;
        private String cardNumber;
        private String accountId;
        private String customerId;
        private BigDecimal amount;
        private String currency;
        private String merchantName;
        private String merchantCategory;
        private String merchantCity;
        private String merchantCountry;
        private LocalDateTime transactionDateTime;
        private TransactionType transactionType;
        private TransactionStatus status;
        private String authorizationCode;
        private String channel;
        private String responseCode;
        private String description;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        private Builder() {
        }

        public Builder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder cardNumber(String cardNumber) {
            this.cardNumber = cardNumber;
            return this;
        }

        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder merchantName(String merchantName) {
            this.merchantName = merchantName;
            return this;
        }

        public Builder merchantCategory(String merchantCategory) {
            this.merchantCategory = merchantCategory;
            return this;
        }

        public Builder merchantCity(String merchantCity) {
            this.merchantCity = merchantCity;
            return this;
        }

        public Builder merchantCountry(String merchantCountry) {
            this.merchantCountry = merchantCountry;
            return this;
        }

        public Builder transactionDateTime(LocalDateTime transactionDateTime) {
            this.transactionDateTime = transactionDateTime;
            return this;
        }

        public Builder transactionType(TransactionType transactionType) {
            this.transactionType = transactionType;
            return this;
        }

        public Builder status(TransactionStatus status) {
            this.status = status;
            return this;
        }

        public Builder authorizationCode(String authorizationCode) {
            this.authorizationCode = authorizationCode;
            return this;
        }

        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder responseCode(String responseCode) {
            this.responseCode = responseCode;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public TransactionEntity build() {
            return new TransactionEntity(this);
        }
    }

    public enum TransactionType {
        PURCHASE("Purchase"),
        CASH_ADVANCE("Cash Advance"),
        REFUND("Refund"),
        REVERSAL("Reversal"),
        TRANSFER("Transfer"),
        PAYMENT("Payment"),
        WITHDRAWAL("ATM Withdrawal"),
        DEPOSIT("Deposit");

        private final String description;

        TransactionType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum TransactionStatus {
        PENDING("Pending"),
        PROCESSING("Processing"),
        APPROVED("Approved"),
        DECLINED("Declined"),
        REVERSED("Reversed"),
        FAILED("Failed"),
        CANCELLED("Cancelled"),
        EXPIRED("Expired");

        private final String description;

        TransactionStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public boolean isFinal() {
            return this == APPROVED || this == DECLINED || this == REVERSED ||
                    this == FAILED || this == CANCELLED || this == EXPIRED;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransactionEntity that = (TransactionEntity) o;
        return Objects.equals(transactionId, that.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId);
    }

    @Override
    public String toString() {
        return "TransactionEntity{" +
                "transactionId='" + transactionId + '\'' +
                ", cardNumber='" + getMaskedCardNumber() + '\'' +
                ", amount=" + amount + " " + currency +
                ", merchantName='" + merchantName + '\'' +
                ", transactionDateTime=" + transactionDateTime +
                ", type=" + transactionType +
                ", status=" + status +
                ", responseCode='" + responseCode + '\'' +
                '}';
    }


    public String toAuditString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TXN[").append(transactionId).append("] ");
        sb.append(getMaskedCardNumber()).append(" ");
        sb.append(amount).append(" ").append(currency).append(" ");
        sb.append(transactionType).append(" ");
        sb.append(status).append(" ");
        if (merchantName != null) {
            sb.append("at ").append(merchantName).append(" ");
        }
        if (responseCode != null) {
            sb.append("(").append(responseCode).append(")");
        }
        return sb.toString();
    }


    public TransactionEntity withStatus(TransactionStatus newStatus) {
        return new Builder()
                .transactionId(this.transactionId)
                .cardNumber(this.cardNumber)
                .accountId(this.accountId)
                .customerId(this.customerId)
                .amount(this.amount)
                .currency(this.currency)
                .merchantName(this.merchantName)
                .merchantCategory(this.merchantCategory)
                .merchantCity(this.merchantCity)
                .merchantCountry(this.merchantCountry)
                .transactionDateTime(this.transactionDateTime)
                .transactionType(this.transactionType)
                .status(newStatus)
                .authorizationCode(this.authorizationCode)
                .channel(this.channel)
                .responseCode(this.responseCode)
                .description(this.description)
                .createdAt(this.createdAt)
                .updatedAt(LocalDateTime.now())
                .build();
    }


    public TransactionEntity withResponseCode(String newResponseCode) {
        return new Builder()
                .transactionId(this.transactionId)
                .cardNumber(this.cardNumber)
                .accountId(this.accountId)
                .customerId(this.customerId)
                .amount(this.amount)
                .currency(this.currency)
                .merchantName(this.merchantName)
                .merchantCategory(this.merchantCategory)
                .merchantCity(this.merchantCity)
                .merchantCountry(this.merchantCountry)
                .transactionDateTime(this.transactionDateTime)
                .transactionType(this.transactionType)
                .status(this.status)
                .authorizationCode(this.authorizationCode)
                .channel(this.channel)
                .responseCode(newResponseCode)
                .description(this.description)
                .createdAt(this.createdAt)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}