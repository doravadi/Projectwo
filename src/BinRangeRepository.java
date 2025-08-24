// BinRangeRepository.java - Specialized BIN repository interface extending range repository
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Specialized BIN repository interface extending range repository
 */
public interface BinRangeRepository extends RangeRepository<BinRange, Long, Long> {

    /**
     * Find BIN range for specific BIN number
     * @param bin BIN number to lookup
     * @return BinRange if found, empty otherwise
     * @throws InfrastructureException If storage fails
     */
    Optional<BinRange> findRangeForBin(long bin) throws InfrastructureException;

    /**
     * Add new BIN range with overlap checking
     * @param range BIN range to add
     * @throws OverlappingRangeException If range overlaps with existing
     * @throws InfrastructureException If storage fails
     */
    void addRange(BinRange range) throws OverlappingRangeException, InfrastructureException;

    /**
     * Bulk lookup for multiple BINs
     * @param bins List of BIN numbers
     * @return Map of BIN to BinRange (missing BINs not included)
     * @throws InfrastructureException If storage fails
     */
    Map<Long, BinRange> findRangesForBins(List<Long> bins) throws InfrastructureException;

    /**
     * Find ranges by bank name
     * @param bankName Bank name to search
     * @return List of ranges for the bank
     * @throws InfrastructureException If storage fails
     */
    List<BinRange> findByBankName(String bankName) throws InfrastructureException;

    /**
     * Find ranges by country
     * @param country Country code to search
     * @return List of ranges for the country
     * @throws InfrastructureException If storage fails
     */
    List<BinRange> findByCountry(String country) throws InfrastructureException;

    /**
     * Validate no overlaps in entire repository
     * @return List of overlapping range pairs (empty if none)
     * @throws InfrastructureException If storage fails
     */
    List<Map.Entry<BinRange, BinRange>> validateNoOverlaps() throws InfrastructureException;
}