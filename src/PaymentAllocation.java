import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;


public final class PaymentAllocation {

    private final String allocationId;
    private final BigDecimal totalPaymentAmount;
    private final LocalDateTime allocationTime;
    private final AllocationStrategy strategy;
    private final Map<String, BigDecimal> bucketAllocations;  
    private final BigDecimal totalInterestSaved;
    private final BigDecimal remainingPayment;
    private final AllocationMetrics metrics;

    public enum AllocationStrategy {
        BANK_RULE("Banka Kuralı - Sabit Sıra"),
        DP_OPTIMAL("DP Optimizasyonu - Minimum Faiz"),
        GREEDY("Greedy - En Yüksek Oran Önce"),
        MANUAL("Manuel Tahsis");

        private final String description;

        AllocationStrategy(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    public PaymentAllocation(String allocationId, BigDecimal totalPaymentAmount,
                             AllocationStrategy strategy, Map<String, BigDecimal> bucketAllocations,
                             BigDecimal totalInterestSaved, BigDecimal remainingPayment,
                             AllocationMetrics metrics) {
        this.allocationId = Objects.requireNonNull(allocationId, "Allocation ID cannot be null");
        this.totalPaymentAmount = Objects.requireNonNull(totalPaymentAmount, "Payment amount cannot be null");
        this.allocationTime = LocalDateTime.now();
        this.strategy = Objects.requireNonNull(strategy, "Strategy cannot be null");
        this.bucketAllocations = new HashMap<>(Objects.requireNonNull(bucketAllocations, "Bucket allocations cannot be null"));
        this.totalInterestSaved = Objects.requireNonNull(totalInterestSaved, "Interest saved cannot be null");
        this.remainingPayment = Objects.requireNonNull(remainingPayment, "Remaining payment cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "Metrics cannot be null");

        validateAllocation();
    }

    
    public static PaymentAllocation createOptimalAllocation(String allocationId,
                                                            BigDecimal paymentAmount,
                                                            Map<String, BigDecimal> bucketAllocations,
                                                            AllocationMetrics metrics) {
        BigDecimal remainingPayment = calculateRemainingPayment(paymentAmount, bucketAllocations);
        return new PaymentAllocation(allocationId, paymentAmount, AllocationStrategy.DP_OPTIMAL,
                bucketAllocations, metrics.getInterestSaved(),
                remainingPayment, metrics);
    }

    public static PaymentAllocation createBankRuleAllocation(String allocationId,
                                                             BigDecimal paymentAmount,
                                                             Map<String, BigDecimal> bucketAllocations) {
        AllocationMetrics defaultMetrics = new AllocationMetrics(0, BigDecimal.ZERO,
                BigDecimal.ZERO, 0L);
        BigDecimal remainingPayment = calculateRemainingPayment(paymentAmount, bucketAllocations);
        return new PaymentAllocation(allocationId, paymentAmount, AllocationStrategy.BANK_RULE,
                bucketAllocations, BigDecimal.ZERO, remainingPayment, defaultMetrics);
    }

    
    public String getAllocationId() { return allocationId; }
    public BigDecimal getTotalPaymentAmount() { return totalPaymentAmount; }
    public LocalDateTime getAllocationTime() { return allocationTime; }
    public AllocationStrategy getStrategy() { return strategy; }
    public Map<String, BigDecimal> getBucketAllocations() {
        return new HashMap<>(bucketAllocations);
    }
    public BigDecimal getTotalInterestSaved() { return totalInterestSaved; }
    public BigDecimal getRemainingPayment() { return remainingPayment; }
    public AllocationMetrics getMetrics() { return metrics; }

    
    public BigDecimal getAllocatedAmount(String bucketId) {
        return bucketAllocations.getOrDefault(bucketId, BigDecimal.ZERO);
    }

    public boolean hasAllocation(String bucketId) {
        BigDecimal amount = bucketAllocations.get(bucketId);
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public Set<String> getAllocatedBuckets() {
        return bucketAllocations.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    public int getAllocatedBucketCount() {
        return getAllocatedBuckets().size();
    }

    public boolean isFullyAllocated() {
        return remainingPayment.compareTo(BigDecimal.ZERO) == 0;
    }

    public BigDecimal getTotalAllocatedAmount() {
        return bucketAllocations.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    
    public BigDecimal getInterestSavingsVs(PaymentAllocation other) {
        return this.totalInterestSaved.subtract(other.totalInterestSaved);
    }

    public boolean isBetterThan(PaymentAllocation other) {
        if (this.strategy == AllocationStrategy.DP_OPTIMAL &&
                other.strategy != AllocationStrategy.DP_OPTIMAL) {
            return true;
        }
        return this.totalInterestSaved.compareTo(other.totalInterestSaved) > 0;
    }

    
    public Map<String, Double> getBucketAllocationPercentages() {
        if (totalPaymentAmount.compareTo(BigDecimal.ZERO) == 0) {
            return new HashMap<>();
        }

        Map<String, Double> percentages = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : bucketAllocations.entrySet()) {
            double percentage = entry.getValue().divide(totalPaymentAmount, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(new BigDecimal("100")).doubleValue();
            percentages.put(entry.getKey(), percentage);
        }
        return percentages;
    }

    public String getStrategyEfficiencyDescription() {
        return switch (strategy) {
            case DP_OPTIMAL -> String.format("Optimal allocation saving %s TL in interest", totalInterestSaved);
            case GREEDY -> String.format("Greedy allocation with %s TL interest impact", totalInterestSaved);
            case BANK_RULE -> "Standard bank allocation rule applied";
            case MANUAL -> "Manual allocation by user";
        };
    }

    private static BigDecimal calculateRemainingPayment(BigDecimal totalPayment,
                                                        Map<String, BigDecimal> allocations) {
        BigDecimal allocated = allocations.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = totalPayment.subtract(allocated);
        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    private void validateAllocation() {
        if (totalPaymentAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total payment amount cannot be negative");
        }

        BigDecimal totalAllocated = getTotalAllocatedAmount();
        if (totalAllocated.compareTo(totalPaymentAmount) > 0) {
            throw new IllegalArgumentException("Total allocated amount exceeds payment amount");
        }

        
        for (Map.Entry<String, BigDecimal> entry : bucketAllocations.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Allocation cannot be negative for bucket: " + entry.getKey());
            }
        }

        if (remainingPayment.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Remaining payment cannot be negative");
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        PaymentAllocation that = (PaymentAllocation) obj;
        return Objects.equals(allocationId, that.allocationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allocationId);
    }

    @Override
    public String toString() {
        return String.format("PaymentAllocation{id='%s', amount=%s, strategy=%s, buckets=%d, saved=%s}",
                allocationId, totalPaymentAmount, strategy,
                getAllocatedBucketCount(), totalInterestSaved);
    }

    
    public static final class AllocationMetrics {
        private final int iterationCount;
        private final BigDecimal interestSaved;
        private final BigDecimal optimizationScore;
        private final long computationTimeMs;

        public AllocationMetrics(int iterationCount, BigDecimal interestSaved,
                                 BigDecimal optimizationScore, long computationTimeMs) {
            this.iterationCount = iterationCount;
            this.interestSaved = Objects.requireNonNull(interestSaved, "Interest saved cannot be null");
            this.optimizationScore = Objects.requireNonNull(optimizationScore, "Optimization score cannot be null");
            this.computationTimeMs = computationTimeMs;
        }

        public int getIterationCount() { return iterationCount; }
        public BigDecimal getInterestSaved() { return interestSaved; }
        public BigDecimal getOptimizationScore() { return optimizationScore; }
        public long getComputationTimeMs() { return computationTimeMs; }

        @Override
        public String toString() {
            return String.format("Metrics{iterations=%d, saved=%s, time=%dms}",
                    iterationCount, interestSaved, computationTimeMs);
        }
    }
}