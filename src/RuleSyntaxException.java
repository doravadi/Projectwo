

public final class RuleSyntaxException extends Exception {

    private final String ruleText;
    private final int position;
    private final String expectedToken;
    private final String actualToken;

    public RuleSyntaxException(String message) {
        super(message);
        this.ruleText = null;
        this.position = -1;
        this.expectedToken = null;
        this.actualToken = null;
    }

    public RuleSyntaxException(String message, Throwable cause) {
        super(message, cause);
        this.ruleText = null;
        this.position = -1;
        this.expectedToken = null;
        this.actualToken = null;
    }

    public RuleSyntaxException(String ruleText, int position, String message) {
        super(buildPositionalMessage(ruleText, position, message));
        this.ruleText = ruleText;
        this.position = position;
        this.expectedToken = null;
        this.actualToken = null;
    }

    public RuleSyntaxException(String ruleText, int position, String expectedToken, String actualToken) {
        super(buildExpectationMessage(ruleText, position, expectedToken, actualToken));
        this.ruleText = ruleText;
        this.position = position;
        this.expectedToken = expectedToken;
        this.actualToken = actualToken;
    }

    private static String buildPositionalMessage(String ruleText, int position, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("Syntax error at position ").append(position).append(": ").append(message);
        sb.append("\n").append(ruleText);
        sb.append("\n").append(" ".repeat(Math.max(0, position))).append("^");
        return sb.toString();
    }

    private static String buildExpectationMessage(String ruleText, int position, String expected, String actual) {
        StringBuilder sb = new StringBuilder();
        sb.append("Syntax error at position ").append(position);
        sb.append(": expected '").append(expected).append("' but found '").append(actual).append("'");
        sb.append("\n").append(ruleText);
        sb.append("\n").append(" ".repeat(Math.max(0, position))).append("^");
        return sb.toString();
    }


    public static RuleSyntaxException unexpectedToken(String ruleText, int position, String actualToken) {
        return new RuleSyntaxException(ruleText, position, "Unexpected token: '" + actualToken + "'");
    }

    public static RuleSyntaxException expectedToken(String ruleText, int position, String expectedToken, String actualToken) {
        return new RuleSyntaxException(ruleText, position, expectedToken, actualToken);
    }

    public static RuleSyntaxException incompleteExpression(String ruleText) {
        return new RuleSyntaxException(ruleText, ruleText.length(), "Incomplete expression");
    }

    public static RuleSyntaxException invalidOperator(String ruleText, int position, String operator) {
        return new RuleSyntaxException(ruleText, position, "Invalid operator: '" + operator + "'");
    }

    public static RuleSyntaxException missingOperand(String ruleText, int position) {
        return new RuleSyntaxException(ruleText, position, "Missing operand");
    }

    public static RuleSyntaxException unbalancedParentheses(String ruleText, int position) {
        return new RuleSyntaxException(ruleText, position, "Unbalanced parentheses");
    }

    public static RuleSyntaxException invalidFieldName(String ruleText, int position, String fieldName) {
        return new RuleSyntaxException(ruleText, position, "Invalid field name: '" + fieldName + "'");
    }

    public static RuleSyntaxException invalidLiteral(String ruleText, int position, String literal) {
        return new RuleSyntaxException(ruleText, position, "Invalid literal: '" + literal + "'");
    }


    public String getRuleText() {
        return ruleText;
    }

    public int getPosition() {
        return position;
    }

    public String getExpectedToken() {
        return expectedToken;
    }

    public String getActualToken() {
        return actualToken;
    }


    public String getUserFriendlyMessage() {
        if (ruleText == null || position < 0) {
            return getMessage();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("DSL Syntax Error:\n");

        if (expectedToken != null && actualToken != null) {
            sb.append("Expected '").append(expectedToken).append("' but found '").append(actualToken).append("'\n");
        } else {
            sb.append(getMessage().split(":")[1].trim()).append("\n");
        }

        sb.append("\nRule: ").append(ruleText).append("\n");
        sb.append("      ").append(" ".repeat(Math.max(0, position))).append("^ error here");


        addSuggestions(sb);

        return sb.toString();
    }

    private void addSuggestions(StringBuilder sb) {
        sb.append("\n\nCommon fixes:");

        if (actualToken != null) {
            switch (actualToken.toLowerCase()) {
                case "=":
                    sb.append("\n- Use '==' for equality comparison, not '='");
                    break;
                case "and":
                case "or":
                    sb.append("\n- Check if condition before '").append(actualToken).append("' is complete");
                    break;
                case ">":
                case "<":
                case ">=":
                case "<=":
                    sb.append("\n- Make sure both sides of comparison are valid");
                    break;
            }
        }

        if (ruleText != null) {
            if (ruleText.contains("amount >") && !ruleText.contains("amount > ")) {
                sb.append("\n- Add space after operators: 'amount > 100'");
            }
            if (ruleText.contains("[") && !ruleText.contains("]")) {
                sb.append("\n- Close bracket with ']'");
            }
            if (ruleText.contains("(") && !ruleText.contains(")")) {
                sb.append("\n- Close parenthesis with ')'");
            }
        }
    }
}