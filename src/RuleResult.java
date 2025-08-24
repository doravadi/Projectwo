

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;


public final class RuleResult {

    private final String ruleId;
    private final ResultType resultType;
    private final boolean applied;
    private final LocalDateTime evaluationTime;
    private final String description;
    private final Map<String, Object> values;
    private final List<String> warnings;
    private final List<String> errors;

    private RuleResult(Builder builder) {
        this.ruleId = Objects.requireNonNull(builder.ruleId, "Rule ID cannot be null");
        this.resultType = Objects.requireNonNull(builder.resultType, "Result type cannot be null");
        this.applied = builder.applied;
        this.evaluationTime = Objects.requireNonNull(builder.evaluationTime, "Evaluation time cannot be null");
        this.description = builder.description;
        this.values = Collections.unmodifiableMap(new HashMap<>(builder.values));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(builder.warnings));
        this.errors = Collections.unmodifiableList(new ArrayList<>(builder.errors));
    }

    
    public String getRuleId() { return ruleId; }
    public ResultType getResultType() { return resultType; }
    public boolean isApplied() { return applied; }
    public LocalDateTime getEvaluationTime() { return evaluationTime; }
    public Optional<String> getDescription() { return Optional.ofNullable(description); }
    public Map<String, Object> getValues() { return values; }
    public List<String> getWarnings() { return warnings; }
    public List<String> getErrors() { return errors; }

    
    public Optional<BigDecimal> getPoints() {
        return getValueAs("points", BigDecimal.class);
    }

    public Optional<BigDecimal> getDiscountAmount() {
        return getValueAs("discount_amount", BigDecimal.class);
    }

    public Optional<Integer> getRiskScore() {
        return getValueAs("risk_score", Integer.class);
    }

    public Optional<String> getAlertMessage() {
        return getValueAs("alert_message", String.class);
    }

    public Optional<BigDecimal> getMultiplier() {
        return getValueAs("multiplier", BigDecimal.class);
    }

    public Optional<String> getAction() {
        return getValueAs("action", String.class);
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> getValueAs(String key, Class<T> type) {
        Object value = values.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public boolean isSuccessful() {
        return applied && !hasErrors();
    }

    
    public static RuleResult notApplied(String ruleId, String reason) {
        return builder(ruleId, ResultType.NOT_APPLIED)
                .applied(false)
                .description(reason)
                .build();
    }

    public static RuleResult points(String ruleId, BigDecimal points, String description) {
        return builder(ruleId, ResultType.POINTS)
                .applied(true)
                .description(description)
                .value("points", points)
                .build();
    }

    public static RuleResult discount(String ruleId, BigDecimal amount, String description) {
        return builder(ruleId, ResultType.DISCOUNT)
                .applied(true)
                .description(description)
                .value("discount_amount", amount)
                .build();
    }

    public static RuleResult alert(String ruleId, String alertMessage, int riskScore) {
        return builder(ruleId, ResultType.ALERT)
                .applied(true)
                .description("Risk alert generated")
                .value("alert_message", alertMessage)
                .value("risk_score", riskScore)
                .build();
    }

    public static RuleResult error(String ruleId, String errorMessage) {
        return builder(ruleId, ResultType.ERROR)
                .applied(false)
                .description("Rule evaluation failed")
                .error(errorMessage)
                .build();
    }

    
    public static Builder builder(String ruleId, ResultType resultType) {
        return new Builder(ruleId, resultType);
    }

    public static final class Builder {
        private final String ruleId;
        private final ResultType resultType;
        private boolean applied = false;
        private LocalDateTime evaluationTime = LocalDateTime.now();
        private String description;
        private final Map<String, Object> values = new HashMap<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        private Builder(String ruleId, ResultType resultType) {
            this.ruleId = ruleId;
            this.resultType = resultType;
        }

        public Builder applied(boolean applied) {
            this.applied = applied;
            return this;
        }

        public Builder evaluationTime(LocalDateTime evaluationTime) {
            this.evaluationTime = evaluationTime;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder value(String key, Object value) {
            this.values.put(key, value);
            return this;
        }

        public Builder values(Map<String, Object> values) {
            this.values.putAll(values);
            return this;
        }

        public Builder warning(String warning) {
            this.warnings.add(warning);
            return this;
        }

        public Builder error(String error) {
            this.errors.add(error);
            return this;
        }

        
        public Builder points(BigDecimal points) {
            return value("points", points);
        }

        public Builder discountAmount(BigDecimal amount) {
            return value("discount_amount", amount);
        }

        public Builder riskScore(int score) {
            return value("risk_score", score);
        }

        public Builder alertMessage(String message) {
            return value("alert_message", message);
        }

        public Builder multiplier(BigDecimal multiplier) {
            return value("multiplier", multiplier);
        }

        public Builder action(String action) {
            return value("action", action);
        }

        public RuleResult build() {
            return new RuleResult(this);
        }
    }

    
    public enum ResultType {
        POINTS("Puan kazanımı/kaybı"),
        DISCOUNT("İndirim uygulaması"),
        ALERT("Uyarı/risk skoru"),
        LIMIT_CHECK("Limit kontrolü"),
        ACTION("Özel aksiyon"),
        NOT_APPLIED("Kural uygulanamadı"),
        ERROR("Hata oluştu");

        private final String description;

        ResultType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RuleResult that = (RuleResult) o;
        return applied == that.applied &&
                Objects.equals(ruleId, that.ruleId) &&
                resultType == that.resultType &&
                Objects.equals(evaluationTime, that.evaluationTime) &&
                Objects.equals(description, that.description) &&
                Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ruleId, resultType, applied, evaluationTime, description, values);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RuleResult{");
        sb.append("ruleId='").append(ruleId).append('\'');
        sb.append(", type=").append(resultType);
        sb.append(", applied=").append(applied);

        if (description != null) {
            sb.append(", description='").append(description).append('\'');
        }

        if (!values.isEmpty()) {
            sb.append(", values=").append(values);
        }

        if (hasWarnings()) {
            sb.append(", warnings=").append(warnings.size());
        }

        if (hasErrors()) {
            sb.append(", errors=").append(errors.size());
        }

        sb.append('}');
        return sb.toString();
    }
}