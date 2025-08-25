
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;


public abstract class AbstractCrudRepository<T, ID> implements CrudRepository<T, ID> {

    protected final ConnectionManager connectionManager;
    protected final String tableName;

    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong totalInserts = new AtomicLong(0);
    private final AtomicLong totalUpdates = new AtomicLong(0);
    private final AtomicLong totalDeletes = new AtomicLong(0);
    private final AtomicLong totalSelects = new AtomicLong(0);
    private final AtomicLong connectionErrors = new AtomicLong(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);

    protected AbstractCrudRepository(ConnectionManager connectionManager, String tableName) {
        this.connectionManager = Objects.requireNonNull(connectionManager, "ConnectionManager cannot be null");
        this.tableName = Objects.requireNonNull(tableName, "Table name cannot be null");
    }


    protected abstract T mapResultSetToEntity(ResultSet rs) throws SQLException;


    protected abstract void setParametersForInsert(PreparedStatement ps, T entity) throws SQLException;


    protected abstract void setParametersForUpdate(PreparedStatement ps, T entity) throws SQLException;


    protected abstract ID getEntityId(T entity);


    protected abstract void setIdParameter(PreparedStatement ps, int parameterIndex, ID id) throws SQLException;


    protected abstract ID createIdFromGeneratedKey(Object generatedKey);


    protected String getSelectByIdSql() {
        return "SELECT * FROM " + tableName + " WHERE id = ?";
    }

    protected String getSelectAllSql() {
        return "SELECT * FROM " + tableName;
    }

    protected String getSelectAllWithLimitSql() {
        return "SELECT * FROM " + tableName + " LIMIT ? OFFSET ?";
    }

    protected String getCountSql() {
        return "SELECT COUNT(*) FROM " + tableName;
    }

    protected String getInsertSql() {
        return "INSERT INTO " + tableName + " " + getInsertColumns() + " VALUES " + getInsertValues();
    }

    protected String getUpdateSql() {
        return "UPDATE " + tableName + " SET " + getUpdateColumns() + " WHERE id = ?";
    }

    protected String getDeleteByIdSql() {
        return "DELETE FROM " + tableName + " WHERE id = ?";
    }

    protected String getDeleteAllSql() {
        return "DELETE FROM " + tableName;
    }

    protected String getExistsByIdSql() {
        return "SELECT 1 FROM " + tableName + " WHERE id = ? LIMIT 1";
    }

    protected abstract String getInsertColumns();

    protected abstract String getInsertValues();

    protected abstract String getUpdateColumns();


    @Override
    public T save(T entity) {
        Objects.requireNonNull(entity, "Entity cannot be null");

        ID id = getEntityId(entity);
        if (id == null || !existsById(id)) {
            return insert(entity);
        } else {
            return update(entity);
        }
    }

    @Override
    public Optional<T> findById(ID id) {
        Objects.requireNonNull(id, "ID cannot be null");

        long startTime = System.nanoTime();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(getSelectByIdSql())) {

            setIdParameter(ps, 1, id);

            try (ResultSet rs = ps.executeQuery()) {
                totalSelects.incrementAndGet();

                if (rs.next()) {
                    return Optional.of(mapResultSetToEntity(rs));
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            connectionErrors.incrementAndGet();
            throw DataAccessException.fromSQLException("Failed to find entity by ID: " + id, e);
        } finally {
            recordExecutionTime(startTime);
        }
    }

    @Override
    public boolean existsById(ID id) {
        Objects.requireNonNull(id, "ID cannot be null");

        long startTime = System.nanoTime();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(getExistsByIdSql())) {

            setIdParameter(ps, 1, id);

            try (ResultSet rs = ps.executeQuery()) {
                totalSelects.incrementAndGet();
                return rs.next();
            }

        } catch (SQLException e) {
            connectionErrors.incrementAndGet();
            throw DataAccessException.fromSQLException("Failed to check existence for ID: " + id, e);
        } finally {
            recordExecutionTime(startTime);
        }
    }

    @Override
    public List<T> findAll() {
        long startTime = System.nanoTime();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(getSelectAllSql());
             ResultSet rs = ps.executeQuery()) {

            totalSelects.incrementAndGet();

            List<T> results = new ArrayList<>();
            while (rs.next()) {
                results.add(mapResultSetToEntity(rs));
            }
            return results;

        } catch (SQLException e) {
            connectionErrors.incrementAndGet();
            throw DataAccessException.fromSQLException("Failed to find all entities", e);
        } finally {
            recordExecutionTime(startTime);
        }
    }

    @Override
    public List<T> findAll(int offset, int limit) {
        if (offset < 0) throw new IllegalArgumentException("Offset cannot be negative");
        if (limit < 1) throw new IllegalArgumentException("Limit must be positive");

        long startTime = System.nanoTime();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(getSelectAllWithLimitSql())) {

            ps.setInt(1, limit);
            ps.setInt(2, offset);

            try (ResultSet rs = ps.executeQuery()) {
                totalSelects.incrementAndGet();

                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs));
                }
                return results;
            }

        } catch (SQLException e) {
            connectionErrors.incrementAndGet();
            throw DataAccessException.fromSQLException(
                    "Failed to find entities with offset=" + offset + ", limit=" + limit, e);
        } finally {
            recordExecutionTime(startTime);
        }
    }

    @Override
    public long count() {
        long startTime = System.nanoTime();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(getCountSql());
             ResultSet rs = ps.executeQuery()) {

            totalSelects.incrementAndGet();

            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;

        } catch (SQLException e) {
            connectionErrors.incrementAndGet();
            throw DataAccessException.fromSQLException("Failed to count entities", e);
        } finally {
            recordExecutionTime(startTime);
        }
    }

    @Override
    public boolean deleteById(ID id) {
        Objects.requireNonNull(id, "ID cannot be null");

        long startTime = System.nanoTime();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(getDeleteByIdSql())) {

            setIdParameter(ps, 1, id);

            int rowsAffected = ps.executeUpdate();
            totalDeletes.incrementAndGet();

            return rowsAffected > 0;

        } catch (SQLException e) {
            connectionErrors.incrementAndGet();
            throw DataAccessException.fromSQLException("Failed to delete entity by ID: " + id, e);
        } finally {
            recordExecutionTime(startTime);
        }
    }

    @Override
    public boolean delete(T entity) {
        Objects.requireNonNull(entity, "Entity cannot be null");

        ID id = getEntityId(entity);
        if (id == null) {
            throw new IllegalArgumentException("Entity ID cannot be null");
        }

        return deleteById(id);
    }

    @Override
    public int deleteAll() {
        long startTime = System.nanoTime();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(getDeleteAllSql())) {

            int rowsAffected = ps.executeUpdate();
            totalDeletes.incrementAndGet();

            return rowsAffected;

        } catch (SQLException e) {
            connectionErrors.incrementAndGet();
            throw DataAccessException.fromSQLException("Failed to delete all entities", e);
        } finally {
            recordExecutionTime(startTime);
        }
    }

    @Override
    public int saveAll(List<T> entities) {
        Objects.requireNonNull(entities, "Entity list cannot be null");
        if (entities.isEmpty()) {
            throw new IllegalArgumentException("Entity list cannot be empty");
        }

        int totalSaved = 0;

        List<T> toInsert = new ArrayList<>();
        List<T> toUpdate = new ArrayList<>();

        for (T entity : entities) {
            ID id = getEntityId(entity);
            if (id == null || !existsById(id)) {
                toInsert.add(entity);
            } else {
                toUpdate.add(entity);
            }
        }

        if (!toInsert.isEmpty()) {
            totalSaved += batchInsert(toInsert);
        }

        if (!toUpdate.isEmpty()) {
            totalSaved += batchUpdate(toUpdate);
        }

        return totalSaved;
    }


    protected T insert(T entity) {
        long startTime = System.nanoTime();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(getInsertSql(), Statement.RETURN_GENERATED_KEYS)) {

            setParametersForInsert(ps, entity);

            int rowsAffected = ps.executeUpdate();
            totalInserts.incrementAndGet();

            if (rowsAffected == 0) {
                throw new DataAccessException("Insert failed, no rows affected");
            }

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {

                    Object generatedKey = generatedKeys.getObject(1);
                    ID newId = createIdFromGeneratedKey(generatedKey);
                    return updateEntityWithId(entity, newId);
                }
            }

            return entity;

        } catch (SQLException e) {
            connectionErrors.incrementAndGet();
            throw DataAccessException.fromSQLException("Failed to insert entity", e);
        } finally {
            recordExecutionTime(startTime);
        }
    }


    protected T update(T entity) {
        long startTime = System.nanoTime();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(getUpdateSql())) {

            setParametersForUpdate(ps, entity);

            int rowsAffected = ps.executeUpdate();
            totalUpdates.incrementAndGet();

            if (rowsAffected == 0) {
                throw new DataAccessException("Update failed, no rows affected");
            }

            return entity;

        } catch (SQLException e) {
            connectionErrors.incrementAndGet();
            throw DataAccessException.fromSQLException("Failed to update entity", e);
        } finally {
            recordExecutionTime(startTime);
        }
    }


    protected int batchInsert(List<T> entities) {
        if (entities.isEmpty()) return 0;

        long startTime = System.nanoTime();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(getInsertSql())) {

            for (T entity : entities) {
                setParametersForInsert(ps, entity);
                ps.addBatch();
            }

            int[] results = ps.executeBatch();
            int totalInserted = Arrays.stream(results).sum();
            totalInserts.addAndGet(totalInserted);

            return totalInserted;

        } catch (SQLException e) {
            connectionErrors.incrementAndGet();
            throw DataAccessException.fromSQLException("Failed to batch insert entities", e);
        } finally {
            recordExecutionTime(startTime);
        }
    }


    protected int batchUpdate(List<T> entities) {
        if (entities.isEmpty()) return 0;

        long startTime = System.nanoTime();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(getUpdateSql())) {

            for (T entity : entities) {
                setParametersForUpdate(ps, entity);
                ps.addBatch();
            }

            int[] results = ps.executeBatch();
            int totalUpdated = Arrays.stream(results).sum();
            totalUpdates.addAndGet(totalUpdated);

            return totalUpdated;

        } catch (SQLException e) {
            connectionErrors.incrementAndGet();
            throw DataAccessException.fromSQLException("Failed to batch update entities", e);
        } finally {
            recordExecutionTime(startTime);
        }
    }


    protected T updateEntityWithId(T entity, ID newId) {

        return entity;
    }


    protected Optional<T> executeQueryForSingleResult(String sql, Object... parameters) {
        long startTime = System.nanoTime();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            setParameters(ps, parameters);

            try (ResultSet rs = ps.executeQuery()) {
                totalSelects.incrementAndGet();

                if (rs.next()) {
                    return Optional.of(mapResultSetToEntity(rs));
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            connectionErrors.incrementAndGet();
            throw DataAccessException.fromSQLException("Failed to execute query: " + sql, e);
        } finally {
            recordExecutionTime(startTime);
        }
    }


    protected List<T> executeQueryForList(String sql, Object... parameters) {
        long startTime = System.nanoTime();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            setParameters(ps, parameters);

            try (ResultSet rs = ps.executeQuery()) {
                totalSelects.incrementAndGet();

                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs));
                }
                return results;
            }

        } catch (SQLException e) {
            connectionErrors.incrementAndGet();
            throw DataAccessException.fromSQLException("Failed to execute query: " + sql, e);
        } finally {
            recordExecutionTime(startTime);
        }
    }


    protected int executeUpdate(String sql, Object... parameters) {
        long startTime = System.nanoTime();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            setParameters(ps, parameters);

            int rowsAffected = ps.executeUpdate();
            totalUpdates.incrementAndGet();

            return rowsAffected;

        } catch (SQLException e) {
            connectionErrors.incrementAndGet();
            throw DataAccessException.fromSQLException("Failed to execute update: " + sql, e);
        } finally {
            recordExecutionTime(startTime);
        }
    }


    protected void setParameters(PreparedStatement ps, Object... parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            Object param = parameters[i];
            if (param == null) {
                ps.setNull(i + 1, Types.NULL);
            } else if (param instanceof String) {
                ps.setString(i + 1, (String) param);
            } else if (param instanceof Integer) {
                ps.setInt(i + 1, (Integer) param);
            } else if (param instanceof Long) {
                ps.setLong(i + 1, (Long) param);
            } else if (param instanceof java.math.BigDecimal) {
                ps.setBigDecimal(i + 1, (java.math.BigDecimal) param);
            } else if (param instanceof java.sql.Timestamp) {
                ps.setTimestamp(i + 1, (java.sql.Timestamp) param);
            } else if (param instanceof java.sql.Date) {
                ps.setDate(i + 1, (java.sql.Date) param);
            } else if (param instanceof Boolean) {
                ps.setBoolean(i + 1, (Boolean) param);
            } else {
                ps.setObject(i + 1, param);
            }
        }
    }


    private void recordExecutionTime(long startTime) {
        long executionTime = System.nanoTime() - startTime;
        totalExecutionTime.addAndGet(executionTime);
        totalQueries.incrementAndGet();
    }


    @Override
    public boolean isHealthy() {
        try (Connection conn = connectionManager.getConnection()) {
            return conn.isValid(5); // 5 second timeout
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public RepositoryStatistics getStatistics() {
        long queries = totalQueries.get();
        long avgTime = queries == 0 ? 0 : totalExecutionTime.get() / queries;

        return new RepositoryStatistics(
                queries,
                avgTime,
                totalInserts.get(),
                totalUpdates.get(),
                totalDeletes.get(),
                totalSelects.get(),
                connectionErrors.get()
        );
    }

    @Override
    public String getTableName() {
        return tableName;
    }
}