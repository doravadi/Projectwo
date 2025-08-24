
import java.util.List;
import java.util.Map;
import java.util.Optional;


public interface BinRangeRepository extends RangeRepository<BinRange, Long, Long> {

    
    Optional<BinRange> findRangeForBin(long bin) throws InfrastructureException;

    
    void addRange(BinRange range) throws OverlappingRangeException, InfrastructureException;

    
    Map<Long, BinRange> findRangesForBins(List<Long> bins) throws InfrastructureException;

    
    List<BinRange> findByBankName(String bankName) throws InfrastructureException;

    
    List<BinRange> findByCountry(String country) throws InfrastructureException;

    
    List<Map.Entry<BinRange, BinRange>> validateNoOverlaps() throws InfrastructureException;
}