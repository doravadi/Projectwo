import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;

/**
 * Payment Allocation Optimizer - Dynamic Programming ile Knapsack çözümü.
 *
 * Problem: Ödeme tutarını borç bucket'larına optimal dağıtarak faiz maliyetini minimize et.
 * Algorithm: Multi-constraint knapsack variant
 * Complexity: O(N × M) - N: bucket sayısı, M: ödeme granülaritesi
 */
public final class PaymentOptimizer {

    private final MathContext mathContext;
    private final int granularityLevel;     // Para birimi granülaritesi (100 = kuruş)
    private final BigDecimal tolerance;     // Optimizasyon toleransı

    // DP tablosu için memory optimization
    private final Map<OptimizationKey, BigDecimal> dpCache;

    public PaymentOptimizer(int granularityLevel) {
        this.mathContext = new MathContext(10, RoundingMode.HALF_UP);
        this.granularityLevel = Math.max(1, granularityLevel);
        this.tolerance = new BigDecimal("0.01");
        this.dpCache = new HashMap<>();
    }

    public static PaymentOptimizer createDefault() {
        return new PaymentOptimizer(100); // Kuruş hassasiyeti
    }

    /**
     * Ana optimizasyon method'u - DP ile optimal tahsis
     */
    public PaymentAllocation optimizePaymentAllocation(List<DebtBucket> debtBuckets,
                                                       BigDecimal paymentAmount,
                                                       String allocationId) {
        Objects.requireNonNull(debtBuckets, "Debt buckets cannot be null");
        Objects.requireNonNull(paymentAmount, "Payment amount cannot be null");
        Objects.requireNonNull(allocationId, "Allocation ID cannot be null");

        long startTime = System.currentTimeMillis();

        // Preprocess: sadece borcu olan bucket'ları al
        List<DebtBucket> activeBuckets = debtBuckets.stream()
                .filter(DebtBucket::hasDebt)
                .sorted() // Priority sıralaması
                .toList();

        if (activeBuckets.isEmpty()) {
            return createEmptyAllocation(allocationId, paymentAmount);
        }

        // DP optimization
        Map<String, BigDecimal> optimalAllocations = solveKnapsack(activeBuckets, paymentAmount);

        // Faiz tasarrufu hesapla
        BigDecimal interestSaved = calculateInterestSavings(activeBuckets, optimalAllocations);

        long computationTime = System.currentTimeMillis() - startTime;
        PaymentAllocation.AllocationMetrics metrics = new PaymentAllocation.AllocationMetrics(
                dpCache.size(), interestSaved, calculateOptimizationScore(optimalAllocations), computationTime);

        // Cache'i temizle
        dpCache.clear();

        return PaymentAllocation.createOptimalAllocation(allocationId, paymentAmount,
                optimalAllocations, metrics);
    }

    /**
     * Greedy allocation (karşılaştırma için)
     */
    public PaymentAllocation greedyAllocation(List<DebtBucket> debtBuckets,
                                              BigDecimal paymentAmount,
                                              String allocationId) {

        List<DebtBucket> sortedBuckets = debtBuckets.stream()
                .filter(DebtBucket::hasDebt)
                .sorted((a, b) -> {
                    // Önce priority, sonra faiz oranı
                    int priorityCompare = Integer.compare(a.getPriority(), b.getPriority());
                    if (priorityCompare != 0) return priorityCompare;
                    return b.getInterestRate().compareTo(a.getInterestRate());
                })
                .toList();

        Map<String, BigDecimal> allocations = new HashMap<>();
        BigDecimal remainingPayment = paymentAmount;

        for (DebtBucket bucket : sortedBuckets) {
            if (remainingPayment.compareTo(BigDecimal.ZERO) <= 0) break;

            // Önce minimum payment'ı karşıla
            BigDecimal minPayment = bucket.getMinimumPayment();
            BigDecimal bucketAllocation = minPayment.min(remainingPayment);

            // Kalan tutardan bucket balance'a kadar tahsis et
            BigDecimal remainingForBucket = remainingPayment.subtract(bucketAllocation);
            BigDecimal maxAdditional = bucket.getCurrentBalance().subtract(minPayment);
            BigDecimal additionalAllocation = maxAdditional.min(remainingForBucket);

            BigDecimal totalBucketAllocation = bucketAllocation.add(additionalAllocation);

            if (totalBucketAllocation.compareTo(BigDecimal.ZERO) > 0) {
                allocations.put(bucket.getBucketId(), totalBucketAllocation);
                remainingPayment = remainingPayment.subtract(totalBucketAllocation);
            }
        }

        return PaymentAllocation.createBankRuleAllocation(allocationId, paymentAmount, allocations);
    }

    /**
     * Core DP knapsack solver
     */
    private Map<String, BigDecimal> solveKnapsack(List<DebtBucket> buckets, BigDecimal paymentAmount) {
        int paymentUnits = paymentAmount.multiply(new BigDecimal(granularityLevel)).intValue();
        int bucketCount = buckets.size();

        if (paymentUnits <= 0 || bucketCount == 0) {
            return new HashMap<>();
        }

        // DP table: dp[bucket][amount] = minimum interest cost
        BigDecimal[][] dp = new BigDecimal[bucketCount + 1][paymentUnits + 1];

        // Initialize base case
        for (int amount = 0; amount <= paymentUnits; amount++) {
            dp[0][amount] = BigDecimal.ZERO;
        }

        // Fill DP table
        for (int i = 1; i <= bucketCount; i++) {
            DebtBucket bucket = buckets.get(i - 1);
            int maxBucketUnits = bucket.getCurrentBalance().multiply(new BigDecimal(granularityLevel)).intValue();

            for (int amount = 0; amount <= paymentUnits; amount++) {
                dp[i][amount] = dp[i-1][amount]; // Don't allocate to this bucket

                // Try allocating different amounts to this bucket
                int maxAllocation = Math.min(amount, maxBucketUnits);
                for (int allocation = 1; allocation <= maxAllocation; allocation++) {
                    BigDecimal allocationAmount = new BigDecimal(allocation).divide(new BigDecimal(granularityLevel));
                    BigDecimal interestBenefit = calculateInterestBenefit(bucket, allocationAmount);

                    BigDecimal totalCost = dp[i-1][amount - allocation].subtract(interestBenefit);
                    if (totalCost.compareTo(dp[i][amount]) < 0) {
                        dp[i][amount] = totalCost;
                    }
                }
            }
        }

        // Backtrack to find optimal allocation
        return backtrackOptimalAllocation(buckets, dp, paymentUnits);
    }

    /**
     * DP tablosundan optimal allocation'u çıkarır
     */
    private Map<String, BigDecimal> backtrackOptimalAllocation(List<DebtBucket> buckets,
                                                               BigDecimal[][] dp,
                                                               int paymentUnits) {
        Map<String, BigDecimal> allocations = new HashMap<>();
        int remainingAmount = paymentUnits;

        for (int i = buckets.size(); i >= 1 && remainingAmount > 0; i--) {
            DebtBucket bucket = buckets.get(i - 1);
            int maxBucketUnits = bucket.getCurrentBalance().multiply(new BigDecimal(granularityLevel)).intValue();

            // Bu bucket'a ne kadar allocate edildi?
            for (int allocation = Math.min(remainingAmount, maxBucketUnits); allocation >= 1; allocation--) {
                BigDecimal allocationAmount = new BigDecimal(allocation).divide(new BigDecimal(granularityLevel));
                BigDecimal interestBenefit = calculateInterestBenefit(bucket, allocationAmount);

                BigDecimal expectedCost = dp[i-1][remainingAmount - allocation].subtract(interestBenefit);
                if (dp[i][remainingAmount].subtract(expectedCost).abs().compareTo(tolerance) <= 0) {
                    allocations.put(bucket.getBucketId(), allocationAmount);
                    remainingAmount -= allocation;
                    break;
                }
            }
        }

        return allocations;
    }

    /**
     * Bir bucket'a ödeme yapmanın faiz faydasını hesaplar
     */
    private BigDecimal calculateInterestBenefit(DebtBucket bucket, BigDecimal allocationAmount) {
        if (allocationAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // 30 günlük faiz tasarrufu (ortalama bir billing cycle)
        BigDecimal dailyRate = bucket.getInterestRate().divide(new BigDecimal("365"), mathContext);
        BigDecimal monthlyBenefit = allocationAmount.multiply(dailyRate).multiply(new BigDecimal("30"));

        return monthlyBenefit;
    }

    /**
     * Toplam faiz tasarrufunu hesaplar
     */
    private BigDecimal calculateInterestSavings(List<DebtBucket> buckets, Map<String, BigDecimal> allocations) {
        BigDecimal totalSavings = BigDecimal.ZERO;

        for (DebtBucket bucket : buckets) {
            BigDecimal allocation = allocations.getOrDefault(bucket.getBucketId(), BigDecimal.ZERO);
            if (allocation.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal savings = calculateInterestBenefit(bucket, allocation);
                totalSavings = totalSavings.add(savings);
            }
        }

        return totalSavings;
    }

    /**
     * Optimization score hesaplar (0-100)
     */
    private BigDecimal calculateOptimizationScore(Map<String, BigDecimal> allocations) {
        if (allocations.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Basit metrik: allocation diversity × efficiency
        int bucketCount = allocations.size();
        BigDecimal totalAmount = allocations.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // Score = bucket diversity factor × amount factor
        BigDecimal diversityScore = new BigDecimal(Math.min(bucketCount * 20, 100));
        return diversityScore;
    }

    private PaymentAllocation createEmptyAllocation(String allocationId, BigDecimal paymentAmount) {
        return PaymentAllocation.createOptimalAllocation(allocationId, paymentAmount,
                new HashMap<>(), new PaymentAllocation.AllocationMetrics(0, BigDecimal.ZERO,
                        BigDecimal.ZERO, 0L));
    }

    // Optimization key for DP memoization
    private static final class OptimizationKey {
        private final int bucketIndex;
        private final int remainingAmount;

        public OptimizationKey(int bucketIndex, int remainingAmount) {
            this.bucketIndex = bucketIndex;
            this.remainingAmount = remainingAmount;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            OptimizationKey that = (OptimizationKey) obj;
            return bucketIndex == that.bucketIndex && remainingAmount == that.remainingAmount;
        }

        @Override
        public int hashCode() {
            return Objects.hash(bucketIndex, remainingAmount);
        }
    }

    @Override
    public String toString() {
        return String.format("PaymentOptimizer{granularity=%d, tolerance=%s}",
                granularityLevel, tolerance);
    }
}