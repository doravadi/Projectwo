// Repository.java - Generic repository interface with bounded type parameters
import java.util.List;
import java.util.Optional;

/**
 * Generic repository interface for CRUD operations
 * @param <T> Entity type - must be serializable for potential caching
 * @param <K> Key type - must be comparable for ordered storage
 */
public interface Repository<T extends java.io.Serializable, K extends Comparable<K>> {

    /**
     * Find entity by primary key
     * @param id Primary key
     * @return Entity if found, empty otherwise
     * @throws InfrastructureException If underlying storage fails
     */
    Optional<T> findById(K id) throws InfrastructureException;

    /**
     * Find all entities
     * @return List of all entities (may be empty)
     * @throws InfrastructureException If underlying storage fails
     */
    List<T> findAll() throws InfrastructureException;

    /**
     * Save new entity or update existing
     * @param entity Entity to save
     * @return Saved entity (may have generated fields)
     * @throws DomainException If business rules violated
     * @throws InfrastructureException If storage fails
     */
    T save(T entity) throws DomainException, InfrastructureException;

    /**
     * Save multiple entities in single transaction
     * @param entities Entities to save
     * @return List of saved entities
     * @throws DomainException If any business rule violated
     * @throws InfrastructureException If storage fails
     */
    List<T> saveAll(List<T> entities) throws DomainException, InfrastructureException;

    /**
     * Delete entity by primary key
     * @param id Primary key
     * @return true if entity was deleted, false if not found
     * @throws InfrastructureException If storage fails
     */
    boolean deleteById(K id) throws InfrastructureException;

    /**
     * Delete entity
     * @param entity Entity to delete
     * @return true if entity was deleted, false if not found
     * @throws InfrastructureException If storage fails
     */
    boolean delete(T entity) throws InfrastructureException;

    /**
     * Check if entity exists
     * @param id Primary key
     * @return true if exists, false otherwise
     * @throws InfrastructureException If storage fails
     */
    boolean existsById(K id) throws InfrastructureException;

    /**
     * Count all entities
     * @return Total number of entities
     * @throws InfrastructureException If storage fails
     */
    long count() throws InfrastructureException;

    /**
     * Find entities with pagination
     * @param offset Number of entities to skip
     * @param limit Maximum number of entities to return
     * @return Paginated list of entities
     * @throws InfrastructureException If storage fails
     */
    List<T> findWithPagination(int offset, int limit) throws InfrastructureException;

    /**
     * Find entities by example (template matching)
     * @param example Example entity with filled criteria fields
     * @return List of matching entities
     * @throws InfrastructureException If storage fails
     */
    List<T> findByExample(T example) throws InfrastructureException;
}

/**
 * Extended repository interface for entities that support queries by criteria
 * @param <T> Entity type
 * @param <K> Key type
 * @param <C> Criteria type for complex queries
 */
interface CriteriaRepository<T extends java.io.Serializable, K extends Comparable<K>, C>
        extends Repository<T, K> {

    /**
     * Find entities matching criteria
     * @param criteria Query criteria
     * @return List of matching entities
     * @throws InfrastructureException If storage fails
     */
    List<T> findByCriteria(C criteria) throws InfrastructureException;

    /**
     * Count entities matching criteria
     * @param criteria Query criteria
     * @return Number of matching entities
     * @throws InfrastructureException If storage fails
     */
    long countByCriteria(C criteria) throws InfrastructureException;
}

/**
 * Repository interface for range-based queries (intervals, dates, etc.)
 * @param <T> Entity type
 * @param <K> Key type
 * @param <R> Range type (must be comparable for ordering)
 */
interface RangeRepository<T extends java.io.Serializable, K extends Comparable<K>, R extends Comparable<R>>
        extends Repository<T, K> {

    /**
     * Find entities within range
     * @param start Range start (inclusive)
     * @param end Range end (inclusive)
     * @return List of entities in range
     * @throws InfrastructureException If storage fails
     */
    List<T> findInRange(R start, R end) throws InfrastructureException;

    /**
     * Find entities overlapping with given range
     * @param start Range start
     * @param end Range end
     * @return List of overlapping entities
     * @throws InfrastructureException If storage fails
     */
    List<T> findOverlapping(R start, R end) throws InfrastructureException;

    /**
     * Find entities containing point
     * @param point Point to search
     * @return List of entities containing the point
     * @throws InfrastructureException If storage fails
     */
    List<T> findContaining(R point) throws InfrastructureException;
}