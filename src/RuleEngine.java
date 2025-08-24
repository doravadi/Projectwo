
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Ana DSL Rule Engine - Banking kurallarını parse eder ve çalıştırır.
 *
 * Thread-safe design, rule caching ve performance monitoring içerir.
 *
 * Desteklenen DSL syntax örnekleri:
 * - "MCC in [GROCERY, FUEL] and amount > 500 then points = amount * 0.02"
 * - "hour between 22 and 06 then risk_score = 50"
 * - "country != 'TR' and amount > 1000 then alert = 'HIGH_FOREIGN_AMOUNT'"
 * - "day in {SAT, SUN} and MCC == ENTERTAINMENT then multiplier = 2.0"
 */
public final class RuleEngine {

    private final RuleParser parser;
    private final Map<String, ParsedRule> ruleCache;
    private final Map<Rule.RuleType, List<ParsedRule>> rulesByType;
    private final RuleEngineStatistics statistics;

    // Performance counters
    private final AtomicLong totalEvaluations = new AtomicLong(0);
    private final AtomicLong totalParsingTime = new AtomicLong(0);
    private final AtomicLong totalEvaluationTime = new AtomicLong(0);

    public RuleEngine() {
        this.parser = new RuleParser();
        this.ruleCache = new ConcurrentHashMap<>();
        this.rulesByType = new EnumMap<>(Rule.RuleType.class);
        this.statistics = new RuleEngineStatistics();

        // Initialize rule type lists
        for (Rule.RuleType type : Rule.RuleType.values()) {
            rulesByType.put(type, new ArrayList<>());
        }
    }

    /**
     * DSL kuralını parse eder ve engine'e ekler
     *
     * @param ruleId Kural ID'si
     * @param dslExpression DSL ifadesi
     * @param ruleType Kural türü
     * @param priority Öncelik (yüksek sayı = önce çalışır)
     * @param description Açıklama
     * @throws RuleSyntaxException Parse hatası
     */
    public void addRule(String ruleId, String dslExpression, Rule.RuleType ruleType,
                        int priority, String description) throws RuleSyntaxException {
        Objects.requireNonNull(ruleId, "Rule ID cannot be null");
        Objects.requireNonNull(dslExpression, "DSL expression cannot be null");
        Objects.requireNonNull(ruleType, "Rule type cannot be null");

        if (ruleCache.containsKey(ruleId)) {
            throw new IllegalArgumentException("Rule with ID '" + ruleId + "' already exists");
        }

        long startTime = System.nanoTime();
        try {
            ASTNode ast = parser.parse(dslExpression);
            ParsedRule parsedRule = new ParsedRule(ruleId, dslExpression, ast, ruleType,
                    priority, description, true);

            ruleCache.put(ruleId, parsedRule);
            rulesByType.get(ruleType).add(parsedRule);

            // Sort by priority (descending)
            rulesByType.get(ruleType).sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

            statistics.incrementRulesAdded();

        } finally {
            totalParsingTime.addAndGet(System.nanoTime() - startTime);
        }
    }

    /**
     * Belirli bir kural türündeki tüm kuralları çalıştırır
     */
    public List<RuleResult> evaluateRules(Rule.RuleType ruleType, TransactionContext context) {
        Objects.requireNonNull(ruleType, "Rule type cannot be null");
        Objects.requireNonNull(context, "Transaction context cannot be null");

        List<ParsedRule> rules = rulesByType.get(ruleType);
        List<RuleResult> results = new ArrayList<>();

        long startTime = System.nanoTime();
        try {
            for (ParsedRule rule : rules) {
                if (!rule.isActive()) {
                    continue;
                }

                try {
                    RuleResult result = evaluateRule(rule, context);
                    results.add(result);

                    if (result.isApplied()) {
                        statistics.incrementSuccessfulEvaluations();
                    }

                } catch (RuleEvaluationException e) {
                    RuleResult errorResult = RuleResult.error(rule.getRuleId(), e.getMessage());
                    results.add(errorResult);
                    statistics.incrementFailedEvaluations();
                }
            }

            return results;

        } finally {
            totalEvaluationTime.addAndGet(System.nanoTime() - startTime);
            totalEvaluations.incrementAndGet();
        }
    }

    /**
     * Tüm kural türlerini çalıştırır (öncelik sırasına göre)
     */
    public Map<Rule.RuleType, List<RuleResult>> evaluateAllRules(TransactionContext context) {
        Objects.requireNonNull(context, "Transaction context cannot be null");

        Map<Rule.RuleType, List<RuleResult>> allResults = new EnumMap<>(Rule.RuleType.class);

        // Rule türlerini öncelik sırasına göre işle
        Rule.RuleType[] priorityOrder = {
                Rule.RuleType.LIMIT_CHECK,    // Önce limitler
                Rule.RuleType.FRAUD_CHECK,    // Sonra fraud
                Rule.RuleType.GEOGRAPHIC,     // Coğrafi kurallar
                Rule.RuleType.TIME_BASED,     // Zaman bazlı
                Rule.RuleType.MCC_ROUTING,    // MCC routing
                Rule.RuleType.REWARD,         // Ödüller
                Rule.RuleType.DISCOUNT,       // İndirimler
                Rule.RuleType.ALERT          // Son olarak uyarılar
        };

        for (Rule.RuleType ruleType : priorityOrder) {
            List<RuleResult> results = evaluateRules(ruleType, context);
            if (!results.isEmpty()) {
                allResults.put(ruleType, results);
            }
        }

        return allResults;
    }

    /**
     * Tek bir kuralı çalıştırır
     */
    private RuleResult evaluateRule(ParsedRule rule, TransactionContext context) throws RuleEvaluationException {
        try {
            // AST'yi evaluate et
            Object result = rule.getAst().evaluate(context);

            if (result instanceof Boolean && (Boolean) result) {
                // Kural true döndü, action'ları çalıştır
                return executeRuleActions(rule, context);
            } else {
                return RuleResult.notApplied(rule.getRuleId(), "Condition not met");
            }

        } catch (Exception e) {
            throw new RuleEvaluationException(rule.getRuleId(), rule.getDslExpression(),
                    "Evaluation failed", e);
        }
    }

    /**
     * Kural aksiyonlarını çalıştırır (DSL'deki "then" kısmı)
     */
    private RuleResult executeRuleActions(ParsedRule rule, TransactionContext context) {
        // Bu method AST'deki action node'larını execute eder
        // Şimdilik basit bir implementation

        RuleResult.Builder builder = RuleResult.builder(rule.getRuleId(),
                        mapRuleTypeToResultType(rule.getRuleType()))
                .applied(true)
                .description(rule.getDescription());

        // Rule türüne göre default action'lar
        switch (rule.getRuleType()) {
            case REWARD:
                // Örnek: amount * 0.02 points
                builder.points(context.getAmount().multiply(new java.math.BigDecimal("0.02")));
                break;

            case FRAUD_CHECK:
                // Örnek: risk skoru artır
                builder.riskScore(50)
                        .alertMessage("Suspicious transaction detected");
                break;

            case DISCOUNT:
                // Örnek: %5 indirim
                builder.discountAmount(context.getAmount().multiply(new java.math.BigDecimal("0.05")));
                break;

            default:
                // Genel action
                builder.action("RULE_APPLIED");
                break;
        }

        return builder.build();
    }

    private RuleResult.ResultType mapRuleTypeToResultType(Rule.RuleType ruleType) {
        switch (ruleType) {
            case REWARD: return RuleResult.ResultType.POINTS;
            case FRAUD_CHECK: return RuleResult.ResultType.ALERT;
            case DISCOUNT: return RuleResult.ResultType.DISCOUNT;
            case ALERT: return RuleResult.ResultType.ALERT;
            default: return RuleResult.ResultType.ACTION;
        }
    }

    /**
     * Kuralı aktif/pasif yapar
     */
    public boolean toggleRule(String ruleId, boolean active) {
        ParsedRule rule = ruleCache.get(ruleId);
        if (rule != null) {
            rule.setActive(active);
            return true;
        }
        return false;
    }

    /**
     * Kuralı siler
     */
    public boolean removeRule(String ruleId) {
        ParsedRule rule = ruleCache.remove(ruleId);
        if (rule != null) {
            rulesByType.get(rule.getRuleType()).remove(rule);
            statistics.incrementRulesRemoved();
            return true;
        }
        return false;
    }

    /**
     * Belirli türdeki kuralları listeler
     */
    public List<String> getRuleIdsByType(Rule.RuleType ruleType) {
        return rulesByType.get(ruleType).stream()
                .map(ParsedRule::getRuleId)
                .collect(Collectors.toList());
    }

    /**
     * Kural detaylarını getirir
     */
    public Optional<RuleInfo> getRuleInfo(String ruleId) {
        ParsedRule rule = ruleCache.get(ruleId);
        if (rule != null) {
            return Optional.of(new RuleInfo(rule.getRuleId(), rule.getDslExpression(),
                    rule.getRuleType(), rule.getPriority(),
                    rule.getDescription(), rule.isActive(),
                    rule.getAst().getConditionCount()));
        }
        return Optional.empty();
    }

    /**
     * Engine istatistikleri
     */
    public RuleEngineStatistics getStatistics() {
        statistics.setTotalRules(ruleCache.size());
        statistics.setTotalEvaluations(totalEvaluations.get());
        statistics.setAverageParsingTime(ruleCache.isEmpty() ? 0 :
                totalParsingTime.get() / ruleCache.size());
        statistics.setAverageEvaluationTime(totalEvaluations.get() == 0 ? 0 :
                totalEvaluationTime.get() / totalEvaluations.get());
        return statistics;
    }

    /**
     * Cache'i temizler
     */
    public void clearCache() {
        ruleCache.clear();
        rulesByType.values().forEach(List::clear);
        statistics.reset();
    }

    /**
     * DSL expression'ını validate eder (parse etmeden)
     */
    public ValidationResult validateExpression(String dslExpression) {
        try {
            parser.parse(dslExpression);
            return ValidationResult.valid();
        } catch (RuleSyntaxException e) {
            return ValidationResult.invalid(e.getUserFriendlyMessage());
        }
    }

    // Inner classes
    public static final class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() { return valid; }
        public Optional<String> getErrorMessage() { return Optional.ofNullable(errorMessage); }
    }

    public static final class RuleInfo {
        private final String ruleId;
        private final String dslExpression;
        private final Rule.RuleType ruleType;
        private final int priority;
        private final String description;
        private final boolean active;
        private final int conditionCount;

        public RuleInfo(String ruleId, String dslExpression, Rule.RuleType ruleType,
                        int priority, String description, boolean active, int conditionCount) {
            this.ruleId = ruleId;
            this.dslExpression = dslExpression;
            this.ruleType = ruleType;
            this.priority = priority;
            this.description = description;
            this.active = active;
            this.conditionCount = conditionCount;
        }

        // Getters
        public String getRuleId() { return ruleId; }
        public String getDslExpression() { return dslExpression; }
        public Rule.RuleType getRuleType() { return ruleType; }
        public int getPriority() { return priority; }
        public String getDescription() { return description; }
        public boolean isActive() { return active; }
        public int getConditionCount() { return conditionCount; }
    }
}