import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public final class AccountRepository extends AbstractCrudRepository<AccountEntity, String> {

    private static final String TABLE_NAME = "ACCOUNT";

    public AccountRepository(ConnectionManager connectionManager) {
        super(connectionManager, TABLE_NAME);
    }


    @Override
    protected AccountEntity mapResultSetToEntity(ResultSet rs) throws SQLException {
        return AccountEntity.builder()
                .accountId(rs.getString("account_id"))
                .customerId(rs.getString("customer_id"))
                .accountNumber(rs.getString("account_number"))
                .iban(rs.getString("iban"))
                .accountType(AccountEntity.AccountType.valueOf(rs.getString("account_type")))
                .status(AccountEntity.AccountStatus.valueOf(rs.getString("status")))
                .currency(rs.getString("currency"))
                .balance(rs.getBigDecimal("balance"))
                .availableBalance(rs.getBigDecimal("available_balance"))
                .creditLimit(rs.getBigDecimal("credit_limit"))
                .dailyLimit(rs.getBigDecimal("daily_limit"))
                .monthlyLimit(rs.getBigDecimal("monthly_limit"))
                .branchCode(rs.getString("branch_code"))
                .openingDate(rs.getDate("opening_date").toLocalDate())
                .lastTransactionDate(rs.getDate("last_transaction_date") != null ?
                        rs.getDate("last_transaction_date").toLocalDate() : null)
                .productCode(rs.getString("product_code"))
                .description(rs.getString("description"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }

    @Override
    protected void setParametersForInsert(PreparedStatement ps, AccountEntity entity) throws SQLException {
        ps.setString(1, entity.getAccountId());
        ps.setString(2, entity.getCustomerId());
        ps.setString(3, entity.getAccountNumber());
        ps.setString(4, entity.getIban());
        ps.setString(5, entity.getAccountType().name());
        ps.setString(6, entity.getStatus().name());
        ps.setString(7, entity.getCurrency());
        ps.setBigDecimal(8, entity.getBalance());
        ps.setBigDecimal(9, entity.getAvailableBalance());
        ps.setBigDecimal(10, entity.getCreditLimit());
        ps.setBigDecimal(11, entity.getDailyLimit());
        ps.setBigDecimal(12, entity.getMonthlyLimit());
        ps.setString(13, entity.getBranchCode());
        ps.setDate(14, Date.valueOf(entity.getOpeningDate()));
        ps.setDate(15, entity.getLastTransactionDate() != null ?
                Date.valueOf(entity.getLastTransactionDate()) : null);
        ps.setString(16, entity.getProductCode());
        ps.setString(17, entity.getDescription());
        ps.setTimestamp(18, Timestamp.valueOf(entity.getCreatedAt()));
        ps.setTimestamp(19, Timestamp.valueOf(entity.getUpdatedAt()));
    }

    @Override
    protected void setParametersForUpdate(PreparedStatement ps, AccountEntity entity) throws SQLException {

        ps.setString(1, entity.getStatus().name());
        ps.setBigDecimal(2, entity.getBalance());
        ps.setBigDecimal(3, entity.getAvailableBalance());
        ps.setBigDecimal(4, entity.getDailyLimit());
        ps.setBigDecimal(5, entity.getMonthlyLimit());
        ps.setDate(6, entity.getLastTransactionDate() != null ?
                Date.valueOf(entity.getLastTransactionDate()) : null);
        ps.setString(7, entity.getDescription());
        ps.setTimestamp(8, Timestamp.valueOf(entity.getUpdatedAt()));
        ps.setString(9, entity.getAccountId()); // WHERE clause
    }

    @Override
    protected String getEntityId(AccountEntity entity) {
        return entity.getAccountId();
    }

    @Override
    protected void setIdParameter(PreparedStatement ps, int parameterIndex, String id) throws SQLException {
        ps.setString(parameterIndex, id);
    }

    @Override
    protected String createIdFromGeneratedKey(Object generatedKey) {

        return generatedKey.toString();
    }

    @Override
    protected String getInsertColumns() {
        return "(account_id, customer_id, account_number, iban, account_type, status, currency, " +
                "balance, available_balance, credit_limit, daily_limit, monthly_limit, " +
                "branch_code, opening_date, last_transaction_date, product_code, description, " +
                "created_at, updated_at)";
    }

    @Override
    protected String getInsertValues() {
        return "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    protected String getUpdateColumns() {
        return "status = ?, balance = ?, available_balance = ?, daily_limit = ?, " +
                "monthly_limit = ?, last_transaction_date = ?, description = ?, updated_at = ?";
    }

    @Override
    protected String getSelectByIdSql() {
        return "SELECT * FROM " + TABLE_NAME + " WHERE account_id = ?";
    }

    @Override
    protected String getExistsByIdSql() {
        return "SELECT 1 FROM " + TABLE_NAME + " WHERE account_id = ? LIMIT 1";
    }


    public Optional<AccountEntity> findByAccountNumber(String accountNumber) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE account_number = ?";
        return executeQueryForSingleResult(sql, accountNumber);
    }


    public Optional<AccountEntity> findByIban(String iban) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE iban = ?";
        return executeQueryForSingleResult(sql, iban);
    }


    public List<AccountEntity> findByCustomerId(String customerId) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE customer_id = ? ORDER BY opening_date";
        return executeQueryForList(sql, customerId);
    }


    public List<AccountEntity> findActiveAccountsByCustomerId(String customerId) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE customer_id = ? AND status = 'ACTIVE' " +
                "ORDER BY opening_date";
        return executeQueryForList(sql, customerId);
    }


    public List<AccountEntity> findByAccountType(AccountEntity.AccountType accountType) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE account_type = ? ORDER BY opening_date";
        return executeQueryForList(sql, accountType.name());
    }


    public List<AccountEntity> findByStatus(AccountEntity.AccountStatus status) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE status = ? ORDER BY opening_date";
        return executeQueryForList(sql, status.name());
    }


    public List<AccountEntity> findByBranchCode(String branchCode) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE branch_code = ? ORDER BY opening_date";
        return executeQueryForList(sql, branchCode);
    }


    public List<AccountEntity> findByCurrency(String currency) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE currency = ? ORDER BY balance DESC";
        return executeQueryForList(sql, currency);
    }


    public List<AccountEntity> findByBalanceRange(BigDecimal minBalance, BigDecimal maxBalance) {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE balance BETWEEN ? AND ? ORDER BY balance DESC";
        return executeQueryForList(sql, minBalance, maxBalance);
    }


    public List<AccountEntity> findOverdraftAccounts() {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE balance < 0 ORDER BY balance";
        return executeQueryForList(sql);
    }


    public List<AccountEntity> findHighBalanceAccounts(BigDecimal threshold) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE balance >= ? ORDER BY balance DESC";
        return executeQueryForList(sql, threshold);
    }


    public List<AccountEntity> findDormantAccounts(int days) {
        LocalDate cutoffDate = LocalDate.now().minusDays(days);
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE (last_transaction_date IS NULL AND opening_date <= ?) " +
                "OR (last_transaction_date IS NOT NULL AND last_transaction_date <= ?) " +
                "ORDER BY COALESCE(last_transaction_date, opening_date)";
        return executeQueryForList(sql, Date.valueOf(cutoffDate), Date.valueOf(cutoffDate));
    }


    public List<AccountEntity> findNewAccounts(int days) {
        LocalDate cutoffDate = LocalDate.now().minusDays(days);
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE opening_date >= ? ORDER BY opening_date DESC";
        return executeQueryForList(sql, Date.valueOf(cutoffDate));
    }


    public List<AccountEntity> findByProductCode(String productCode) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE product_code = ? ORDER BY opening_date";
        return executeQueryForList(sql, productCode);
    }


    public List<AccountEntity> findAccountsRequiringAttention() {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE status IN ('SUSPENDED', 'FROZEN', 'PENDING_ACTIVATION', 'PENDING_CLOSURE') " +
                "ORDER BY updated_at";
        return executeQueryForList(sql);
    }


    public boolean updateBalance(String accountId, BigDecimal newBalance, BigDecimal newAvailableBalance) {
        String sql = "UPDATE " + TABLE_NAME +
                " SET balance = ?, available_balance = ?, last_transaction_date = ?, updated_at = ? " +
                "WHERE account_id = ?";

        LocalDateTime now = LocalDateTime.now();
        int rowsAffected = executeUpdate(sql, newBalance, newAvailableBalance,
                Date.valueOf(LocalDate.now()), Timestamp.valueOf(now), accountId);
        return rowsAffected > 0;
    }


    public boolean creditAccount(String accountId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }

        String sql = "UPDATE " + TABLE_NAME +
                " SET balance = balance + ?, available_balance = available_balance + ?, " +
                "last_transaction_date = ?, updated_at = ? WHERE account_id = ?";

        LocalDateTime now = LocalDateTime.now();
        int rowsAffected = executeUpdate(sql, amount, amount,
                Date.valueOf(LocalDate.now()), Timestamp.valueOf(now), accountId);
        return rowsAffected > 0;
    }


    public boolean debitAccount(String accountId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }

        String sql = "UPDATE " + TABLE_NAME +
                " SET balance = balance - ?, available_balance = available_balance - ?, " +
                "last_transaction_date = ?, updated_at = ? WHERE account_id = ?";

        LocalDateTime now = LocalDateTime.now();
        int rowsAffected = executeUpdate(sql, amount, amount,
                Date.valueOf(LocalDate.now()), Timestamp.valueOf(now), accountId);
        return rowsAffected > 0;
    }


    public boolean updateStatus(String accountId, AccountEntity.AccountStatus newStatus) {
        String sql = "UPDATE " + TABLE_NAME + " SET status = ?, updated_at = ? WHERE account_id = ?";

        int rowsAffected = executeUpdate(sql, newStatus.name(),
                Timestamp.valueOf(LocalDateTime.now()), accountId);
        return rowsAffected > 0;
    }


    public boolean updateLimits(String accountId, BigDecimal dailyLimit, BigDecimal monthlyLimit) {
        String sql = "UPDATE " + TABLE_NAME +
                " SET daily_limit = ?, monthly_limit = ?, updated_at = ? WHERE account_id = ?";

        int rowsAffected = executeUpdate(sql, dailyLimit, monthlyLimit,
                Timestamp.valueOf(LocalDateTime.now()), accountId);
        return rowsAffected > 0;
    }


    public long countByCustomerId(String customerId) {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE customer_id = ?";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to count accounts by customer ID", e);
        }
    }


    public BigDecimal sumBalancesByCustomerId(String customerId) {
        String sql = "SELECT COALESCE(SUM(balance), 0) FROM " + TABLE_NAME +
                " WHERE customer_id = ? AND status = 'ACTIVE'";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to sum balances by customer ID", e);
        }
    }


    public List<AccountTypeBalance> getTotalBalancesByType() {
        String sql = "SELECT account_type, currency, COUNT(*) as account_count, " +
                "SUM(balance) as total_balance, AVG(balance) as average_balance " +
                "FROM " + TABLE_NAME + " WHERE status = 'ACTIVE' " +
                "GROUP BY account_type, currency ORDER BY total_balance DESC";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<AccountTypeBalance> balances = new java.util.ArrayList<>();
            while (rs.next()) {
                balances.add(new AccountTypeBalance(
                        AccountEntity.AccountType.valueOf(rs.getString("account_type")),
                        rs.getString("currency"),
                        rs.getInt("account_count"),
                        rs.getBigDecimal("total_balance"),
                        rs.getBigDecimal("average_balance")
                ));
            }
            return balances;
        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to get balances by account type", e);
        }
    }


    public List<AccountStatusStats> getAccountStatsByStatus() {
        String sql = "SELECT status, COUNT(*) as account_count, " +
                "MIN(opening_date) as oldest_account, MAX(opening_date) as newest_account " +
                "FROM " + TABLE_NAME + " GROUP BY status ORDER BY account_count DESC";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<AccountStatusStats> stats = new java.util.ArrayList<>();
            while (rs.next()) {
                stats.add(new AccountStatusStats(
                        AccountEntity.AccountStatus.valueOf(rs.getString("status")),
                        rs.getInt("account_count"),
                        rs.getDate("oldest_account").toLocalDate(),
                        rs.getDate("newest_account").toLocalDate()
                ));
            }
            return stats;
        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to get account stats by status", e);
        }
    }


    public boolean isAccountNumberExists(String accountNumber) {
        String sql = "SELECT 1 FROM " + TABLE_NAME + " WHERE account_number = ? LIMIT 1";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to check account number existence", e);
        }
    }


    public int closeAccounts(List<String> accountIds) {
        if (accountIds.isEmpty()) return 0;

        String sql = "UPDATE " + TABLE_NAME + " SET status = 'CLOSED', updated_at = ? WHERE account_id = ?";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            for (String accountId : accountIds) {
                ps.setTimestamp(1, now);
                ps.setString(2, accountId);
                ps.addBatch();
            }

            int[] results = ps.executeBatch();
            return java.util.Arrays.stream(results).sum();

        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to close accounts", e);
        }
    }


    public void createTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS ACCOUNT (
                    account_id VARCHAR(50) PRIMARY KEY,
                    customer_id VARCHAR(50) NOT NULL,
                    account_number VARCHAR(20) NOT NULL UNIQUE,
                    iban VARCHAR(34),
                    account_type VARCHAR(20) NOT NULL,
                    status VARCHAR(30) NOT NULL,
                    currency VARCHAR(3) NOT NULL,
                    balance DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                    available_balance DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                    credit_limit DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                    daily_limit DECIMAL(15,2) NOT NULL DEFAULT 10000.00,
                    monthly_limit DECIMAL(15,2) NOT NULL DEFAULT 50000.00,
                    branch_code VARCHAR(10),
                    opening_date DATE NOT NULL,
                    last_transaction_date DATE,
                    product_code VARCHAR(50),
                    description VARCHAR(500),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """;

        executeUpdate(sql);

        String[] indexes = {
                "CREATE INDEX IF NOT EXISTS idx_customer_id ON ACCOUNT(customer_id)",
                "CREATE INDEX IF NOT EXISTS idx_account_number ON ACCOUNT(account_number)",
                "CREATE INDEX IF NOT EXISTS idx_status ON ACCOUNT(status)",
                "CREATE INDEX IF NOT EXISTS idx_account_type ON ACCOUNT(account_type)",
                "CREATE INDEX IF NOT EXISTS idx_balance ON ACCOUNT(balance)",
                "CREATE INDEX IF NOT EXISTS idx_opening_date ON ACCOUNT(opening_date)"
        };

        for (String indexSql : indexes) {
            executeUpdate(indexSql);
        }
    }


    public void dropTable() {
        executeUpdate("DROP TABLE IF EXISTS " + TABLE_NAME);
    }


    public static final class AccountTypeBalance {
        private final AccountEntity.AccountType accountType;
        private final String currency;
        private final int accountCount;
        private final BigDecimal totalBalance;
        private final BigDecimal averageBalance;

        public AccountTypeBalance(AccountEntity.AccountType accountType, String currency,
                                  int accountCount, BigDecimal totalBalance, BigDecimal averageBalance) {
            this.accountType = accountType;
            this.currency = currency;
            this.accountCount = accountCount;
            this.totalBalance = totalBalance;
            this.averageBalance = averageBalance;
        }

        public AccountEntity.AccountType getAccountType() {
            return accountType;
        }

        public String getCurrency() {
            return currency;
        }

        public int getAccountCount() {
            return accountCount;
        }

        public BigDecimal getTotalBalance() {
            return totalBalance;
        }

        public BigDecimal getAverageBalance() {
            return averageBalance;
        }

        @Override
        public String toString() {
            return "AccountTypeBalance{" +
                    "type=" + accountType +
                    ", currency='" + currency + '\'' +
                    ", count=" + accountCount +
                    ", total=" + totalBalance +
                    ", avg=" + averageBalance +
                    '}';
        }
    }


    public static final class AccountStatusStats {
        private final AccountEntity.AccountStatus status;
        private final int accountCount;
        private final LocalDate oldestAccount;
        private final LocalDate newestAccount;

        public AccountStatusStats(AccountEntity.AccountStatus status, int accountCount,
                                  LocalDate oldestAccount, LocalDate newestAccount) {
            this.status = status;
            this.accountCount = accountCount;
            this.oldestAccount = oldestAccount;
            this.newestAccount = newestAccount;
        }

        public AccountEntity.AccountStatus getStatus() {
            return status;
        }

        public int getAccountCount() {
            return accountCount;
        }

        public LocalDate getOldestAccount() {
            return oldestAccount;
        }

        public LocalDate getNewestAccount() {
            return newestAccount;
        }

        @Override
        public String toString() {
            return "AccountStatusStats{" +
                    "status=" + status +
                    ", count=" + accountCount +
                    ", oldest=" + oldestAccount +
                    ", newest=" + newestAccount +
                    '}';
        }
    }
}