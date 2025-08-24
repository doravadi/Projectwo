
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

public class BinRepository {

    
    private final NavigableMap<Long, BinRange> rangesByStart;

    
    private long queryCount = 0;
    private long totalQueryTime = 0;

    public BinRepository() {
        
        this.rangesByStart = new ConcurrentSkipListMap<>();
    }

    
    public void addRange(BinRange range) throws OverlappingRangeException {
        validateNoOverlap(range);
        rangesByStart.put(range.getStartBin(), range);
    }

    
    public Optional<BinRange> findRangeForBin(long bin) {
        long startTime = System.nanoTime();

        try {
            
            Map.Entry<Long, BinRange> floorEntry = rangesByStart.floorEntry(bin);

            if (floorEntry != null) {
                BinRange range = floorEntry.getValue();
                if (range.contains(bin)) {
                    return Optional.of(range);
                }
            }

            return Optional.empty();

        } finally {
            
            long elapsed = System.nanoTime() - startTime;
            recordQuery(elapsed);
        }
    }

    
    public Map<Long, BinRange> findRangesForBins(List<Long> bins) {
        Map<Long, BinRange> results = new HashMap<>();

        
        List<Long> sortedBins = new ArrayList<>(bins);
        Collections.sort(sortedBins);

        Iterator<Map.Entry<Long, BinRange>> rangeIterator = rangesByStart.entrySet().iterator();
        Map.Entry<Long, BinRange> currentRange = rangeIterator.hasNext() ? rangeIterator.next() : null;

        for (Long bin : sortedBins) {
            
            while (currentRange != null && currentRange.getValue().getEndBin() < bin) {
                currentRange = rangeIterator.hasNext() ? rangeIterator.next() : null;
            }

            
            if (currentRange != null && currentRange.getValue().contains(bin)) {
                results.put(bin, currentRange.getValue());
            }
        }

        return results;
    }

    
    public List<BinRange> getAllRanges() {
        return new ArrayList<>(rangesByStart.values());
    }

    
    public List<BinRange> findRangesInInterval(long minBin, long maxBin) {
        List<BinRange> result = new ArrayList<>();

        
        NavigableMap<Long, BinRange> subMap = rangesByStart.subMap(
                rangesByStart.floorKey(minBin), true,   
                maxBin, true                            
        );

        for (BinRange range : subMap.values()) {
            
            if (range.getEndBin() >= minBin && range.getStartBin() <= maxBin) {
                result.add(range);
            }
        }

        return result;
    }

    
    private void validateNoOverlap(BinRange newRange) throws OverlappingRangeException {
        
        Map.Entry<Long, BinRange> floorEntry = rangesByStart.floorEntry(newRange.getStartBin());
        if (floorEntry != null && floorEntry.getValue().overlaps(newRange)) {
            throw new OverlappingRangeException(floorEntry.getValue(), newRange);
        }

        
        Map.Entry<Long, BinRange> ceilingEntry = rangesByStart.ceilingEntry(newRange.getStartBin());
        if (ceilingEntry != null && ceilingEntry.getValue().overlaps(newRange)) {
            throw new OverlappingRangeException(ceilingEntry.getValue(), newRange);
        }

        
        NavigableMap<Long, BinRange> overlappingCandidates = rangesByStart.subMap(
                newRange.getStartBin(), false,   
                newRange.getEndBin(), true       
        );

        if (!overlappingCandidates.isEmpty()) {
            BinRange firstOverlapping = overlappingCandidates.firstEntry().getValue();
            throw new OverlappingRangeException(firstOverlapping, newRange);
        }
    }

    
    public boolean removeRange(long startBin) {
        return rangesByStart.remove(startBin) != null;
    }

    
    public void clear() {
        rangesByStart.clear();
        resetStatistics();
    }

    
    public int getRangeCount() {
        return rangesByStart.size();
    }

    public double getAverageQueryTimeMs() {
        if (queryCount == 0) return 0.0;
        return (totalQueryTime / (double) queryCount) / 1_000_000.0; 
    }

    public long getQueryCount() {
        return queryCount;
    }

    private void recordQuery(long elapsedNanos) {
        queryCount++;
        totalQueryTime += elapsedNanos;
    }

    private void resetStatistics() {
        queryCount = 0;
        totalQueryTime = 0;
    }

    
    public String getRepositoryStats() {
        return String.format(
                "BinRepository Stats: ranges=%d, queries=%d, avgTime=%.3fms",
                getRangeCount(),
                getQueryCount(),
                getAverageQueryTimeMs()
        );
    }

    
    public Iterator<BinRange> rangeIterator() {
        return rangesByStart.values().iterator();
    }

    
    public double calculateRangeDensity() {
        if (rangesByStart.isEmpty()) return 0.0;

        long totalCoverage = 0;
        long minBin = rangesByStart.firstKey();
        long maxBin = rangesByStart.lastEntry().getValue().getEndBin();

        for (BinRange range : rangesByStart.values()) {
            totalCoverage += range.getRangeSize();
        }

        long totalSpace = maxBin - minBin + 1;
        return (double) totalCoverage / totalSpace;
    }
}