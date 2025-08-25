
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransactionRollbackException;


public class DataAccessException extends RuntimeException {

    private final String sqlState;
    private final int sqlErrorCode;
    private final int vendorErrorCode;
    private final ErrorCategory category;

    public DataAccessException(String message, SQLException cause) {
        super(message, cause);
        this.sqlState = cause.getSQLState();
        this.sqlErrorCode = cause.getErrorCode();
        this.vendorErrorCode = cause.getErrorCode();
        this.category = categorizeError(cause);
    }

    public DataAccessException(String message) {
        super(message);
        this.sqlState = null;
        this.sqlErrorCode = 0;
        this.vendorErrorCode = 0;
        this.category = ErrorCategory.UNKNOWN;
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
        this.sqlState = null;
        this.sqlErrorCode = 0;
        this.vendorErrorCode = 0;
        this.category = ErrorCategory.UNKNOWN;
    }

    public DataAccessException(String message, SQLException cause, ErrorCategory category) {
        super(message, cause);
        this.sqlState = cause.getSQLState();
        this.sqlErrorCode = cause.getErrorCode();
        this.vendorErrorCode = cause.getErrorCode();
        this.category = category;
    }

    public static DataAccessException duplicateKey(String message, SQLException cause) {
        return new DataAccessException("Duplicate key constraint violation: " + message, cause,
                ErrorCategory.INTEGRITY_CONSTRAINT);
    }

    public static DataAccessException foreignKeyViolation(String message, SQLException cause) {
        return new DataAccessException("Foreign key constraint violation: " + message, cause,
                ErrorCategory.INTEGRITY_CONSTRAINT);
    }

    public static DataAccessException connectionTimeout(String message, SQLException cause) {
        return new DataAccessException("Database connection timeout: " + message, cause,
                ErrorCategory.CONNECTION_FAILURE);
    }

    public static DataAccessException queryTimeout(String message, SQLException cause) {
        return new DataAccessException("Query execution timeout: " + message, cause,
                ErrorCategory.TIMEOUT);
    }

    public static DataAccessException transactionRollback(String message, SQLException cause) {
        return new DataAccessException("Transaction was rolled back: " + message, cause,
                ErrorCategory.TRANSACTION_ROLLBACK);
    }

    public static DataAccessException dataIntegrityViolation(String message, SQLException cause) {
        return new DataAccessException("Data integrity violation: " + message, cause,
                ErrorCategory.DATA_INTEGRITY);
    }

    public static DataAccessException permissionDenied(String message, SQLException cause) {
        return new DataAccessException("Database permission denied: " + message, cause,
                ErrorCategory.PERMISSION_DENIED);
    }

    public static DataAccessException dataConversionError(String message, SQLException cause) {
        return new DataAccessException("Data conversion error: " + message, cause,
                ErrorCategory.DATA_CONVERSION);
    }

    public static DataAccessException deadlock(String message, SQLException cause) {
        return new DataAccessException("Database deadlock detected: " + message, cause,
                ErrorCategory.DEADLOCK);
    }

    public static DataAccessException resourceExhaustion(String message, SQLException cause) {
        return new DataAccessException("Database resource exhaustion: " + message, cause,
                ErrorCategory.RESOURCE_EXHAUSTION);
    }

    public static DataAccessException fromSQLException(SQLException sqlException) {
        return fromSQLException("Database operation failed", sqlException);
    }

    public static DataAccessException fromSQLException(String message, SQLException sqlException) {

        if (sqlException instanceof SQLIntegrityConstraintViolationException) {
            return new DataAccessException(message + ": " + sqlException.getMessage(),
                    sqlException, ErrorCategory.INTEGRITY_CONSTRAINT);
        }

        if (sqlException instanceof SQLTimeoutException) {
            return new DataAccessException(message + ": " + sqlException.getMessage(),
                    sqlException, ErrorCategory.TIMEOUT);
        }

        if (sqlException instanceof SQLTransactionRollbackException) {
            return new DataAccessException(message + ": " + sqlException.getMessage(),
                    sqlException, ErrorCategory.TRANSACTION_ROLLBACK);
        }

        String sqlState = sqlException.getSQLState();
        if (sqlState != null) {
            if (sqlState.startsWith("23")) { // Integrity constraint violations
                return dataIntegrityViolation(message, sqlException);
            }
            if (sqlState.startsWith("08")) { // Connection exceptions
                return connectionTimeout(message, sqlException);
            }
            if (sqlState.startsWith("40")) { // Transaction rollback
                return transactionRollback(message, sqlException);
            }
            if (sqlState.startsWith("42")) { // Syntax error or access rule violation
                return permissionDenied(message, sqlException);
            }
            if (sqlState.startsWith("22")) { // Data exception
                return dataConversionError(message, sqlException);
            }
        }

        int errorCode = sqlException.getErrorCode();

        if (errorCode == 1062) { // Duplicate entry
            return duplicateKey(message, sqlException);
        }
        if (errorCode == 1452) { // Foreign key constraint fails
            return foreignKeyViolation(message, sqlException);
        }
        if (errorCode == 1213) { // Deadlock
            return deadlock(message, sqlException);
        }

        if (errorCode == 0 && sqlState != null) {
            switch (sqlState) {
                case "23505": // unique_violation
                    return duplicateKey(message, sqlException);
                case "23503": // foreign_key_violation
                    return foreignKeyViolation(message, sqlException);
                case "40P01": // deadlock_detected
                    return deadlock(message, sqlException);
            }
        }

        return new DataAccessException(message + ": " + sqlException.getMessage(), sqlException);
    }

    private ErrorCategory categorizeError(SQLException sqlException) {

        if (sqlException instanceof SQLIntegrityConstraintViolationException) {
            return ErrorCategory.INTEGRITY_CONSTRAINT;
        }
        if (sqlException instanceof SQLTimeoutException) {
            return ErrorCategory.TIMEOUT;
        }
        if (sqlException instanceof SQLTransactionRollbackException) {
            return ErrorCategory.TRANSACTION_ROLLBACK;
        }

        String sqlState = sqlException.getSQLState();
        if (sqlState != null) {
            if (sqlState.startsWith("23")) return ErrorCategory.INTEGRITY_CONSTRAINT;
            if (sqlState.startsWith("08")) return ErrorCategory.CONNECTION_FAILURE;
            if (sqlState.startsWith("40")) return ErrorCategory.TRANSACTION_ROLLBACK;
            if (sqlState.startsWith("42")) return ErrorCategory.PERMISSION_DENIED;
            if (sqlState.startsWith("22")) return ErrorCategory.DATA_CONVERSION;
        }

        return ErrorCategory.UNKNOWN;
    }

    public String getSqlState() {
        return sqlState;
    }

    public int getSqlErrorCode() {
        return sqlErrorCode;
    }

    public int getVendorErrorCode() {
        return vendorErrorCode;
    }

    public ErrorCategory getCategory() {
        return category;
    }

    public boolean isIntegrityConstraintViolation() {
        return category == ErrorCategory.INTEGRITY_CONSTRAINT;
    }

    public boolean isConnectionFailure() {
        return category == ErrorCategory.CONNECTION_FAILURE;
    }

    public boolean isTimeout() {
        return category == ErrorCategory.TIMEOUT;
    }

    public boolean isTransactionRollback() {
        return category == ErrorCategory.TRANSACTION_ROLLBACK;
    }

    public boolean isRetryable() {
        return category == ErrorCategory.TIMEOUT ||
                category == ErrorCategory.DEADLOCK ||
                category == ErrorCategory.CONNECTION_FAILURE;
    }

    public boolean isUserError() {
        return category == ErrorCategory.DATA_CONVERSION ||
                category == ErrorCategory.PERMISSION_DENIED;
    }

    public String getBusinessMessage() {
        switch (category) {
            case INTEGRITY_CONSTRAINT:
                return "Data constraint violation - this operation conflicts with existing data";
            case CONNECTION_FAILURE:
                return "Database connection failed - please try again";
            case TIMEOUT:
                return "Operation timed out - please try again or contact support";
            case TRANSACTION_ROLLBACK:
                return "Transaction was cancelled due to conflict - please retry";
            case DATA_INTEGRITY:
                return "Data validation failed - please check your input";
            case PERMISSION_DENIED:
                return "Access denied - insufficient privileges for this operation";
            case DATA_CONVERSION:
                return "Data format error - please check your input values";
            case DEADLOCK:
                return "System conflict detected - please try again";
            case RESOURCE_EXHAUSTION:
                return "System resources temporarily unavailable - please try later";
            default:
                return "Database operation failed - please contact support";
        }
    }

    public enum ErrorCategory {
        INTEGRITY_CONSTRAINT("Constraint violation"),
        CONNECTION_FAILURE("Connection issue"),
        TIMEOUT("Operation timeout"),
        TRANSACTION_ROLLBACK("Transaction rollback"),
        DATA_INTEGRITY("Data integrity issue"),
        PERMISSION_DENIED("Access denied"),
        DATA_CONVERSION("Data conversion error"),
        DEADLOCK("Deadlock detected"),
        RESOURCE_EXHAUSTION("Resource exhaustion"),
        UNKNOWN("Unknown error");

        private final String description;

        ErrorCategory(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DataAccessException{");
        sb.append("category=").append(category);
        if (sqlState != null) {
            sb.append(", sqlState='").append(sqlState).append('\'');
        }
        if (sqlErrorCode != 0) {
            sb.append(", sqlErrorCode=").append(sqlErrorCode);
        }
        sb.append(", message='").append(getMessage()).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public String getDetailedErrorReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DataAccessException Details ===\n");
        sb.append("Category: ").append(category.name()).append(" (").append(category.getDescription()).append(")\n");
        sb.append("Message: ").append(getMessage()).append("\n");
        sb.append("Business Message: ").append(getBusinessMessage()).append("\n");

        if (sqlState != null) {
            sb.append("SQL State: ").append(sqlState).append("\n");
        }
        if (sqlErrorCode != 0) {
            sb.append("SQL Error Code: ").append(sqlErrorCode).append("\n");
        }
        if (vendorErrorCode != 0) {
            sb.append("Vendor Error Code: ").append(vendorErrorCode).append("\n");
        }

        sb.append("Retryable: ").append(isRetryable()).append("\n");
        sb.append("User Error: ").append(isUserError()).append("\n");

        if (getCause() != null) {
            sb.append("Root Cause: ").append(getCause().getClass().getSimpleName())
                    .append(": ").append(getCause().getMessage()).append("\n");
        }

        return sb.toString();
    }
}