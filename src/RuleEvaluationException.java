


public final class RuleEvaluationException extends Exception {

    private final String ruleId;
    private final String expression;

    public RuleEvaluationException(String message) {
        super(message);
        this.ruleId = null;
        this.expression = null;
    }

    public RuleEvaluationException(String message, Throwable cause) {
        super(message, cause);
        this.ruleId = null;
        this.expression = null;
    }

    public RuleEvaluationException(String ruleId, String expression, String message) {
        super(buildMessage(ruleId, expression, message));
        this.ruleId = ruleId;
        this.expression = expression;
    }

    public RuleEvaluationException(String ruleId, String expression, String message, Throwable cause) {
        super(buildMessage(ruleId, expression, message), cause);
        this.ruleId = ruleId;
        this.expression = expression;
    }

    private static String buildMessage(String ruleId, String expression, String message) {
        return "Rule evaluation failed [ruleId=" + ruleId +
                ", expression='" + expression + "']: " + message;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getExpression() {
        return expression;
    }
}