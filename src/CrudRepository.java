
import java.util.List;
import java.util.Optional;


public interface CrudRepository<T, ID> {


    T save(T entity);


    Optional<T> findById(ID id);


    boolean existsById(ID id);


    List<T> findAll();


    List<T> findAll(int offset, int limit);


    long count();


    boolean deleteById(ID id);


    boolean delete(T entity);


    int deleteAll();


    int saveAll(List<T> entities);


    boolean isHealthy();


    RepositoryStatistics getStatistics();


    default String getTableName() {
        return getClass().getSimpleName().replace("Repository", "").toUpperCase();
    }


    final class RepositoryStatistics {
        private final long totalQueries;
        private final long averageQueryTime;
        private final long totalInserts;
        private final long totalUpdates;
        private final long totalDeletes;
        private final long totalSelects;
        private final long connectionErrors;

        public RepositoryStatistics(long totalQueries, long averageQueryTime,
                                    long totalInserts, long totalUpdates,
                                    long totalDeletes, long totalSelects,
                                    long connectionErrors) {
            this.totalQueries = totalQueries;
            this.averageQueryTime = averageQueryTime;
            this.totalInserts = totalInserts;
            this.totalUpdates = totalUpdates;
            this.totalDeletes = totalDeletes;
            this.totalSelects = totalSelects;
            this.connectionErrors = connectionErrors;
        }

        public long getTotalQueries() {
            return totalQueries;
        }

        public long getAverageQueryTime() {
            return averageQueryTime;
        }

        public long getTotalInserts() {
            return totalInserts;
        }

        public long getTotalUpdates() {
            return totalUpdates;
        }

        public long getTotalDeletes() {
            return totalDeletes;
        }

        public long getTotalSelects() {
            return totalSelects;
        }

        public long getConnectionErrors() {
            return connectionErrors;
        }

        public double getAverageQueryTimeMs() {
            return averageQueryTime / 1_000_000.0;
        }

        public double getErrorRate() {
            return totalQueries == 0 ? 0.0 : (double) connectionErrors / totalQueries * 100.0;
        }

        @Override
        public String toString() {
            return "CrudRepositoryStatistics{" +
                    "queries=" + totalQueries +
                    ", avgTime=" + String.format("%.2fms", getAverageQueryTimeMs()) +
                    ", inserts=" + totalInserts +
                    ", updates=" + totalUpdates +
                    ", deletes=" + totalDeletes +
                    ", selects=" + totalSelects +
                    ", errors=" + connectionErrors +
                    ", errorRate=" + String.format("%.2f%%", getErrorRate()) +
                    '}';
        }
    }
}