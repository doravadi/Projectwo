
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;


public final class RuleDSLDemo {

    private final RuleEngine ruleEngine;
    private final Random random;

    public RuleDSLDemo() {
        this.ruleEngine = new RuleEngine();
        this.random = new Random(12345); 
    }

    
    public void runAllDemos() {
        System.out.println("🎯 DSL RULE ENGINE MODULE DEMO");
        System.out.println("==============================");

        try {
            
            System.out.println("\n1️⃣ BASIC DSL PARSING & EVALUATION");
            basicDSLDemo();

            
            System.out.println("\n2️⃣ DIFFERENT RULE TYPES DEMO");
            ruleTypesDemo();

            
            System.out.println("\n3️⃣ COMPLEX CONDITIONS & LOGIC");
            complexConditionsDemo();

            
            System.out.println("\n4️⃣ REAL-WORLD BANKING SCENARIOS");
            bankingScenariosDemo();

            
            System.out.println("\n5️⃣ ERROR HANDLING & EDGE CASES");
            errorHandlingDemo();

            
            System.out.println("\n6️⃣ PERFORMANCE BENCHMARK");
            performanceBenchmarkDemo();

            
            System.out.println("\n7️⃣ ENGINE STATISTICS");
            statisticsDemo();

            System.out.println("\n✅ ALL DSL RULE ENGINE DEMOS COMPLETED!");

        } catch (Exception e) {
            System.err.println("❌ Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    
    private void basicDSLDemo() throws RuleSyntaxException {
        System.out.println("Testing basic DSL expressions...");

        
        ruleEngine.addRule("RULE_001", "amount > 1000", Rule.RuleType.REWARD, 10,
                "High amount bonus");

        
        ruleEngine.addRule("RULE_002", "mcc == GROCERY", Rule.RuleType.DISCOUNT, 5,
                "Grocery discount");

        
        ruleEngine.addRule("RULE_003", "hour >= 22 or hour <= 6", Rule.RuleType.FRAUD_CHECK, 20,
                "Night transaction check");

        
        TransactionContext context = createSampleContext(new BigDecimal("1500"),
                TransactionContext.MccCategory.GROCERY, 23);

        Map<Rule.RuleType, List<RuleResult>> results = ruleEngine.evaluateAllRules(context);

        System.out.println("Context: " + context);
        results.forEach((ruleType, ruleResults) -> {
            System.out.println("  " + ruleType + ":");
            ruleResults.forEach(result ->
                    System.out.println("    " + result.getRuleId() + " -> " +
                            (result.isApplied() ? "APPLIED" : "NOT_APPLIED")));
        });
    }

    
    private void ruleTypesDemo() throws RuleSyntaxException {
        System.out.println("Testing different rule types...");

        
        ruleEngine.addRule("REWARD_001", "mcc in [GROCERY, FUEL] and amount > 500",
                Rule.RuleType.REWARD, 10, "Category bonus");

        ruleEngine.addRule("REWARD_002", "day in [SAT, SUN] and mcc == ENTERTAINMENT",
                Rule.RuleType.REWARD, 15, "Weekend entertainment bonus");

        
        ruleEngine.addRule("FRAUD_001", "country != 'TR' and amount > 2000",
                Rule.RuleType.FRAUD_CHECK, 50, "High foreign transaction");

        ruleEngine.addRule("FRAUD_002", "hour >= 2 and hour <= 5 and amount > 1000",
                Rule.RuleType.FRAUD_CHECK, 30, "Late night high amount");

        
        ruleEngine.addRule("DISCOUNT_001", "customer_segment == 'PREMIUM' and amount > 1000",
                Rule.RuleType.DISCOUNT, 5, "Premium customer discount");

        
        TransactionContext[] testContexts = {
                createSampleContext(new BigDecimal("800"), TransactionContext.MccCategory.GROCERY, 14), 
                createWeekendContext(new BigDecimal("300"), TransactionContext.MccCategory.ENTERTAINMENT), 
                createForeignContext(new BigDecimal("2500"), "US"), 
                createNightContext(new BigDecimal("1200"), 3) 
        };

        for (int i = 0; i < testContexts.length; i++) {
            System.out.println("Scenario " + (i + 1) + ":");
            Map<Rule.RuleType, List<RuleResult>> results = ruleEngine.evaluateAllRules(testContexts[i]);
            printRuleResults(results);
        }
    }

    
    private void complexConditionsDemo() throws RuleSyntaxException {
        System.out.println("Testing complex logical conditions...");

        
        ruleEngine.addRule("COMPLEX_001",
                "(mcc == GROCERY and amount > 500) or (mcc == FUEL and amount > 300)",
                Rule.RuleType.REWARD, 10, "Multi-category bonus");

        
        ruleEngine.addRule("COMPLEX_002",
                "not (country == 'TR' and customer_age >= 65) and amount > 1000",
                Rule.RuleType.ALERT, 5, "Non-senior foreign high amount");

        
        ruleEngine.addRule("COMPLEX_003",
                "customer_segment == 'PREMIUM' and monthly_spending > 5000 and day in [MON, TUE, WED, THU, FRI]",
                Rule.RuleType.DISCOUNT, 15, "Premium weekday bonus");

        
        TransactionContext complexContext = TransactionContext.builder()
                .transactionId("TXN_COMPLEX_001")
                .cardNumber("4111111111111111")
                .customerId("CUST_001")
                .accountId("ACC_001")
                .amount(new BigDecimal("750"))
                .currency("TRY")
                .mccCategory(TransactionContext.MccCategory.GROCERY)
                .merchantCountry("TR")
                .customerAge(45)
                .customerSegment("PREMIUM")
                .monthlySpending(new BigDecimal("6000"))
                .transactionDateTime(LocalDateTime.now().withDayOfMonth(15)) 
                .transactionType(TransactionContext.TransactionType.PURCHASE)
                .build();

        System.out.println("Complex scenario test:");
        Map<Rule.RuleType, List<RuleResult>> results = ruleEngine.evaluateAllRules(complexContext);
        printRuleResults(results);
    }

    
    private void bankingScenariosDemo() throws RuleSyntaxException {
        System.out.println("Testing real-world banking scenarios...");

        
        ruleEngine.addRule("CASH_LIMIT_001", "transaction_type == CASH_ADVANCE and amount > 2000",
                Rule.RuleType.LIMIT_CHECK, 100, "Daily cash advance limit");

        
        ruleEngine.addRule("INTL_001", "country != 'TR' and amount > 500",
                Rule.RuleType.ALERT, 25, "International transaction alert");

        
        ruleEngine.addRule("STUDENT_001", "customer_segment == 'STUDENT' and mcc in [EDUCATION, ENTERTAINMENT]",
                Rule.RuleType.DISCOUNT, 8, "Student category discount");

        
        ruleEngine.addRule("HIGH_VALUE_001", "amount > 10000",
                Rule.RuleType.ALERT, 90, "High-value transaction monitoring");

        
        ruleEngine.addRule("FUEL_WEEKEND_001", "mcc == FUEL and day in [SAT, SUN]",
                Rule.RuleType.REWARD, 12, "Weekend fuel bonus");

        
        TransactionContext[] businessScenarios = {
                createBusinessContext("CASH_ADVANCE", new BigDecimal("2500")), 
                createStudentContext(TransactionContext.MccCategory.EDUCATION, new BigDecimal("200")), 
                createHighValueContext(new BigDecimal("15000")), 
                createWeekendFuelContext(new BigDecimal("150")) 
        };

        for (int i = 0; i < businessScenarios.length; i++) {
            System.out.println("Business Scenario " + (i + 1) + ":");
            System.out.println("  Context: " + businessScenarios[i]);
            Map<Rule.RuleType, List<RuleResult>> results = ruleEngine.evaluateAllRules(businessScenarios[i]);
            printRuleResults(results);
        }
    }

    
    private void errorHandlingDemo() {
        System.out.println("Testing error handling and validation...");

        String[] invalidRules = {
                "amount >", 
                "MCC in GROCERY, FUEL]", 
                "amount == 'text'", 
                "invalid_field > 100", 
                "amount >> 500", 
                "day in [MON and hour > 22", 
                "not", 
                "amount > 500 and", 
        };

        String[] validationMessages = {
                "Incomplete comparison operator",
                "Missing opening bracket",
                "Type compatibility check",
                "Invalid field name",
                "Invalid operator syntax",
                "Unbalanced parentheses",
                "Missing operand for NOT",
                "Trailing logical operator"
        };

        System.out.println("Testing invalid DSL expressions:");
        for (int i = 0; i < invalidRules.length; i++) {
            System.out.printf("  %d. %-30s -> ", i + 1, "'" + invalidRules[i] + "'");

            RuleEngine.ValidationResult result = ruleEngine.validateExpression(invalidRules[i]);
            if (!result.isValid()) {
                System.out.println("❌ " + validationMessages[i]);
                
                
            } else {
                System.out.println("✅ Unexpectedly valid!");
            }
        }

        
        System.out.println("\nTesting runtime evaluation scenarios:");
        try {
            ruleEngine.addRule("RUNTIME_ERROR_001", "amount > customer_age", Rule.RuleType.ALERT, 1,
                    "Comparing amount with age (potential type issue)");

            TransactionContext errorContext = createSampleContext(new BigDecimal("1000"),
                    TransactionContext.MccCategory.GROCERY, 14);

            List<RuleResult> results = ruleEngine.evaluateRules(Rule.RuleType.ALERT, errorContext);
            results.forEach(result -> {
                if (result.hasErrors()) {
                    System.out.println("  Runtime Error: " + result.getRuleId() + " -> " + result.getErrors());
                } else {
                    System.out.println("  Success: " + result.getRuleId() + " -> " + result.isApplied());
                }
            });

        } catch (RuleSyntaxException e) {
            System.out.println("  Parse Error: " + e.getUserFriendlyMessage());
        }
    }

    
    private void performanceBenchmarkDemo() throws RuleSyntaxException {
        System.out.println("Running performance benchmark...");

        
        String[] benchmarkRules = {
                "amount > 100",
                "mcc == GROCERY",
                "hour >= 9 and hour <= 17",
                "day in [MON, TUE, WED, THU, FRI]",
                "customer_segment == 'PREMIUM'",
                "country != 'TR' and amount > 1000",
                "mcc in [GROCERY, FUEL, RESTAURANT] and amount > 500",
                "(hour < 8 or hour > 22) and amount > 2000",
                "customer_age >= 18 and customer_age <= 65 and monthly_spending > 3000",
                "not (mcc == ONLINE and country == 'TR') and amount > 1500"
        };

        for (int i = 0; i < benchmarkRules.length; i++) {
            ruleEngine.addRule("BENCH_" + String.format("%03d", i + 1), benchmarkRules[i],
                    Rule.RuleType.REWARD, i + 1, "Benchmark rule " + (i + 1));
        }

        
        List<TransactionContext> testContexts = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            testContexts.add(createRandomContext());
        }

        
        System.out.println("  Warming up...");
        for (int i = 0; i < 100; i++) {
            ruleEngine.evaluateAllRules(testContexts.get(i % testContexts.size()));
        }

        
        System.out.println("  Running benchmark with " + testContexts.size() + " contexts...");
        long startTime = System.nanoTime();

        int totalRulesApplied = 0;
        for (TransactionContext context : testContexts) {
            Map<Rule.RuleType, List<RuleResult>> results = ruleEngine.evaluateAllRules(context);
            totalRulesApplied += results.values().stream()
                    .mapToInt(List::size)
                    .sum();
        }

        long totalTime = System.nanoTime() - startTime;

        System.out.printf("  Results:\n");
        System.out.printf("    Total Time: %.2f ms\n", totalTime / 1_000_000.0);
        System.out.printf("    Contexts Processed: %d\n", testContexts.size());
        System.out.printf("    Average Time/Context: %.3f ms\n", totalTime / 1_000_000.0 / testContexts.size());
        System.out.printf("    Total Rules Applied: %d\n", totalRulesApplied);
        System.out.printf("    Average Rules/Context: %.1f\n", (double) totalRulesApplied / testContexts.size());
    }

    
    private void statisticsDemo() {
        System.out.println("Displaying engine statistics...");

        RuleEngineStatistics stats = ruleEngine.getStatistics();
        System.out.println(stats.generateSummaryReport());

        
        System.out.println("Rule Management:");
        for (Rule.RuleType ruleType : Rule.RuleType.values()) {
            List<String> ruleIds = ruleEngine.getRuleIdsByType(ruleType);
            if (!ruleIds.isEmpty()) {
                System.out.printf("  %s: %d rules -> %s\n",
                        ruleType, ruleIds.size(), ruleIds);
            }
        }
    }

    
    private TransactionContext createSampleContext(BigDecimal amount,
                                                   TransactionContext.MccCategory mcc, int hour) {
        return TransactionContext.builder()
                .transactionId("TXN_" + System.nanoTime())
                .cardNumber("4111111111111111")
                .customerId("CUST_001")
                .accountId("ACC_001")
                .amount(amount)
                .currency("TRY")
                .mccCategory(mcc)
                .merchantName("Test Merchant")
                .merchantCity("Istanbul")
                .merchantCountry("TR")
                .transactionDateTime(LocalDateTime.now().withHour(hour))
                .transactionType(TransactionContext.TransactionType.PURCHASE)
                .customerAge(30)
                .customerSegment("STANDARD")
                .customerCity("Istanbul")
                .customerCountry("TR")
                .accountBalance(new BigDecimal("10000"))
                .monthlySpending(new BigDecimal("2000"))
                .build();
    }

    private TransactionContext createWeekendContext(BigDecimal amount, TransactionContext.MccCategory mcc) {
        LocalDateTime weekendTime = LocalDateTime.now()
                .with(DayOfWeek.SATURDAY)
                .withHour(15);

        return TransactionContext.builder()
                .transactionId("TXN_WEEKEND_" + System.nanoTime())
                .cardNumber("4111111111111111")
                .customerId("CUST_002")
                .accountId("ACC_002")
                .amount(amount)
                .currency("TRY")
                .mccCategory(mcc)
                .transactionDateTime(weekendTime)
                .transactionType(TransactionContext.TransactionType.PURCHASE)
                .customerAge(25)
                .customerSegment("STANDARD")
                .customerCountry("TR")
                .build();
    }

    private TransactionContext createForeignContext(BigDecimal amount, String country) {
        return TransactionContext.builder()
                .transactionId("TXN_FOREIGN_" + System.nanoTime())
                .cardNumber("4111111111111111")
                .customerId("CUST_003")
                .accountId("ACC_003")
                .amount(amount)
                .currency("USD")
                .mccCategory(TransactionContext.MccCategory.SHOPPING)
                .merchantCountry(country)
                .transactionDateTime(LocalDateTime.now())
                .transactionType(TransactionContext.TransactionType.PURCHASE)
                .customerCountry("TR")
                .build();
    }

    private TransactionContext createNightContext(BigDecimal amount, int hour) {
        return createSampleContext(amount, TransactionContext.MccCategory.OTHER, hour);
    }

    private TransactionContext createBusinessContext(String transactionType, BigDecimal amount) {
        return TransactionContext.builder()
                .transactionId("TXN_BUSINESS_" + System.nanoTime())
                .cardNumber("4111111111111111")
                .customerId("CUST_BUSINESS_001")
                .accountId("ACC_BUSINESS_001")
                .amount(amount)
                .currency("TRY")
                .mccCategory(TransactionContext.MccCategory.OTHER)
                .transactionDateTime(LocalDateTime.now())
                .transactionType("CASH_ADVANCE".equals(transactionType)
                        ? TransactionContext.TransactionType.CASH_ADVANCE
                        : TransactionContext.TransactionType.PURCHASE)
                .customerSegment("STANDARD")
                .build();
    }

    private TransactionContext createStudentContext(TransactionContext.MccCategory mcc, BigDecimal amount) {
        return TransactionContext.builder()
                .transactionId("TXN_STUDENT_" + System.nanoTime())
                .cardNumber("4111111111111111")
                .customerId("CUST_STUDENT_001")
                .accountId("ACC_STUDENT_001")
                .amount(amount)
                .currency("TRY")
                .mccCategory(mcc)
                .transactionDateTime(LocalDateTime.now())
                .transactionType(TransactionContext.TransactionType.PURCHASE)
                .customerAge(20)
                .customerSegment("STUDENT")
                .build();
    }

    private TransactionContext createHighValueContext(BigDecimal amount) {
        return createSampleContext(amount, TransactionContext.MccCategory.SHOPPING, 14);
    }

    private TransactionContext createWeekendFuelContext(BigDecimal amount) {
        return createWeekendContext(amount, TransactionContext.MccCategory.FUEL);
    }

    private TransactionContext createRandomContext() {
        BigDecimal[] amounts = {
                new BigDecimal("50"), new BigDecimal("150"), new BigDecimal("300"),
                new BigDecimal("750"), new BigDecimal("1200"), new BigDecimal("2500")
        };

        TransactionContext.MccCategory[] mccs = TransactionContext.MccCategory.values();

        return createSampleContext(
                amounts[random.nextInt(amounts.length)],
                mccs[random.nextInt(mccs.length)],
                8 + random.nextInt(14) 
        );
    }

    private void printRuleResults(Map<Rule.RuleType, List<RuleResult>> results) {
        if (results.isEmpty()) {
            System.out.println("    No rules applied");
            return;
        }

        results.forEach((ruleType, ruleResults) -> {
            System.out.println("    " + ruleType + ":");
            ruleResults.forEach(result -> {
                String status = result.isApplied() ? "✅ APPLIED" : "❌ NOT_APPLIED";
                String details = "";

                if (result.isApplied()) {
                    if (result.getPoints().isPresent()) {
                        details += " (points: " + result.getPoints().get() + ")";
                    }
                    if (result.getRiskScore().isPresent()) {
                        details += " (risk: " + result.getRiskScore().get() + ")";
                    }
                    if (result.getDiscountAmount().isPresent()) {
                        details += " (discount: " + result.getDiscountAmount().get() + ")";
                    }
                }

                System.out.printf("      %s -> %s%s\n", result.getRuleId(), status, details);
            });
        });
    }

    
    public static void main(String[] args) {
        RuleDSLDemo demo = new RuleDSLDemo();
        demo.runAllDemos();
    }
}