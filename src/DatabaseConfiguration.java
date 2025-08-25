
import java.io.*;
import java.time.Duration;
import java.util.*;


public final class DatabaseConfiguration {

    private static final String DEFAULT_CONFIG_FILE = "database.properties";
    private static final String ENV_PREFIX = "DB_";

    private final Properties properties;
    private final Environment environment;
    private final String configSource;
    private final long loadTime;

    private final Map<String, Object> cache = new HashMap<>();

    private DatabaseConfiguration(Properties properties, Environment environment, String configSource) {
        this.properties = new Properties(properties);
        this.environment = environment;
        this.configSource = configSource;
        this.loadTime = System.currentTimeMillis();
        validateConfiguration();
    }


    public static DatabaseConfiguration loadFromFile() {
        return loadFromFile(DEFAULT_CONFIG_FILE);
    }


    public static DatabaseConfiguration loadFromFile(String filename) {
        try (InputStream is = DatabaseConfiguration.class.getClassLoader().getResourceAsStream(filename)) {
            if (is == null) {

                return createDefaultConfiguration();
            }

            Properties props = new Properties();
            props.load(is);

            Environment env = determineEnvironment(props);
            return new DatabaseConfiguration(props, env, "file:" + filename);

        } catch (IOException e) {
            throw new IllegalStateException("Failed to load database configuration from " + filename, e);
        }
    }


    public static DatabaseConfiguration loadFromEnvironment() {
        Properties props = new Properties();

        System.getProperties().forEach((key, value) -> {
            if (key.toString().startsWith("db.")) {
                props.setProperty(key.toString(), value.toString());
            }
        });

        System.getenv().forEach((key, value) -> {
            if (key.startsWith(ENV_PREFIX)) {
                String propKey = "db." + key.substring(ENV_PREFIX.length()).toLowerCase().replace('_', '.');
                props.setProperty(propKey, value);
            }
        });

        Environment env = determineEnvironment(props);
        if (props.isEmpty()) {
            return createDefaultConfiguration();
        }

        return new DatabaseConfiguration(props, env, "environment");
    }


    public static Builder builder() {
        return new Builder();
    }


    public static DatabaseConfiguration createTestConfiguration() {
        return builder()
                .environment(Environment.TEST)
                .jdbcUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .poolMinSize(2)
                .poolMaxSize(10)
                .build();
    }


    private static DatabaseConfiguration createDefaultConfiguration() {
        return builder()
                .environment(Environment.DEVELOPMENT)
                .jdbcUrl("jdbc:h2:mem:banking;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .build();
    }

    private static Environment determineEnvironment(Properties props) {
        String envValue = props.getProperty("db.environment",
                System.getProperty("db.environment",
                        System.getenv("DB_ENVIRONMENT")));

        if (envValue != null) {
            try {
                return Environment.valueOf(envValue.toUpperCase());
            } catch (IllegalArgumentException e) {

            }
        }

        return Environment.DEVELOPMENT;
    }


    public Environment getEnvironment() {
        return environment;
    }

    public String getConfigSource() {
        return configSource;
    }

    public long getLoadTime() {
        return loadTime;
    }

    public String getJdbcUrl() {
        return getStringProperty("db.url", "jdbc.url", "jdbcUrl");
    }

    public String getUsername() {
        return getStringProperty("db.username", "db.user", "username");
    }

    public String getPassword() {
        return getStringProperty("db.password", "password");
    }

    public String getDriverClassName() {
        return getStringProperty("db.driver", "db.driver.class", "driverClassName");
    }

    public int getPoolMinSize() {
        return getIntProperty("db.pool.min.size", 5);
    }

    public int getPoolMaxSize() {
        return getIntProperty("db.pool.max.size", 20);
    }

    public Duration getConnectionTimeout() {
        return Duration.ofMillis(getLongProperty("db.connection.timeout.ms", 30000L));
    }

    public Duration getValidationTimeout() {
        return Duration.ofMillis(getLongProperty("db.validation.timeout.ms", 5000L));
    }

    public Duration getMaxIdleTime() {
        return Duration.ofMillis(getLongProperty("db.max.idle.time.ms", 600000L)); // 10 minutes
    }

    public Duration getLeakDetectionThreshold() {
        return Duration.ofMillis(getLongProperty("db.leak.detection.threshold.ms", 60000L)); // 1 minute
    }

    public Duration getDefaultTransactionTimeout() {
        return Duration.ofSeconds(getLongProperty("db.transaction.timeout.seconds", 30L));
    }

    public String getDefaultTransactionIsolation() {
        return getStringProperty("db.transaction.isolation", "READ_COMMITTED");
    }

    public boolean isAutoCommit() {
        return getBooleanProperty("db.auto.commit", false);
    }

    public boolean isSchemaValidationEnabled() {
        return getBooleanProperty("db.schema.validation.enabled", true);
    }

    public boolean isSchemaCreationEnabled() {
        return getBooleanProperty("db.schema.creation.enabled", environment != Environment.PRODUCTION);
    }

    public String getSchemaName() {
        return getStringProperty("db.schema.name", "banking");
    }

    public boolean isHealthCheckEnabled() {
        return getBooleanProperty("db.health.check.enabled", true);
    }

    public Duration getHealthCheckInterval() {
        return Duration.ofSeconds(getLongProperty("db.health.check.interval.seconds", 60L));
    }

    public boolean isMetricsEnabled() {
        return getBooleanProperty("db.metrics.enabled", true);
    }

    public boolean isSqlLoggingEnabled() {
        return getBooleanProperty("db.sql.logging.enabled", environment == Environment.DEVELOPMENT);
    }

    public boolean isSlowQueryLoggingEnabled() {
        return getBooleanProperty("db.slow.query.logging.enabled", true);
    }

    public Duration getSlowQueryThreshold() {
        return Duration.ofMillis(getLongProperty("db.slow.query.threshold.ms", 1000L));
    }

    public boolean isSslEnabled() {
        return getBooleanProperty("db.ssl.enabled", environment == Environment.PRODUCTION);
    }

    public String getSslMode() {
        return getStringProperty("db.ssl.mode", "require");
    }

    public boolean isCredentialEncryptionEnabled() {
        return getBooleanProperty("db.credential.encryption.enabled", environment == Environment.PRODUCTION);
    }


    private String getStringProperty(String... keys) {
        return getStringProperty(null, keys);
    }

    private String getStringProperty(String defaultValue, String... keys) {
        for (String key : keys) {
            String value = properties.getProperty(key);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return defaultValue;
    }

    private int getIntProperty(String key, int defaultValue) {
        return getCachedProperty(key, Integer.class, () -> {
            String value = properties.getProperty(key);
            return value != null ? Integer.parseInt(value.trim()) : defaultValue;
        });
    }

    private long getLongProperty(String key, long defaultValue) {
        return getCachedProperty(key, Long.class, () -> {
            String value = properties.getProperty(key);
            return value != null ? Long.parseLong(value.trim()) : defaultValue;
        });
    }

    private boolean getBooleanProperty(String key, boolean defaultValue) {
        return getCachedProperty(key, Boolean.class, () -> {
            String value = properties.getProperty(key);
            return value != null ? Boolean.parseBoolean(value.trim()) : defaultValue;
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T getCachedProperty(String key, Class<T> type, PropertyLoader<T> loader) {
        return (T) cache.computeIfAbsent(key, k -> {
            try {
                return loader.load();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load property: " + key, e);
            }
        });
    }

    @FunctionalInterface
    private interface PropertyLoader<T> {
        T load() throws Exception;
    }


    private void validateConfiguration() {
        List<String> errors = new ArrayList<>();

        if (getJdbcUrl() == null || getJdbcUrl().isEmpty()) {
            errors.add("JDBC URL is required");
        }

        if (getUsername() == null) {
            errors.add("Database username is required");
        }

        if (getPoolMinSize() < 1) {
            errors.add("Pool minimum size must be at least 1");
        }

        if (getPoolMaxSize() < getPoolMinSize()) {
            errors.add("Pool maximum size must be greater than or equal to minimum size");
        }

        if (getConnectionTimeout().isNegative() || getConnectionTimeout().isZero()) {
            errors.add("Connection timeout must be positive");
        }

        if (getValidationTimeout().isNegative() || getValidationTimeout().isZero()) {
            errors.add("Validation timeout must be positive");
        }

        if (environment == Environment.PRODUCTION) {
            if (getPassword() == null || getPassword().isEmpty()) {
                errors.add("Password is required in production environment");
            }

            if (!isSslEnabled()) {
                errors.add("SSL should be enabled in production environment");
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid database configuration: " + String.join(", ", errors));
        }
    }


    public DatabaseConfiguration reload() {
        if (configSource.startsWith("file:")) {
            String filename = configSource.substring(5);
            return loadFromFile(filename);
        } else if ("environment".equals(configSource)) {
            return loadFromEnvironment();
        } else {
            throw new UnsupportedOperationException("Cannot reload configuration from source: " + configSource);
        }
    }


    public Properties exportSafeProperties() {
        Properties safeProps = new Properties();

        properties.forEach((key, value) -> {
            String keyStr = key.toString();
            if (!isSensitiveProperty(keyStr)) {
                safeProps.setProperty(keyStr, value.toString());
            } else {
                safeProps.setProperty(keyStr, "***");
            }
        });

        return safeProps;
    }

    private boolean isSensitiveProperty(String key) {
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("password") ||
                lowerKey.contains("secret") ||
                lowerKey.contains("key") ||
                lowerKey.contains("token");
    }


    public ConnectionManager createConnectionManager() {
        ConnectionManager.Builder builder = ConnectionManager.builder(getJdbcUrl(), getUsername(), getPassword())
                .minPoolSize(getPoolMinSize())
                .maxPoolSize(getPoolMaxSize())
                .connectionTimeout((int) getConnectionTimeout().toMillis())
                .validationTimeout((int) getValidationTimeout().toMillis())
                .maxIdleTime((int) getMaxIdleTime().toMillis());

        if (isSslEnabled()) {
            builder.connectionProperty("ssl", "true")
                    .connectionProperty("sslmode", getSslMode());
        }

        return builder.build();
    }


    public static final class Builder {
        private final Properties properties = new Properties();
        private Environment environment = Environment.DEVELOPMENT;

        public Builder environment(Environment environment) {
            this.environment = environment;
            return this;
        }

        public Builder jdbcUrl(String url) {
            properties.setProperty("db.url", url);
            return this;
        }

        public Builder username(String username) {
            properties.setProperty("db.username", username);
            return this;
        }

        public Builder password(String password) {
            properties.setProperty("db.password", password);
            return this;
        }

        public Builder driverClassName(String driverClassName) {
            properties.setProperty("db.driver", driverClassName);
            return this;
        }

        public Builder poolMinSize(int minSize) {
            properties.setProperty("db.pool.min.size", String.valueOf(minSize));
            return this;
        }

        public Builder poolMaxSize(int maxSize) {
            properties.setProperty("db.pool.max.size", String.valueOf(maxSize));
            return this;
        }

        public Builder connectionTimeout(Duration timeout) {
            properties.setProperty("db.connection.timeout.ms", String.valueOf(timeout.toMillis()));
            return this;
        }

        public Builder validationTimeout(Duration timeout) {
            properties.setProperty("db.validation.timeout.ms", String.valueOf(timeout.toMillis()));
            return this;
        }

        public Builder maxIdleTime(Duration maxIdle) {
            properties.setProperty("db.max.idle.time.ms", String.valueOf(maxIdle.toMillis()));
            return this;
        }

        public Builder enableSsl(boolean enabled) {
            properties.setProperty("db.ssl.enabled", String.valueOf(enabled));
            return this;
        }

        public Builder sslMode(String mode) {
            properties.setProperty("db.ssl.mode", mode);
            return this;
        }

        public Builder enableSqlLogging(boolean enabled) {
            properties.setProperty("db.sql.logging.enabled", String.valueOf(enabled));
            return this;
        }

        public Builder enableMetrics(boolean enabled) {
            properties.setProperty("db.metrics.enabled", String.valueOf(enabled));
            return this;
        }

        public Builder schemaName(String schemaName) {
            properties.setProperty("db.schema.name", schemaName);
            return this;
        }

        public Builder property(String key, String value) {
            properties.setProperty(key, value);
            return this;
        }

        public DatabaseConfiguration build() {

            properties.setProperty("db.environment", environment.name());

            return new DatabaseConfiguration(properties, environment, "builder");
        }
    }


    public enum Environment {
        DEVELOPMENT("dev", "Development environment"),
        TEST("test", "Testing environment"),
        STAGING("staging", "Staging environment"),
        PRODUCTION("prod", "Production environment");

        private final String shortName;
        private final String description;

        Environment(String shortName, String description) {
            this.shortName = shortName;
            this.description = description;
        }

        public String getShortName() {
            return shortName;
        }

        public String getDescription() {
            return description;
        }

        public boolean isProduction() {
            return this == PRODUCTION;
        }

        public boolean isDevelopment() {
            return this == DEVELOPMENT;
        }

        public boolean isTest() {
            return this == TEST;
        }
    }

    @Override
    public String toString() {
        return "DatabaseConfiguration{" +
                "environment=" + environment +
                ", configSource='" + configSource + '\'' +
                ", jdbcUrl='" + maskUrl(getJdbcUrl()) + '\'' +
                ", poolSize=" + getPoolMinSize() + "-" + getPoolMaxSize() +
                ", loadTime=" + new Date(loadTime) +
                '}';
    }

    private String maskUrl(String url) {
        if (url == null) return null;

        int atIndex = url.lastIndexOf('@');
        if (atIndex > 0) {
            int protocolIndex = url.indexOf("://");
            if (protocolIndex > 0 && protocolIndex < atIndex) {
                return url.substring(0, protocolIndex + 3) + "***@" + url.substring(atIndex + 1);
            }
        }
        return url;
    }


    public String getConfigurationSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Database Configuration Summary ===\n");
        sb.append("Environment: ").append(environment.getDescription()).append("\n");
        sb.append("Configuration Source: ").append(configSource).append("\n");
        sb.append("JDBC URL: ").append(maskUrl(getJdbcUrl())).append("\n");
        sb.append("Connection Pool: ").append(getPoolMinSize()).append("-").append(getPoolMaxSize()).append("\n");
        sb.append("Connection Timeout: ").append(getConnectionTimeout()).append("\n");
        sb.append("Schema Management: ").append(isSchemaCreationEnabled() ? "Enabled" : "Disabled").append("\n");
        sb.append("SSL: ").append(isSslEnabled() ? "Enabled" : "Disabled").append("\n");
        sb.append("SQL Logging: ").append(isSqlLoggingEnabled() ? "Enabled" : "Disabled").append("\n");
        sb.append("Metrics: ").append(isMetricsEnabled() ? "Enabled" : "Disabled").append("\n");
        sb.append("Health Check: ").append(isHealthCheckEnabled() ? "Enabled" : "Disabled").append("\n");
        sb.append("Load Time: ").append(new Date(loadTime)).append("\n");
        return sb.toString();
    }
}