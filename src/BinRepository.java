// BinRepository.java - BIN aralık yönetimi
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

public class BinRepository {

    // TreeMap: Otomatik sıralama için (O(log n) performans)
    private final NavigableMap<Long, BinRange> rangesByStart;

    // Performance measurement için
    private long queryCount = 0;
    private long totalQueryTime = 0;

    public BinRepository() {
        // ConcurrentSkipListMap: Thread-safe TreeMap equivalent
        this.rangesByStart = new ConcurrentSkipListMap<>();
    }

    /**
     * Yeni BIN aralığı ekler. Çakışma kontrolü yapar.
     * @param range Eklenecek aralık
     * @throws OverlappingRangeException Çakışma varsa
     */
    public void addRange(BinRange range) throws OverlappingRangeException {
        validateNoOverlap(range);
        rangesByStart.put(range.getStartBin(), range);
    }

    /**
     * Verilen BIN numarasına ait aralığı bulur.
     * @param bin Aranacak BIN numarası
     * @return Bulunan aralık veya Optional.empty()
     */
    public Optional<BinRange> findRangeForBin(long bin) {
        long startTime = System.nanoTime();

        try {
            // floor(): bin'den küçük veya eşit en büyük key
            Map.Entry<Long, BinRange> floorEntry = rangesByStart.floorEntry(bin);

            if (floorEntry != null) {
                BinRange range = floorEntry.getValue();
                if (range.contains(bin)) {
                    return Optional.of(range);
                }
            }

            return Optional.empty();

        } finally {
            // Performance tracking
            long elapsed = System.nanoTime() - startTime;
            recordQuery(elapsed);
        }
    }

    /**
     * Toplu BIN arama - batch processing için optimize edilmiş
     * @param bins Aranacak BIN listesi
     * @return BIN -> BinRange mapping
     */
    public Map<Long, BinRange> findRangesForBins(List<Long> bins) {
        Map<Long, BinRange> results = new HashMap<>();

        // Bins'i sırala ki TreeMap üzerinde efficient geçiş yapabilelim
        List<Long> sortedBins = new ArrayList<>(bins);
        Collections.sort(sortedBins);

        Iterator<Map.Entry<Long, BinRange>> rangeIterator = rangesByStart.entrySet().iterator();
        Map.Entry<Long, BinRange> currentRange = rangeIterator.hasNext() ? rangeIterator.next() : null;

        for (Long bin : sortedBins) {
            // İlerle: Mevcut aralık bin'den küçükse sonraki aralığa geç
            while (currentRange != null && currentRange.getValue().getEndBin() < bin) {
                currentRange = rangeIterator.hasNext() ? rangeIterator.next() : null;
            }

            // Kontrol et: Mevcut aralık bin'i kapsıyor mu?
            if (currentRange != null && currentRange.getValue().contains(bin)) {
                results.put(bin, currentRange.getValue());
            }
        }

        return results;
    }

    /**
     * Tüm aralıkları sıralı şekilde döndürür
     */
    public List<BinRange> getAllRanges() {
        return new ArrayList<>(rangesByStart.values());
    }

    /**
     * Belirli bir aralıktaki BIN'leri bulur
     * @param minBin Minimum BIN (dahil)
     * @param maxBin Maximum BIN (dahil)
     * @return Aralık içindeki BinRange'ler
     */
    public List<BinRange> findRangesInInterval(long minBin, long maxBin) {
        List<BinRange> result = new ArrayList<>();

        // subMap: minBin'den başlayıp maxBin+1'e kadar (exclusive)
        NavigableMap<Long, BinRange> subMap = rangesByStart.subMap(
                rangesByStart.floorKey(minBin), true,   // Include floor
                maxBin, true                            // Include maxBin
        );

        for (BinRange range : subMap.values()) {
            // Gerçekten kesişiyor mu kontrol et
            if (range.getEndBin() >= minBin && range.getStartBin() <= maxBin) {
                result.add(range);
            }
        }

        return result;
    }

    /**
     * Aralık çakışması kontrolü
     */
    private void validateNoOverlap(BinRange newRange) throws OverlappingRangeException {
        // Yeni aralığın başlangıcından önceki en yakın aralığı bul
        Map.Entry<Long, BinRange> floorEntry = rangesByStart.floorEntry(newRange.getStartBin());
        if (floorEntry != null && floorEntry.getValue().overlaps(newRange)) {
            throw new OverlappingRangeException(floorEntry.getValue(), newRange);
        }

        // Yeni aralığın başlangıcından sonraki en yakın aralığı bul
        Map.Entry<Long, BinRange> ceilingEntry = rangesByStart.ceilingEntry(newRange.getStartBin());
        if (ceilingEntry != null && ceilingEntry.getValue().overlaps(newRange)) {
            throw new OverlappingRangeException(ceilingEntry.getValue(), newRange);
        }

        // Yeni aralık içinde başlayan mevcut aralıkları kontrol et
        NavigableMap<Long, BinRange> overlappingCandidates = rangesByStart.subMap(
                newRange.getStartBin(), false,   // Başlangıç hariç
                newRange.getEndBin(), true       // Bitiş dahil
        );

        if (!overlappingCandidates.isEmpty()) {
            BinRange firstOverlapping = overlappingCandidates.firstEntry().getValue();
            throw new OverlappingRangeException(firstOverlapping, newRange);
        }
    }

    /**
     * Aralık silme
     */
    public boolean removeRange(long startBin) {
        return rangesByStart.remove(startBin) != null;
    }

    /**
     * Repository temizleme
     */
    public void clear() {
        rangesByStart.clear();
        resetStatistics();
    }

    /**
     * Repository istatistikleri
     */
    public int getRangeCount() {
        return rangesByStart.size();
    }

    public double getAverageQueryTimeMs() {
        if (queryCount == 0) return 0.0;
        return (totalQueryTime / (double) queryCount) / 1_000_000.0; // ns to ms
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

    /**
     * Debug ve monitoring için repository durumu
     */
    public String getRepositoryStats() {
        return String.format(
                "BinRepository Stats: ranges=%d, queries=%d, avgTime=%.3fms",
                getRangeCount(),
                getQueryCount(),
                getAverageQueryTimeMs()
        );
    }

    /**
     * Memory-efficient iterator - büyük dataset'ler için
     */
    public Iterator<BinRange> rangeIterator() {
        return rangesByStart.values().iterator();
    }

    /**
     * Range density analysis - overlap riski değerlendirmesi için
     */
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