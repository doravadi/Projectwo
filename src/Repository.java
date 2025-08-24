
import java.util.List;
import java.util.Optional;


public interface Repository<T extends java.io.Serializable, K extends Comparable<K>> {

    
    Optional<T> findById(K id) throws InfrastructureException;

    
    List<T> findAll() throws InfrastructureException;

    
    T save(T entity) throws DomainException, InfrastructureException;

    
    List<T> saveAll(List<T> entities) throws DomainException, InfrastructureException;

    
    boolean deleteById(K id) throws InfrastructureException;

    
    boolean delete(T entity) throws InfrastructureException;

    
    boolean existsById(K id) throws InfrastructureException;

    
    long count() throws InfrastructureException;

    
    List<T> findWithPagination(int offset, int limit) throws InfrastructureException;

    
    List<T> findByExample(T example) throws InfrastructureException;
}


interface CriteriaRepository<T extends java.io.Serializable, K extends Comparable<K>, C>
        extends Repository<T, K> {

    
    List<T> findByCriteria(C criteria) throws InfrastructureException;

    
    long countByCriteria(C criteria) throws InfrastructureException;
}


interface RangeRepository<T extends java.io.Serializable, K extends Comparable<K>, R extends Comparable<R>>
        extends Repository<T, K> {

    
    List<T> findInRange(R start, R end) throws InfrastructureException;

    
    List<T> findOverlapping(R start, R end) throws InfrastructureException;

    
    List<T> findContaining(R point) throws InfrastructureException;
}