import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Payment Allocation Demo & Test Utility
 *
 * DP Payment Allocation modülünü test etmek ve showcase etmek için.
 * Farklı senaryoları simüle eder ve performance benchmark yapar.
 */
public final class PaymentAllocationDemo {

    private final PaymentAllocationService service;
    private final Random random;

    public PaymentAllocationDemo() {
        this.service = new PaymentAllocationService();
        this.random = new Random(12345); // Reproducible results
    }

    /**
     * Ana demo method - tüm senaryoları çalıştırır
     */
    public void runAllDemos() {
        System.out.println("🎯 PAYMENT ALLOCATION DP MODULE DEMO");
        System.out.println("=====================================");

        try {
            // Demo 1: Basic scenario
            System.out.println("\n1️⃣ BASIC ALLOCATION DEMO");
            basicAllocationDemo();

            // Demo 2: Strategy comparison
            System.out.println("\n2️⃣ STRATEGY COMPARISON DEMO");
            strategyComparisonDemo();

            // Demo 3: DP vs Bank Rule
            System.out.println("\n3️⃣ DP vs BANK RULE COMPARISON");
            dpVsBankRuleDemo();

            // Demo 4: Granularity impact
            System.out.println("\n4️⃣ GRANULARITY IMPACT TEST");
            granularityImpactDemo();

            // Demo 5: Edge cases
            System.out.println("\n5️⃣ EDGE CASES & EXCEPTION HANDLING");
            edgeCasesDemo();

            // Demo 6: Performance benchmark
            System.out.println("\n6️⃣ PERFORMANCE BENCHMARK");
            performanceBenchmarkDemo();

            System.out.println("\n✅ ALL DEMOS COMPLETED SUCCESSFULLY!");

        } catch (Exception e) {
            System.err.println("❌ Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Demo 1: Temel tahsis senaryosu
     */
    private void basicAllocationDemo() {
        String accountId = "ACC_001";

        // Örnek debt bucket'lar oluştur
        List<DebtBucket> buckets = createSampleBuckets();
        service.setAccountBuckets(accountId, buckets);

        System.out.println("📋 Debt Buckets:");
        buckets.forEach(bucket -> System.out.println("  " + bucket));

        BigDecimal paymentAmount = new BigDecimal("1500.00");
        System.out.println("\n💰 Payment Amount: " + paymentAmount + " TL");

        // DP Optimal allocation
        PaymentAllocation allocation = service.allocatePayment(accountId, paymentAmount,
                PaymentAllocation.AllocationStrategy.DP_OPTIMAL);

        System.out.println("\n🎯 DP Optimal Allocation:");
        System.out.println("  Strategy: " + allocation.getStrategy());
        System.out.println("  Total Interest Saved: " + allocation.getTotalInterestSaved() + " TL");
        System.out.println("  Allocated to " + allocation.getAllocatedBucketCount() + " buckets:");

        for (Map.Entry<String, BigDecimal> entry : allocation.getBucketAllocations().entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                System.out.println("    " + entry.getKey() + ": " + entry.getValue() + " TL");
            }
        }

        System.out.println("  Remaining Payment: " + allocation.getRemainingPayment() + " TL");
        System.out.println("  Computation Time: " + allocation.getMetrics().getComputationTimeMs() + "ms");
    }

    /**
     * Demo 2: Tüm stratejileri karşılaştır
     */
    private void strategyComparisonDemo() {
        String accountId = "ACC_002";
        List<DebtBucket> buckets = createComplexBuckets();
        service.setAccountBuckets(accountId, buckets);

        BigDecimal paymentAmount = new BigDecimal("2000.00");
        PaymentAllocationService.StrategyComparison comparison =
                service.compareAllStrategies(accountId, paymentAmount);

        System.out.println("📊 Strategy Comparison Results:");
        System.out.println("  Payment Amount: " + paymentAmount + " TL");
        System.out.println("  Best Strategy: " + comparison.getBestStrategy());

        Map<PaymentAllocation.AllocationStrategy, PaymentAllocation> results = comparison.getResults();

        System.out.println("\n  Results by Strategy:");
        for (Map.Entry<PaymentAllocation.AllocationStrategy, PaymentAllocation> entry : results.entrySet()) {
            PaymentAllocation allocation = entry.getValue();
            System.out.printf("    %-12s: Interest Saved = %6.2f TL, Buckets = %d\n",
                    entry.getKey(),
                    allocation.getTotalInterestSaved().doubleValue(),
                    allocation.getAllocatedBucketCount());
        }

        if (!comparison.getErrors().isEmpty()) {
            System.out.println("  ⚠️ Strategy Errors:");
            comparison.getErrors().forEach((strategy, error) ->
                    System.out.println("    " + strategy + ": " + error.getMessage()));
        }
    }

    /**
     * Demo 3: DP vs Bank Rule detaylı karşılaştırma
     */
    private void dpVsBankRuleDemo() {
        String accountId = "ACC_003";
        List<DebtBucket> buckets = createHighInterestBuckets();
        service.setAccountBuckets(accountId, buckets);

        BigDecimal paymentAmount = new BigDecimal("3000.00");
        PaymentAllocationService.AllocationComparison comparison =
                service.compareDPvsBankRule(accountId, paymentAmount);

        System.out.println("⚔️ DP vs Bank Rule Detailed Comparison:");
        System.out.println("  Payment Amount: " + paymentAmount + " TL");
        System.out.println("  DP is Better: " + (comparison.isDpBetter() ? "✅ YES" : "❌ NO"));
        System.out.println("  Interest Savings Difference: " + comparison.getInterestSavingsDifference() + " TL");
        System.out.printf("  Improvement: %.2f%%\n", comparison.getImprovementPercentage());

        System.out.println("\n  📈 Bank Rule Allocation:");
        printAllocationDetails(comparison.getBankRuleAllocation());

        System.out.println("\n  🎯 DP Optimal Allocation:");
        printAllocationDetails(comparison.getDpOptimalAllocation());
    }

    /**
     * Demo 4: Granularite seviyesinin etkisi
     */
    private void granularityImpactDemo() {
        String accountId = "ACC_004";
        List<DebtBucket> buckets = createSampleBuckets();
        service.setAccountBuckets(accountId, buckets);

        BigDecimal paymentAmount = new BigDecimal("1000.00");

        System.out.println("🔬 Granularity Impact Analysis:");
        System.out.println("  Payment Amount: " + paymentAmount + " TL");

        int[] granularities = {1, 10, 100, 1000}; // 1TL, 10kr, 1kr, 0.1kr

        for (int granularity : granularities) {
            PaymentOptimizer optimizer = new PaymentOptimizer(granularity);
            PaymentAllocationService testService = new PaymentAllocationService(optimizer);
            testService.setAccountBuckets(accountId, buckets);

            long startTime = System.currentTimeMillis();
            PaymentAllocation allocation = testService.allocatePayment(accountId, paymentAmount,
                    PaymentAllocation.AllocationStrategy.DP_OPTIMAL);
            long duration = System.currentTimeMillis() - startTime;

            System.out.printf("    Granularity %4d: Time=%3dms, Interest Saved=%6.2f TL, Buckets=%d\n",
                    granularity, duration,
                    allocation.getTotalInterestSaved().doubleValue(),
                    allocation.getAllocatedBucketCount());
        }
    }

    /**
     * Demo 5: Edge case'ler ve exception handling
     */
    private void edgeCasesDemo() {
        System.out.println("🧪 Edge Cases Testing:");

        // Case 1: Empty buckets
        testEdgeCase("Empty buckets", () -> {
            service.setAccountBuckets("EDGE_001", new ArrayList<>());
            return service.allocatePayment("EDGE_001", new BigDecimal("100"),
                    PaymentAllocation.AllocationStrategy.DP_OPTIMAL);
        });

        // Case 2: Zero payment
        testEdgeCase("Zero payment", () -> {
            List<DebtBucket> buckets = createSampleBuckets();
            service.setAccountBuckets("EDGE_002", buckets);
            return service.allocatePayment("EDGE_002", BigDecimal.ZERO,
                    PaymentAllocation.AllocationStrategy.BANK_RULE);
        });

        // Case 3: Payment exceeds total debt
        testEdgeCase("Payment exceeds total debt", () -> {
            List<DebtBucket> buckets = List.of(
                    DebtBucket.createPurchaseBucket("SMALL_1", new BigDecimal("50"), LocalDate.now().plusDays(30))
            );
            service.setAccountBuckets("EDGE_003", buckets);
            return service.allocatePayment("EDGE_003", new BigDecimal("1000"),
                    PaymentAllocation.AllocationStrategy.DP_OPTIMAL);
        });

        // Case 4: Minimum payment validation
        testEdgeCase("Manual allocation overflow", () -> {
            List<DebtBucket> buckets = createSampleBuckets();
            service.setAccountBuckets("EDGE_004", buckets);

            // Try to allocate more than bucket capacity
            Map<String, BigDecimal> manualAllocations = new HashMap<>();
            manualAllocations.put("PURCHASE_001", new BigDecimal("10000")); // Way too much

            AllocationStrategy manualStrategy = new AllocationStrategy.ManualStrategy(manualAllocations);
            return manualStrategy.allocate(buckets, new BigDecimal("500"), "MANUAL_001");
        });
    }

    /**
     * Demo 6: Performance benchmark - büyük ölçekli test
     */
    private void performanceBenchmarkDemo() {
        System.out.println("⚡ Performance Benchmark:");

        int[] bucketCounts = {5, 10, 20, 50};
        BigDecimal basePayment = new BigDecimal("5000");

        for (int bucketCount : bucketCounts) {
            String accountId = "PERF_" + bucketCount;
            List<DebtBucket> buckets = createRandomBuckets(bucketCount);
            service.setAccountBuckets(accountId, buckets);

            // Benchmark DP strategy
            long startTime = System.nanoTime();
            PaymentAllocation dpResult = service.allocatePayment(accountId, basePayment,
                    PaymentAllocation.AllocationStrategy.DP_OPTIMAL);
            long dpTime = System.nanoTime() - startTime;

            // Benchmark Bank Rule strategy  
            startTime = System.nanoTime();
            PaymentAllocation bankResult = service.allocatePayment(accountId, basePayment,
                    PaymentAllocation.AllocationStrategy.BANK_RULE);
            long bankTime = System.nanoTime() - startTime;

            System.out.printf("  %2d buckets: DP=%4.1fms (saved=%.2f), Bank=%3.1fms (saved=%.2f), Ratio=%.1fx\n",
                    bucketCount,
                    dpTime / 1_000_000.0, dpResult.getTotalInterestSaved().doubleValue(),
                    bankTime / 1_000_000.0, bankResult.getTotalInterestSaved().doubleValue(),
                    (double) dpTime / bankTime);
        }

        // System statistics
        System.out.println("\n📊 System Statistics:");
        Map<String, Object> stats = service.getSystemStatistics();
        stats.forEach((key, value) -> System.out.println("  " + key + ": " + value));
    }

    // Helper methods
    private List<DebtBucket> createSampleBuckets() {
        return List.of(
                DebtBucket.createPurchaseBucket("PURCHASE_001", new BigDecimal("1200.50"), LocalDate.now().plusDays(30)),
                DebtBucket.createCashAdvanceBucket("CASH_001", new BigDecimal("800.00"), LocalDate.now().plusDays(25)),
                new DebtBucket("INSTALLMENT_001", DebtBucket.BucketType.INSTALLMENT, new BigDecimal("2000.00"),
                        new BigDecimal("0.15"), new BigDecimal("200.00"), LocalDate.now().plusDays(35), 3),
                DebtBucket.createOverdueBucket("OVERDUE_001", new BigDecimal("300.00"), LocalDate.now().minusDays(5))
        );
    }

    private List<DebtBucket> createComplexBuckets() {
        return List.of(
                DebtBucket.createPurchaseBucket("PURCH_001", new BigDecimal("1500.00"), LocalDate.now().plusDays(30)),
                DebtBucket.createPurchaseBucket("PURCH_002", new BigDecimal("800.00"), LocalDate.now().plusDays(25)),
                DebtBucket.createCashAdvanceBucket("CASH_001", new BigDecimal("600.00"), LocalDate.now().plusDays(20)),
                new DebtBucket("INSTALL_001", DebtBucket.BucketType.INSTALLMENT, new BigDecimal("3000.00"),
                        new BigDecimal("0.12"), new BigDecimal("300.00"), LocalDate.now().plusDays(60), 3),
                new DebtBucket("FEES_001", DebtBucket.BucketType.FEES_INTEREST, new BigDecimal("150.00"),
                        new BigDecimal("0.30"), new BigDecimal("150.00"), LocalDate.now().plusDays(15), 4),
                DebtBucket.createOverdueBucket("OVERDUE_001", new BigDecimal("450.00"), LocalDate.now().minusDays(10))
        );
    }

    private List<DebtBucket> createHighInterestBuckets() {
        return List.of(
                new DebtBucket("HIGH_RATE_1", DebtBucket.BucketType.CASH_ADVANCE, new BigDecimal("2000.00"),
                        new BigDecimal("0.35"), new BigDecimal("200.00"), LocalDate.now().plusDays(20), 2),
                new DebtBucket("HIGH_RATE_2", DebtBucket.BucketType.FEES_INTEREST, new BigDecimal("800.00"),
                        new BigDecimal("0.45"), new BigDecimal("800.00"), LocalDate.now().plusDays(10), 4),
                DebtBucket.createPurchaseBucket("LOW_RATE_1", new BigDecimal("3000.00"), LocalDate.now().plusDays(45)),
                DebtBucket.createOverdueBucket("OVERDUE_HIGH", new BigDecimal("1200.00"), LocalDate.now().minusDays(15))
        );
    }

    private List<DebtBucket> createRandomBuckets(int count) {
        List<DebtBucket> buckets = new ArrayList<>();
        DebtBucket.BucketType[] types = DebtBucket.BucketType.values();

        for (int i = 0; i < count; i++) {
            DebtBucket.BucketType type = types[random.nextInt(types.length)];
            BigDecimal balance = new BigDecimal(100 + random.nextInt(4900)); // 100-5000 TL
            LocalDate dueDate = LocalDate.now().plusDays(random.nextInt(60) - 10); // -10 to +50 days

            String bucketId = type.name() + "_" + String.format("%03d", i);

            switch (type) {
                case PURCHASE -> buckets.add(DebtBucket.createPurchaseBucket(bucketId, balance, dueDate));
                case CASH_ADVANCE -> buckets.add(DebtBucket.createCashAdvanceBucket(bucketId, balance, dueDate));
                case OVERDUE -> buckets.add(DebtBucket.createOverdueBucket(bucketId, balance, dueDate));
                default -> {
                    BigDecimal rate = new BigDecimal("0.10").add(new BigDecimal(random.nextDouble() * 0.30)); // 10-40%
                    BigDecimal minPayment = balance.multiply(new BigDecimal("0.05")); // 5% minimum
                    buckets.add(new DebtBucket(bucketId, type, balance, rate, minPayment, dueDate, type.getDefaultPriority()));
                }
            }
        }

        return buckets;
    }

    private void printAllocationDetails(PaymentAllocation allocation) {
        System.out.println("    Strategy: " + allocation.getStrategy());
        System.out.println("    Total Interest Saved: " + allocation.getTotalInterestSaved() + " TL");
        System.out.println("    Bucket Allocations:");

        allocation.getBucketAllocations().entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .forEach(entry -> System.out.println("      " + entry.getKey() + ": " + entry.getValue() + " TL"));
    }

    private void testEdgeCase(String caseName, java.util.concurrent.Callable<PaymentAllocation> testCase) {
        System.out.print("    " + caseName + ": ");
        try {
            PaymentAllocation result = testCase.call();
            System.out.println("✅ SUCCESS - " + result.getStrategy());
        } catch (AllocationInvariantBroken e) {
            System.out.println("⚠️ CAUGHT AllocationInvariantBroken - " + e.getViolationType());
        } catch (Exception e) {
            System.out.println("❌ CAUGHT " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    /**
     * Main method - demo çalıştırıcı
     */
    public static void main(String[] args) {
        PaymentAllocationDemo demo = new PaymentAllocationDemo();
        demo.runAllDemos();
    }

    @Override
    public String toString() {
        return "PaymentAllocationDemo{service=" + service + "}";
    }
}