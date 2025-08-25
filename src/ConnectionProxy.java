

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;


final class ConnectionProxy implements Connection {

    private final Connection delegate;
    private final ConnectionManager connectionManager;
    private volatile boolean closed = false;

    ConnectionProxy(Connection delegate, ConnectionManager connectionManager) {
        this.delegate = delegate;
        this.connectionManager = connectionManager;
    }


    @Override
    public void close() throws SQLException {
        if (closed) {
            return;
        }

        closed = true;
        connectionManager.returnConnection(this);
    }


    public boolean isClosed() throws SQLException {
        return closed || delegate.isClosed();
    }


    Connection getDelegate() {
        return delegate;
    }


    private void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("Connection is closed");
        }
    }


    @Override
    public Statement createStatement() throws SQLException {
        checkClosed();
        return delegate.createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        checkClosed();
        return delegate.prepareStatement(sql);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        checkClosed();
        return delegate.prepareCall(sql);
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        checkClosed();
        return delegate.nativeSQL(sql);
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        checkClosed();
        delegate.setAutoCommit(autoCommit);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        checkClosed();
        return delegate.getAutoCommit();
    }

    @Override
    public void commit() throws SQLException {
        checkClosed();
        delegate.commit();
    }

    @Override
    public void rollback() throws SQLException {
        checkClosed();
        delegate.rollback();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        checkClosed();
        return delegate.getMetaData();
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        checkClosed();
        delegate.setReadOnly(readOnly);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        checkClosed();
        return delegate.isReadOnly();
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        checkClosed();
        delegate.setCatalog(catalog);
    }

    @Override
    public String getCatalog() throws SQLException {
        checkClosed();
        return delegate.getCatalog();
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        checkClosed();
        delegate.setTransactionIsolation(level);
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        checkClosed();
        return delegate.getTransactionIsolation();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkClosed();
        return delegate.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        checkClosed();
        delegate.clearWarnings();
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        checkClosed();
        return delegate.createStatement(resultSetType, resultSetConcurrency);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        checkClosed();
        return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        checkClosed();
        return delegate.prepareCall(sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        checkClosed();
        return delegate.getTypeMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        checkClosed();
        delegate.setTypeMap(map);
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        checkClosed();
        delegate.setHoldability(holdability);
    }

    @Override
    public int getHoldability() throws SQLException {
        checkClosed();
        return delegate.getHoldability();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        checkClosed();
        return delegate.setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        checkClosed();
        return delegate.setSavepoint(name);
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        checkClosed();
        delegate.rollback(savepoint);
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        checkClosed();
        delegate.releaseSavepoint(savepoint);
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        checkClosed();
        return delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        checkClosed();
        return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        checkClosed();
        return delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        checkClosed();
        return delegate.prepareStatement(sql, autoGeneratedKeys);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        checkClosed();
        return delegate.prepareStatement(sql, columnIndexes);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        checkClosed();
        return delegate.prepareStatement(sql, columnNames);
    }

    @Override
    public Clob createClob() throws SQLException {
        checkClosed();
        return delegate.createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        checkClosed();
        return delegate.createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        checkClosed();
        return delegate.createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        checkClosed();
        return delegate.createSQLXML();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        checkClosed();
        return delegate.isValid(timeout);
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        try {
            checkClosed();
            delegate.setClientInfo(name, value);
        } catch (SQLException e) {
            throw new SQLClientInfoException(e.getMessage(), null);
        }
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        try {
            checkClosed();
            delegate.setClientInfo(properties);
        } catch (SQLException e) {
            throw new SQLClientInfoException(e.getMessage(), null);
        }
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        checkClosed();
        return delegate.getClientInfo(name);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        checkClosed();
        return delegate.getClientInfo();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        checkClosed();
        return delegate.createArrayOf(typeName, elements);
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        checkClosed();
        return delegate.createStruct(typeName, attributes);
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        checkClosed();
        delegate.setSchema(schema);
    }

    @Override
    public String getSchema() throws SQLException {
        checkClosed();
        return delegate.getSchema();
    }

    @Override
    public void abort(Executor executor) throws SQLException {

        delegate.abort(executor);
        closed = true;
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        checkClosed();
        delegate.setNetworkTimeout(executor, milliseconds);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        checkClosed();
        return delegate.getNetworkTimeout();
    }


    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }

    @Override
    public String toString() {
        return "ConnectionProxy{" +
                "delegate=" + delegate.getClass().getSimpleName() +
                ", closed=" + closed +
                ", hashCode=" + System.identityHashCode(this) +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        ConnectionProxy that = (ConnectionProxy) obj;
        return delegate.equals(that.delegate);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}