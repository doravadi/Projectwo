

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.EnumMap;
import java.util.Map;


public final class DailyBalance implements Comparable<DailyBalance> {
    private final LocalDate date;
    private final EnumMap<BalanceBucket, BigDecimal> balances;
    private final BigDecimal totalBalance;

    public enum BalanceBucket {
        PURCHASE,           
        CASH_ADVANCE,       
        INSTALLMENT,        
        FEES_INTEREST       
    }

    public DailyBalance(LocalDate date, EnumMap<BalanceBucket, BigDecimal> balances) {
        this.date = Objects.requireNonNull(date, "Date cannot be null");
        Objects.requireNonNull(balances, "Balances cannot be null");

        
        this.balances = new EnumMap<>(BalanceBucket.class);
        for (BalanceBucket bucket : BalanceBucket.values()) {
            BigDecimal balance = balances.getOrDefault(bucket, BigDecimal.ZERO);
            this.balances.put(bucket, Objects.requireNonNull(balance, "Balance cannot be null"));
        }

        
        this.totalBalance = this.balances.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    
    public static DailyBalance of(LocalDate date, BigDecimal totalBalance) {
        EnumMap<BalanceBucket, BigDecimal> balances = new EnumMap<>(BalanceBucket.class);
        balances.put(BalanceBucket.PURCHASE, totalBalance);
        for (BalanceBucket bucket : BalanceBucket.values()) {
            if (bucket != BalanceBucket.PURCHASE) {
                balances.put(bucket, BigDecimal.ZERO);
            }
        }
        return new DailyBalance(date, balances);
    }

    public static DailyBalance zero(LocalDate date) {
        EnumMap<BalanceBucket, BigDecimal> balances = new EnumMap<>(BalanceBucket.class);
        for (BalanceBucket bucket : BalanceBucket.values()) {
            balances.put(bucket, BigDecimal.ZERO);
        }
        return new DailyBalance(date, balances);
    }

    
    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getTotalBalance() {
        return totalBalance;
    }

    public BigDecimal getBalance(BalanceBucket bucket) {
        return balances.get(bucket);
    }

    public EnumMap<BalanceBucket, BigDecimal> getAllBalances() {
        return new EnumMap<>(balances); 
    }

    
    public DailyBalance addChange(BalanceChange change, BalanceBucket targetBucket) {
        if (!date.equals(change.getDate())) {
            throw new IllegalArgumentException("Change date must match balance date");
        }

        EnumMap<BalanceBucket, BigDecimal> newBalances = new EnumMap<>(balances);
        BigDecimal currentBalance = newBalances.get(targetBucket);
        newBalances.put(targetBucket, currentBalance.add(change.getAmount()));

        return new DailyBalance(date, newBalances);
    }

    public DailyBalance addToBalance(BalanceBucket bucket, BigDecimal amount) {
        EnumMap<BalanceBucket, BigDecimal> newBalances = new EnumMap<>(balances);
        BigDecimal currentBalance = newBalances.get(bucket);
        newBalances.put(bucket, currentBalance.add(amount));

        return new DailyBalance(date, newBalances);
    }

    
    public BigDecimal getInterestBearingBalance() {
        
        return balances.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean hasPositiveBalance() {
        return totalBalance.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasNegativeBalance() {
        return totalBalance.compareTo(BigDecimal.ZERO) < 0;
    }

    public BalanceBucket getDominantBucket() {
        return balances.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(BalanceBucket.PURCHASE);
    }

    public int getActiveBucketCount() {
        return (int) balances.values().stream()
                .filter(balance -> balance.compareTo(BigDecimal.ZERO) != 0)
                .count();
    }

    public BigDecimal getBucketPercentage(BalanceBucket bucket) {
        if (totalBalance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal bucketBalance = balances.get(bucket);
        return bucketBalance.divide(totalBalance, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        DailyBalance that = (DailyBalance) obj;
        return Objects.equals(date, that.date) &&
                Objects.equals(balances, that.balances);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, balances);
    }

    @Override
    public int compareTo(DailyBalance other) {
        return date.compareTo(other.date);
    }

    @Override
    public String toString() {
        return String.format("DailyBalance{%s: total=%s, buckets=%d active}",
                date, totalBalance, getActiveBucketCount());
    }
}