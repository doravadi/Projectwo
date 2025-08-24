import java.math.BigDecimal;
import java.util.*;

/**
 * Payment allocation strategy interface - Strategy Pattern.
 *
 * Farklı tahsis stratejilerini implement etmek için:
 * - BankRuleStrategy: Geleneksel banka kuralları
 * - OptimalStrategy: DP ile minimum faiz
 * - GreedyStrategy: En yüksek faiz oranından başla
 * - ManualStrategy: Kullanıcı tanımlı tahsis
 */
public abstract class AllocationStrategy {

    protected final String strategyName;
    protected final String description;

    protected AllocationStrategy(String strategyName, String description) {
        this.strategyName = Objects.requireNonNull(strategyName, "Strategy name cannot be null");
        this.description = Objects.requireNonNull(description, "Description cannot be null");
    }

    /**
     * Ana tahsis method'u - her strategy implement etmeli
     */
    public abstract PaymentAllocation allocate(List<DebtBucket> debtBuckets,
                                               BigDecimal paymentAmount,
                                               String allocationId);

    /**
     * Strategy'nin uygun olup olmadığını kontrol eder
     */
    public abstract boolean isApplicable(List<DebtBucket> debtBuckets, BigDecimal paymentAmount);

    /**
     * Tahsis öncesi validasyon
     */
    public void validateInputs(List<DebtBucket> debtBuckets, BigDecimal paymentAmount) {
        Objects.requireNonNull(debtBuckets, "Debt buckets cannot be null");
        Objects.requireNonNull(paymentAmount, "Payment amount cannot be null");

        if (paymentAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Payment amount cannot be negative");
        }

        if (debtBuckets.isEmpty()) {
            throw new IllegalArgumentException("Debt buckets cannot be empty");
        }

        // Bucket validation
        for (DebtBucket bucket : debtBuckets) {
            if (bucket == null) {
                throw new IllegalArgumentException("Debt bucket cannot be null");
            }
        }
    }

    // Getters
    public String getStrategyName() { return strategyName; }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return String.format("%s: %s", strategyName, description);
    }

    // ================================
    // CONCRETE STRATEGY IMPLEMENTATIONS
    // ================================

    /**
     * Banka Kuralı Stratejisi - Sabit öncelik sırası
     */
    public static final class BankRuleStrategy extends AllocationStrategy {

        private final List<DebtBucket.BucketType> priorityOrder;

        public BankRuleStrategy() {
            super("Bank Rule", "Geleneksel banka tahsis kuralları");
            this.priorityOrder = Arrays.asList(
                    DebtBucket.BucketType.OVERDUE,
                    DebtBucket.BucketType.FEES_INTEREST,
                    DebtBucket.BucketType.CASH_ADVANCE,
                    DebtBucket.BucketType.PURCHASE,
                    DebtBucket.BucketType.INSTALLMENT
            );
        }

        @Override
        public PaymentAllocation allocate(List<DebtBucket> debtBuckets,
                                          BigDecimal paymentAmount, String allocationId) {
            validateInputs(debtBuckets, paymentAmount);

            Map<String, BigDecimal> allocations = new HashMap<>();
            BigDecimal remainingPayment = paymentAmount;

            // Priority sırasına göre tahsis et
            for (DebtBucket.BucketType type : priorityOrder) {
                if (remainingPayment.compareTo(BigDecimal.ZERO) <= 0) break;

                List<DebtBucket> bucketsOfType = debtBuckets.stream()
                        .filter(b -> b.getType() == type && b.hasDebt())
                        .sorted(Comparator.comparing(DebtBucket::getDueDate))
                        .toList();

                for (DebtBucket bucket : bucketsOfType) {
                    if (remainingPayment.compareTo(BigDecimal.ZERO) <= 0) break;

                    // Önce minimum payment
                    BigDecimal minPayment = bucket.getMinimumPayment();
                    BigDecimal allocation = minPayment.min(remainingPayment);

                    // Sonra kalan balance'a kadar
                    BigDecimal remaining = remainingPayment.subtract(allocation);
                    BigDecimal maxAdditional = bucket.getCurrentBalance().subtract(minPayment);
                    BigDecimal additional = maxAdditional.min(remaining);

                    BigDecimal totalAllocation = allocation.add(additional);
                    if (totalAllocation.compareTo(BigDecimal.ZERO) > 0) {
                        allocations.put(bucket.getBucketId(), totalAllocation);
                        remainingPayment = remainingPayment.subtract(totalAllocation);
                    }
                }
            }

            return PaymentAllocation.createBankRuleAllocation(allocationId, paymentAmount, allocations);
        }

        @Override
        public boolean isApplicable(List<DebtBucket> debtBuckets, BigDecimal paymentAmount) {
            return true; // Bank rule her zaman uygulanabilir
        }
    }

    /**
     * Optimal DP Stratejisi - Minimum faiz hedefli
     */
    public static final class OptimalStrategy extends AllocationStrategy {

        private final PaymentOptimizer optimizer;

        public OptimalStrategy(PaymentOptimizer optimizer) {
            super("DP Optimal", "Dynamic Programming ile faiz minimizasyonu");
            this.optimizer = Objects.requireNonNull(optimizer, "Optimizer cannot be null");
        }

        public OptimalStrategy() {
            this(PaymentOptimizer.createDefault());
        }

        @Override
        public PaymentAllocation allocate(List<DebtBucket> debtBuckets,
                                          BigDecimal paymentAmount, String allocationId) {
            validateInputs(debtBuckets, paymentAmount);
            return optimizer.optimizePaymentAllocation(debtBuckets, paymentAmount, allocationId);
        }

        @Override
        public boolean isApplicable(List<DebtBucket> debtBuckets, BigDecimal paymentAmount) {
            // DP için minimum koşullar
            int activeBucketCount = (int) debtBuckets.stream()
                    .filter(DebtBucket::hasDebt)
                    .count();

            return activeBucketCount >= 2 &&
                    paymentAmount.compareTo(new BigDecimal("10")) > 0; // Min 10 TL
        }
    }

    /**
     * Greedy Stratejisi - En yüksek faiz oranından başla
     */
    public static final class GreedyStrategy extends AllocationStrategy {

        private final PaymentOptimizer optimizer;

        public GreedyStrategy(PaymentOptimizer optimizer) {
            super("Greedy", "En yüksek faiz oranı öncelikli tahsis");
            this.optimizer = Objects.requireNonNull(optimizer, "Optimizer cannot be null");
        }

        public GreedyStrategy() {
            this(PaymentOptimizer.createDefault());
        }

        @Override
        public PaymentAllocation allocate(List<DebtBucket> debtBuckets,
                                          BigDecimal paymentAmount, String allocationId) {
            validateInputs(debtBuckets, paymentAmount);
            return optimizer.greedyAllocation(debtBuckets, paymentAmount, allocationId);
        }

        @Override
        public boolean isApplicable(List<DebtBucket> debtBuckets, BigDecimal paymentAmount) {
            return debtBuckets.stream().anyMatch(DebtBucket::hasDebt);
        }
    }

    /**
     * Manuel Stratejisi - Kullanıcı tanımlı tahsis
     */
    public static final class ManualStrategy extends AllocationStrategy {

        private final Map<String, BigDecimal> predefinedAllocations;

        public ManualStrategy(Map<String, BigDecimal> predefinedAllocations) {
            super("Manual", "Kullanıcı tanımlı manuel tahsis");
            this.predefinedAllocations = new HashMap<>(
                    Objects.requireNonNull(predefinedAllocations, "Predefined allocations cannot be null")
            );
        }

        @Override
        public PaymentAllocation allocate(List<DebtBucket> debtBuckets,
                                          BigDecimal paymentAmount, String allocationId) {
            validateInputs(debtBuckets, paymentAmount);

            // Predefined allocation'ları validate et
            BigDecimal totalPredefined = predefinedAllocations.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalPredefined.compareTo(paymentAmount) > 0) {
                throw new IllegalArgumentException("Total predefined allocations exceed payment amount");
            }

            // Bucket existence check
            Set<String> existingBucketIds = debtBuckets.stream()
                    .map(DebtBucket::getBucketId)
                    .collect(java.util.stream.Collectors.toSet());

            Map<String, BigDecimal> validAllocations = new HashMap<>();
            for (Map.Entry<String, BigDecimal> entry : predefinedAllocations.entrySet()) {
                String bucketId = entry.getKey();
                BigDecimal amount = entry.getValue();

                if (existingBucketIds.contains(bucketId) && amount.compareTo(BigDecimal.ZERO) > 0) {
                    validAllocations.put(bucketId, amount);
                }
            }

            return PaymentAllocation.createBankRuleAllocation(allocationId, paymentAmount, validAllocations);
        }

        @Override
        public boolean isApplicable(List<DebtBucket> debtBuckets, BigDecimal paymentAmount) {
            return !predefinedAllocations.isEmpty();
        }

        public Map<String, BigDecimal> getPredefinedAllocations() {
            return new HashMap<>(predefinedAllocations);
        }
    }

    // ================================
    // STRATEGY FACTORY
    // ================================

    /**
     * Strategy factory method
     */
    public static AllocationStrategy createStrategy(PaymentAllocation.AllocationStrategy strategyType,
                                                    Object... parameters) {
        return switch (strategyType) {
            case BANK_RULE -> new BankRuleStrategy();
            case DP_OPTIMAL -> parameters.length > 0 && parameters[0] instanceof PaymentOptimizer ?
                    new OptimalStrategy((PaymentOptimizer) parameters[0]) :
                    new OptimalStrategy();
            case GREEDY -> parameters.length > 0 && parameters[0] instanceof PaymentOptimizer ?
                    new GreedyStrategy((PaymentOptimizer) parameters[0]) :
                    new GreedyStrategy();
            case MANUAL -> {
                if (parameters.length > 0 && parameters[0] instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, BigDecimal> allocations = (Map<String, BigDecimal>) parameters[0];
                    yield new ManualStrategy(allocations);
                }
                throw new IllegalArgumentException("Manual strategy requires predefined allocations map");
            }
        };
    }
}