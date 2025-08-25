
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;


public final class DatabaseService implements AutoCloseable {

    private final ConnectionManager connectionManager;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    private final AtomicLong totalServiceCalls = new AtomicLong(0);
    private final AtomicLong successfulTransactions = new AtomicLong(0);
    private final AtomicLong failedTransactions = new AtomicLong(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);

    public DatabaseService(ConnectionManager connectionManager) {
        this.connectionManager = Objects.requireNonNull(connectionManager, "ConnectionManager cannot be null");
        this.transactionRepository = new TransactionRepository(connectionManager);
        this.accountRepository = new AccountRepository(connectionManager);
        this.customerRepository = new CustomerRepository(connectionManager);
    }


    public void initializeDatabase() {
        long startTime = System.nanoTime();
        try {
            connectionManager.initialize();

            customerRepository.createTable();
            accountRepository.createTable();
            transactionRepository.createTable();

            recordServiceCall(startTime, true);

        } catch (Exception e) {
            recordServiceCall(startTime, false);
            throw new DataAccessException("Failed to initialize database", e);
        }
    }


    public void dropAllTables() {
        long startTime = System.nanoTime();
        try {

            transactionRepository.dropTable();
            accountRepository.dropTable();
            customerRepository.dropTable();

            recordServiceCall(startTime, true);

        } catch (Exception e) {
            recordServiceCall(startTime, false);
            throw new DataAccessException("Failed to drop tables", e);
        }
    }


    public TransactionRepository getTransactionRepository() {
        return transactionRepository;
    }

    public AccountRepository getAccountRepository() {
        return accountRepository;
    }

    public CustomerRepository getCustomerRepository() {
        return customerRepository;
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }


    public CustomerAccountResult createCustomerWithAccount(CustomerEntity customer,
                                                           AccountEntity account) {
        long startTime = System.nanoTime();
        totalServiceCalls.incrementAndGet();

        try {
            connectionManager.beginTransaction();

            validateNewCustomerAccount(customer, account);

            CustomerEntity savedCustomer = customerRepository.save(customer);

            AccountEntity savedAccount = accountRepository.save(account);

            connectionManager.commitTransaction();
            successfulTransactions.incrementAndGet();

            return new CustomerAccountResult(savedCustomer, savedAccount, true, null);

        } catch (Exception e) {
            connectionManager.rollbackTransaction();
            failedTransactions.incrementAndGet();

            String errorMessage = "Failed to create customer with account: " + e.getMessage();
            return new CustomerAccountResult(customer, account, false, errorMessage);

        } finally {
            recordServiceCall(startTime, true);
        }
    }


    public TransactionResult processTransaction(TransactionEntity transaction) {
        long startTime = System.nanoTime();
        totalServiceCalls.incrementAndGet();

        try {
            connectionManager.beginTransaction();

            TransactionValidationResult validation = validateTransaction(transaction);
            if (!validation.isValid()) {
                return new TransactionResult(transaction, false, validation.getErrorMessage());
            }

            Optional<AccountEntity> accountOpt = accountRepository.findById(transaction.getAccountId());
            if (accountOpt.isEmpty()) {
                return new TransactionResult(transaction, false, "Account not found");
            }

            AccountEntity account = accountOpt.get();

            if (!account.canPerformTransactions()) {
                return new TransactionResult(transaction, false, "Account cannot perform transactions");
            }

            BigDecimal newBalance = calculateNewBalance(account.getBalance(), transaction);

            if (!isTransactionAllowed(account, transaction, newBalance)) {

                TransactionEntity declinedTransaction = transaction.withStatus(
                        TransactionEntity.TransactionStatus.DECLINED).withResponseCode("05");

                transactionRepository.save(declinedTransaction);
                connectionManager.commitTransaction();

                return new TransactionResult(declinedTransaction, true, "Transaction declined due to business rules");
            }

            BigDecimal newAvailableBalance = calculateNewAvailableBalance(account, transaction, newBalance);
            AccountEntity updatedAccount = account.withBalance(newBalance);
            accountRepository.save(updatedAccount);

            TransactionEntity approvedTransaction = transaction.withStatus(
                    TransactionEntity.TransactionStatus.APPROVED).withResponseCode("00");
            transactionRepository.save(approvedTransaction);

            connectionManager.commitTransaction();
            successfulTransactions.incrementAndGet();

            return new TransactionResult(approvedTransaction, true, "Transaction processed successfully");

        } catch (Exception e) {
            connectionManager.rollbackTransaction();
            failedTransactions.incrementAndGet();

            String errorMessage = "Failed to process transaction: " + e.getMessage();
            return new TransactionResult(transaction, false, errorMessage);

        } finally {
            recordServiceCall(startTime, true);
        }
    }


    public TransferResult transferMoney(String fromAccountId, String toAccountId,
                                        BigDecimal amount, String description) {
        long startTime = System.nanoTime();
        totalServiceCalls.incrementAndGet();

        try {
            connectionManager.beginTransaction();

            if (fromAccountId.equals(toAccountId)) {
                return new TransferResult(false, "Cannot transfer to same account", null, null);
            }

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return new TransferResult(false, "Transfer amount must be positive", null, null);
            }

            Optional<AccountEntity> fromAccountOpt = accountRepository.findById(fromAccountId);
            Optional<AccountEntity> toAccountOpt = accountRepository.findById(toAccountId);

            if (fromAccountOpt.isEmpty()) {
                return new TransferResult(false, "Source account not found", null, null);
            }
            if (toAccountOpt.isEmpty()) {
                return new TransferResult(false, "Destination account not found", null, null);
            }

            AccountEntity fromAccount = fromAccountOpt.get();
            AccountEntity toAccount = toAccountOpt.get();

            if (!fromAccount.canPerformTransactions() || !toAccount.canPerformTransactions()) {
                return new TransferResult(false, "One or both accounts cannot perform transactions", null, null);
            }

            BigDecimal newFromBalance = fromAccount.getBalance().subtract(amount);
            if (newFromBalance.compareTo(fromAccount.getCreditLimit().negate()) < 0) {
                return new TransferResult(false, "Insufficient funds", null, null);
            }

            TransactionEntity debitTransaction = TransactionEntity.builder()
                    .transactionId("TXN_DEBIT_" + System.currentTimeMillis())
                    .cardNumber("INTERNAL_TRANSFER")
                    .accountId(fromAccountId)
                    .customerId(fromAccount.getCustomerId())
                    .amount(amount)
                    .currency(fromAccount.getCurrency())
                    .merchantName("INTERNAL_TRANSFER")
                    .transactionDateTime(LocalDateTime.now())
                    .transactionType(TransactionEntity.TransactionType.TRANSFER)
                    .status(TransactionEntity.TransactionStatus.APPROVED)
                    .responseCode("00")
                    .description(description != null ? description : "Transfer to " + toAccountId)
                    .build();

            TransactionEntity creditTransaction = TransactionEntity.builder()
                    .transactionId("TXN_CREDIT_" + System.currentTimeMillis())
                    .cardNumber("INTERNAL_TRANSFER")
                    .accountId(toAccountId)
                    .customerId(toAccount.getCustomerId())
                    .amount(amount)
                    .currency(toAccount.getCurrency())
                    .merchantName("INTERNAL_TRANSFER")
                    .transactionDateTime(LocalDateTime.now())
                    .transactionType(TransactionEntity.TransactionType.TRANSFER)
                    .status(TransactionEntity.TransactionStatus.APPROVED)
                    .responseCode("00")
                    .description(description != null ? description : "Transfer from " + fromAccountId)
                    .build();

            accountRepository.debitAccount(fromAccountId, amount);
            accountRepository.creditAccount(toAccountId, amount);

            transactionRepository.save(debitTransaction);
            transactionRepository.save(creditTransaction);

            connectionManager.commitTransaction();
            successfulTransactions.incrementAndGet();

            return new TransferResult(true, "Transfer completed successfully",
                    debitTransaction, creditTransaction);

        } catch (Exception e) {
            connectionManager.rollbackTransaction();
            failedTransactions.incrementAndGet();

            String errorMessage = "Failed to transfer money: " + e.getMessage();
            return new TransferResult(false, errorMessage, null, null);

        } finally {
            recordServiceCall(startTime, true);
        }
    }


    public CustomerProfile getCustomerProfile(String customerId) {
        long startTime = System.nanoTime();
        totalServiceCalls.incrementAndGet();

        try {
            Optional<CustomerEntity> customerOpt = customerRepository.findById(customerId);
            if (customerOpt.isEmpty()) {
                return new CustomerProfile(null, Collections.emptyList(),
                        Collections.emptyList(), false, "Customer not found");
            }

            CustomerEntity customer = customerOpt.get();
            List<AccountEntity> accounts = accountRepository.findByCustomerId(customerId);

            List<TransactionEntity> recentTransactions = new ArrayList<>();
            for (AccountEntity account : accounts) {
                List<TransactionEntity> accountTransactions = transactionRepository.findByAccountId(account.getAccountId());
                recentTransactions.addAll(accountTransactions.stream().limit(10).toList());
            }

            recentTransactions.sort((t1, t2) -> t2.getTransactionDateTime().compareTo(t1.getTransactionDateTime()));

            recordServiceCall(startTime, true);
            return new CustomerProfile(customer, accounts, recentTransactions, true, null);

        } catch (Exception e) {
            recordServiceCall(startTime, false);
            String errorMessage = "Failed to get customer profile: " + e.getMessage();
            return new CustomerProfile(null, Collections.emptyList(),
                    Collections.emptyList(), false, errorMessage);
        }
    }


    public AccountStatement getAccountStatement(String accountId, LocalDate startDate, LocalDate endDate) {
        long startTime = System.nanoTime();
        totalServiceCalls.incrementAndGet();

        try {
            Optional<AccountEntity> accountOpt = accountRepository.findById(accountId);
            if (accountOpt.isEmpty()) {
                return new AccountStatement(null, Collections.emptyList(), false, "Account not found");
            }

            AccountEntity account = accountOpt.get();

            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

            List<TransactionEntity> transactions = transactionRepository.findByDateRange(startDateTime, endDateTime)
                    .stream()
                    .filter(t -> t.getAccountId().equals(accountId))
                    .sorted((t1, t2) -> t1.getTransactionDateTime().compareTo(t2.getTransactionDateTime()))
                    .toList();

            recordServiceCall(startTime, true);
            return new AccountStatement(account, transactions, true, null);

        } catch (Exception e) {
            recordServiceCall(startTime, false);
            String errorMessage = "Failed to get account statement: " + e.getMessage();
            return new AccountStatement(null, Collections.emptyList(), false, errorMessage);
        }
    }


    public BatchProcessResult processBatchTransactions(List<TransactionEntity> transactions) {
        long startTime = System.nanoTime();
        totalServiceCalls.incrementAndGet();

        List<TransactionEntity> successful = new ArrayList<>();
        List<TransactionEntity> failed = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try {
            connectionManager.beginTransaction();

            for (TransactionEntity transaction : transactions) {
                try {
                    TransactionResult result = processTransactionInternal(transaction);
                    if (result.isSuccessful()) {
                        successful.add(result.getTransaction());
                    } else {
                        failed.add(transaction);
                        errors.add(result.getErrorMessage());
                    }
                } catch (Exception e) {
                    failed.add(transaction);
                    errors.add(e.getMessage());
                }
            }

            connectionManager.commitTransaction();
            successfulTransactions.incrementAndGet();

            recordServiceCall(startTime, true);
            return new BatchProcessResult(successful, failed, errors, true, null);

        } catch (Exception e) {
            connectionManager.rollbackTransaction();
            failedTransactions.incrementAndGet();
            recordServiceCall(startTime, false);

            return new BatchProcessResult(Collections.emptyList(), transactions,
                    List.of(e.getMessage()), false, "Batch processing failed");
        }
    }


    public DatabaseHealthStatus getHealthStatus() {
        try {
            boolean connectionHealthy = connectionManager.isHealthy();
            boolean transactionRepoHealthy = transactionRepository.isHealthy();
            boolean accountRepoHealthy = accountRepository.isHealthy();
            boolean customerRepoHealthy = customerRepository.isHealthy();

            boolean overallHealthy = connectionHealthy && transactionRepoHealthy &&
                    accountRepoHealthy && customerRepoHealthy;

            Map<String, Boolean> componentHealth = Map.of(
                    "ConnectionManager", connectionHealthy,
                    "TransactionRepository", transactionRepoHealthy,
                    "AccountRepository", accountRepoHealthy,
                    "CustomerRepository", customerRepoHealthy
            );

            DatabaseServiceStatistics stats = getServiceStatistics();

            return new DatabaseHealthStatus(overallHealthy, componentHealth, stats,
                    LocalDateTime.now());

        } catch (Exception e) {
            Map<String, Boolean> componentHealth = Map.of(
                    "ConnectionManager", false,
                    "TransactionRepository", false,
                    "AccountRepository", false,
                    "CustomerRepository", false
            );

            return new DatabaseHealthStatus(false, componentHealth, null,
                    LocalDateTime.now());
        }
    }


    public DatabaseServiceStatistics getServiceStatistics() {
        ConnectionManager.ConnectionPoolStatistics poolStats = connectionManager.getStatistics();
        CrudRepository.RepositoryStatistics transactionStats = transactionRepository.getStatistics();
        CrudRepository.RepositoryStatistics accountStats = accountRepository.getStatistics();
        CrudRepository.RepositoryStatistics customerStats = customerRepository.getStatistics();

        long totalCalls = totalServiceCalls.get();
        long avgExecutionTime = totalCalls == 0 ? 0 : totalExecutionTime.get() / totalCalls;

        return new DatabaseServiceStatistics(
                totalCalls,
                successfulTransactions.get(),
                failedTransactions.get(),
                avgExecutionTime,
                poolStats,
                transactionStats,
                accountStats,
                customerStats
        );
    }


    public CompletableFuture<DatabaseHealthStatus> getHealthStatusAsync() {
        return CompletableFuture.supplyAsync(this::getHealthStatus);
    }


    private void validateNewCustomerAccount(CustomerEntity customer, AccountEntity account) {
        if (!customer.getCustomerId().equals(account.getCustomerId())) {
            throw new IllegalArgumentException("Customer ID mismatch between customer and account");
        }

        if (!customer.isActive()) {
            throw new IllegalArgumentException("Customer must be active to create account");
        }

        if (customerRepository.isEmailExists(customer.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + customer.getEmail());
        }

        if (customerRepository.isNationalIdExists(customer.getNationalId())) {
            throw new IllegalArgumentException("National ID already exists");
        }

        if (accountRepository.isAccountNumberExists(account.getAccountNumber())) {
            throw new IllegalArgumentException("Account number already exists: " + account.getAccountNumber());
        }
    }

    private TransactionValidationResult validateTransaction(TransactionEntity transaction) {
        if (transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return new TransactionValidationResult(false, "Transaction amount must be positive");
        }

        if (transaction.getTransactionDateTime().isAfter(LocalDateTime.now().plusMinutes(5))) {
            return new TransactionValidationResult(false, "Transaction datetime cannot be in future");
        }

        return new TransactionValidationResult(true, null);
    }

    private BigDecimal calculateNewBalance(BigDecimal currentBalance, TransactionEntity transaction) {
        switch (transaction.getTransactionType()) {
            case PURCHASE:
            case CASH_ADVANCE:
            case WITHDRAWAL:
            case TRANSFER:
                return currentBalance.subtract(transaction.getAmount());
            case REFUND:
            case DEPOSIT:
            case PAYMENT:
                return currentBalance.add(transaction.getAmount());
            default:
                return currentBalance;
        }
    }

    private BigDecimal calculateNewAvailableBalance(AccountEntity account, TransactionEntity transaction,
                                                    BigDecimal newBalance) {

        return newBalance.add(account.getCreditLimit());
    }

    private boolean isTransactionAllowed(AccountEntity account, TransactionEntity transaction,
                                         BigDecimal newBalance) {

        BigDecimal minAllowedBalance = account.getCreditLimit().negate();
        if (newBalance.compareTo(minAllowedBalance) < 0) {
            return false;
        }

        BigDecimal dailyLimit = account.getDailyLimit();
        if (transaction.getAmount().compareTo(dailyLimit) > 0) {
            return false;
        }

        return true;
    }

    private TransactionResult processTransactionInternal(TransactionEntity transaction) {


        try {
            TransactionEntity savedTransaction = transactionRepository.save(transaction);
            return new TransactionResult(savedTransaction, true, "Transaction processed");
        } catch (Exception e) {
            return new TransactionResult(transaction, false, e.getMessage());
        }
    }

    private void recordServiceCall(long startTime, boolean successful) {
        long executionTime = System.nanoTime() - startTime;
        totalExecutionTime.addAndGet(executionTime);

        if (!successful) {
            failedTransactions.incrementAndGet();
        }
    }

    @Override
    public void close() {
        try {
            connectionManager.close();
        } catch (Exception e) {

        }
    }


    public static final class CustomerAccountResult {
        private final CustomerEntity customer;
        private final AccountEntity account;
        private final boolean successful;
        private final String errorMessage;

        public CustomerAccountResult(CustomerEntity customer, AccountEntity account,
                                     boolean successful, String errorMessage) {
            this.customer = customer;
            this.account = account;
            this.successful = successful;
            this.errorMessage = errorMessage;
        }

        public CustomerEntity getCustomer() {
            return customer;
        }

        public AccountEntity getAccount() {
            return account;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static final class TransactionResult {
        private final TransactionEntity transaction;
        private final boolean successful;
        private final String errorMessage;

        public TransactionResult(TransactionEntity transaction, boolean successful, String errorMessage) {
            this.transaction = transaction;
            this.successful = successful;
            this.errorMessage = errorMessage;
        }

        public TransactionEntity getTransaction() {
            return transaction;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static final class TransferResult {
        private final boolean successful;
        private final String message;
        private final TransactionEntity debitTransaction;
        private final TransactionEntity creditTransaction;

        public TransferResult(boolean successful, String message,
                              TransactionEntity debitTransaction, TransactionEntity creditTransaction) {
            this.successful = successful;
            this.message = message;
            this.debitTransaction = debitTransaction;
            this.creditTransaction = creditTransaction;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getMessage() {
            return message;
        }

        public TransactionEntity getDebitTransaction() {
            return debitTransaction;
        }

        public TransactionEntity getCreditTransaction() {
            return creditTransaction;
        }
    }

    public static final class CustomerProfile {
        private final CustomerEntity customer;
        private final List<AccountEntity> accounts;
        private final List<TransactionEntity> recentTransactions;
        private final boolean successful;
        private final String errorMessage;

        public CustomerProfile(CustomerEntity customer, List<AccountEntity> accounts,
                               List<TransactionEntity> recentTransactions,
                               boolean successful, String errorMessage) {
            this.customer = customer;
            this.accounts = accounts;
            this.recentTransactions = recentTransactions;
            this.successful = successful;
            this.errorMessage = errorMessage;
        }

        public CustomerEntity getCustomer() {
            return customer;
        }

        public List<AccountEntity> getAccounts() {
            return accounts;
        }

        public List<TransactionEntity> getRecentTransactions() {
            return recentTransactions;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static final class AccountStatement {
        private final AccountEntity account;
        private final List<TransactionEntity> transactions;
        private final boolean successful;
        private final String errorMessage;

        public AccountStatement(AccountEntity account, List<TransactionEntity> transactions,
                                boolean successful, String errorMessage) {
            this.account = account;
            this.transactions = transactions;
            this.successful = successful;
            this.errorMessage = errorMessage;
        }

        public AccountEntity getAccount() {
            return account;
        }

        public List<TransactionEntity> getTransactions() {
            return transactions;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static final class BatchProcessResult {
        private final List<TransactionEntity> successful;
        private final List<TransactionEntity> failed;
        private final List<String> errors;
        private final boolean overallSuccess;
        private final String errorMessage;

        public BatchProcessResult(List<TransactionEntity> successful, List<TransactionEntity> failed,
                                  List<String> errors, boolean overallSuccess, String errorMessage) {
            this.successful = successful;
            this.failed = failed;
            this.errors = errors;
            this.overallSuccess = overallSuccess;
            this.errorMessage = errorMessage;
        }

        public List<TransactionEntity> getSuccessful() {
            return successful;
        }

        public List<TransactionEntity> getFailed() {
            return failed;
        }

        public List<String> getErrors() {
            return errors;
        }

        public boolean isOverallSuccess() {
            return overallSuccess;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public int getSuccessCount() {
            return successful.size();
        }

        public int getFailedCount() {
            return failed.size();
        }

        public double getSuccessRate() {
            int total = successful.size() + failed.size();
            return total == 0 ? 0.0 : (double) successful.size() / total * 100.0;
        }
    }

    private static final class TransactionValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public TransactionValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static final class DatabaseHealthStatus {
        private final boolean healthy;
        private final Map<String, Boolean> componentHealth;
        private final DatabaseServiceStatistics statistics;
        private final LocalDateTime checkTime;

        public DatabaseHealthStatus(boolean healthy, Map<String, Boolean> componentHealth,
                                    DatabaseServiceStatistics statistics, LocalDateTime checkTime) {
            this.healthy = healthy;
            this.componentHealth = componentHealth;
            this.statistics = statistics;
            this.checkTime = checkTime;
        }

        public boolean isHealthy() {
            return healthy;
        }

        public Map<String, Boolean> getComponentHealth() {
            return componentHealth;
        }

        public DatabaseServiceStatistics getStatistics() {
            return statistics;
        }

        public LocalDateTime getCheckTime() {
            return checkTime;
        }

        @Override
        public String toString() {
            return "DatabaseHealthStatus{" +
                    "healthy=" + healthy +
                    ", components=" + componentHealth +
                    ", checkTime=" + checkTime +
                    '}';
        }
    }

    public static final class DatabaseServiceStatistics {
        private final long totalServiceCalls;
        private final long successfulTransactions;
        private final long failedTransactions;
        private final long averageExecutionTime;
        private final ConnectionManager.ConnectionPoolStatistics poolStatistics;
        private final CrudRepository.RepositoryStatistics transactionStats;
        private final CrudRepository.RepositoryStatistics accountStats;
        private final CrudRepository.RepositoryStatistics customerStats;

        public DatabaseServiceStatistics(long totalServiceCalls, long successfulTransactions,
                                         long failedTransactions, long averageExecutionTime,
                                         ConnectionManager.ConnectionPoolStatistics poolStatistics,
                                         CrudRepository.RepositoryStatistics transactionStats,
                                         CrudRepository.RepositoryStatistics accountStats,
                                         CrudRepository.RepositoryStatistics customerStats) {
            this.totalServiceCalls = totalServiceCalls;
            this.successfulTransactions = successfulTransactions;
            this.failedTransactions = failedTransactions;
            this.averageExecutionTime = averageExecutionTime;
            this.poolStatistics = poolStatistics;
            this.transactionStats = transactionStats;
            this.accountStats = accountStats;
            this.customerStats = customerStats;
        }

        public long getTotalServiceCalls() {
            return totalServiceCalls;
        }

        public long getSuccessfulTransactions() {
            return successfulTransactions;
        }

        public long getFailedTransactions() {
            return failedTransactions;
        }

        public long getAverageExecutionTime() {
            return averageExecutionTime;
        }

        public ConnectionManager.ConnectionPoolStatistics getPoolStatistics() {
            return poolStatistics;
        }

        public CrudRepository.RepositoryStatistics getTransactionStats() {
            return transactionStats;
        }

        public CrudRepository.RepositoryStatistics getAccountStats() {
            return accountStats;
        }

        public CrudRepository.RepositoryStatistics getCustomerStats() {
            return customerStats;
        }

        public double getSuccessRate() {
            return totalServiceCalls == 0 ? 0.0 : (double) successfulTransactions / totalServiceCalls * 100.0;
        }

        public double getAverageExecutionTimeMs() {
            return averageExecutionTime / 1_000_000.0;
        }

        @Override
        public String toString() {
            return "DatabaseServiceStatistics{" +
                    "totalCalls=" + totalServiceCalls +
                    ", successRate=" + String.format("%.1f%%", getSuccessRate()) +
                    ", avgTime=" + String.format("%.2fms", getAverageExecutionTimeMs()) +
                    ", poolStats=" + poolStatistics +
                    '}';
        }
    }
}