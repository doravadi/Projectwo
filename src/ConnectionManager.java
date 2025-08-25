
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


public final class ConnectionManager implements AutoCloseable {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final Properties connectionProperties;

    private final int minPoolSize;
    private final int maxPoolSize;
    private final int connectionTimeoutMs;
    private final int validationTimeoutMs;
    private final int maxIdleTimeMs;

    private final BlockingQueue<PooledConnection> availableConnections;
    private final ConcurrentMap<Connection, PooledConnection> activeConnections;
    private final AtomicInteger totalConnections;
    private final AtomicInteger activeConnectionCount;

    private final AtomicLong totalConnectionsCreated;
    private final AtomicLong totalConnectionsDestroyed;
    private final AtomicLong totalConnectionsRequested;
    private final AtomicLong totalConnectionTimeouts;
    private final AtomicLong totalValidationFailures;

    private volatile boolean initialized;
    private volatile boolean shutdown;
    private final ScheduledExecutorService maintenanceExecutor;

    private final ThreadLocal<TransactionContext> transactionContext;

    private ConnectionManager(Builder builder) {
        this.jdbcUrl = Objects.requireNonNull(builder.jdbcUrl, "JDBC URL cannot be null");
        this.username = Objects.requireNonNull(builder.username, "Username cannot be null");
        this.password = Objects.requireNonNull(builder.password, "Password cannot be null");
        this.connectionProperties = new Properties(builder.connectionProperties);

        this.minPoolSize = builder.minPoolSize;
        this.maxPoolSize = builder.maxPoolSize;
        this.connectionTimeoutMs = builder.connectionTimeoutMs;
        this.validationTimeoutMs = builder.validationTimeoutMs;
        this.maxIdleTimeMs = builder.maxIdleTimeMs;

        this.availableConnections = new ArrayBlockingQueue<>(maxPoolSize);
        this.activeConnections = new ConcurrentHashMap<>();
        this.totalConnections = new AtomicInteger(0);
        this.activeConnectionCount = new AtomicInteger(0);

        this.totalConnectionsCreated = new AtomicLong(0);
        this.totalConnectionsDestroyed = new AtomicLong(0);
        this.totalConnectionsRequested = new AtomicLong(0);
        this.totalConnectionTimeouts = new AtomicLong(0);
        this.totalValidationFailures = new AtomicLong(0);

        this.transactionContext = new ThreadLocal<>();
        this.maintenanceExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "ConnectionManager-Maintenance");
            t.setDaemon(true);
            return t;
        });
    }


    public synchronized void initialize() throws DataAccessException {
        if (initialized) {
            return;
        }

        try {

            try (Connection testConn = createPhysicalConnection()) {
                if (!isValidConnection(testConn)) {
                    throw new DataAccessException("Initial connection validation failed");
                }
            }

            for (int i = 0; i < minPoolSize; i++) {
                PooledConnection pooledConn = createPooledConnection();
                availableConnections.offer(pooledConn);
                totalConnections.incrementAndGet();
            }

            startMaintenanceTasks();

            initialized = true;

        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to initialize connection manager", e);
        }
    }


    public Connection getConnection() throws DataAccessException {
        if (shutdown) {
            throw new DataAccessException("Connection manager is shut down");
        }

        if (!initialized) {
            initialize();
        }

        totalConnectionsRequested.incrementAndGet();

        TransactionContext txContext = transactionContext.get();
        if (txContext != null && txContext.getConnection() != null) {
            return txContext.getConnection();
        }

        try {
            PooledConnection pooledConn = getPooledConnection();
            Connection connection = pooledConn.getConnection();

            activeConnections.put(connection, pooledConn);
            activeConnectionCount.incrementAndGet();

            return new ConnectionProxy(connection, this);

        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to get connection from pool", e);
        }
    }


    void returnConnection(Connection connection) {
        if (connection instanceof ConnectionProxy) {
            connection = ((ConnectionProxy) connection).getDelegate();
        }

        PooledConnection pooledConn = activeConnections.remove(connection);
        if (pooledConn != null) {
            activeConnectionCount.decrementAndGet();

            try {
                if (!connection.isClosed()) {
                    connection.setAutoCommit(true);
                    connection.clearWarnings();
                }

                if (isValidConnection(connection) &&
                        !pooledConn.isExpired(maxIdleTimeMs)) {
                    pooledConn.updateLastUsed();
                    availableConnections.offer(pooledConn);
                } else {
                    destroyPooledConnection(pooledConn);
                }

            } catch (SQLException e) {
                destroyPooledConnection(pooledConn);
            }
        }
    }


    public void beginTransaction() throws DataAccessException {
        TransactionContext txContext = transactionContext.get();
        if (txContext != null) {
            throw new DataAccessException("Transaction already active");
        }

        Connection connection = getConnection();
        try {
            connection.setAutoCommit(false);
            transactionContext.set(new TransactionContext(connection));
        } catch (SQLException e) {
            returnConnection(connection);
            throw DataAccessException.fromSQLException("Failed to begin transaction", e);
        }
    }


    public void commitTransaction() throws DataAccessException {
        TransactionContext txContext = transactionContext.get();
        if (txContext == null) {
            throw new DataAccessException("No active transaction");
        }

        try {
            txContext.getConnection().commit();
        } catch (SQLException e) {
            throw DataAccessException.fromSQLException("Failed to commit transaction", e);
        } finally {
            endTransaction();
        }
    }


    public void rollbackTransaction() {
        TransactionContext txContext = transactionContext.get();
        if (txContext == null) {
            return; // No active transaction
        }

        try {
            txContext.getConnection().rollback();
        } catch (SQLException e) {

        } finally {
            endTransaction();
        }
    }


    public boolean isInTransaction() {
        return transactionContext.get() != null;
    }

    private void endTransaction() {
        TransactionContext txContext = transactionContext.get();
        if (txContext != null) {
            returnConnection(txContext.getConnection());
            transactionContext.remove();
        }
    }


    private PooledConnection getPooledConnection() throws SQLException {

        PooledConnection pooledConn = availableConnections.poll();

        if (pooledConn != null) {

            if (isValidConnection(pooledConn.getConnection()) &&
                    !pooledConn.isExpired(maxIdleTimeMs)) {
                pooledConn.updateLastUsed();
                return pooledConn;
            } else {
                destroyPooledConnection(pooledConn);
            }
        }

        if (totalConnections.get() < maxPoolSize) {
            pooledConn = createPooledConnection();
            totalConnections.incrementAndGet();
            return pooledConn;
        }

        try {
            pooledConn = availableConnections.poll(connectionTimeoutMs, TimeUnit.MILLISECONDS);
            if (pooledConn == null) {
                totalConnectionTimeouts.incrementAndGet();
                throw new SQLException("Connection timeout after " + connectionTimeoutMs + "ms");
            }

            if (isValidConnection(pooledConn.getConnection()) &&
                    !pooledConn.isExpired(maxIdleTimeMs)) {
                pooledConn.updateLastUsed();
                return pooledConn;
            } else {
                destroyPooledConnection(pooledConn);

                return getPooledConnection();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for connection", e);
        }
    }


    private Connection createPhysicalConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl, username, password);

        if (connectionProperties.containsKey("connectTimeout")) {


        }

        totalConnectionsCreated.incrementAndGet();
        return connection;
    }


    private PooledConnection createPooledConnection() throws SQLException {
        Connection connection = createPhysicalConnection();
        return new PooledConnection(connection);
    }


    private void destroyPooledConnection(PooledConnection pooledConn) {
        try {
            pooledConn.getConnection().close();
        } catch (SQLException e) {

        } finally {
            totalConnections.decrementAndGet();
            totalConnectionsDestroyed.incrementAndGet();
        }
    }


    private boolean isValidConnection(Connection connection) {
        try {
            return connection != null &&
                    !connection.isClosed() &&
                    connection.isValid(validationTimeoutMs / 1000);
        } catch (SQLException e) {
            totalValidationFailures.incrementAndGet();
            return false;
        }
    }


    private void startMaintenanceTasks() {

        maintenanceExecutor.scheduleAtFixedRate(this::cleanupExpiredConnections,
                5, 5, TimeUnit.MINUTES);

        maintenanceExecutor.scheduleAtFixedRate(this::performHealthCheck,
                1, 1, TimeUnit.MINUTES);
    }


    private void cleanupExpiredConnections() {
        try {
            int cleaned = 0;
            PooledConnection[] connections = availableConnections.toArray(new PooledConnection[0]);

            for (PooledConnection pooledConn : connections) {
                if (pooledConn.isExpired(maxIdleTimeMs) ||
                        !isValidConnection(pooledConn.getConnection())) {
                    if (availableConnections.remove(pooledConn)) {
                        destroyPooledConnection(pooledConn);
                        cleaned++;
                    }
                }
            }

            while (totalConnections.get() < minPoolSize) {
                try {
                    PooledConnection newConn = createPooledConnection();
                    availableConnections.offer(newConn);
                    totalConnections.incrementAndGet();
                } catch (SQLException e) {
                    break; // Can't create more connections
                }
            }

        } catch (Exception e) {

        }
    }


    private void performHealthCheck() {

        int total = totalConnections.get();
        int active = activeConnectionCount.get();
        int available = availableConnections.size();

        if (active > total * 0.9) {

        }
    }


    public ConnectionPoolStatistics getStatistics() {
        return new ConnectionPoolStatistics(
                totalConnections.get(),
                activeConnectionCount.get(),
                availableConnections.size(),
                totalConnectionsCreated.get(),
                totalConnectionsDestroyed.get(),
                totalConnectionsRequested.get(),
                totalConnectionTimeouts.get(),
                totalValidationFailures.get()
        );
    }


    public boolean isHealthy() {
        return initialized &&
                !shutdown &&
                totalConnections.get() >= minPoolSize &&
                totalConnections.get() <= maxPoolSize;
    }

    @Override
    public void close() {
        if (shutdown) {
            return;
        }

        shutdown = true;

        maintenanceExecutor.shutdown();
        try {
            if (!maintenanceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                maintenanceExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            maintenanceExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        for (PooledConnection pooledConn : availableConnections) {
            destroyPooledConnection(pooledConn);
        }
        availableConnections.clear();

        for (PooledConnection pooledConn : activeConnections.values()) {
            destroyPooledConnection(pooledConn);
        }
        activeConnections.clear();

        totalConnections.set(0);
        activeConnectionCount.set(0);
    }

    public static Builder builder(String jdbcUrl, String username, String password) {
        return new Builder(jdbcUrl, username, password);
    }

    public static final class Builder {
        private final String jdbcUrl;
        private final String username;
        private final String password;
        private final Properties connectionProperties = new Properties();

        private int minPoolSize = 5;
        private int maxPoolSize = 20;
        private int connectionTimeoutMs = 30000; // 30 seconds
        private int validationTimeoutMs = 5000;  // 5 seconds
        private int maxIdleTimeMs = 600000;      // 10 minutes

        private Builder(String jdbcUrl, String username, String password) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
        }

        public Builder minPoolSize(int minPoolSize) {
            if (minPoolSize < 1) throw new IllegalArgumentException("Min pool size must be >= 1");
            this.minPoolSize = minPoolSize;
            return this;
        }

        public Builder maxPoolSize(int maxPoolSize) {
            if (maxPoolSize < 1) throw new IllegalArgumentException("Max pool size must be >= 1");
            this.maxPoolSize = maxPoolSize;
            return this;
        }

        public Builder connectionTimeout(int timeoutMs) {
            if (timeoutMs < 1000) throw new IllegalArgumentException("Connection timeout must be >= 1000ms");
            this.connectionTimeoutMs = timeoutMs;
            return this;
        }

        public Builder validationTimeout(int timeoutMs) {
            if (timeoutMs < 1000) throw new IllegalArgumentException("Validation timeout must be >= 1000ms");
            this.validationTimeoutMs = timeoutMs;
            return this;
        }

        public Builder maxIdleTime(int maxIdleMs) {
            if (maxIdleMs < 60000) throw new IllegalArgumentException("Max idle time must be >= 60000ms");
            this.maxIdleTimeMs = maxIdleMs;
            return this;
        }

        public Builder connectionProperty(String key, String value) {
            this.connectionProperties.setProperty(key, value);
            return this;
        }

        public ConnectionManager build() {
            if (minPoolSize > maxPoolSize) {
                throw new IllegalArgumentException("Min pool size cannot exceed max pool size");
            }
            return new ConnectionManager(this);
        }
    }

    private static final class PooledConnection {
        private final Connection connection;
        private volatile long lastUsed;
        private volatile long created;

        public PooledConnection(Connection connection) {
            this.connection = connection;
            this.created = System.currentTimeMillis();
            this.lastUsed = created;
        }

        public Connection getConnection() {
            return connection;
        }

        public long getLastUsed() {
            return lastUsed;
        }

        public long getCreated() {
            return created;
        }

        public void updateLastUsed() {
            this.lastUsed = System.currentTimeMillis();
        }

        public boolean isExpired(int maxIdleTimeMs) {
            return System.currentTimeMillis() - lastUsed > maxIdleTimeMs;
        }
    }

    private static final class TransactionContext {
        private final Connection connection;
        private final LocalDateTime startTime;

        public TransactionContext(Connection connection) {
            this.connection = connection;
            this.startTime = LocalDateTime.now();
        }

        public Connection getConnection() {
            return connection;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }
    }

    public static final class ConnectionPoolStatistics {
        private final int totalConnections;
        private final int activeConnections;
        private final int availableConnections;
        private final long connectionsCreated;
        private final long connectionsDestroyed;
        private final long connectionsRequested;
        private final long connectionTimeouts;
        private final long validationFailures;

        public ConnectionPoolStatistics(int totalConnections, int activeConnections,
                                        int availableConnections, long connectionsCreated,
                                        long connectionsDestroyed, long connectionsRequested,
                                        long connectionTimeouts, long validationFailures) {
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.availableConnections = availableConnections;
            this.connectionsCreated = connectionsCreated;
            this.connectionsDestroyed = connectionsDestroyed;
            this.connectionsRequested = connectionsRequested;
            this.connectionTimeouts = connectionTimeouts;
            this.validationFailures = validationFailures;
        }

        public int getTotalConnections() {
            return totalConnections;
        }

        public int getActiveConnections() {
            return activeConnections;
        }

        public int getAvailableConnections() {
            return availableConnections;
        }

        public long getConnectionsCreated() {
            return connectionsCreated;
        }

        public long getConnectionsDestroyed() {
            return connectionsDestroyed;
        }

        public long getConnectionsRequested() {
            return connectionsRequested;
        }

        public long getConnectionTimeouts() {
            return connectionTimeouts;
        }

        public long getValidationFailures() {
            return validationFailures;
        }

        public double getConnectionUtilization() {
            return totalConnections == 0 ? 0.0 : (double) activeConnections / totalConnections * 100.0;
        }

        public double getTimeoutRate() {
            return connectionsRequested == 0 ? 0.0 : (double) connectionTimeouts / connectionsRequested * 100.0;
        }

        @Override
        public String toString() {
            return "ConnectionPoolStatistics{" +
                    "total=" + totalConnections +
                    ", active=" + activeConnections +
                    ", available=" + availableConnections +
                    ", utilization=" + String.format("%.1f%%", getConnectionUtilization()) +
                    ", timeouts=" + connectionTimeouts +
                    ", validationFailures=" + validationFailures +
                    '}';
        }
    }
}