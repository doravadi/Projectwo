

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;


public final class SweepLineCalculator {

    
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

    
    public void addBalanceChange(BalanceChange change, DailyBalance.BalanceBucket targetBucket) {
        Objects.requireNonNull(change, "BalanceChange cannot be null");
        Objects.requireNonNull(targetBucket, "Target bucket cannot be null");

        LocalDate date = change.getDate();

        
        EnumMap<DailyBalance.BalanceBucket, BigDecimal> deltas =
                deltaMap.computeIfAbsent(date, k -> createZeroBuckets());

        
        BigDecimal currentDelta = deltas.get(targetBucket);
        deltas.put(targetBucket, currentDelta.add(change.getAmount()));
    }

    
    public void addBalanceChanges(List<BalanceChange> changes,
                                  Map<BalanceChange, DailyBalance.BalanceBucket> bucketMapping) {
        for (BalanceChange change : changes) {
            DailyBalance.BalanceBucket bucket = bucketMapping.get(change);
            if (bucket != null) {
                addBalanceChange(change, bucket);
            }
        }
    }

    
    public List<DailyBalance> calculateDailyBalances(DateRange period) {
        Objects.requireNonNull(period, "Period cannot be null");

        List<DailyBalance> result = new ArrayList<>();
        EnumMap<DailyBalance.BalanceBucket, BigDecimal> runningBalances =
                new EnumMap<>(initialBalances);

        LocalDate currentDate = period.getStartDate();

        
        for (Map.Entry<LocalDate, EnumMap<DailyBalance.BalanceBucket, BigDecimal>> entry :
                deltaMap.headMap(currentDate, false).entrySet()) {
            applyDeltas(runningBalances, entry.getValue());
        }

        
        while (!currentDate.isAfter(period.getEndDate())) {
            
            EnumMap<DailyBalance.BalanceBucket, BigDecimal> dailyDeltas =
                    deltaMap.get(currentDate);

            if (dailyDeltas != null) {
                applyDeltas(runningBalances, dailyDeltas);
            }

            
            DailyBalance dailyBalance = new DailyBalance(currentDate,
                    new EnumMap<>(runningBalances));
            result.add(dailyBalance);

            currentDate = currentDate.plusDays(1);
        }

        return result;
    }

    
    public DailyBalance getBalanceAt(LocalDate date) {
        Objects.requireNonNull(date, "Date cannot be null");

        EnumMap<DailyBalance.BalanceBucket, BigDecimal> runningBalances =
                new EnumMap<>(initialBalances);

        
        for (Map.Entry<LocalDate, EnumMap<DailyBalance.BalanceBucket, BigDecimal>> entry :
                deltaMap.headMap(date, true).entrySet()) {
            applyDeltas(runningBalances, entry.getValue());
        }

        return new DailyBalance(date, runningBalances);
    }

    
    public EnumMap<DailyBalance.BalanceBucket, BigDecimal> calculateAverageBalances(DateRange period) {
        List<DailyBalance> dailyBalances = calculateDailyBalances(period);
        int dayCount = dailyBalances.size();

        if (dayCount == 0) {
            return createZeroBuckets();
        }

        EnumMap<DailyBalance.BalanceBucket, BigDecimal> sums = createZeroBuckets();

        
        for (DailyBalance daily : dailyBalances) {
            for (DailyBalance.BalanceBucket bucket : DailyBalance.BalanceBucket.values()) {
                BigDecimal currentSum = sums.get(bucket);
                BigDecimal dailyAmount = daily.getBalance(bucket);
                sums.put(bucket, currentSum.add(dailyAmount));
            }
        }

        
        EnumMap<DailyBalance.BalanceBucket, BigDecimal> averages = createZeroBuckets();
        BigDecimal dayCountDecimal = new BigDecimal(dayCount);

        for (DailyBalance.BalanceBucket bucket : DailyBalance.BalanceBucket.values()) {
            BigDecimal sum = sums.get(bucket);
            BigDecimal average = sum.divide(dayCountDecimal, 6, BigDecimal.ROUND_HALF_UP);
            averages.put(bucket, average);
        }

        return averages;
    }

    
    public BigDecimal getTotalDelta(DailyBalance.BalanceBucket bucket, DateRange period) {
        return deltaMap.entrySet().stream()
                .filter(entry -> period.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .map(deltas -> deltas.get(bucket))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    
    public Set<LocalDate> getChangePoints() {
        return new TreeSet<>(deltaMap.keySet());
    }

    
    public void clear() {
        deltaMap.clear();
        for (DailyBalance.BalanceBucket bucket : DailyBalance.BalanceBucket.values()) {
            initialBalances.put(bucket, BigDecimal.ZERO);
        }
    }

    
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("changePointCount", deltaMap.size());
        stats.put("dateRange", deltaMap.isEmpty() ? "empty" :
                deltaMap.firstKey() + " to " + deltaMap.lastKey());

        
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