

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Sweep line algoritması ile günlük bakiye hesaplama.
 * O(K log K) kompleksitesi - K: bakiye değişim noktası sayısı.
 * Gün sayısından bağımsız çalışır.
 */
public final class SweepLineCalculator {

    // TreeMap: otomatik sıralama + O(log K) erişim
    private final NavigableMap<LocalDate, EnumMap<DailyBalance.BalanceBucket, BigDecimal>> deltaMap;
    private final EnumMap<DailyBalance.BalanceBucket, BigDecimal> initialBalances;

    public SweepLineCalculator() {
        this.deltaMap = new TreeMap<>();
        this.initialBalances = createZeroBuckets();
    }

    public SweepLineCalculator(EnumMap<DailyBalance.BalanceBucket, BigDecimal> initialBalances) {
        this.deltaMap = new TreeMap<>();
        this.initialBalances = new EnumMap<>(initialBalances);
    }

    /**
     * Bakiye değişimini sweep line'a ekler - O(log K)
     */
    public void addBalanceChange(BalanceChange change, DailyBalance.BalanceBucket targetBucket) {
        Objects.requireNonNull(change, "BalanceChange cannot be null");
        Objects.requireNonNull(targetBucket, "Target bucket cannot be null");

        LocalDate date = change.getDate();

        // Delta map'te bu tarih yoksa oluştur
        EnumMap<DailyBalance.BalanceBucket, BigDecimal> deltas =
                deltaMap.computeIfAbsent(date, k -> createZeroBuckets());

        // Mevcut delta'ya ekle
        BigDecimal currentDelta = deltas.get(targetBucket);
        deltas.put(targetBucket, currentDelta.add(change.getAmount()));
    }

    /**
     * Birden fazla değişimi toplu olarak ekler
     */
    public void addBalanceChanges(List<BalanceChange> changes,
                                  Map<BalanceChange, DailyBalance.BalanceBucket> bucketMapping) {
        for (BalanceChange change : changes) {
            DailyBalance.BalanceBucket bucket = bucketMapping.get(change);
            if (bucket != null) {
                addBalanceChange(change, bucket);
            }
        }
    }

    /**
     * Belirli bir tarih aralığındaki günlük bakiyeleri hesaplar.
     * Prefix sum yaklaşımı ile O(K + N) kompleksitesi.
     */
    public List<DailyBalance> calculateDailyBalances(DateRange period) {
        Objects.requireNonNull(period, "Period cannot be null");

        List<DailyBalance> result = new ArrayList<>();
        EnumMap<DailyBalance.BalanceBucket, BigDecimal> runningBalances =
                new EnumMap<>(initialBalances);

        LocalDate currentDate = period.getStartDate();

        // Period başından önce gelen tüm değişimleri uygula
        for (Map.Entry<LocalDate, EnumMap<DailyBalance.BalanceBucket, BigDecimal>> entry :
                deltaMap.headMap(currentDate, false).entrySet()) {
            applyDeltas(runningBalances, entry.getValue());
        }

        // Period boyunca gün gün hesapla
        while (!currentDate.isAfter(period.getEndDate())) {
            // Bu günün değişimlerini uygula
            EnumMap<DailyBalance.BalanceBucket, BigDecimal> dailyDeltas =
                    deltaMap.get(currentDate);

            if (dailyDeltas != null) {
                applyDeltas(runningBalances, dailyDeltas);
            }

            // Bu günün bakiyesini kaydet
            DailyBalance dailyBalance = new DailyBalance(currentDate,
                    new EnumMap<>(runningBalances));
            result.add(dailyBalance);

            currentDate = currentDate.plusDays(1);
        }

        return result;
    }

    /**
     * Belirli bir tarihteki bakiyeyi hesaplar (tek nokta sorgusu) - O(K)
     */
    public DailyBalance getBalanceAt(LocalDate date) {
        Objects.requireNonNull(date, "Date cannot be null");

        EnumMap<DailyBalance.BalanceBucket, BigDecimal> runningBalances =
                new EnumMap<>(initialBalances);

        // Bu tarihe kadar olan tüm değişimleri uygula
        for (Map.Entry<LocalDate, EnumMap<DailyBalance.BalanceBucket, BigDecimal>> entry :
                deltaMap.headMap(date, true).entrySet()) {
            applyDeltas(runningBalances, entry.getValue());
        }

        return new DailyBalance(date, runningBalances);
    }

    /**
     * Ortalama günlük bakiyeyi hesaplar (faiz hesabı için kritik!)
     */
    public EnumMap<DailyBalance.BalanceBucket, BigDecimal> calculateAverageBalances(DateRange period) {
        List<DailyBalance> dailyBalances = calculateDailyBalances(period);
        int dayCount = dailyBalances.size();

        if (dayCount == 0) {
            return createZeroBuckets();
        }

        EnumMap<DailyBalance.BalanceBucket, BigDecimal> sums = createZeroBuckets();

        // Her bucket için günlük bakiyeleri topla
        for (DailyBalance daily : dailyBalances) {
            for (DailyBalance.BalanceBucket bucket : DailyBalance.BalanceBucket.values()) {
                BigDecimal currentSum = sums.get(bucket);
                BigDecimal dailyAmount = daily.getBalance(bucket);
                sums.put(bucket, currentSum.add(dailyAmount));
            }
        }

        // Ortalama hesapla
        EnumMap<DailyBalance.BalanceBucket, BigDecimal> averages = createZeroBuckets();
        BigDecimal dayCountDecimal = new BigDecimal(dayCount);

        for (DailyBalance.BalanceBucket bucket : DailyBalance.BalanceBucket.values()) {
            BigDecimal sum = sums.get(bucket);
            BigDecimal average = sum.divide(dayCountDecimal, 6, BigDecimal.ROUND_HALF_UP);
            averages.put(bucket, average);
        }

        return averages;
    }

    /**
     * Belirli bucket'taki toplam delta'yı hesaplar
     */
    public BigDecimal getTotalDelta(DailyBalance.BalanceBucket bucket, DateRange period) {
        return deltaMap.entrySet().stream()
                .filter(entry -> period.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .map(deltas -> deltas.get(bucket))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Delta noktalarını döndürür (debug/monitoring için)
     */
    public Set<LocalDate> getChangePoints() {
        return new TreeSet<>(deltaMap.keySet());
    }

    /**
     * Sweep line'ı temizler
     */
    public void clear() {
        deltaMap.clear();
        for (DailyBalance.BalanceBucket bucket : DailyBalance.BalanceBucket.values()) {
            initialBalances.put(bucket, BigDecimal.ZERO);
        }
    }

    /**
     * Sistem istatistikleri
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("changePointCount", deltaMap.size());
        stats.put("dateRange", deltaMap.isEmpty() ? "empty" :
                deltaMap.firstKey() + " to " + deltaMap.lastKey());

        // Bucket değişim sayıları
        EnumMap<DailyBalance.BalanceBucket, Integer> bucketChangeCounts =
                new EnumMap<>(DailyBalance.BalanceBucket.class);

        for (DailyBalance.BalanceBucket bucket : DailyBalance.BalanceBucket.values()) {
            int changeCount = (int) deltaMap.values().stream()
                    .filter(deltas -> deltas.get(bucket).compareTo(BigDecimal.ZERO) != 0)
                    .count();
            bucketChangeCounts.put(bucket, changeCount);
        }

        stats.put("bucketChangeCounts", bucketChangeCounts);
        return stats;
    }

    // Helper methods
    private void applyDeltas(EnumMap<DailyBalance.BalanceBucket, BigDecimal> balances,
                             EnumMap<DailyBalance.BalanceBucket, BigDecimal> deltas) {
        for (DailyBalance.BalanceBucket bucket : DailyBalance.BalanceBucket.values()) {
            BigDecimal currentBalance = balances.get(bucket);
            BigDecimal delta = deltas.get(bucket);
            balances.put(bucket, currentBalance.add(delta));
        }
    }

    private EnumMap<DailyBalance.BalanceBucket, BigDecimal> createZeroBuckets() {
        EnumMap<DailyBalance.BalanceBucket, BigDecimal> buckets =
                new EnumMap<>(DailyBalance.BalanceBucket.class);
        for (DailyBalance.BalanceBucket bucket : DailyBalance.BalanceBucket.values()) {
            buckets.put(bucket, BigDecimal.ZERO);
        }
        return buckets;
    }

    @Override
    public String toString() {
        return String.format("SweepLineCalculator{changePoints=%d, range=%s}",
                deltaMap.size(),
                deltaMap.isEmpty() ? "empty" :
                        deltaMap.firstKey() + " to " + deltaMap.lastKey());
    }
}