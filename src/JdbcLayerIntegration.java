
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;


public final class JdbcLayerIntegration implements AutoCloseable {

    private final DatabaseConfiguration configuration;
    private final DatabaseService databaseService;
    private final ScheduledExecutorService healthCheckExecutor;
    private final List<HealthCheckListener> healthCheckListeners;
    private final IntegrationStatistics statistics;

    private volatile boolean initialized = false;
    private volatile boolean healthCheckRunning = false;

    public JdbcLayerIntegration(DatabaseConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "Configuration cannot be null");
        this.healthCheckExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "JDBC-HealthCheck");
            t.setDaemon(true);
            return t;
        });
        this.healthCheckListeners = new ArrayList<>();
        this.statistics = new IntegrationStatistics();

        ConnectionManager connectionManager = configuration.createConnectionManager();
        this.databaseService = new DatabaseService(connectionManager);
    }


    public static JdbcLayerIntegration forDevelopment() {
        DatabaseConfiguration config = DatabaseConfiguration.builder()
                .environment(DatabaseConfiguration.Environment.DEVELOPMENT)
                .jdbcUrl("jdbc:h2:mem:banking_dev;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .poolMinSize(3)
                .poolMaxSize(10)
                .enableSqlLogging(true)
                .enableMetrics(true)
                .build();

        return new JdbcLayerIntegration(config);
    }


    public static JdbcLayerIntegration forTesting() {
        DatabaseConfiguration config = DatabaseConfiguration.createTestConfiguration();
        return new JdbcLayerIntegration(config);
    }


    public static JdbcLayerIntegration forProduction(String jdbcUrl, String username, String password) {
        DatabaseConfiguration config = DatabaseConfiguration.builder()
                .environment(DatabaseConfiguration.Environment.PRODUCTION)
                .jdbcUrl(jdbcUrl)
                .username(username)
                .password(password)
                .poolMinSize(10)
                .poolMaxSize(50)
                .connectionTimeout(java.time.Duration.ofSeconds(30))
                .enableSsl(true)
                .sslMode("require")
                .enableMetrics(true)
                .schemaName("banking_prod")
                .build();

        return new JdbcLayerIntegration(config);
    }


    public static JdbcLayerIntegration fromConfigurationFile(String configFile) {
        DatabaseConfiguration config = DatabaseConfiguration.loadFromFile(configFile);
        return new JdbcLayerIntegration(config);
    }


    public static JdbcLayerIntegration fromEnvironment() {
        DatabaseConfiguration config = DatabaseConfiguration.loadFromEnvironment();
        return new JdbcLayerIntegration(config);
    }


    public synchronized InitializationResult initialize() {
        if (initialized) {
            return new InitializationResult(true, "Already initialized", Collections.emptyList());
        }

        long startTime = System.nanoTime();
        List<String> initSteps = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try {

            initSteps.add("Initializing database service...");
            databaseService.initializeDatabase();
            initSteps.add("✅ Database service initialized");

            if (configuration.isSchemaValidationEnabled()) {
                initSteps.add("Validating database schema...");
                validateDatabaseSchema();
                initSteps.add("✅ Schema validation completed");
            }

            if (configuration.isHealthCheckEnabled()) {
                initSteps.add("Starting health monitoring...");
                startHealthMonitoring();
                initSteps.add("✅ Health monitoring started");
            }

            initSteps.add("Warming up connection pool...");
            warmUpConnectionPool();
            initSteps.add("✅ Connection pool warmed up");

            initSteps.add("Running initial health check...");
            DatabaseService.DatabaseHealthStatus health = databaseService.getHealthStatus();
            if (!health.isHealthy()) {
                warnings.add("Initial health check shows some issues: " + health.getComponentHealth());
            } else {
                initSteps.add("✅ Initial health check passed");
            }

            if (configuration.isMetricsEnabled()) {
                initSteps.add("Setting up statistics collection...");
                setupStatisticsCollection();
                initSteps.add("✅ Statistics collection enabled");
            }

            initialized = true;
            long initTime = (System.nanoTime() - startTime) / 1_000_000;

            initSteps.add("🎉 JDBC Layer initialization completed in " + initTime + "ms");

            if (configuration.getEnvironment().isDevelopment()) {
                System.out.println(configuration.getConfigurationSummary());
            }

            return new InitializationResult(true, "Initialization successful", initSteps, warnings);

        } catch (Exception e) {
            initialized = false;
            initSteps.add("❌ Initialization failed: " + e.getMessage());
            return new InitializationResult(false, "Initialization failed: " + e.getMessage(), initSteps);
        }
    }


    public synchronized void shutdown() {
        if (!initialized) {
            return;
        }

        System.out.println("🔄 Shutting down JDBC Layer Integration...");

        try {

            if (healthCheckRunning) {
                healthCheckRunning = false;
                healthCheckExecutor.shutdown();
                if (!healthCheckExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    healthCheckExecutor.shutdownNow();
                }
                System.out.println("  ✅ Health monitoring stopped");
            }

            if (databaseService != null) {
                databaseService.close();
                System.out.println("  ✅ Database service closed");
            }

            if (configuration.isMetricsEnabled()) {
                System.out.println("  📊 Final Statistics:");
                System.out.println(statistics.generateReport());
            }

            initialized = false;
            System.out.println("  🎉 JDBC Layer Integration shutdown completed");

        } catch (Exception e) {
            System.err.println("  ⚠️ Error during shutdown: " + e.getMessage());
        }
    }


    public BankingScenarioResult runBankingScenario(String scenarioName) {
        if (!initialized) {
            return new BankingScenarioResult(scenarioName, false, "System not initialized", null);
        }

        long startTime = System.nanoTime();
        List<String> steps = new ArrayList<>();

        try {
            steps.add("🏦 Starting banking scenario: " + scenarioName);

            CustomerEntity customer = createSampleCustomer("SCENARIO_" + System.currentTimeMillis());
            steps.add("✅ Customer created: " + customer.getFullName());

            AccountEntity checkingAccount = createSampleCheckingAccount(customer.getCustomerId());
            AccountEntity savingsAccount = createSampleSavingsAccount(customer.getCustomerId());

            DatabaseService.CustomerAccountResult checkingResult =
                    databaseService.createCustomerWithAccount(customer, checkingAccount);
            steps.add("✅ Checking account created: " + checkingResult.isSuccessful());

            savingsAccount = databaseService.getAccountRepository().save(savingsAccount);
            steps.add("✅ Savings account created");

            BigDecimal initialDeposit = new BigDecimal("1000.00");
            databaseService.getAccountRepository().creditAccount(checkingAccount.getAccountId(), initialDeposit);
            steps.add("✅ Initial deposit: " + initialDeposit + " TRY");

            List<TransactionEntity> transactions = createSampleTransactions(
                    checkingAccount.getAccountId(), customer.getCustomerId());

            DatabaseService.BatchProcessResult batchResult =
                    databaseService.processBatchTransactions(transactions);
            steps.add("✅ Processed " + batchResult.getSuccessCount() + "/" +
                    transactions.size() + " transactions");

            BigDecimal transferAmount = new BigDecimal("200.00");
            DatabaseService.TransferResult transferResult = databaseService.transferMoney(
                    checkingAccount.getAccountId(), savingsAccount.getAccountId(),
                    transferAmount, "Scenario transfer");
            steps.add("✅ Transfer: " + transferResult.isSuccessful());

            DatabaseService.CustomerProfile profile =
                    databaseService.getCustomerProfile(customer.getCustomerId());
            steps.add("✅ Customer profile retrieved: " + profile.getAccounts().size() + " accounts");

            Optional<AccountEntity> finalChecking =
                    databaseService.getAccountRepository().findById(checkingAccount.getAccountId());
            Optional<AccountEntity> finalSavings =
                    databaseService.getAccountRepository().findById(savingsAccount.getAccountId());

            if (finalChecking.isPresent() && finalSavings.isPresent()) {
                steps.add("💰 Final balances: Checking=" + finalChecking.get().getBalance() +
                        ", Savings=" + finalSavings.get().getBalance());
            }

            long duration = (System.nanoTime() - startTime) / 1_000_000;
            steps.add("🎉 Banking scenario completed in " + duration + "ms");

            statistics.recordScenario(scenarioName, duration, true);

            BankingScenarioData scenarioData = new BankingScenarioData(
                    customer, Arrays.asList(finalChecking.orElse(null), finalSavings.orElse(null)),
                    profile.getRecentTransactions(), batchResult, transferResult);

            return new BankingScenarioResult(scenarioName, true, "Scenario completed successfully",
                    scenarioData, steps, duration);

        } catch (Exception e) {
            long duration = (System.nanoTime() - startTime) / 1_000_000;
            steps.add("❌ Scenario failed: " + e.getMessage());
            statistics.recordScenario(scenarioName, duration, false);

            return new BankingScenarioResult(scenarioName, false, "Scenario failed: " + e.getMessage(),
                    null, steps, duration);
        }
    }


    public StressTestResult runStressTest(int concurrentUsers, int operationsPerUser, Duration duration) {
        if (!initialized) {
            return new StressTestResult(false, "System not initialized", null);
        }

        System.out.println("🔥 Starting stress test: " + concurrentUsers + " users, " +
                operationsPerUser + " ops/user, " + duration);

        ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);
        List<Future<UserStressResult>> futures = new ArrayList<>();
        long startTime = System.nanoTime();

        for (int i = 0; i < concurrentUsers; i++) {
            final int userId = i;
            futures.add(executor.submit(() -> runUserStressTest(userId, operationsPerUser)));
        }

        List<UserStressResult> userResults = new ArrayList<>();
        int totalSuccess = 0;
        int totalFailed = 0;

        for (Future<UserStressResult> future : futures) {
            try {
                UserStressResult result = future.get(duration.getSeconds() + 10, TimeUnit.SECONDS);
                userResults.add(result);
                totalSuccess += result.getSuccessfulOperations();
                totalFailed += result.getFailedOperations();
            } catch (Exception e) {
                userResults.add(new UserStressResult(-1, 0, operationsPerUser, e.getMessage()));
                totalFailed += operationsPerUser;
            }
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        long totalTime = (System.nanoTime() - startTime) / 1_000_000;
        double throughput = (double) totalSuccess / (totalTime / 1000.0); // ops/second

        StressTestMetrics metrics = new StressTestMetrics(
                concurrentUsers, operationsPerUser, totalSuccess, totalFailed,
                totalTime, throughput);

        System.out.println("🎯 Stress test completed: " + totalSuccess + " success, " +
                totalFailed + " failed, " + String.format("%.1f", throughput) + " ops/sec");

        return new StressTestResult(true, "Stress test completed", metrics, userResults);
    }


    public MigrationResult migrateData(List<CustomerEntity> customers,
                                       List<AccountEntity> accounts,
                                       List<TransactionEntity> transactions) {
        if (!initialized) {
            return new MigrationResult(false, "System not initialized", null);
        }

        long startTime = System.nanoTime();
        MigrationStatistics migrationStats = new MigrationStatistics();
        List<String> migrationSteps = new ArrayList<>();

        try {
            migrationSteps.add("🔄 Starting data migration...");
            migrationSteps.add("  Customers: " + customers.size());
            migrationSteps.add("  Accounts: " + accounts.size());
            migrationSteps.add("  Transactions: " + transactions.size());

            migrationSteps.add("📥 Migrating customers...");
            for (CustomerEntity customer : customers) {
                try {
                    databaseService.getCustomerRepository().save(customer);
                    migrationStats.customersSucceeded++;
                } catch (Exception e) {
                    migrationStats.customersFailed++;
                    migrationStats.errors.add("Customer " + customer.getCustomerId() + ": " + e.getMessage());
                }
            }
            migrationSteps.add("  ✅ Customers: " + migrationStats.customersSucceeded + " success, " +
                    migrationStats.customersFailed + " failed");

            migrationSteps.add("📥 Migrating accounts...");
            for (AccountEntity account : accounts) {
                try {
                    databaseService.getAccountRepository().save(account);
                    migrationStats.accountsSucceeded++;
                } catch (Exception e) {
                    migrationStats.accountsFailed++;
                    migrationStats.errors.add("Account " + account.getAccountId() + ": " + e.getMessage());
                }
            }
            migrationSteps.add("  ✅ Accounts: " + migrationStats.accountsSucceeded + " success, " +
                    migrationStats.accountsFailed + " failed");

            migrationSteps.add("📥 Migrating transactions...");
            int batchSize = 100;
            for (int i = 0; i < transactions.size(); i += batchSize) {
                List<TransactionEntity> batch = transactions.subList(i,
                        Math.min(i + batchSize, transactions.size()));

                DatabaseService.BatchProcessResult batchResult =
                        databaseService.processBatchTransactions(batch);

                migrationStats.transactionsSucceeded += batchResult.getSuccessCount();
                migrationStats.transactionsFailed += batchResult.getFailedCount();
                migrationStats.errors.addAll(batchResult.getErrors());
            }
            migrationSteps.add("  ✅ Transactions: " + migrationStats.transactionsSucceeded + " success, " +
                    migrationStats.transactionsFailed + " failed");

            long migrationTime = (System.nanoTime() - startTime) / 1_000_000;
            migrationStats.totalTimeMs = migrationTime;

            boolean success = migrationStats.customersFailed == 0 &&
                    migrationStats.accountsFailed == 0 &&
                    migrationStats.transactionsFailed < transactions.size() * 0.05; // Allow 5% failure rate

            migrationSteps.add("🎉 Migration completed in " + migrationTime + "ms");

            return new MigrationResult(success, "Migration completed", migrationStats, migrationSteps);

        } catch (Exception e) {
            migrationSteps.add("❌ Migration failed: " + e.getMessage());
            return new MigrationResult(false, "Migration failed: " + e.getMessage(),
                    migrationStats, migrationSteps);
        }
    }


    public void addHealthCheckListener(HealthCheckListener listener) {
        healthCheckListeners.add(listener);
    }

    public void removeHealthCheckListener(HealthCheckListener listener) {
        healthCheckListeners.remove(listener);
    }

    private void startHealthMonitoring() {
        if (healthCheckRunning) {
            return;
        }

        healthCheckRunning = true;

        healthCheckExecutor.scheduleAtFixedRate(
                this::performHealthCheck,
                0,
                configuration.getHealthCheckInterval().getSeconds(),
                TimeUnit.SECONDS
        );
    }

    private void performHealthCheck() {
        try {
            DatabaseService.DatabaseHealthStatus health = databaseService.getHealthStatus();
            statistics.recordHealthCheck(health.isHealthy());

            for (HealthCheckListener listener : healthCheckListeners) {
                try {
                    listener.onHealthCheck(health);
                } catch (Exception e) {

                    System.err.println("Health check listener error: " + e.getMessage());
                }
            }

            if (!health.isHealthy()) {
                System.err.println("🚨 Database health check failed: " + health.getComponentHealth());
            }

        } catch (Exception e) {
            statistics.recordHealthCheck(false);
            System.err.println("Health check error: " + e.getMessage());
        }
    }


    private void validateDatabaseSchema() {

        try {
            databaseService.getCustomerRepository().count();
            databaseService.getAccountRepository().count();
            databaseService.getTransactionRepository().count();
        } catch (Exception e) {
            throw new IllegalStateException("Schema validation failed", e);
        }
    }

    private void warmUpConnectionPool() {

        List<CompletableFuture<Void>> warmupTasks = new ArrayList<>();

        for (int i = 0; i < configuration.getPoolMinSize(); i++) {
            warmupTasks.add(CompletableFuture.runAsync(() -> {
                try {
                    databaseService.getCustomerRepository().count();
                } catch (Exception e) {

                }
            }));
        }

        CompletableFuture.allOf(warmupTasks.toArray(new CompletableFuture[0])).join();
    }

    private void setupStatisticsCollection() {

        healthCheckExecutor.scheduleAtFixedRate(
                this::collectStatistics,
                60, // Start after 1 minute
                60, // Every minute
                TimeUnit.SECONDS
        );
    }

    private void collectStatistics() {
        try {
            DatabaseService.DatabaseServiceStatistics serviceStats = databaseService.getServiceStatistics();
            statistics.recordServiceStatistics(serviceStats);
        } catch (Exception e) {

            System.err.println("Statistics collection error: " + e.getMessage());
        }
    }

    private UserStressResult runUserStressTest(int userId, int operationsPerUser) {
        int successful = 0;
        int failed = 0;
        String lastError = null;

        for (int i = 0; i < operationsPerUser; i++) {
            try {

                switch (i % 4) {
                    case 0:

                        databaseService.getCustomerRepository().findAll(0, 10);
                        break;
                    case 1:

                        databaseService.getAccountRepository().findAll(0, 10);
                        break;
                    case 2:

                        databaseService.getTransactionRepository().findAll(0, 10);
                        break;
                    case 3:

                        databaseService.getHealthStatus();
                        break;
                }
                successful++;
            } catch (Exception e) {
                failed++;
                lastError = e.getMessage();
            }
        }

        return new UserStressResult(userId, successful, failed, lastError);
    }

    private CustomerEntity createSampleCustomer(String suffix) {
        return CustomerEntity.newIndividualCustomer(
                "CUST_" + suffix,
                "12345678901", // Would need unique values in real scenario
                "Test",
                "Customer " + suffix,
                "test." + suffix + "@example.com",
                "+905551234567",
                LocalDate.of(1985, 1, 1)
        );
    }

    private AccountEntity createSampleCheckingAccount(String customerId) {
        return AccountEntity.newCheckingAccount(
                "ACC_CHECK_" + customerId,
                customerId,
                "1000" + customerId.substring(customerId.length() - 8),
                "TRY"
        );
    }

    private AccountEntity createSampleSavingsAccount(String customerId) {
        return AccountEntity.newSavingsAccount(
                "ACC_SAVE_" + customerId,
                customerId,
                "2000" + customerId.substring(customerId.length() - 8),
                "TRY",
                BigDecimal.ZERO
        );
    }

    private List<TransactionEntity> createSampleTransactions(String accountId, String customerId) {
        List<TransactionEntity> transactions = new ArrayList<>();

        String[] merchants = {"Grocery Store", "Gas Station", "Restaurant", "Online Shop", "ATM"};
        BigDecimal[] amounts = {new BigDecimal("25.50"), new BigDecimal("75.00"),
                new BigDecimal("150.75"), new BigDecimal("300.00")};

        for (int i = 0; i < 5; i++) {
            String txnId = "TXN_" + accountId + "_" + i;
            String merchant = merchants[i % merchants.length];
            BigDecimal amount = amounts[i % amounts.length];

            TransactionEntity transaction = TransactionEntity.approvedPurchase(
                    txnId, "4111111111111111", accountId, customerId, amount, "TRY", merchant);

            transactions.add(transaction);
        }

        return transactions;
    }

    @Override
    public void close() {
        shutdown();
    }


    public DatabaseConfiguration getConfiguration() {
        return configuration;
    }

    public DatabaseService getDatabaseService() {
        return databaseService;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public IntegrationStatistics getStatistics() {
        return statistics;
    }


    @FunctionalInterface
    public interface HealthCheckListener {
        void onHealthCheck(DatabaseService.DatabaseHealthStatus healthStatus);
    }

    public static final class InitializationResult {
        private final boolean successful;
        private final String message;
        private final List<String> steps;
        private final List<String> warnings;

        public InitializationResult(boolean successful, String message, List<String> steps) {
            this(successful, message, steps, Collections.emptyList());
        }

        public InitializationResult(boolean successful, String message, List<String> steps, List<String> warnings) {
            this.successful = successful;
            this.message = message;
            this.steps = new ArrayList<>(steps);
            this.warnings = new ArrayList<>(warnings);
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getMessage() {
            return message;
        }

        public List<String> getSteps() {
            return steps;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public void printReport() {
            System.out.println("=== Initialization Report ===");
            System.out.println("Status: " + (successful ? "SUCCESS" : "FAILED"));
            System.out.println("Message: " + message);
            System.out.println("\nSteps:");
            steps.forEach(step -> System.out.println("  " + step));
            if (!warnings.isEmpty()) {
                System.out.println("\nWarnings:");
                warnings.forEach(warning -> System.out.println("  ⚠️ " + warning));
            }
        }
    }

    public static final class BankingScenarioResult {
        private final String scenarioName;
        private final boolean successful;
        private final String message;
        private final BankingScenarioData data;
        private final List<String> steps;
        private final long durationMs;

        public BankingScenarioResult(String scenarioName, boolean successful, String message, BankingScenarioData data) {
            this(scenarioName, successful, message, data, Collections.emptyList(), 0);
        }

        public BankingScenarioResult(String scenarioName, boolean successful, String message,
                                     BankingScenarioData data, List<String> steps, long durationMs) {
            this.scenarioName = scenarioName;
            this.successful = successful;
            this.message = message;
            this.data = data;
            this.steps = steps != null ? new ArrayList<>(steps) : Collections.emptyList();
            this.durationMs = durationMs;
        }

        public String getScenarioName() {
            return scenarioName;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getMessage() {
            return message;
        }

        public BankingScenarioData getData() {
            return data;
        }

        public List<String> getSteps() {
            return steps;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public void printReport() {
            System.out.println("=== Banking Scenario Report: " + scenarioName + " ===");
            System.out.println("Status: " + (successful ? "SUCCESS" : "FAILED"));
            System.out.println("Duration: " + durationMs + "ms");
            System.out.println("Message: " + message);
            System.out.println("\nSteps:");
            steps.forEach(step -> System.out.println("  " + step));
            if (data != null) {
                System.out.println("\nData Summary:");
                System.out.println("  Customer: " + data.customer.getFullName());
                System.out.println("  Accounts: " + data.accounts.size());
                System.out.println("  Transactions: " + data.transactions.size());
            }
        }
    }

    public static final class BankingScenarioData {
        private final CustomerEntity customer;
        private final List<AccountEntity> accounts;
        private final List<TransactionEntity> transactions;
        private final DatabaseService.BatchProcessResult batchResult;
        private final DatabaseService.TransferResult transferResult;

        public BankingScenarioData(CustomerEntity customer, List<AccountEntity> accounts,
                                   List<TransactionEntity> transactions,
                                   DatabaseService.BatchProcessResult batchResult,
                                   DatabaseService.TransferResult transferResult) {
            this.customer = customer;
            this.accounts = accounts;
            this.transactions = transactions;
            this.batchResult = batchResult;
            this.transferResult = transferResult;
        }

        public CustomerEntity getCustomer() {
            return customer;
        }

        public List<AccountEntity> getAccounts() {
            return accounts;
        }

        public List<TransactionEntity> getTransactions() {
            return transactions;
        }

        public DatabaseService.BatchProcessResult getBatchResult() {
            return batchResult;
        }

        public DatabaseService.TransferResult getTransferResult() {
            return transferResult;
        }
    }

    public static final class StressTestResult {
        private final boolean successful;
        private final String message;
        private final StressTestMetrics metrics;
        private final List<UserStressResult> userResults;

        public StressTestResult(boolean successful, String message, StressTestMetrics metrics) {
            this(successful, message, metrics, Collections.emptyList());
        }

        public StressTestResult(boolean successful, String message, StressTestMetrics metrics,
                                List<UserStressResult> userResults) {
            this.successful = successful;
            this.message = message;
            this.metrics = metrics;
            this.userResults = userResults != null ? new ArrayList<>(userResults) : Collections.emptyList();
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getMessage() {
            return message;
        }

        public StressTestMetrics getMetrics() {
            return metrics;
        }

        public List<UserStressResult> getUserResults() {
            return userResults;
        }
    }

    public static final class StressTestMetrics {
        private final int concurrentUsers;
        private final int operationsPerUser;
        private final int totalSuccess;
        private final int totalFailed;
        private final long totalTimeMs;
        private final double throughput;

        public StressTestMetrics(int concurrentUsers, int operationsPerUser, int totalSuccess,
                                 int totalFailed, long totalTimeMs, double throughput) {
            this.concurrentUsers = concurrentUsers;
            this.operationsPerUser = operationsPerUser;
            this.totalSuccess = totalSuccess;
            this.totalFailed = totalFailed;
            this.totalTimeMs = totalTimeMs;
            this.throughput = throughput;
        }

        public int getConcurrentUsers() {
            return concurrentUsers;
        }

        public int getOperationsPerUser() {
            return operationsPerUser;
        }

        public int getTotalSuccess() {
            return totalSuccess;
        }

        public int getTotalFailed() {
            return totalFailed;
        }

        public long getTotalTimeMs() {
            return totalTimeMs;
        }

        public double getThroughput() {
            return throughput;
        }

        public double getSuccessRate() {
            int total = totalSuccess + totalFailed;
            return total == 0 ? 0.0 : (double) totalSuccess / total * 100.0;
        }
    }

    private static final class UserStressResult {
        private final int userId;
        private final int successfulOperations;
        private final int failedOperations;
        private final String lastError;

        public UserStressResult(int userId, int successfulOperations, int failedOperations, String lastError) {
            this.userId = userId;
            this.successfulOperations = successfulOperations;
            this.failedOperations = failedOperations;
            this.lastError = lastError;
        }

        public int getUserId() {
            return userId;
        }

        public int getSuccessfulOperations() {
            return successfulOperations;
        }

        public int getFailedOperations() {
            return failedOperations;
        }

        public String getLastError() {
            return lastError;
        }
    }

    public static final class MigrationResult {
        private final boolean successful;
        private final String message;
        private final MigrationStatistics statistics;
        private final List<String> steps;

        public MigrationResult(boolean successful, String message, MigrationStatistics statistics) {
            this(successful, message, statistics, Collections.emptyList());
        }

        public MigrationResult(boolean successful, String message, MigrationStatistics statistics, List<String> steps) {
            this.successful = successful;
            this.message = message;
            this.statistics = statistics;
            this.steps = steps;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getMessage() {
            return message;
        }

        public MigrationStatistics getStatistics() {
            return statistics;
        }

        public List<String> getSteps() {
            return steps;
        }
    }

    private static final class MigrationStatistics {
        private int customersSucceeded = 0;
        private int customersFailed = 0;
        private int accountsSucceeded = 0;
        private int accountsFailed = 0;
        private int transactionsSucceeded = 0;
        private int transactionsFailed = 0;
        private long totalTimeMs = 0;
        private final List<String> errors = new ArrayList<>();
    }

    public static final class IntegrationStatistics {
        private int totalScenarios = 0;
        private int successfulScenarios = 0;
        private int failedScenarios = 0;
        private long totalScenarioTime = 0;
        private int healthChecks = 0;
        private int healthCheckFailures = 0;
        private final Map<String, Long> scenarioTimes = new HashMap<>();
        private DatabaseService.DatabaseServiceStatistics lastServiceStats;

        public synchronized void recordScenario(String name, long durationMs, boolean successful) {
            totalScenarios++;
            totalScenarioTime += durationMs;
            scenarioTimes.put(name, durationMs);

            if (successful) {
                successfulScenarios++;
            } else {
                failedScenarios++;
            }
        }

        public synchronized void recordHealthCheck(boolean healthy) {
            healthChecks++;
            if (!healthy) {
                healthCheckFailures++;
            }
        }

        public synchronized void recordServiceStatistics(DatabaseService.DatabaseServiceStatistics stats) {
            this.lastServiceStats = stats;
        }

        public synchronized String generateReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Integration Statistics ===\n");
            sb.append("Scenarios: ").append(totalScenarios).append(" total (")
                    .append(successfulScenarios).append(" success, ").append(failedScenarios).append(" failed)\n");

            if (totalScenarios > 0) {
                double avgTime = (double) totalScenarioTime / totalScenarios;
                double successRate = (double) successfulScenarios / totalScenarios * 100.0;
                sb.append("Average Scenario Time: ").append(String.format("%.1fms", avgTime)).append("\n");
                sb.append("Success Rate: ").append(String.format("%.1f%%", successRate)).append("\n");
            }

            sb.append("Health Checks: ").append(healthChecks).append(" total (")
                    .append(healthCheckFailures).append(" failures)\n");

            if (lastServiceStats != null) {
                sb.append("Last Service Stats: ").append(lastServiceStats.toString()).append("\n");
            }

            return sb.toString();
        }
    }

    public static void main(String[] args) {
        System.out.println("🚀 JDBC Layer Integration - Production Ready Demo");

        try (JdbcLayerIntegration integration = JdbcLayerIntegration.forDevelopment()) {

            InitializationResult initResult = integration.initialize();
            initResult.printReport();

            if (!initResult.isSuccessful()) {
                System.exit(1);
            }

            BankingScenarioResult scenarioResult = integration.runBankingScenario("Complete Banking Demo");
            scenarioResult.printReport();

            StressTestResult stressResult = integration.runStressTest(5, 20, Duration.ofSeconds(30));
            System.out.println("Stress test: " + stressResult.getMessage());
            if (stressResult.getMetrics() != null) {
                StressTestMetrics metrics = stressResult.getMetrics();
                System.out.println("Throughput: " + String.format("%.1f", metrics.getThroughput()) + " ops/sec");
                System.out.println("Success rate: " + String.format("%.1f%%", metrics.getSuccessRate()));
            }

            System.out.println("\n" + integration.getStatistics().generateReport());

        } catch (Exception e) {
            System.err.println("❌ Integration demo failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("🎉 JDBC Layer Integration demo completed successfully!");
    }
}