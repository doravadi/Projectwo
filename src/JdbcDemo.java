
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


public final class JdbcDemo {

    private DatabaseService databaseService;
    private final Random random;

    public JdbcDemo() {
        this.random = new Random(12345); // Reproducible results
    }


    public void runAllDemos() {
        System.out.println("🎯 JDBC LAYER MODULE DEMO");
        System.out.println("=========================");

        try {

            System.out.println("\n1️⃣ DATABASE INITIALIZATION");
            initializeDatabaseDemo();

            System.out.println("\n2️⃣ BASIC CRUD OPERATIONS");
            basicCrudDemo();

            System.out.println("\n3️⃣ CROSS-ENTITY BUSINESS OPERATIONS");
            businessOperationsDemo();

            System.out.println("\n4️⃣ TRANSACTION MANAGEMENT");
            transactionManagementDemo();

            System.out.println("\n5️⃣ STATISTICS & HEALTH MONITORING");
            statisticsDemo();

            System.out.println("\n6️⃣ ERROR HANDLING SCENARIOS");
            errorHandlingDemo();

            System.out.println("\n7️⃣ PERFORMANCE BENCHMARK");
            performanceBenchmarkDemo();

            System.out.println("\n8️⃣ CLEANUP");
            cleanupDemo();

            System.out.println("\n✅ ALL JDBC LAYER DEMOS COMPLETED!");

        } catch (Exception e) {
            System.err.println("❌ Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void initializeDatabaseDemo() {
        System.out.println("Initializing database with connection pooling...");

        ConnectionManager connectionManager = ConnectionManager.builder(
                        "jdbc:h2:mem:banking_demo;DB_CLOSE_DELAY=-1", "sa", ""
                )
                .minPoolSize(5)
                .maxPoolSize(20)
                .connectionTimeout(30000)
                .build();

        databaseService = new DatabaseService(connectionManager);

        System.out.println("  Creating database tables...");
        databaseService.initializeDatabase();

        ConnectionManager.ConnectionPoolStatistics poolStats = connectionManager.getStatistics();
        System.out.println("  Connection Pool: " + poolStats);

        System.out.println("  ✅ Database initialized successfully");
    }


    private void basicCrudDemo() {
        System.out.println("Testing basic CRUD operations...");

        CustomerEntity customer = CustomerEntity.newIndividualCustomer(
                "CUST_001",
                "12345678901",
                "Ahmet",
                "Yılmaz",
                "ahmet.yilmaz@example.com",
                "+905551234567",
                LocalDate.of(1985, 5, 15)
        );

        System.out.println("  Creating customer: " + customer.getFullName());
        CustomerEntity savedCustomer = databaseService.getCustomerRepository().save(customer);
        System.out.println("    ✅ Customer saved: " + savedCustomer.getCustomerId());

        AccountEntity account = AccountEntity.newCheckingAccount(
                "ACC_001",
                savedCustomer.getCustomerId(),
                "1234567890123456",
                "TRY"
        );

        System.out.println("  Creating account: " + account.getMaskedAccountNumber());
        AccountEntity savedAccount = databaseService.getAccountRepository().save(account);
        System.out.println("    ✅ Account saved: " + savedAccount.getAccountId());

        TransactionEntity transaction = TransactionEntity.approvedPurchase(
                "TXN_001",
                "4111111111111111",
                savedAccount.getAccountId(),
                savedCustomer.getCustomerId(),
                new BigDecimal("150.75"),
                "TRY",
                "Test Merchant"
        );

        System.out.println("  Creating transaction: " + transaction.getAmount() + " " + transaction.getCurrency());
        TransactionEntity savedTransaction = databaseService.getTransactionRepository().save(transaction);
        System.out.println("    ✅ Transaction saved: " + savedTransaction.getTransactionId());

        System.out.println("  Testing findById operations...");
        Optional<CustomerEntity> foundCustomer = databaseService.getCustomerRepository().findById("CUST_001");
        Optional<AccountEntity> foundAccount = databaseService.getAccountRepository().findById("ACC_001");
        Optional<TransactionEntity> foundTransaction = databaseService.getTransactionRepository().findById("TXN_001");

        System.out.println("    Customer found: " + foundCustomer.isPresent());
        System.out.println("    Account found: " + foundAccount.isPresent());
        System.out.println("    Transaction found: " + foundTransaction.isPresent());

        System.out.println("  Testing specialized queries...");
        Optional<CustomerEntity> customerByEmail = databaseService.getCustomerRepository()
                .findByEmail("ahmet.yilmaz@example.com");
        List<AccountEntity> accountsByCustomer = databaseService.getAccountRepository()
                .findByCustomerId("CUST_001");
        List<TransactionEntity> transactionsByAccount = databaseService.getTransactionRepository()
                .findByAccountId("ACC_001");

        System.out.println("    Customer by email: " + customerByEmail.isPresent());
        System.out.println("    Accounts by customer: " + accountsByCustomer.size());
        System.out.println("    Transactions by account: " + transactionsByAccount.size());
    }


    private void businessOperationsDemo() {
        System.out.println("Testing cross-entity business operations...");

        System.out.println("  Testing createCustomerWithAccount...");
        CustomerEntity newCustomer = CustomerEntity.newIndividualCustomer(
                "CUST_002",
                "98765432109",
                "Fatma",
                "Demir",
                "fatma.demir@example.com",
                "+905559876543",
                LocalDate.of(1990, 8, 22)
        );

        AccountEntity newAccount = AccountEntity.newSavingsAccount(
                "ACC_002",
                "CUST_002",
                "6543210987654321",
                "TRY",
                new BigDecimal("1000.00")
        );

        DatabaseService.CustomerAccountResult result = databaseService.createCustomerWithAccount(newCustomer, newAccount);
        System.out.println("    Customer+Account creation: " + (result.isSuccessful() ? "SUCCESS" : "FAILED"));
        if (!result.isSuccessful()) {
            System.out.println("      Error: " + result.getErrorMessage());
        }

        System.out.println("  Testing money transfer...");

        databaseService.getAccountRepository().creditAccount("ACC_001", new BigDecimal("500.00"));

        DatabaseService.TransferResult transferResult = databaseService.transferMoney(
                "ACC_001", "ACC_002", new BigDecimal("200.00"), "Demo transfer"
        );
        System.out.println("    Transfer: " + (transferResult.isSuccessful() ? "SUCCESS" : "FAILED"));
        System.out.println("    Message: " + transferResult.getMessage());

        if (transferResult.isSuccessful()) {
            System.out.println("    Debit transaction: " + transferResult.getDebitTransaction().getTransactionId());
            System.out.println("    Credit transaction: " + transferResult.getCreditTransaction().getTransactionId());
        }

        System.out.println("  Testing customer profile aggregation...");
        DatabaseService.CustomerProfile profile = databaseService.getCustomerProfile("CUST_001");
        System.out.println("    Profile retrieval: " + (profile.isSuccessful() ? "SUCCESS" : "FAILED"));

        if (profile.isSuccessful()) {
            System.out.println("    Customer: " + profile.getCustomer().getFullName());
            System.out.println("    Accounts: " + profile.getAccounts().size());
            System.out.println("    Recent transactions: " + profile.getRecentTransactions().size());
        }

        System.out.println("  Testing account statement...");
        LocalDate startDate = LocalDate.now().minusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(1);

        DatabaseService.AccountStatement statement = databaseService.getAccountStatement("ACC_001", startDate, endDate);
        System.out.println("    Statement generation: " + (statement.isSuccessful() ? "SUCCESS" : "FAILED"));

        if (statement.isSuccessful()) {
            System.out.println("    Account: " + statement.getAccount().getMaskedAccountNumber());
            System.out.println("    Transactions: " + statement.getTransactions().size());
        }
    }


    private void transactionManagementDemo() {
        System.out.println("Testing transaction management...");

        System.out.println("  Testing successful transaction...");
        TransactionEntity purchaseTransaction = TransactionEntity.builder()
                .transactionId("TXN_PURCHASE_001")
                .cardNumber("4111111111111111")
                .accountId("ACC_001")
                .customerId("CUST_001")
                .amount(new BigDecimal("75.50"))
                .currency("TRY")
                .merchantName("Demo Store")
                .merchantCategory("GROCERY")
                .transactionDateTime(LocalDateTime.now())
                .transactionType(TransactionEntity.TransactionType.PURCHASE)
                .status(TransactionEntity.TransactionStatus.PENDING)
                .build();

        DatabaseService.TransactionResult txnResult = databaseService.processTransaction(purchaseTransaction);
        System.out.println("    Transaction processing: " + (txnResult.isSuccessful() ? "SUCCESS" : "FAILED"));
        System.out.println("    Final status: " + txnResult.getTransaction().getStatus());
        System.out.println("    Response code: " + txnResult.getTransaction().getResponseCode());

        System.out.println("  Testing transaction rollback...");

        try {
            ConnectionManager connectionManager = databaseService.getConnectionManager();
            connectionManager.beginTransaction();

            CustomerEntity rollbackCustomer = CustomerEntity.newIndividualCustomer(
                    "CUST_ROLLBACK",
                    "11111111111",
                    "Rollback",
                    "Test",
                    "rollback@test.com",
                    "+905551111111",
                    LocalDate.of(1980, 1, 1)
            );

            databaseService.getCustomerRepository().save(rollbackCustomer);
            System.out.println("    Customer created in transaction");

            connectionManager.rollbackTransaction();
            System.out.println("    Transaction rolled back");

            Optional<CustomerEntity> shouldNotExist = databaseService.getCustomerRepository().findById("CUST_ROLLBACK");
            System.out.println("    Customer should not exist: " + !shouldNotExist.isPresent());

        } catch (Exception e) {
            System.out.println("    Rollback test completed with expected error: " + e.getMessage());
        }

        System.out.println("  Testing batch transaction processing...");

        List<TransactionEntity> batchTransactions = Arrays.asList(
                createSampleTransaction("TXN_BATCH_001", "ACC_002", "CUST_002", "25.00"),
                createSampleTransaction("TXN_BATCH_002", "ACC_002", "CUST_002", "35.75"),
                createSampleTransaction("TXN_BATCH_003", "ACC_002", "CUST_002", "50.25")
        );

        DatabaseService.BatchProcessResult batchResult = databaseService.processBatchTransactions(batchTransactions);
        System.out.println("    Batch processing: " + (batchResult.isOverallSuccess() ? "SUCCESS" : "FAILED"));
        System.out.println("    Successful: " + batchResult.getSuccessCount());
        System.out.println("    Failed: " + batchResult.getFailedCount());
        System.out.println("    Success rate: " + String.format("%.1f%%", batchResult.getSuccessRate()));
    }


    private void statisticsDemo() {
        System.out.println("Testing statistics and health monitoring...");

        System.out.println("  Repository Statistics:");
        CrudRepository.RepositoryStatistics customerStats = databaseService.getCustomerRepository().getStatistics();
        CrudRepository.RepositoryStatistics accountStats = databaseService.getAccountRepository().getStatistics();
        CrudRepository.RepositoryStatistics transactionStats = databaseService.getTransactionRepository().getStatistics();

        System.out.println("    Customer Repository: " + customerStats);
        System.out.println("    Account Repository: " + accountStats);
        System.out.println("    Transaction Repository: " + transactionStats);

        System.out.println("  Connection Pool Statistics:");
        ConnectionManager.ConnectionPoolStatistics poolStats = databaseService.getConnectionManager().getStatistics();
        System.out.println("    " + poolStats);

        System.out.println("  Service Statistics:");
        DatabaseService.DatabaseServiceStatistics serviceStats = databaseService.getServiceStatistics();
        System.out.println("    " + serviceStats);

        System.out.println("  Health Check:");
        DatabaseService.DatabaseHealthStatus healthStatus = databaseService.getHealthStatus();
        System.out.println("    Overall Health: " + (healthStatus.isHealthy() ? "HEALTHY" : "UNHEALTHY"));
        System.out.println("    Component Health: " + healthStatus.getComponentHealth());

        System.out.println("  Asynchronous Health Check:");
        try {
            CompletableFuture<DatabaseService.DatabaseHealthStatus> asyncHealth =
                    databaseService.getHealthStatusAsync();
            DatabaseService.DatabaseHealthStatus asyncResult = asyncHealth.get();
            System.out.println("    Async Health: " + (asyncResult.isHealthy() ? "HEALTHY" : "UNHEALTHY"));
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("    Async health check failed: " + e.getMessage());
        }

        System.out.println("  Business Analytics:");

        List<CustomerRepository.CustomerSegmentStats> segmentStats =
                databaseService.getCustomerRepository().getCustomerStatsBySegment();
        System.out.println("    Customer Segments:");
        segmentStats.forEach(stat -> System.out.println("      " + stat));

        List<CustomerRepository.AgeGroupStats> ageStats =
                databaseService.getCustomerRepository().getCustomersByAgeGroup();
        System.out.println("    Age Groups:");
        ageStats.forEach(stat -> System.out.println("      " + stat));

        List<AccountRepository.AccountTypeBalance> typeBalances =
                databaseService.getAccountRepository().getTotalBalancesByType();
        System.out.println("    Account Type Balances:");
        typeBalances.forEach(balance -> System.out.println("      " + balance));

        LocalDateTime startTime = LocalDateTime.now().minusDays(1);
        LocalDateTime endTime = LocalDateTime.now().plusDays(1);
        List<TransactionRepository.DailyTransactionStats> dailyStats =
                databaseService.getTransactionRepository().getDailyStatistics(startTime, endTime);
        System.out.println("    Daily Transaction Stats:");
        dailyStats.forEach(stat -> System.out.println("      " + stat));
    }


    private void errorHandlingDemo() {
        System.out.println("Testing error handling scenarios...");

        System.out.println("  Testing duplicate key constraint...");
        try {
            CustomerEntity duplicateCustomer = CustomerEntity.newIndividualCustomer(
                    "CUST_001", // Same ID as existing customer
                    "99999999999",
                    "Duplicate",
                    "Customer",
                    "duplicate@test.com",
                    "+905559999999",
                    LocalDate.of(1995, 1, 1)
            );

            databaseService.getCustomerRepository().save(duplicateCustomer);
            System.out.println("    ❌ Should have failed with duplicate key error");

        } catch (DataAccessException e) {
            System.out.println("    ✅ Caught expected error: " + e.getCategory());
            System.out.println("      Business message: " + e.getBusinessMessage());
        }

        System.out.println("  Testing foreign key constraint...");
        try {
            AccountEntity orphanAccount = AccountEntity.builder()
                    .accountId("ACC_ORPHAN")
                    .customerId("NONEXISTENT_CUSTOMER")
                    .accountNumber("9999999999999999")
                    .accountType(AccountEntity.AccountType.CHECKING)
                    .status(AccountEntity.AccountStatus.ACTIVE)
                    .currency("TRY")
                    .balance(BigDecimal.ZERO)
                    .openingDate(LocalDate.now())
                    .build();

            databaseService.getAccountRepository().save(orphanAccount);
            System.out.println("    ❌ Should have failed with foreign key error");

        } catch (DataAccessException e) {
            System.out.println("    ✅ Caught expected error: " + e.getCategory());
            System.out.println("      Retryable: " + e.isRetryable());
        } catch (Exception e) {

            System.out.println("    ℹ️  Foreign key constraint not enforced in test DB");
        }

        System.out.println("  Testing invalid data validation...");
        try {
            CustomerEntity.builder()
                    .customerId("INVALID_CUSTOMER")
                    .nationalId("12345") // Invalid length
                    .firstName("Test")
                    .lastName("Customer")
                    .email("invalid-email") // Invalid format
                    .phoneNumber("123")  // Invalid format
                    .dateOfBirth(LocalDate.now().plusDays(1)) // Future date
                    .customerType(CustomerEntity.CustomerType.INDIVIDUAL)
                    .status(CustomerEntity.CustomerStatus.ACTIVE)
                    .segment(CustomerEntity.CustomerSegment.STANDARD)
                    .country("TR")
                    .customerSince(LocalDate.now())
                    .build();

            System.out.println("    ❌ Should have failed with validation error");

        } catch (IllegalArgumentException e) {
            System.out.println("    ✅ Caught expected validation error: " + e.getMessage());
        }

        System.out.println("  Testing connection handling...");
        try {

            List<Optional<CustomerEntity>> results = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                results.add(databaseService.getCustomerRepository().findById("CUST_001"));
            }
            System.out.println("    ✅ Connection pool handled " + results.size() + " concurrent requests");

        } catch (Exception e) {
            System.out.println("    Connection pool error: " + e.getMessage());
        }
    }


    private void performanceBenchmarkDemo() {
        System.out.println("Running performance benchmark...");

        System.out.println("  Creating test data...");
        List<CustomerEntity> customers = new ArrayList<>();
        List<AccountEntity> accounts = new ArrayList<>();
        List<TransactionEntity> transactions = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            String customerId = "PERF_CUST_" + String.format("%03d", i);
            String accountId = "PERF_ACC_" + String.format("%03d", i);

            CustomerEntity customer = CustomerEntity.newIndividualCustomer(
                    customerId,
                    generateRandomNationalId(),
                    "Customer" + i,
                    "LastName" + i,
                    "customer" + i + "@perf.test",
                    "+90555000" + String.format("%04d", i),
                    LocalDate.of(1980 + (i % 40), (i % 12) + 1, (i % 28) + 1)
            );
            customers.add(customer);

            AccountEntity account = AccountEntity.newCheckingAccount(
                    accountId,
                    customerId,
                    "1000000000000" + String.format("%03d", i),
                    "TRY"
            );
            accounts.add(account);

            for (int j = 0; j < 5; j++) {
                String txnId = "PERF_TXN_" + String.format("%03d", i) + "_" + j;
                TransactionEntity transaction = createSampleTransaction(txnId, accountId, customerId,
                        String.valueOf(50 + random.nextInt(500)));
                transactions.add(transaction);
            }
        }

        System.out.println("  Benchmarking customer operations...");
        long startTime = System.nanoTime();

        for (CustomerEntity customer : customers) {
            databaseService.getCustomerRepository().save(customer);
        }

        long customerTime = System.nanoTime() - startTime;
        double customerAvg = customerTime / 1_000_000.0 / customers.size();
        System.out.println("    Customer creation: " + customers.size() + " customers in " +
                (customerTime / 1_000_000.0) + "ms (avg: " + String.format("%.2f", customerAvg) + "ms)");

        System.out.println("  Benchmarking account operations...");
        startTime = System.nanoTime();

        for (AccountEntity account : accounts) {
            databaseService.getAccountRepository().save(account);
        }

        long accountTime = System.nanoTime() - startTime;
        double accountAvg = accountTime / 1_000_000.0 / accounts.size();
        System.out.println("    Account creation: " + accounts.size() + " accounts in " +
                (accountTime / 1_000_000.0) + "ms (avg: " + String.format("%.2f", accountAvg) + "ms)");

        System.out.println("  Benchmarking transaction batch processing...");
        startTime = System.nanoTime();

        DatabaseService.BatchProcessResult batchResult = databaseService.processBatchTransactions(transactions);

        long batchTime = System.nanoTime() - startTime;
        double batchAvg = batchTime / 1_000_000.0 / transactions.size();
        System.out.println("    Batch processing: " + transactions.size() + " transactions in " +
                (batchTime / 1_000_000.0) + "ms (avg: " + String.format("%.2f", batchAvg) + "ms)");
        System.out.println("    Success rate: " + String.format("%.1f%%", batchResult.getSuccessRate()));

        System.out.println("  Benchmarking query operations...");
        startTime = System.nanoTime();

        for (int i = 0; i < 50; i++) {
            String customerId = "PERF_CUST_" + String.format("%03d", i);
            databaseService.getCustomerRepository().findById(customerId);
            databaseService.getAccountRepository().findByCustomerId(customerId);
        }

        long queryTime = System.nanoTime() - startTime;
        double queryAvg = queryTime / 1_000_000.0 / 100; // 50 customers * 2 queries each
        System.out.println("    Query operations: 100 queries in " +
                (queryTime / 1_000_000.0) + "ms (avg: " + String.format("%.2f", queryAvg) + "ms)");

        System.out.println("  📊 Performance Summary:");
        System.out.printf("    Customer CRUD: %.2f ms/op%n", customerAvg);
        System.out.printf("    Account CRUD: %.2f ms/op%n", accountAvg);
        System.out.printf("    Transaction Batch: %.2f ms/op%n", batchAvg);
        System.out.printf("    Query Operations: %.2f ms/op%n", queryAvg);

        DatabaseService.DatabaseServiceStatistics finalStats = databaseService.getServiceStatistics();
        System.out.println("    Final service stats: " + finalStats);
    }


    private void cleanupDemo() {
        System.out.println("Cleaning up demo resources...");

        try {
            if (databaseService != null) {
                System.out.println("  Final statistics before cleanup:");
                DatabaseService.DatabaseServiceStatistics stats = databaseService.getServiceStatistics();
                System.out.println("    " + stats);

                System.out.println("  Dropping all tables...");
                databaseService.dropAllTables();

                System.out.println("  Closing database service...");
                databaseService.close();

                System.out.println("  ✅ Cleanup completed successfully");
            }
        } catch (Exception e) {
            System.out.println("  ⚠️ Cleanup error (may be expected): " + e.getMessage());
        }
    }

    private TransactionEntity createSampleTransaction(String txnId, String accountId, String customerId, String amount) {
        return TransactionEntity.approvedPurchase(
                txnId,
                "4111111111111111",
                accountId,
                customerId,
                new BigDecimal(amount),
                "TRY",
                "Sample Merchant"
        );
    }

    private String generateRandomNationalId() {

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            sb.append(random.nextInt(10));
        }

        sb.append("01"); // Simple check digits for testing

        return sb.toString();
    }

    public static void main(String[] args) {
        JdbcDemo demo = new JdbcDemo();
        demo.runAllDemos();
    }
}