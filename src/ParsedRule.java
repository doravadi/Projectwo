
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;


final class ParsedRule {

    private final String ruleId;
    private final String dslExpression;
    private final ASTNode ast;
    private final Rule.RuleType ruleType;
    private final int priority;
    private final String description;
    private final LocalDateTime creationTime;
    private volatile boolean active;

    public ParsedRule(String ruleId, String dslExpression, ASTNode ast,
                      Rule.RuleType ruleType, int priority, String description, boolean active) {
        this.ruleId = Objects.requireNonNull(ruleId, "Rule ID cannot be null");
        this.dslExpression = Objects.requireNonNull(dslExpression, "DSL expression cannot be null");
        this.ast = Objects.requireNonNull(ast, "AST cannot be null");
        this.ruleType = Objects.requireNonNull(ruleType, "Rule type cannot be null");
        this.priority = priority;
        this.description = description;
        this.active = active;
        this.creationTime = LocalDateTime.now();
    }


    public String getRuleId() {
        return ruleId;
    }

    public String getDslExpression() {
        return dslExpression;
    }

    public ASTNode getAst() {
        return ast;
    }

    public Rule.RuleType getRuleType() {
        return ruleType;
    }

    public int getPriority() {
        return priority;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreationTime() {
        return creationTime;
    }


    public void setActive(boolean active) {
        this.active = active;
    }


    public int getConditionCount() {
        return ast.getConditionCount();
    }

    public String getExpressionPreview() {
        String expr = dslExpression;
        if (expr.length() > 50) {
            expr = expr.substring(0, 47) + "...";
        }
        return expr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParsedRule that = (ParsedRule) o;
        return Objects.equals(ruleId, that.ruleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ruleId);
    }

    @Override
    public String toString() {
        return "ParsedRule{" +
                "id='" + ruleId + '\'' +
                ", type=" + ruleType +
                ", priority=" + priority +
                ", active=" + active +
                ", expression='" + getExpressionPreview() + '\'' +
                '}';
    }
}


final class RuleEngineStatistics {


    private final AtomicLong rulesAdded = new AtomicLong(0);
    private final AtomicLong rulesRemoved = new AtomicLong(0);
    private volatile int totalRules = 0;
    private volatile int activeRules = 0;


    private final AtomicLong totalEvaluations = new AtomicLong(0);
    private final AtomicLong successfulEvaluations = new AtomicLong(0);
    private final AtomicLong failedEvaluations = new AtomicLong(0);


    private volatile long averageParsingTime = 0;
    private volatile long averageEvaluationTime = 0;
    private volatile long maxEvaluationTime = 0;
    private volatile long minEvaluationTime = Long.MAX_VALUE;


    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);


    private final AtomicLong parseErrors = new AtomicLong(0);
    private final AtomicLong evaluationErrors = new AtomicLong(0);


    private final LocalDateTime startTime = LocalDateTime.now();


    public void incrementRulesAdded() {
        rulesAdded.incrementAndGet();
    }

    public void incrementRulesRemoved() {
        rulesRemoved.incrementAndGet();
    }

    public void incrementSuccessfulEvaluations() {
        successfulEvaluations.incrementAndGet();
    }

    public void incrementFailedEvaluations() {
        failedEvaluations.incrementAndGet();
    }

    public void incrementCacheHits() {
        cacheHits.incrementAndGet();
    }

    public void incrementCacheMisses() {
        cacheMisses.incrementAndGet();
    }

    public void incrementParseErrors() {
        parseErrors.incrementAndGet();
    }

    public void incrementEvaluationErrors() {
        evaluationErrors.incrementAndGet();
    }


    public void recordEvaluationTime(long nanoTime) {

        long current;
        do {
            current = minEvaluationTime;
        } while (nanoTime < current && !compareAndSetMinEvaluationTime(current, nanoTime));

        do {
            current = maxEvaluationTime;
        } while (nanoTime > current && !compareAndSetMaxEvaluationTime(current, nanoTime));
    }

    private boolean compareAndSetMinEvaluationTime(long expected, long update) {

        if (minEvaluationTime == expected) {
            minEvaluationTime = update;
            return true;
        }
        return false;
    }

    private boolean compareAndSetMaxEvaluationTime(long expected, long update) {
        if (maxEvaluationTime == expected) {
            maxEvaluationTime = update;
            return true;
        }
        return false;
    }


    public void setTotalRules(int totalRules) {
        this.totalRules = totalRules;
    }

    public void setActiveRules(int activeRules) {
        this.activeRules = activeRules;
    }

    public void setTotalEvaluations(long totalEvaluations) {
        this.totalEvaluations.set(totalEvaluations);
    }

    public void setAverageParsingTime(long averageParsingTime) {
        this.averageParsingTime = averageParsingTime;
    }

    public void setAverageEvaluationTime(long averageEvaluationTime) {
        this.averageEvaluationTime = averageEvaluationTime;
    }


    public long getRulesAdded() {
        return rulesAdded.get();
    }

    public long getRulesRemoved() {
        return rulesRemoved.get();
    }

    public int getTotalRules() {
        return totalRules;
    }

    public int getActiveRules() {
        return activeRules;
    }

    public long getTotalEvaluations() {
        return totalEvaluations.get();
    }

    public long getSuccessfulEvaluations() {
        return successfulEvaluations.get();
    }

    public long getFailedEvaluations() {
        return failedEvaluations.get();
    }

    public long getAverageParsingTime() {
        return averageParsingTime;
    }

    public long getAverageEvaluationTime() {
        return averageEvaluationTime;
    }

    public long getMaxEvaluationTime() {
        return maxEvaluationTime;
    }

    public long getMinEvaluationTime() {
        return minEvaluationTime == Long.MAX_VALUE ? 0 : minEvaluationTime;
    }

    public long getCacheHits() {
        return cacheHits.get();
    }

    public long getCacheMisses() {
        return cacheMisses.get();
    }

    public long getParseErrors() {
        return parseErrors.get();
    }

    public long getEvaluationErrors() {
        return evaluationErrors.get();
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }


    public double getSuccessRate() {
        long total = getTotalEvaluations();
        return total == 0 ? 0.0 : (double) getSuccessfulEvaluations() / total * 100.0;
    }

    public double getCacheHitRate() {
        long totalCacheAccess = getCacheHits() + getCacheMisses();
        return totalCacheAccess == 0 ? 0.0 : (double) getCacheHits() / totalCacheAccess * 100.0;
    }

    public double getAverageParsingTimeMs() {
        return getAverageParsingTime() / 1_000_000.0;
    }

    public double getAverageEvaluationTimeMs() {
        return getAverageEvaluationTime() / 1_000_000.0;
    }

    public double getMaxEvaluationTimeMs() {
        return getMaxEvaluationTime() / 1_000_000.0;
    }

    public double getMinEvaluationTimeMs() {
        return getMinEvaluationTime() / 1_000_000.0;
    }

    public long getUpTimeMinutes() {
        return java.time.Duration.between(startTime, LocalDateTime.now()).toMinutes();
    }


    public void reset() {
        rulesAdded.set(0);
        rulesRemoved.set(0);
        totalRules = 0;
        activeRules = 0;

        totalEvaluations.set(0);
        successfulEvaluations.set(0);
        failedEvaluations.set(0);

        averageParsingTime = 0;
        averageEvaluationTime = 0;
        maxEvaluationTime = 0;
        minEvaluationTime = Long.MAX_VALUE;

        cacheHits.set(0);
        cacheMisses.set(0);

        parseErrors.set(0);
        evaluationErrors.set(0);
    }


    public String generateSummaryReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DSL Rule Engine Statistics ===\n");
        sb.append("Uptime: ").append(getUpTimeMinutes()).append(" minutes\n");
        sb.append("\nRule Management:\n");
        sb.append("  Total Rules: ").append(getTotalRules()).append("\n");
        sb.append("  Active Rules: ").append(getActiveRules()).append("\n");
        sb.append("  Rules Added: ").append(getRulesAdded()).append("\n");
        sb.append("  Rules Removed: ").append(getRulesRemoved()).append("\n");

        sb.append("\nEvaluation Performance:\n");
        sb.append("  Total Evaluations: ").append(getTotalEvaluations()).append("\n");
        sb.append("  Success Rate: ").append(String.format("%.1f%%", getSuccessRate())).append("\n");
        sb.append("  Average Eval Time: ").append(String.format("%.2fms", getAverageEvaluationTimeMs())).append("\n");
        sb.append("  Min/Max Eval Time: ").append(String.format("%.2fms/%.2fms", getMinEvaluationTimeMs(), getMaxEvaluationTimeMs())).append("\n");

        sb.append("\nParsing Performance:\n");
        sb.append("  Average Parse Time: ").append(String.format("%.2fms", getAverageParsingTimeMs())).append("\n");
        sb.append("  Parse Errors: ").append(getParseErrors()).append("\n");

        sb.append("\nCache Performance:\n");
        sb.append("  Cache Hit Rate: ").append(String.format("%.1f%%", getCacheHitRate())).append("\n");
        sb.append("  Cache Hits/Misses: ").append(getCacheHits()).append("/").append(getCacheMisses()).append("\n");

        sb.append("\nError Summary:\n");
        sb.append("  Parse Errors: ").append(getParseErrors()).append("\n");
        sb.append("  Evaluation Errors: ").append(getEvaluationErrors()).append("\n");

        return sb.toString();
    }

    @Override
    public String toString() {
        return "RuleEngineStatistics{" +
                "totalRules=" + totalRules +
                ", totalEvaluations=" + getTotalEvaluations() +
                ", successRate=" + String.format("%.1f%%", getSuccessRate()) +
                ", avgEvalTime=" + String.format("%.2fms", getAverageEvaluationTimeMs()) +
                ", cacheHitRate=" + String.format("%.1f%%", getCacheHitRate()) +
                '}';
    }
}