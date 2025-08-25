
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public final class TransactionRepository extends AbstractCrudRepository<TransactionEntity, String> {

    private static final String TABLE_NAME = "TRANSACTION";

    public TransactionRepository(ConnectionManager connectionManager) {
        super(connectionManager, TABLE_NAME);
    }


    @Override
    protected TransactionEntity mapResultSetToEntity(ResultSet rs) throws SQLException {
        return TransactionEntity.builder()
                .transactionId(rs.getString("transaction_id"))
                .cardNumber(rs.getString("card_number"))
                .accountId(rs.getString("account_id"))
                .customerId(rs.getString("customer_id"))
                .amount(rs.getBigDecimal("amount"))
                .currency(rs.getString("currency"))
                .merchantName(rs.getString("merchant_name"))
                .merchantCategory(rs.getString("merchant_category"))
                .merchantCity(rs.getString("merchant_city"))
                .merchantCountry(rs.getString("merchant_country"))
                .transactionDateTime(rs.getTimestamp("transaction_datetime").toLocalDateTime())
                .transactionType(TransactionEntity.TransactionType.valueOf(rs.getString("transaction_type")))
                .status(TransactionEntity.TransactionStatus.valueOf(rs.getString("status")))
                .authorizationCode(rs.getString("authorization_code"))
                .channel(rs.getString("channel"))
                .responseCode(rs.getString("response_code"))
                .description(rs.getString("description"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }

    @Override
    protected void setParametersForInsert(PreparedStatement ps, TransactionEntity entity) throws SQLException {
        ps.setString(1, entity.getTransactionId());
        ps.setString(2, entity.getCardNumber());
        ps.setString(3, entity.getAccountId());
        ps.setString(4, entity.getCustomerId());
        ps.setBigDecimal(5, entity.getAmount());
        ps.setString(6, entity.getCurrency());
        ps.setString(7, entity.getMerchantName());
        ps.setString(8, entity.getMerchantCategory());
        ps.setString(9, entity.getMerchantCity());
        ps.setString(10, entity.getMerchantCountry());
        ps.setTimestamp(11, Timestamp.valueOf(entity.getTransactionDateTime()));
        ps.setString(12, entity.getTransactionType().name());
        ps.setString(13, entity.getStatus().name());
        ps.setString(14, entity.getAuthorizationCode());
        ps.setString(15, entity.getChannel());
        ps.setString(16, entity.getResponseCode());
        ps.setString(17, entity.getDescription());
        ps.setTimestamp(18, Timestamp.valueOf(entity.getCreatedAt()));
        ps.setTimestamp(19, Timestamp.valueOf(entity.getUpdatedAt()));
    }

    @Override
    protected void setParametersForUpdate(PreparedStatement ps, TransactionEntity entity) throws SQLException {

        ps.setString(1, entity.getStatus().name());
        ps.setString(2, entity.getResponseCode());
        ps.setString(3, entity.getDescription());
        ps.setTimestamp(4, Timestamp.valueOf(entity.getUpdatedAt()));
        ps.setString(5, entity.getTransactionId()); // WHERE clause
    }

    @Override
    protected String getEntityId(TransactionEntity entity) {
        return entity.getTransactionId();
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
        return "(transaction_id, card_number, account_id, customer_id, amount, currency, " +
                "merchant_name, merchant_category, merchant_city, merchant_country, " +
                "transaction_datetime, transaction_type, status, authorization_code, " +
                "channel, response_code, description, created_at, updated_at)";
    }

    @Override
    protected String getInsertValues() {
        return "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    protected String getUpdateColumns() {

        return "status = ?, response_code = ?, description = ?, updated_at = ?";
    }

    @Override
    protected String getSelectByIdSql() {
        return "SELECT * FROM " + TABLE_NAME + " WHERE transaction_id = ?";
    }

    @Override
    protected String getExistsByIdSql() {
        return "SELECT 1 FROM " + TABLE_NAME + " WHERE transaction_id = ? LIMIT 1";
    }


    public List<TransactionEntity> findByCardNumber(String cardNumber) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE card_number = ? ORDER BY transaction_datetime DESC";
        return executeQueryForList(sql, cardNumber);
    }


    public List<TransactionEntity> findByCardNumber(String cardNumber, int limit) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE card_number = ? " +
                "ORDER BY transaction_datetime DESC LIMIT ?";
        return executeQueryForList(sql, cardNumber, limit);
    }


    public List<TransactionEntity> findByAccountId(String accountId) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE account_id = ? ORDER BY transaction_datetime DESC";
        return executeQueryForList(sql, accountId);
    }


    public List<TransactionEntity> findByCustomerId(String customerId) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE customer_id = ? ORDER BY transaction_datetime DESC";
        return executeQueryForList(sql, customerId);
    }


    public List<TransactionEntity> findByStatus(TransactionEntity.TransactionStatus status) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE status = ? ORDER BY transaction_datetime DESC";
        return executeQueryForList(sql, status.name());
    }


    public List<TransactionEntity> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE transaction_datetime BETWEEN ? AND ? ORDER BY transaction_datetime DESC";
        return executeQueryForList(sql, Timestamp.valueOf(startDate), Timestamp.valueOf(endDate));
    }


    public List<TransactionEntity> findByAmountRange(BigDecimal minAmount, BigDecimal maxAmount) {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE amount BETWEEN ? AND ? ORDER BY amount DESC";
        return executeQueryForList(sql, minAmount, maxAmount);
    }


    public List<TransactionEntity> findByMerchant(String merchantName) {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE merchant_name LIKE ? ORDER BY transaction_datetime DESC";
        return executeQueryForList(sql, "%" + merchantName + "%");
    }


    public List<TransactionEntity> findByMerchantCategory(String merchantCategory) {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE merchant_category = ? ORDER BY transaction_datetime DESC";
        return executeQueryForList(sql, merchantCategory);
    }


    public List<TransactionEntity> findForeignTransactions() {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE merchant_country IS NOT NULL AND merchant_country != 'TR' " +
                "ORDER BY transaction_datetime DESC";
        return executeQueryForList(sql);
    }


    public List<TransactionEntity> findHighValueTransactions(BigDecimal threshold) {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE amount >= ? ORDER BY amount DESC";
        return executeQueryForList(sql, threshold);
    }


    public List<TransactionEntity> findRecentTransactions(int days) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE transaction_datetime >= ? ORDER BY transaction_datetime DESC";
        return executeQueryForList(sql, Timestamp.valueOf(cutoffDate));
    }


    public List<TransactionEntity> findByResponseCode(String responseCode) {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE response_code = ? ORDER BY transaction_datetime DESC";
        return executeQueryForList(sql, responseCode);
    }


    public List<TransactionEntity> findDeclinedTransactions() {
        return findByStatus(TransactionEntity.TransactionStatus.DECLINED);
    }


    public List<TransactionEntity> findPendingTransactions() {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE status IN ('PENDING', 'PROCESSING') ORDER BY transaction_datetime ASC";
        return executeQueryForList(sql);
    }


    public long countByCardNumber(String cardNumber) {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE card_number = ?";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, cardNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to count transactions by card number", e);
        }
    }


    public BigDecimal sumAmountsByAccountId(String accountId) {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM " + TABLE_NAME +
                " WHERE account_id = ? AND status = 'APPROVED'";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to sum amounts by account ID", e);
        }
    }


    public List<DailyTransactionStats> getDailyStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        String sql = "SELECT DATE(transaction_datetime) as transaction_date, " +
                "COUNT(*) as transaction_count, " +
                "SUM(CASE WHEN status = 'APPROVED' THEN amount ELSE 0 END) as approved_amount, " +
                "COUNT(CASE WHEN status = 'APPROVED' THEN 1 END) as approved_count, " +
                "COUNT(CASE WHEN status = 'DECLINED' THEN 1 END) as declined_count " +
                "FROM " + TABLE_NAME + " " +
                "WHERE transaction_datetime BETWEEN ? AND ? " +
                "GROUP BY DATE(transaction_datetime) " +
                "ORDER BY transaction_date";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(startDate));
            ps.setTimestamp(2, Timestamp.valueOf(endDate));

            try (ResultSet rs = ps.executeQuery()) {
                List<DailyTransactionStats> stats = new java.util.ArrayList<>();
                while (rs.next()) {
                    stats.add(new DailyTransactionStats(
                            rs.getDate("transaction_date").toLocalDate(),
                            rs.getInt("transaction_count"),
                            rs.getBigDecimal("approved_amount"),
                            rs.getInt("approved_count"),
                            rs.getInt("declined_count")
                    ));
                }
                return stats;
            }
        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to get daily statistics", e);
        }
    }


    public boolean updateStatus(String transactionId, TransactionEntity.TransactionStatus newStatus,
                                String responseCode) {
        String sql = "UPDATE " + TABLE_NAME + " SET status = ?, response_code = ?, updated_at = ? " +
                "WHERE transaction_id = ?";

        int rowsAffected = executeUpdate(sql, newStatus.name(), responseCode,
                Timestamp.valueOf(LocalDateTime.now()), transactionId);
        return rowsAffected > 0;
    }


    public int bulkUpdateStatus(List<String> transactionIds, TransactionEntity.TransactionStatus newStatus) {
        if (transactionIds.isEmpty()) return 0;

        String sql = "UPDATE " + TABLE_NAME + " SET status = ?, updated_at = ? WHERE transaction_id = ?";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            for (String transactionId : transactionIds) {
                ps.setString(1, newStatus.name());
                ps.setTimestamp(2, now);
                ps.setString(3, transactionId);
                ps.addBatch();
            }

            int[] results = ps.executeBatch();
            return java.util.Arrays.stream(results).sum();

        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to bulk update transaction status", e);
        }
    }


    public int deleteOldTransactions(int daysOld) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE transaction_datetime < ?";

        return executeUpdate(sql, Timestamp.valueOf(cutoffDate));
    }


    public void createTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS TRANSACTION (
                    transaction_id VARCHAR(50) PRIMARY KEY,
                    card_number VARCHAR(20) NOT NULL,
                    account_id VARCHAR(50) NOT NULL,
                    customer_id VARCHAR(50) NOT NULL,
                    amount DECIMAL(15,2) NOT NULL,
                    currency VARCHAR(3) NOT NULL,
                    merchant_name VARCHAR(255),
                    merchant_category VARCHAR(50),
                    merchant_city VARCHAR(100),
                    merchant_country VARCHAR(3),
                    transaction_datetime TIMESTAMP NOT NULL,
                    transaction_type VARCHAR(20) NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    authorization_code VARCHAR(20),
                    channel VARCHAR(20),
                    response_code VARCHAR(10),
                    description VARCHAR(500),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """;

        executeUpdate(sql);

        String[] indexes = {
                "CREATE INDEX IF NOT EXISTS idx_card_number ON TRANSACTION(card_number)",
                "CREATE INDEX IF NOT EXISTS idx_account_id ON TRANSACTION(account_id)",
                "CREATE INDEX IF NOT EXISTS idx_customer_id ON TRANSACTION(customer_id)",
                "CREATE INDEX IF NOT EXISTS idx_transaction_datetime ON TRANSACTION(transaction_datetime)",
                "CREATE INDEX IF NOT EXISTS idx_status ON TRANSACTION(status)",
                "CREATE INDEX IF NOT EXISTS idx_amount ON TRANSACTION(amount)"
        };

        for (String indexSql : indexes) {
            executeUpdate(indexSql);
        }
    }


    public void dropTable() {
        executeUpdate("DROP TABLE IF EXISTS " + TABLE_NAME);
    }


    public static final class DailyTransactionStats {
        private final java.time.LocalDate date;
        private final int totalCount;
        private final BigDecimal approvedAmount;
        private final int approvedCount;
        private final int declinedCount;

        public DailyTransactionStats(java.time.LocalDate date, int totalCount,
                                     BigDecimal approvedAmount, int approvedCount, int declinedCount) {
            this.date = date;
            this.totalCount = totalCount;
            this.approvedAmount = approvedAmount;
            this.approvedCount = approvedCount;
            this.declinedCount = declinedCount;
        }

        public java.time.LocalDate getDate() {
            return date;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public BigDecimal getApprovedAmount() {
            return approvedAmount;
        }

        public int getApprovedCount() {
            return approvedCount;
        }

        public int getDeclinedCount() {
            return declinedCount;
        }

        public double getApprovalRate() {
            return totalCount == 0 ? 0.0 : (double) approvedCount / totalCount * 100.0;
        }

        @Override
        public String toString() {
            return "DailyTransactionStats{" +
                    "date=" + date +
                    ", total=" + totalCount +
                    ", approved=" + approvedCount + " (" + String.format("%.1f%%", getApprovalRate()) + ")" +
                    ", declined=" + declinedCount +
                    ", approvedAmount=" + approvedAmount +
                    '}';
        }
    }
}