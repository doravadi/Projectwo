

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;


public final class TransactionContext {

    private final String transactionId;
    private final String cardNumber;
    private final String customerId;
    private final String accountId;
    private final BigDecimal amount;
    private final String currency;
    private final MccCategory mccCategory;
    private final String merchantName;
    private final String merchantCity;
    private final String merchantCountry;
    private final LocalDateTime transactionDateTime;
    private final TransactionType transactionType;
    private final String channel; 

    
    private final Integer customerAge;
    private final String customerSegment; 
    private final String customerCity;
    private final String customerCountry;
    private final BigDecimal accountBalance;
    private final BigDecimal monthlySpending; 

    private TransactionContext(Builder builder) {
        this.transactionId = Objects.requireNonNull(builder.transactionId, "Transaction ID cannot be null");
        this.cardNumber = Objects.requireNonNull(builder.cardNumber, "Card number cannot be null");
        this.customerId = Objects.requireNonNull(builder.customerId, "Customer ID cannot be null");
        this.accountId = Objects.requireNonNull(builder.accountId, "Account ID cannot be null");
        this.amount = Objects.requireNonNull(builder.amount, "Amount cannot be null");
        this.currency = Objects.requireNonNull(builder.currency, "Currency cannot be null");
        this.mccCategory = Objects.requireNonNull(builder.mccCategory, "MCC category cannot be null");
        this.merchantName = builder.merchantName;
        this.merchantCity = builder.merchantCity;
        this.merchantCountry = builder.merchantCountry;
        this.transactionDateTime = Objects.requireNonNull(builder.transactionDateTime, "Transaction date time cannot be null");
        this.transactionType = Objects.requireNonNull(builder.transactionType, "Transaction type cannot be null");
        this.channel = builder.channel;
        this.customerAge = builder.customerAge;
        this.customerSegment = builder.customerSegment;
        this.customerCity = builder.customerCity;
        this.customerCountry = builder.customerCountry;
        this.accountBalance = builder.accountBalance;
        this.monthlySpending = builder.monthlySpending;

        validate();
    }

    private void validate() {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amount);
        }
        if (currency.length() != 3) {
            throw new IllegalArgumentException("Currency must be 3 characters: " + currency);
        }
    }

    
    public String getTransactionId() { return transactionId; }
    public String getCardNumber() { return cardNumber; }
    public String getCustomerId() { return customerId; }
    public String getAccountId() { return accountId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public MccCategory getMccCategory() { return mccCategory; }
    public Optional<String> getMerchantName() { return Optional.ofNullable(merchantName); }
    public Optional<String> getMerchantCity() { return Optional.ofNullable(merchantCity); }
    public Optional<String> getMerchantCountry() { return Optional.ofNullable(merchantCountry); }
    public LocalDateTime getTransactionDateTime() { return transactionDateTime; }
    public TransactionType getTransactionType() { return transactionType; }
    public Optional<String> getChannel() { return Optional.ofNullable(channel); }
    public Optional<Integer> getCustomerAge() { return Optional.ofNullable(customerAge); }
    public Optional<String> getCustomerSegment() { return Optional.ofNullable(customerSegment); }
    public Optional<String> getCustomerCity() { return Optional.ofNullable(customerCity); }
    public Optional<String> getCustomerCountry() { return Optional.ofNullable(customerCountry); }
    public Optional<BigDecimal> getAccountBalance() { return Optional.ofNullable(accountBalance); }
    public Optional<BigDecimal> getMonthlySpending() { return Optional.ofNullable(monthlySpending); }

    
    public LocalDate getTransactionDate() {
        return transactionDateTime.toLocalDate();
    }

    public LocalTime getTransactionTime() {
        return transactionDateTime.toLocalTime();
    }

    public DayOfWeek getDayOfWeek() {
        return transactionDateTime.getDayOfWeek();
    }

    public int getHourOfDay() {
        return transactionDateTime.getHour();
    }

    public boolean isWeekend() {
        DayOfWeek day = getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    public boolean isNightTime() {
        int hour = getHourOfDay();
        return hour >= 22 || hour <= 6;
    }

    public boolean isForeignTransaction() {
        return merchantCountry != null &&
                customerCountry != null &&
                !merchantCountry.equals(customerCountry);
    }

    
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String transactionId;
        private String cardNumber;
        private String customerId;
        private String accountId;
        private BigDecimal amount;
        private String currency;
        private MccCategory mccCategory;
        private String merchantName;
        private String merchantCity;
        private String merchantCountry;
        private LocalDateTime transactionDateTime;
        private TransactionType transactionType;
        private String channel;
        private Integer customerAge;
        private String customerSegment;
        private String customerCity;
        private String customerCountry;
        private BigDecimal accountBalance;
        private BigDecimal monthlySpending;

        public Builder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder cardNumber(String cardNumber) {
            this.cardNumber = cardNumber;
            return this;
        }

        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder accountId(String accountId) {
            this.accountId = accountId;
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

        public Builder mccCategory(MccCategory mccCategory) {
            this.mccCategory = mccCategory;
            return this;
        }

        public Builder merchantName(String merchantName) {
            this.merchantName = merchantName;
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

        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder customerAge(Integer customerAge) {
            this.customerAge = customerAge;
            return this;
        }

        public Builder customerSegment(String customerSegment) {
            this.customerSegment = customerSegment;
            return this;
        }

        public Builder customerCity(String customerCity) {
            this.customerCity = customerCity;
            return this;
        }

        public Builder customerCountry(String customerCountry) {
            this.customerCountry = customerCountry;
            return this;
        }

        public Builder accountBalance(BigDecimal accountBalance) {
            this.accountBalance = accountBalance;
            return this;
        }

        public Builder monthlySpending(BigDecimal monthlySpending) {
            this.monthlySpending = monthlySpending;
            return this;
        }

        public TransactionContext build() {
            return new TransactionContext(this);
        }
    }

    
    public enum TransactionType {
        PURCHASE,
        CASH_ADVANCE,
        INSTALLMENT,
        REFUND,
        TRANSFER,
        PAYMENT,
        WITHDRAWAL
    }

    
    public enum MccCategory {
        GROCERY,
        FUEL,
        RESTAURANT,
        ENTERTAINMENT,
        TRAVEL,
        SHOPPING,
        HEALTHCARE,
        EDUCATION,
        AUTOMOTIVE,
        UTILITIES,
        ONLINE,
        OTHER
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionContext that = (TransactionContext) o;
        return Objects.equals(transactionId, that.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId);
    }

    @Override
    public String toString() {
        return "TransactionContext{" +
                "id='" + transactionId + '\'' +
                ", amount=" + amount + " " + currency +
                ", mcc=" + mccCategory +
                ", merchant='" + merchantName + '\'' +
                ", dateTime=" + transactionDateTime +
                ", type=" + transactionType +
                '}';
    }
}