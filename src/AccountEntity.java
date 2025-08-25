
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;


public final class AccountEntity {

    private final String accountId;
    private final String customerId;
    private final String accountNumber;
    private final String iban;
    private final AccountType accountType;
    private final AccountStatus status;
    private final String currency;
    private final BigDecimal balance;
    private final BigDecimal availableBalance;
    private final BigDecimal creditLimit;
    private final BigDecimal dailyLimit;
    private final BigDecimal monthlyLimit;
    private final String branchCode;
    private final LocalDate openingDate;
    private final LocalDate lastTransactionDate;
    private final String productCode; // Account product (e.g., "PREMIUM_CHECKING")
    private final String description;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private AccountEntity(Builder builder) {

        this.accountId = validateRequired(builder.accountId, "Account ID");
        this.customerId = validateRequired(builder.customerId, "Customer ID");
        this.accountNumber = validateRequired(builder.accountNumber, "Account number");
        this.accountType = Objects.requireNonNull(builder.accountType, "Account type cannot be null");
        this.status = Objects.requireNonNull(builder.status, "Account status cannot be null");
        this.currency = validateCurrency(builder.currency);
        this.balance = validateBalance(builder.balance);
        this.openingDate = Objects.requireNonNull(builder.openingDate, "Opening date cannot be null");

        this.iban = builder.iban;
        this.availableBalance = builder.availableBalance != null ? builder.availableBalance : balance;
        this.creditLimit = builder.creditLimit != null ? builder.creditLimit : BigDecimal.ZERO;
        this.dailyLimit = builder.dailyLimit != null ? builder.dailyLimit : new BigDecimal("10000");
        this.monthlyLimit = builder.monthlyLimit != null ? builder.monthlyLimit : new BigDecimal("50000");
        this.branchCode = builder.branchCode;
        this.lastTransactionDate = builder.lastTransactionDate;
        this.productCode = builder.productCode;
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

    private String validateCurrency(String currency) {
        if (currency == null || currency.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency cannot be null or empty");
        }
        if (currency.length() != 3) {
            throw new IllegalArgumentException("Currency must be 3 characters: " + currency);
        }
        return currency.toUpperCase();
    }

    private BigDecimal validateBalance(BigDecimal balance) {
        if (balance == null) {
            throw new IllegalArgumentException("Balance cannot be null");
        }
        return balance;
    }

    private void validateBusinessRules() {

        if (accountNumber.length() < 8 || accountNumber.length() > 20) {
            throw new IllegalArgumentException("Invalid account number length");
        }

        if (iban != null && (iban.length() < 15 || iban.length() > 34)) {
            throw new IllegalArgumentException("Invalid IBAN length");
        }

        if (openingDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Opening date cannot be in future");
        }

        if (accountType == AccountType.SAVINGS && balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Savings account cannot have negative balance");
        }

        BigDecimal maxAvailable = balance.add(creditLimit);
        if (availableBalance.compareTo(maxAvailable) > 0) {
            throw new IllegalArgumentException("Available balance cannot exceed balance + credit limit");
        }

        if (dailyLimit.compareTo(BigDecimal.ZERO) < 0 || monthlyLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Transaction limits cannot be negative");
        }

        if (dailyLimit.compareTo(monthlyLimit) > 0) {
            throw new IllegalArgumentException("Daily limit cannot exceed monthly limit");
        }
    }

    public String getAccountId() {
        return accountId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getIban() {
        return iban;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public LocalDate getOpeningDate() {
        return openingDate;
    }

    public LocalDate getLastTransactionDate() {
        return lastTransactionDate;
    }

    public String getProductCode() {
        return productCode;
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

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isClosed() {
        return status == AccountStatus.CLOSED;
    }

    public boolean isSuspended() {
        return status == AccountStatus.SUSPENDED || status == AccountStatus.FROZEN;
    }

    public boolean canPerformTransactions() {
        return isActive() && !isSuspended();
    }

    public boolean hasOverdraft() {
        return balance.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean hasCreditLimit() {
        return creditLimit.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isCheckingAccount() {
        return accountType == AccountType.CHECKING;
    }

    public boolean isSavingsAccount() {
        return accountType == AccountType.SAVINGS;
    }

    public boolean isCreditAccount() {
        return accountType == AccountType.CREDIT;
    }

    public BigDecimal getOverdraftAmount() {
        if (!hasOverdraft()) {
            return BigDecimal.ZERO;
        }
        return balance.abs();
    }

    public BigDecimal getRemainingCreditLimit() {
        if (!hasCreditLimit()) {
            return BigDecimal.ZERO;
        }
        if (balance.compareTo(BigDecimal.ZERO) >= 0) {
            return creditLimit;
        }
        return creditLimit.add(balance); // balance is negative, so this subtracts used credit
    }

    public BigDecimal getEffectiveBalance() {

        if (hasCreditLimit()) {
            return balance.add(getRemainingCreditLimit());
        }
        return balance;
    }

    public String getMaskedAccountNumber() {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "*".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
    }

    public int getAccountAge() {
        return java.time.Period.between(openingDate, LocalDate.now()).getYears();
    }

    public int getDaysSinceLastTransaction() {
        if (lastTransactionDate == null) {
            return java.time.Period.between(openingDate, LocalDate.now()).getDays();
        }
        return java.time.Period.between(lastTransactionDate, LocalDate.now()).getDays();
    }

    public boolean isDormant(int dormantDays) {
        return getDaysSinceLastTransaction() > dormantDays;
    }

    public static AccountEntity newCheckingAccount(String accountId, String customerId,
                                                   String accountNumber, String currency) {
        return builder()
                .accountId(accountId)
                .customerId(customerId)
                .accountNumber(accountNumber)
                .accountType(AccountType.CHECKING)
                .status(AccountStatus.ACTIVE)
                .currency(currency)
                .balance(BigDecimal.ZERO)
                .openingDate(LocalDate.now())
                .productCode("STANDARD_CHECKING")
                .build();
    }

    public static AccountEntity newSavingsAccount(String accountId, String customerId,
                                                  String accountNumber, String currency,
                                                  BigDecimal initialDeposit) {
        return builder()
                .accountId(accountId)
                .customerId(customerId)
                .accountNumber(accountNumber)
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .currency(currency)
                .balance(initialDeposit)
                .openingDate(LocalDate.now())
                .productCode("STANDARD_SAVINGS")
                .build();
    }

    public static AccountEntity newCreditAccount(String accountId, String customerId,
                                                 String accountNumber, String currency,
                                                 BigDecimal creditLimit) {
        return builder()
                .accountId(accountId)
                .customerId(customerId)
                .accountNumber(accountNumber)
                .accountType(AccountType.CREDIT)
                .status(AccountStatus.ACTIVE)
                .currency(currency)
                .balance(BigDecimal.ZERO)
                .creditLimit(creditLimit)
                .openingDate(LocalDate.now())
                .productCode("STANDARD_CREDIT")
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String accountId;
        private String customerId;
        private String accountNumber;
        private String iban;
        private AccountType accountType;
        private AccountStatus status;
        private String currency;
        private BigDecimal balance;
        private BigDecimal availableBalance;
        private BigDecimal creditLimit;
        private BigDecimal dailyLimit;
        private BigDecimal monthlyLimit;
        private String branchCode;
        private LocalDate openingDate;
        private LocalDate lastTransactionDate;
        private String productCode;
        private String description;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        private Builder() {
        }

        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder accountNumber(String accountNumber) {
            this.accountNumber = accountNumber;
            return this;
        }

        public Builder iban(String iban) {
            this.iban = iban;
            return this;
        }

        public Builder accountType(AccountType accountType) {
            this.accountType = accountType;
            return this;
        }

        public Builder status(AccountStatus status) {
            this.status = status;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder balance(BigDecimal balance) {
            this.balance = balance;
            return this;
        }

        public Builder availableBalance(BigDecimal availableBalance) {
            this.availableBalance = availableBalance;
            return this;
        }

        public Builder creditLimit(BigDecimal creditLimit) {
            this.creditLimit = creditLimit;
            return this;
        }

        public Builder dailyLimit(BigDecimal dailyLimit) {
            this.dailyLimit = dailyLimit;
            return this;
        }

        public Builder monthlyLimit(BigDecimal monthlyLimit) {
            this.monthlyLimit = monthlyLimit;
            return this;
        }

        public Builder branchCode(String branchCode) {
            this.branchCode = branchCode;
            return this;
        }

        public Builder openingDate(LocalDate openingDate) {
            this.openingDate = openingDate;
            return this;
        }

        public Builder lastTransactionDate(LocalDate lastTransactionDate) {
            this.lastTransactionDate = lastTransactionDate;
            return this;
        }

        public Builder productCode(String productCode) {
            this.productCode = productCode;
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

        public AccountEntity build() {
            return new AccountEntity(this);
        }
    }

    public enum AccountType {
        CHECKING("Checking Account"),
        SAVINGS("Savings Account"),
        CREDIT("Credit Account"),
        LOAN("Loan Account"),
        INVESTMENT("Investment Account"),
        BUSINESS("Business Account"),
        JOINT("Joint Account");

        private final String description;

        AccountType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public boolean allowsNegativeBalance() {
            return this == CREDIT || this == LOAN;
        }

        public boolean requiresMinimumBalance() {
            return this == SAVINGS || this == INVESTMENT;
        }
    }

    public enum AccountStatus {
        ACTIVE("Active"),
        INACTIVE("Inactive"),
        SUSPENDED("Suspended"),
        FROZEN("Frozen"),
        CLOSED("Closed"),
        PENDING_CLOSURE("Pending Closure"),
        PENDING_ACTIVATION("Pending Activation");

        private final String description;

        AccountStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public boolean canPerformTransactions() {
            return this == ACTIVE;
        }

        public boolean isFinalStatus() {
            return this == CLOSED;
        }

        public boolean requiresApproval() {
            return this == PENDING_ACTIVATION || this == PENDING_CLOSURE;
        }
    }

    public AccountEntity withBalance(BigDecimal newBalance) {
        return new Builder()
                .accountId(this.accountId)
                .customerId(this.customerId)
                .accountNumber(this.accountNumber)
                .iban(this.iban)
                .accountType(this.accountType)
                .status(this.status)
                .currency(this.currency)
                .balance(newBalance)
                .availableBalance(this.availableBalance)
                .creditLimit(this.creditLimit)
                .dailyLimit(this.dailyLimit)
                .monthlyLimit(this.monthlyLimit)
                .branchCode(this.branchCode)
                .openingDate(this.openingDate)
                .lastTransactionDate(LocalDate.now()) // Update last transaction date
                .productCode(this.productCode)
                .description(this.description)
                .createdAt(this.createdAt)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public AccountEntity withStatus(AccountStatus newStatus) {
        return new Builder()
                .accountId(this.accountId)
                .customerId(this.customerId)
                .accountNumber(this.accountNumber)
                .iban(this.iban)
                .accountType(this.accountType)
                .status(newStatus)
                .currency(this.currency)
                .balance(this.balance)
                .availableBalance(this.availableBalance)
                .creditLimit(this.creditLimit)
                .dailyLimit(this.dailyLimit)
                .monthlyLimit(this.monthlyLimit)
                .branchCode(this.branchCode)
                .openingDate(this.openingDate)
                .lastTransactionDate(this.lastTransactionDate)
                .productCode(this.productCode)
                .description(this.description)
                .createdAt(this.createdAt)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public AccountEntity withLimits(BigDecimal newDailyLimit, BigDecimal newMonthlyLimit) {
        return new Builder()
                .accountId(this.accountId)
                .customerId(this.customerId)
                .accountNumber(this.accountNumber)
                .iban(this.iban)
                .accountType(this.accountType)
                .status(this.status)
                .currency(this.currency)
                .balance(this.balance)
                .availableBalance(this.availableBalance)
                .creditLimit(this.creditLimit)
                .dailyLimit(newDailyLimit)
                .monthlyLimit(newMonthlyLimit)
                .branchCode(this.branchCode)
                .openingDate(this.openingDate)
                .lastTransactionDate(this.lastTransactionDate)
                .productCode(this.productCode)
                .description(this.description)
                .createdAt(this.createdAt)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AccountEntity that = (AccountEntity) o;
        return Objects.equals(accountId, that.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId);
    }

    @Override
    public String toString() {
        return "AccountEntity{" +
                "accountId='" + accountId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", accountNumber='" + getMaskedAccountNumber() + '\'' +
                ", type=" + accountType +
                ", status=" + status +
                ", balance=" + balance + " " + currency +
                ", availableBalance=" + availableBalance + " " + currency +
                ", creditLimit=" + creditLimit + " " + currency +
                '}';
    }


    public String toAuditString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ACC[").append(accountId).append("] ");
        sb.append(getMaskedAccountNumber()).append(" ");
        sb.append(accountType).append(" ");
        sb.append(status).append(" ");
        sb.append("BAL:").append(balance).append(" ").append(currency);
        if (hasCreditLimit()) {
            sb.append(" CL:").append(creditLimit);
        }
        return sb.toString();
    }


    public String getAccountSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(accountType.getDescription());
        sb.append(" (").append(getMaskedAccountNumber()).append(")");
        sb.append("\nBalance: ").append(balance).append(" ").append(currency);
        if (hasCreditLimit()) {
            sb.append("\nAvailable: ").append(getEffectiveBalance()).append(" ").append(currency);
        }
        sb.append("\nStatus: ").append(status.getDescription());
        return sb.toString();
    }
}