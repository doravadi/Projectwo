import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;


public final class FraudDetectionDemo {

    private final FraudDetectionService fraudService;
    private final Random random;


    private static final String[] CARD_NUMBERS = {
            "1234567890123456", "2345678901234567", "3456789012345678",
            "4567890123456789", "5678901234567890"
    };

    private static final String[] MERCHANTS = {
            "STARBUCKS", "MCDONALDS", "SHELL_GAS", "AMAZON", "GROCERY_STORE",
            "ATM_WITHDRAW", "ONLINE_SHOP", "RESTAURANT", "PHARMACY", "TAXI"
    };

    private static final LocationData[] LOCATIONS = {
            new LocationData("Istanbul", "TR", 41.0082, 28.9784),
            new LocationData("Ankara", "TR", 39.9334, 32.8597),
            new LocationData("London", "UK", 51.5074, -0.1278),
            new LocationData("Paris", "FR", 48.8566, 2.3522),
            new LocationData("New York", "US", 40.7128, -74.0060),
            new LocationData("Tokyo", "JP", 35.6762, 139.6503),
            new LocationData("Berlin", "DE", 52.5200, 13.4050)
    };

    public FraudDetectionDemo() {
        this.fraudService = FraudDetectionService.createDefault();
        this.random = new Random(42);
    }


    public void runAllDemos() {
        System.out.println("🕵️ FRAUD DETECTION MODULE DEMO");
        System.out.println("================================");

        try {

            System.out.println("\n1️⃣ NORMAL TRANSACTION PATTERNS");
            demonstrateNormalTransactions();


            System.out.println("\n2️⃣ DUPLICATE TRANSACTION DETECTION");
            demonstrateDuplicateDetection();


            System.out.println("\n3️⃣ IMPOSSIBLE LOCATION JUMPS");
            demonstrateLocationJumping();


            System.out.println("\n4️⃣ HIGH VELOCITY ATTACKS");
            demonstrateVelocityAttacks();


            System.out.println("\n5️⃣ PATTERN-BASED FRAUD");
            demonstratePatternFraud();


            System.out.println("\n6️⃣ MIXED ATTACK SCENARIOS");
            demonstrateMixedAttacks();


            System.out.println("\n7️⃣ PERFORMANCE BENCHMARKS");
            demonstratePerformance();


            System.out.println("\n8️⃣ FRAUD DETECTION STATISTICS");
            showStatistics();

            System.out.println("\n✅ ALL FRAUD DETECTION DEMOS COMPLETED!");

        } catch (Exception e) {
            System.err.println("❌ Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void demonstrateNormalTransactions() {
        System.out.println("📊 Testing normal spending patterns...");

        String cardNumber = CARD_NUMBERS[0];
        LocalDateTime startTime = LocalDateTime.now().minusHours(24);


        for (int i = 0; i < 20; i++) {
            LocalDateTime txnTime = startTime.plusMinutes(i * 60 + random.nextInt(30));
            LocationData location = LOCATIONS[random.nextInt(3)];
            String merchant = MERCHANTS[random.nextInt(MERCHANTS.length)];
            BigDecimal amount = new BigDecimal(50 + random.nextInt(200));

            String txnId = "NORMAL_" + String.format("%03d", i);

            FraudDetectionService.FraudAnalysisResult result = fraudService.analyzeTransaction(
                    txnId, cardNumber, amount, merchant, location.city, location.country,
                    location.latitude, location.longitude, txnTime,
                    TransactionRisk.TransactionType.PURCHASE);

            if (result.hasAlert()) {
                System.out.printf("  ⚠️ Alert on normal transaction %s: %s\n",
                        txnId, result.getFraudAlert().getSeverity());
            }
        }

        System.out.println("  ✅ Normal pattern test completed");
    }


    private void demonstrateDuplicateDetection() {
        System.out.println("🔄 Testing duplicate transaction detection...");

        String cardNumber = CARD_NUMBERS[1];
        LocationData location = LOCATIONS[0];
        String merchant = "COFFEE_SHOP";
        BigDecimal amount = new BigDecimal("45.50");
        LocalDateTime baseTime = LocalDateTime.now();


        FraudDetectionService.FraudAnalysisResult originalResult = fraudService.analyzeTransaction(
                "DUP_001", cardNumber, amount, merchant, location.city, location.country,
                location.latitude, location.longitude, baseTime,
                TransactionRisk.TransactionType.PURCHASE);

        System.out.printf("  Original transaction: Alert=%s\n",
                originalResult.hasAlert() ? "YES" : "NO");


        FraudDetectionService.FraudAnalysisResult duplicateResult = fraudService.analyzeTransaction(
                "DUP_002", cardNumber, amount, merchant, location.city, location.country,
                location.latitude, location.longitude, baseTime.plusMinutes(2),
                TransactionRisk.TransactionType.PURCHASE);

        System.out.printf("  Duplicate transaction: Alert=%s, PossibleDup=%s\n",
                duplicateResult.hasAlert() ? "YES" : "NO",
                duplicateResult.isPossibleDuplicate() ? "YES" : "NO");

        if (duplicateResult.hasAlert()) {
            System.out.printf("  🚨 Alert Details: %s (Score: %d)\n",
                    duplicateResult.getFraudAlert().getSeverity(),
                    duplicateResult.getFraudAlert().getCompositeRiskScore());
        }

        System.out.println("  ✅ Duplicate detection test completed");
    }


    private void demonstrateLocationJumping() {
        System.out.println("🌍 Testing impossible location jumps...");

        String cardNumber = CARD_NUMBERS[2];
        LocalDateTime startTime = LocalDateTime.now();


        LocationData istanbul = LOCATIONS[0];
        FraudDetectionService.FraudAnalysisResult result1 = fraudService.analyzeTransaction(
                "LOC_001", cardNumber, new BigDecimal("100"), "RESTAURANT",
                istanbul.city, istanbul.country, istanbul.latitude, istanbul.longitude,
                startTime, TransactionRisk.TransactionType.PURCHASE);

        System.out.printf("  Istanbul transaction: Alert=%s\n", result1.hasAlert() ? "YES" : "NO");


        LocationData london = LOCATIONS[2];
        FraudDetectionService.FraudAnalysisResult result2 = fraudService.analyzeTransaction(
                "LOC_002", cardNumber, new BigDecimal("75"), "PUB",
                london.city, london.country, london.latitude, london.longitude,
                startTime.plusMinutes(5), TransactionRisk.TransactionType.PURCHASE);

        System.out.printf("  London transaction (5min later): Alert=%s\n", result2.hasAlert() ? "YES" : "NO");

        if (result2.hasAlert()) {
            System.out.printf("  🚨 Location Alert: %s (Score: %d)\n",
                    result2.getFraudAlert().getSeverity(),
                    result2.getFraudAlert().getCompositeRiskScore());

            if (result2.getLocationAnalysis() != null) {
                System.out.printf("  📍 Distance: %.1f km, Speed: %.1f km/h\n",
                        result2.getLocationAnalysis().getDistanceKm(),
                        result2.getLocationAnalysis().getSpeedKmh());
            }
        }

        System.out.println("  ✅ Location jumping test completed");
    }


    private void demonstrateVelocityAttacks() {
        System.out.println("⚡ Testing high velocity attacks...");

        String cardNumber = CARD_NUMBERS[3];
        LocationData location = LOCATIONS[0];
        LocalDateTime startTime = LocalDateTime.now();
        int alertCount = 0;


        for (int i = 0; i < 20; i++) {
            LocalDateTime txnTime = startTime.plusSeconds(i * 15);
            String merchant = MERCHANTS[random.nextInt(MERCHANTS.length)];
            BigDecimal amount = new BigDecimal(20 + random.nextInt(50));

            String txnId = "VEL_" + String.format("%03d", i);

            FraudDetectionService.FraudAnalysisResult result = fraudService.analyzeTransaction(
                    txnId, cardNumber, amount, merchant, location.city, location.country,
                    location.latitude, location.longitude, txnTime,
                    TransactionRisk.TransactionType.PURCHASE);

            if (result.hasAlert()) {
                alertCount++;
                if (alertCount <= 3) {
                    System.out.printf("  🚨 Alert #%d on %s: %s (Score: %d)\n",
                            alertCount, txnId, result.getFraudAlert().getSeverity(),
                            result.getFraudAlert().getCompositeRiskScore());

                    if (result.getWindowAnalysis() != null) {
                        System.out.printf("      Velocity: %.2f txns/min, Risk Pattern Score: %d\n",
                                result.getWindowAnalysis().getTransactionsPerMinute(),
                                result.getWindowAnalysis().getRiskPattern().getRiskScore());
                    }
                }
            }
        }

        System.out.printf("  📊 Velocity Attack Results: %d/20 transactions triggered alerts\n", alertCount);
        System.out.println("  ✅ Velocity attack test completed");
    }


    private void demonstratePatternFraud() {
        System.out.println("🎭 Testing pattern-based fraud...");

        String cardNumber = CARD_NUMBERS[4];
        LocationData location = LOCATIONS[0];
        LocalDateTime startTime = LocalDateTime.now().withHour(2);


        System.out.println("  Testing sequential amount pattern...");
        for (int i = 1; i <= 10; i++) {
            BigDecimal amount = new BigDecimal(i * 10);
            String txnId = "PAT_SEQ_" + String.format("%02d", i);

            FraudDetectionService.FraudAnalysisResult result = fraudService.analyzeTransaction(
                    txnId, cardNumber, amount, "TEST_MERCHANT", location.city, location.country,
                    location.latitude, location.longitude, startTime.plusMinutes(i),
                    TransactionRisk.TransactionType.PURCHASE);

            if (result.hasAlert() && result.getWindowAnalysis() != null) {
                RiskWindow.RiskPattern pattern = result.getWindowAnalysis().getRiskPattern();
                if (pattern.hasPattern(RiskWindow.RiskPattern.PatternType.SEQUENTIAL_AMOUNTS)) {
                    System.out.printf("    🎯 Sequential pattern detected on transaction %s\n", txnId);
                }
            }
        }


        System.out.println("  Testing round amount pattern...");
        BigDecimal[] roundAmounts = {
                new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("50"),
                new BigDecimal("300"), new BigDecimal("150"), new BigDecimal("250")
        };

        for (int i = 0; i < roundAmounts.length; i++) {
            String txnId = "PAT_ROUND_" + String.format("%02d", i);

            FraudDetectionService.FraudAnalysisResult result = fraudService.analyzeTransaction(
                    txnId, cardNumber, roundAmounts[i], "ATM_MACHINE", location.city, location.country,
                    location.latitude, location.longitude, startTime.plusMinutes(20 + i * 2),
                    TransactionRisk.TransactionType.WITHDRAWAL);

            if (result.hasAlert() && result.getWindowAnalysis() != null) {
                RiskWindow.RiskPattern pattern = result.getWindowAnalysis().getRiskPattern();
                if (pattern.hasPattern(RiskWindow.RiskPattern.PatternType.ROUND_AMOUNTS)) {
                    System.out.printf("    💰 Round amount pattern detected\n");
                }
                if (pattern.hasPattern(RiskWindow.RiskPattern.PatternType.NIGHT_ACTIVITY)) {
                    System.out.printf("    🌙 Night activity pattern detected\n");
                }
            }
        }

        System.out.println("  ✅ Pattern-based fraud test completed");
    }


    private void demonstrateMixedAttacks() {
        System.out.println("🎯 Testing mixed attack scenarios...");

        String cardNumber = "9999888877776666";


        System.out.println("  Scenario: Location jump + Duplicate + High velocity");

        LocalDateTime startTime = LocalDateTime.now();


        FraudDetectionService.FraudAnalysisResult result1 = fraudService.analyzeTransaction(
                "MIX_001", cardNumber, new BigDecimal("500"), "LUXURY_STORE",
                "Istanbul", "TR", 41.0082, 28.9784, startTime,
                TransactionRisk.TransactionType.PURCHASE);


        FraudDetectionService.FraudAnalysisResult result2 = fraudService.analyzeTransaction(
                "MIX_002", cardNumber, new BigDecimal("500"), "LUXURY_STORE",
                "Paris", "FR", 48.8566, 2.3522, startTime.plusMinutes(3),
                TransactionRisk.TransactionType.PURCHASE);


        FraudDetectionService.FraudAnalysisResult result3 = fraudService.analyzeTransaction(
                "MIX_003", cardNumber, new BigDecimal("500"), "LUXURY_STORE",
                "Paris", "FR", 48.8566, 2.3522, startTime.plusMinutes(4),
                TransactionRisk.TransactionType.PURCHASE);

        System.out.printf("    Transaction 1 (Istanbul): Alert=%s\n",
                result1.hasAlert() ? result1.getFraudAlert().getSeverity() : "NONE");
        System.out.printf("    Transaction 2 (Paris, 3min): Alert=%s\n",
                result2.hasAlert() ? result2.getFraudAlert().getSeverity() : "NONE");
        System.out.printf("    Transaction 3 (Duplicate): Alert=%s, Recommendation=%s\n",
                result3.hasAlert() ? result3.getFraudAlert().getSeverity() : "NONE",
                result3.getRecommendation());

        if (result3.hasAlert()) {
            System.out.printf("    🔥 Final Alert Score: %d, Reasons: %d\n",
                    result3.getFraudAlert().getCompositeRiskScore(),
                    result3.getFraudAlert().getReasons().size());
        }

        System.out.println("  ✅ Mixed attack scenarios completed");
    }


    private void demonstratePerformance() {
        System.out.println("🚀 Testing performance benchmarks...");

        int[] transactionCounts = {100, 500, 1000, 5000};

        for (int count : transactionCounts) {
            long startTime = System.nanoTime();


            for (int i = 0; i < count; i++) {
                String cardNumber = CARD_NUMBERS[random.nextInt(CARD_NUMBERS.length)];
                LocationData location = LOCATIONS[random.nextInt(LOCATIONS.length)];
                String merchant = MERCHANTS[random.nextInt(MERCHANTS.length)];
                BigDecimal amount = new BigDecimal(10 + random.nextInt(500));
                LocalDateTime txnTime = LocalDateTime.now().minusMinutes(random.nextInt(1440));

                String txnId = "PERF_" + i;

                fraudService.analyzeTransaction(txnId, cardNumber, amount, merchant,
                        location.city, location.country, location.latitude, location.longitude,
                        txnTime, TransactionRisk.TransactionType.PURCHASE);
            }

            long endTime = System.nanoTime();
            double durationMs = (endTime - startTime) / 1_000_000.0;
            double avgPerTransaction = durationMs / count;
            double throughput = count / (durationMs / 1000.0);

            System.out.printf("    %,5d transactions: %.1f ms total, %.3f ms/txn, %.0f txn/sec\n",
                    count, durationMs, avgPerTransaction, throughput);
        }

        System.out.println("  ✅ Performance benchmark completed");
    }


    private void showStatistics() {
        System.out.println("📈 Comprehensive Fraud Detection Statistics:");

        FraudDetectionService.FraudDetectionStatistics stats = fraudService.getStatistics();

        System.out.printf("  📊 Transactions Processed: %,d\n", stats.getTotalTransactionsProcessed());
        System.out.printf("  🚨 Total Alerts Generated: %,d (%.2f%% alert rate)\n",
                stats.getTotalAlertsGenerated(), stats.getAlertRate());
        System.out.printf("  💳 Active Card Windows: %,d\n", stats.getActiveCardWindows());

        System.out.println("\n  📋 Alert Breakdown by Severity:");
        for (Map.Entry<FraudAlert.AlertSeverity, Long> entry : stats.getAlertCounts().entrySet()) {
            System.out.printf("    %-8s: %,3d alerts\n", entry.getKey(), entry.getValue());
        }

        System.out.println("\n  🔍 Bloom Filter Statistics:");
        BloomFilter.BloomFilterStatistics bloomStats = stats.getBloomFilterStats();
        System.out.printf("    Elements: %,d, Utilization: %.1f%%, Est. FPR: %.3f%%\n",
                bloomStats.getElementCount(), bloomStats.getMemoryUtilization(),
                bloomStats.getEstimatedFPR() * 100);
        System.out.printf("    Performance: %s\n", bloomStats.getPerformanceAssessment());

        System.out.println("\n  📍 Location Tracker Statistics:");
        LocationTracker.TrackerStatistics locationStats = stats.getLocationStats();
        System.out.printf("    Entries: %,d, Unique Cards: %,d, Unique Cities: %,d\n",
                locationStats.getTotalEntries(), locationStats.getUniqueCards(),
                locationStats.getUniqueCities());

        System.out.println("\n  🎯 Recent High-Priority Alerts:");
        List<FraudAlert> highSeverityAlerts = fraudService.getHighSeverityAlerts();
        int alertsToShow = Math.min(5, highSeverityAlerts.size());

        for (int i = 0; i < alertsToShow; i++) {
            FraudAlert alert = highSeverityAlerts.get(i);
            System.out.printf("    %s: %s (Score: %d, Reasons: %d) - %s\n",
                    alert.getAlertId().substring(alert.getAlertId().length() - 8),
                    alert.getSeverity(), alert.getCompositeRiskScore(),
                    alert.getReasons().size(),
                    alert.getMaskedCardNumber());
        }

        if (alertsToShow == 0) {
            System.out.println("    No high-severity alerts found");
        }
    }


    private static final class LocationData {
        final String city;
        final String country;
        final double latitude;
        final double longitude;

        LocationData(String city, String country, double latitude, double longitude) {
            this.city = city;
            this.country = country;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }


    public void runInteractiveDemo() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("🕵️ INTERACTIVE FRAUD DETECTION DEMO");
        System.out.println("Enter transaction details to test fraud detection:");

        while (true) {
            try {
                System.out.print("\nCard Number (or 'quit'): ");
                String input = scanner.nextLine().trim();
                if ("quit".equalsIgnoreCase(input)) break;

                System.out.print("Amount (TL): ");
                BigDecimal amount = new BigDecimal(scanner.nextLine().trim());

                System.out.print("Merchant: ");
                String merchant = scanner.nextLine().trim();

                System.out.print("Location (city,country): ");
                String[] locationParts = scanner.nextLine().trim().split(",");

                LocationData selectedLocation = LOCATIONS[0];
                for (LocationData loc : LOCATIONS) {
                    if (loc.city.equalsIgnoreCase(locationParts[0].trim())) {
                        selectedLocation = loc;
                        break;
                    }
                }

                String txnId = "INTERACTIVE_" + System.currentTimeMillis();

                FraudDetectionService.FraudAnalysisResult result = fraudService.analyzeTransaction(
                        txnId, input, amount, merchant, selectedLocation.city, selectedLocation.country,
                        selectedLocation.latitude, selectedLocation.longitude, LocalDateTime.now(),
                        TransactionRisk.TransactionType.PURCHASE);

                System.out.println("\n🔍 ANALYSIS RESULTS:");
                System.out.printf("Risk Score: %d\n", result.getTransactionRisk().getRiskScore());
                System.out.printf("Alert: %s\n", result.hasAlert() ? result.getFraudAlert().getSeverity() : "NONE");
                System.out.printf("Recommendation: %s\n", result.getRecommendation());
                System.out.printf("Possible Duplicate: %s\n", result.isPossibleDuplicate() ? "YES" : "NO");

                if (result.hasAlert()) {
                    System.out.printf("Alert Reasons (%d): %s\n",
                            result.getFraudAlert().getReasons().size(),
                            result.getFraudAlert().getReasons());
                }

            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }

        scanner.close();
        System.out.println("👋 Interactive demo ended");
    }


    public static void main(String[] args) {
        FraudDetectionDemo demo = new FraudDetectionDemo();

        if (args.length > 0 && "interactive".equals(args[0])) {
            demo.runInteractiveDemo();
        } else {
            demo.runAllDemos();
        }
    }

    @Override
    public String toString() {
        return "FraudDetectionDemo{service=" + fraudService + "}";
    }
}