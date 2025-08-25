
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public final class CustomerRepository extends AbstractCrudRepository<CustomerEntity, String> {

    private static final String TABLE_NAME = "CUSTOMER";

    public CustomerRepository(ConnectionManager connectionManager) {
        super(connectionManager, TABLE_NAME);
    }


    @Override
    protected CustomerEntity mapResultSetToEntity(ResultSet rs) throws SQLException {
        return CustomerEntity.builder()
                .customerId(rs.getString("customer_id"))
                .nationalId(rs.getString("national_id"))
                .firstName(rs.getString("first_name"))
                .lastName(rs.getString("last_name"))
                .email(rs.getString("email"))
                .phoneNumber(rs.getString("phone_number"))
                .dateOfBirth(rs.getDate("date_of_birth").toLocalDate())
                .gender(rs.getString("gender") != null ?
                        CustomerEntity.Gender.valueOf(rs.getString("gender")) : null)
                .customerType(CustomerEntity.CustomerType.valueOf(rs.getString("customer_type")))
                .status(CustomerEntity.CustomerStatus.valueOf(rs.getString("status")))
                .segment(CustomerEntity.CustomerSegment.valueOf(rs.getString("segment")))
                .address(rs.getString("address"))
                .city(rs.getString("city"))
                .country(rs.getString("country"))
                .postalCode(rs.getString("postal_code"))
                .occupation(rs.getString("occupation"))
                .employerName(rs.getString("employer_name"))
                .customerSince(rs.getDate("customer_since").toLocalDate())
                .branchCode(rs.getString("branch_code"))
                .relationshipManager(rs.getString("relationship_manager"))
                .riskProfile(rs.getString("risk_profile"))
                .kycStatus(rs.getString("kyc_status"))
                .lastLoginDate(rs.getTimestamp("last_login_date") != null ?
                        rs.getTimestamp("last_login_date").toLocalDateTime() : null)
                .preferredLanguage(rs.getString("preferred_language"))
                .marketingConsent(rs.getBoolean("marketing_consent"))
                .notes(rs.getString("notes"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }

    @Override
    protected void setParametersForInsert(PreparedStatement ps, CustomerEntity entity) throws SQLException {
        ps.setString(1, entity.getCustomerId());
        ps.setString(2, entity.getNationalId());
        ps.setString(3, entity.getFirstName());
        ps.setString(4, entity.getLastName());
        ps.setString(5, entity.getEmail());
        ps.setString(6, entity.getPhoneNumber());
        ps.setDate(7, Date.valueOf(entity.getDateOfBirth()));
        ps.setString(8, entity.getGender() != null ? entity.getGender().name() : null);
        ps.setString(9, entity.getCustomerType().name());
        ps.setString(10, entity.getStatus().name());
        ps.setString(11, entity.getSegment().name());
        ps.setString(12, entity.getAddress());
        ps.setString(13, entity.getCity());
        ps.setString(14, entity.getCountry());
        ps.setString(15, entity.getPostalCode());
        ps.setString(16, entity.getOccupation());
        ps.setString(17, entity.getEmployerName());
        ps.setDate(18, Date.valueOf(entity.getCustomerSince()));
        ps.setString(19, entity.getBranchCode());
        ps.setString(20, entity.getRelationshipManager());
        ps.setString(21, entity.getRiskProfile());
        ps.setString(22, entity.getKycStatus());
        ps.setTimestamp(23, entity.getLastLoginDate() != null ?
                Timestamp.valueOf(entity.getLastLoginDate()) : null);
        ps.setString(24, entity.getPreferredLanguage());
        ps.setBoolean(25, entity.isMarketingConsent());
        ps.setString(26, entity.getNotes());
        ps.setTimestamp(27, Timestamp.valueOf(entity.getCreatedAt()));
        ps.setTimestamp(28, Timestamp.valueOf(entity.getUpdatedAt()));
    }

    @Override
    protected void setParametersForUpdate(PreparedStatement ps, CustomerEntity entity) throws SQLException {

        ps.setString(1, entity.getEmail());
        ps.setString(2, entity.getPhoneNumber());
        ps.setString(3, entity.getStatus().name());
        ps.setString(4, entity.getSegment().name());
        ps.setString(5, entity.getAddress());
        ps.setString(6, entity.getCity());
        ps.setString(7, entity.getPostalCode());
        ps.setString(8, entity.getOccupation());
        ps.setString(9, entity.getEmployerName());
        ps.setString(10, entity.getRelationshipManager());
        ps.setString(11, entity.getRiskProfile());
        ps.setString(12, entity.getKycStatus());
        ps.setTimestamp(13, entity.getLastLoginDate() != null ?
                Timestamp.valueOf(entity.getLastLoginDate()) : null);
        ps.setString(14, entity.getPreferredLanguage());
        ps.setBoolean(15, entity.isMarketingConsent());
        ps.setString(16, entity.getNotes());
        ps.setTimestamp(17, Timestamp.valueOf(entity.getUpdatedAt()));
        ps.setString(18, entity.getCustomerId()); // WHERE clause
    }

    @Override
    protected String getEntityId(CustomerEntity entity) {
        return entity.getCustomerId();
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
        return "(customer_id, national_id, first_name, last_name, email, phone_number, " +
                "date_of_birth, gender, customer_type, status, segment, address, city, country, " +
                "postal_code, occupation, employer_name, customer_since, branch_code, " +
                "relationship_manager, risk_profile, kyc_status, last_login_date, " +
                "preferred_language, marketing_consent, notes, created_at, updated_at)";
    }

    @Override
    protected String getInsertValues() {
        return "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    protected String getUpdateColumns() {
        return "email = ?, phone_number = ?, status = ?, segment = ?, address = ?, city = ?, " +
                "postal_code = ?, occupation = ?, employer_name = ?, relationship_manager = ?, " +
                "risk_profile = ?, kyc_status = ?, last_login_date = ?, preferred_language = ?, " +
                "marketing_consent = ?, notes = ?, updated_at = ?";
    }

    @Override
    protected String getSelectByIdSql() {
        return "SELECT * FROM " + TABLE_NAME + " WHERE customer_id = ?";
    }

    @Override
    protected String getExistsByIdSql() {
        return "SELECT 1 FROM " + TABLE_NAME + " WHERE customer_id = ? LIMIT 1";
    }


    public Optional<CustomerEntity> findByNationalId(String nationalId) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE national_id = ?";
        return executeQueryForSingleResult(sql, nationalId);
    }


    public Optional<CustomerEntity> findByEmail(String email) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE email = ?";
        return executeQueryForSingleResult(sql, email.toLowerCase());
    }


    public Optional<CustomerEntity> findByPhoneNumber(String phoneNumber) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE phone_number = ?";
        return executeQueryForSingleResult(sql, phoneNumber);
    }


    public List<CustomerEntity> findByName(String searchTerm) {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE LOWER(first_name) LIKE LOWER(?) OR LOWER(last_name) LIKE LOWER(?) " +
                "ORDER BY first_name, last_name";
        String pattern = "%" + searchTerm + "%";
        return executeQueryForList(sql, pattern, pattern);
    }


    public List<CustomerEntity> findByCustomerType(CustomerEntity.CustomerType customerType) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE customer_type = ? ORDER BY customer_since";
        return executeQueryForList(sql, customerType.name());
    }


    public List<CustomerEntity> findByStatus(CustomerEntity.CustomerStatus status) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE status = ? ORDER BY updated_at DESC";
        return executeQueryForList(sql, status.name());
    }


    public List<CustomerEntity> findBySegment(CustomerEntity.CustomerSegment segment) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE segment = ? ORDER BY customer_since";
        return executeQueryForList(sql, segment.name());
    }


    public List<CustomerEntity> findByCity(String city) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE city = ? ORDER BY first_name, last_name";
        return executeQueryForList(sql, city);
    }


    public List<CustomerEntity> findByCountry(String country) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE country = ? ORDER BY city, first_name";
        return executeQueryForList(sql, country);
    }


    public List<CustomerEntity> findByAgeRange(int minAge, int maxAge) {
        LocalDate maxDate = LocalDate.now().minusYears(minAge);
        LocalDate minDate = LocalDate.now().minusYears(maxAge + 1);

        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE date_of_birth BETWEEN ? AND ? ORDER BY date_of_birth";
        return executeQueryForList(sql, Date.valueOf(minDate), Date.valueOf(maxDate));
    }


    public List<CustomerEntity> findByRiskProfile(String riskProfile) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE risk_profile = ? ORDER BY customer_since";
        return executeQueryForList(sql, riskProfile);
    }


    public List<CustomerEntity> findByKycStatus(String kycStatus) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE kyc_status = ? ORDER BY updated_at";
        return executeQueryForList(sql, kycStatus);
    }


    public List<CustomerEntity> findVipCustomers() {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE segment IN ('VIP', 'PRIVATE_BANKING') AND status = 'ACTIVE' " +
                "ORDER BY customer_since";
        return executeQueryForList(sql);
    }


    public List<CustomerEntity> findHighRiskCustomers() {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE risk_profile = 'HIGH' ORDER BY updated_at DESC";
        return executeQueryForList(sql);
    }


    public List<CustomerEntity> findCustomersRequiringKyc() {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE kyc_status IN ('PENDING', 'EXPIRED', 'INCOMPLETE') " +
                "ORDER BY customer_since";
        return executeQueryForList(sql);
    }


    public List<CustomerEntity> findNewCustomers(int days) {
        LocalDate cutoffDate = LocalDate.now().minusDays(days);
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE customer_since >= ? ORDER BY customer_since DESC";
        return executeQueryForList(sql, Date.valueOf(cutoffDate));
    }


    public List<CustomerEntity> findLongTermCustomers(int years) {
        LocalDate cutoffDate = LocalDate.now().minusYears(years);
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE customer_since <= ? ORDER BY customer_since";
        return executeQueryForList(sql, Date.valueOf(cutoffDate));
    }


    public List<CustomerEntity> findDormantCustomers(int days) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE last_login_date IS NULL OR last_login_date <= ? " +
                "ORDER BY COALESCE(last_login_date, created_at)";
        return executeQueryForList(sql, Timestamp.valueOf(cutoffDate));
    }


    public List<CustomerEntity> findByBranchCode(String branchCode) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE branch_code = ? ORDER BY first_name, last_name";
        return executeQueryForList(sql, branchCode);
    }


    public List<CustomerEntity> findByRelationshipManager(String relationshipManager) {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE relationship_manager = ? ORDER BY customer_since";
        return executeQueryForList(sql, relationshipManager);
    }


    public List<CustomerEntity> findByOccupation(String occupation) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE occupation LIKE ? ORDER BY first_name, last_name";
        return executeQueryForList(sql, "%" + occupation + "%");
    }


    public List<CustomerEntity> findWithMarketingConsent() {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE marketing_consent = TRUE AND status = 'ACTIVE' " +
                "ORDER BY segment, first_name";
        return executeQueryForList(sql);
    }


    public List<CustomerEntity> findByPreferredLanguage(String language) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE preferred_language = ? ORDER BY first_name, last_name";
        return executeQueryForList(sql, language);
    }


    public boolean updateStatus(String customerId, CustomerEntity.CustomerStatus newStatus) {
        String sql = "UPDATE " + TABLE_NAME + " SET status = ?, updated_at = ? WHERE customer_id = ?";

        int rowsAffected = executeUpdate(sql, newStatus.name(),
                Timestamp.valueOf(LocalDateTime.now()), customerId);
        return rowsAffected > 0;
    }


    public boolean updateSegment(String customerId, CustomerEntity.CustomerSegment newSegment) {
        String sql = "UPDATE " + TABLE_NAME + " SET segment = ?, updated_at = ? WHERE customer_id = ?";

        int rowsAffected = executeUpdate(sql, newSegment.name(),
                Timestamp.valueOf(LocalDateTime.now()), customerId);
        return rowsAffected > 0;
    }


    public boolean updateRiskProfile(String customerId, String riskProfile) {
        String sql = "UPDATE " + TABLE_NAME + " SET risk_profile = ?, updated_at = ? WHERE customer_id = ?";

        int rowsAffected = executeUpdate(sql, riskProfile,
                Timestamp.valueOf(LocalDateTime.now()), customerId);
        return rowsAffected > 0;
    }


    public boolean updateKycStatus(String customerId, String kycStatus) {
        String sql = "UPDATE " + TABLE_NAME + " SET kyc_status = ?, updated_at = ? WHERE customer_id = ?";

        int rowsAffected = executeUpdate(sql, kycStatus,
                Timestamp.valueOf(LocalDateTime.now()), customerId);
        return rowsAffected > 0;
    }


    public boolean updateLastLogin(String customerId, LocalDateTime loginTime) {
        String sql = "UPDATE " + TABLE_NAME + " SET last_login_date = ?, updated_at = ? WHERE customer_id = ?";

        int rowsAffected = executeUpdate(sql, Timestamp.valueOf(loginTime),
                Timestamp.valueOf(LocalDateTime.now()), customerId);
        return rowsAffected > 0;
    }


    public boolean updateContactInfo(String customerId, String email, String phoneNumber, String address) {
        String sql = "UPDATE " + TABLE_NAME +
                " SET email = ?, phone_number = ?, address = ?, updated_at = ? WHERE customer_id = ?";

        int rowsAffected = executeUpdate(sql, email.toLowerCase(), phoneNumber, address,
                Timestamp.valueOf(LocalDateTime.now()), customerId);
        return rowsAffected > 0;
    }


    public long countByStatus(CustomerEntity.CustomerStatus status) {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE status = ?";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to count customers by status", e);
        }
    }


    public long countByCustomerType(CustomerEntity.CustomerType customerType) {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE customer_type = ?";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, customerType.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to count customers by type", e);
        }
    }


    public List<CustomerSegmentStats> getCustomerStatsBySegment() {
        String sql = "SELECT segment, COUNT(*) as customer_count, " +
                "MIN(customer_since) as oldest_customer, MAX(customer_since) as newest_customer, " +
                "AVG(DATEDIFF(CURDATE(), date_of_birth) / 365.25) as average_age " +
                "FROM " + TABLE_NAME + " WHERE status = 'ACTIVE' " +
                "GROUP BY segment ORDER BY customer_count DESC";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<CustomerSegmentStats> stats = new java.util.ArrayList<>();
            while (rs.next()) {
                stats.add(new CustomerSegmentStats(
                        CustomerEntity.CustomerSegment.valueOf(rs.getString("segment")),
                        rs.getInt("customer_count"),
                        rs.getDate("oldest_customer").toLocalDate(),
                        rs.getDate("newest_customer").toLocalDate(),
                        rs.getDouble("average_age")
                ));
            }
            return stats;
        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to get customer stats by segment", e);
        }
    }


    public List<AgeGroupStats> getCustomersByAgeGroup() {
        String sql = """
                SELECT 
                    CASE 
                        WHEN DATEDIFF(CURDATE(), date_of_birth) / 365.25 BETWEEN 18 AND 24 THEN '18-24'
                        WHEN DATEDIFF(CURDATE(), date_of_birth) / 365.25 BETWEEN 25 AND 34 THEN '25-34'
                        WHEN DATEDIFF(CURDATE(), date_of_birth) / 365.25 BETWEEN 35 AND 44 THEN '35-44'
                        WHEN DATEDIFF(CURDATE(), date_of_birth) / 365.25 BETWEEN 45 AND 54 THEN '45-54'
                        WHEN DATEDIFF(CURDATE(), date_of_birth) / 365.25 BETWEEN 55 AND 64 THEN '55-64'
                        ELSE '65+'
                    END as age_group,
                    COUNT(*) as customer_count,
                    COUNT(CASE WHEN gender = 'MALE' THEN 1 END) as male_count,
                    COUNT(CASE WHEN gender = 'FEMALE' THEN 1 END) as female_count
                FROM CUSTOMER 
                WHERE status = 'ACTIVE'
                GROUP BY age_group 
                ORDER BY MIN(DATEDIFF(CURDATE(), date_of_birth) / 365.25)
                """;

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<AgeGroupStats> stats = new java.util.ArrayList<>();
            while (rs.next()) {
                stats.add(new AgeGroupStats(
                        rs.getString("age_group"),
                        rs.getInt("customer_count"),
                        rs.getInt("male_count"),
                        rs.getInt("female_count")
                ));
            }
            return stats;
        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to get customers by age group", e);
        }
    }


    public boolean isEmailExists(String email) {
        String sql = "SELECT 1 FROM " + TABLE_NAME + " WHERE email = ? LIMIT 1";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to check email existence", e);
        }
    }


    public boolean isNationalIdExists(String nationalId) {
        String sql = "SELECT 1 FROM " + TABLE_NAME + " WHERE national_id = ? LIMIT 1";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, nationalId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to check national ID existence", e);
        }
    }


    public void createTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS CUSTOMER (
                    customer_id VARCHAR(50) PRIMARY KEY,
                    national_id VARCHAR(11) NOT NULL UNIQUE,
                    first_name VARCHAR(100) NOT NULL,
                    last_name VARCHAR(100) NOT NULL,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    phone_number VARCHAR(20) NOT NULL,
                    date_of_birth DATE NOT NULL,
                    gender VARCHAR(20),
                    customer_type VARCHAR(30) NOT NULL,
                    status VARCHAR(30) NOT NULL,
                    segment VARCHAR(30) NOT NULL,
                    address VARCHAR(500),
                    city VARCHAR(100),
                    country VARCHAR(3) NOT NULL,
                    postal_code VARCHAR(20),
                    occupation VARCHAR(100),
                    employer_name VARCHAR(200),
                    customer_since DATE NOT NULL,
                    branch_code VARCHAR(10),
                    relationship_manager VARCHAR(100),
                    risk_profile VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
                    kyc_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    last_login_date TIMESTAMP,
                    preferred_language VARCHAR(5) NOT NULL DEFAULT 'TR',
                    marketing_consent BOOLEAN NOT NULL DEFAULT FALSE,
                    notes TEXT,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """;

        executeUpdate(sql);

        String[] indexes = {
                "CREATE INDEX IF NOT EXISTS idx_national_id ON CUSTOMER(national_id)",
                "CREATE INDEX IF NOT EXISTS idx_email ON CUSTOMER(email)",
                "CREATE INDEX IF NOT EXISTS idx_phone_number ON CUSTOMER(phone_number)",
                "CREATE INDEX IF NOT EXISTS idx_customer_type ON CUSTOMER(customer_type)",
                "CREATE INDEX IF NOT EXISTS idx_status ON CUSTOMER(status)",
                "CREATE INDEX IF NOT EXISTS idx_segment ON CUSTOMER(segment)",
                "CREATE INDEX IF NOT EXISTS idx_date_of_birth ON CUSTOMER(date_of_birth)",
                "CREATE INDEX IF NOT EXISTS idx_customer_since ON CUSTOMER(customer_since)",
                "CREATE INDEX IF NOT EXISTS idx_risk_profile ON CUSTOMER(risk_profile)",
                "CREATE INDEX IF NOT EXISTS idx_kyc_status ON CUSTOMER(kyc_status)"
        };

        for (String indexSql : indexes) {
            executeUpdate(indexSql);
        }
    }


    public void dropTable() {
        executeUpdate("DROP TABLE IF EXISTS " + TABLE_NAME);
    }


    public static final class CustomerSegmentStats {
        private final CustomerEntity.CustomerSegment segment;
        private final int customerCount;
        private final LocalDate oldestCustomer;
        private final LocalDate newestCustomer;
        private final double averageAge;

        public CustomerSegmentStats(CustomerEntity.CustomerSegment segment, int customerCount,
                                    LocalDate oldestCustomer, LocalDate newestCustomer, double averageAge) {
            this.segment = segment;
            this.customerCount = customerCount;
            this.oldestCustomer = oldestCustomer;
            this.newestCustomer = newestCustomer;
            this.averageAge = averageAge;
        }

        public CustomerEntity.CustomerSegment getSegment() {
            return segment;
        }

        public int getCustomerCount() {
            return customerCount;
        }

        public LocalDate getOldestCustomer() {
            return oldestCustomer;
        }

        public LocalDate getNewestCustomer() {
            return newestCustomer;
        }

        public double getAverageAge() {
            return averageAge;
        }

        @Override
        public String toString() {
            return "CustomerSegmentStats{" +
                    "segment=" + segment +
                    ", count=" + customerCount +
                    ", avgAge=" + String.format("%.1f", averageAge) +
                    ", oldest=" + oldestCustomer +
                    ", newest=" + newestCustomer +
                    '}';
        }
    }


    public static final class AgeGroupStats {
        private final String ageGroup;
        private final int totalCount;
        private final int maleCount;
        private final int femaleCount;

        public AgeGroupStats(String ageGroup, int totalCount, int maleCount, int femaleCount) {
            this.ageGroup = ageGroup;
            this.totalCount = totalCount;
            this.maleCount = maleCount;
            this.femaleCount = femaleCount;
        }

        public String getAgeGroup() {
            return ageGroup;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public int getMaleCount() {
            return maleCount;
        }

        public int getFemaleCount() {
            return femaleCount;
        }

        public double getMalePercentage() {
            return totalCount == 0 ? 0.0 : (double) maleCount / totalCount * 100.0;
        }

        public double getFemalePercentage() {
            return totalCount == 0 ? 0.0 : (double) femaleCount / totalCount * 100.0;
        }

        @Override
        public String toString() {
            return "AgeGroupStats{" +
                    "group='" + ageGroup + '\'' +
                    ", total=" + totalCount +
                    ", male=" + maleCount + " (" + String.format("%.1f%%", getMalePercentage()) + ")" +
                    ", female=" + femaleCount + " (" + String.format("%.1f%%", getFemalePercentage()) + ")" +
                    '}';
        }
    }
}