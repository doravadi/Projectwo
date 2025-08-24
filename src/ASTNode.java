

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.*;

/**
 * Abstract Syntax Tree node'ları için base class.
 *
 * DSL parser tarafından oluşturulan AST'nin tüm node'ları bu interface'i implement eder.
 * Visitor pattern ile evaluation yapılır.
 *
 * Örnek AST yapısı:
 * "amount > 500 and MCC == GROCERY"
 *
 *       AND
 *      /   \
 *    GT     EQ
 *   /  \   /  \
 * amount 500 MCC GROCERY
 */
public abstract class ASTNode {

    /**
     * Node'u evaluate eder ve sonuç döner
     *
     * @param context Transaction context
     * @return Evaluation sonucu
     * @throws RuleEvaluationException Evaluation hatası
     */
    public abstract Object evaluate(TransactionContext context) throws RuleEvaluationException;

    /**
     * Node'daki koşul sayısını döner (complexity metriği)
     */
    public abstract int getConditionCount();

    /**
     * Node türü
     */
    public abstract NodeType getNodeType();

    /**
     * Node'un string representation'ı
     */
    public abstract String toExpressionString();

    // Node türleri
    public enum NodeType {
        // Logical operators
        AND, OR, NOT,

        // Comparison operators
        EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN, GREATER_EQUAL, LESS_EQUAL,

        // Membership operators
        IN, NOT_IN, BETWEEN,

        // Arithmetic operators
        PLUS, MINUS, MULTIPLY, DIVIDE,

        // Literals
        NUMBER, STRING, BOOLEAN, DATE, TIME,

        // Field references
        FIELD,

        // Collections
        LIST, SET,

        // Actions (then clause)
        ASSIGNMENT, ACTION_BLOCK
    }

    // Binary operator base class
    public static abstract class BinaryOperatorNode extends ASTNode {
        protected final ASTNode left;
        protected final ASTNode right;

        protected BinaryOperatorNode(ASTNode left, ASTNode right) {
            this.left = Objects.requireNonNull(left, "Left operand cannot be null");
            this.right = Objects.requireNonNull(right, "Right operand cannot be null");
        }

        @Override
        public int getConditionCount() {
            return left.getConditionCount() + right.getConditionCount() + 1;
        }

        public ASTNode getLeft() { return left; }
        public ASTNode getRight() { return right; }
    }

    // Unary operator base class
    public static abstract class UnaryOperatorNode extends ASTNode {
        protected final ASTNode operand;

        protected UnaryOperatorNode(ASTNode operand) {
            this.operand = Objects.requireNonNull(operand, "Operand cannot be null");
        }

        @Override
        public int getConditionCount() {
            return operand.getConditionCount() + 1;
        }

        public ASTNode getOperand() { return operand; }
    }

    // Logical AND node
    public static final class AndNode extends BinaryOperatorNode {
        public AndNode(ASTNode left, ASTNode right) {
            super(left, right);
        }

        @Override
        public Object evaluate(TransactionContext context) throws RuleEvaluationException {
            Object leftResult = left.evaluate(context);
            if (!isTrue(leftResult)) {
                return false; // Short-circuit evaluation
            }

            Object rightResult = right.evaluate(context);
            return isTrue(rightResult);
        }

        @Override
        public NodeType getNodeType() {
            return NodeType.AND;
        }

        @Override
        public String toExpressionString() {
            return "(" + left.toExpressionString() + " AND " + right.toExpressionString() + ")";
        }
    }

    // Logical OR node
    public static final class OrNode extends BinaryOperatorNode {
        public OrNode(ASTNode left, ASTNode right) {
            super(left, right);
        }

        @Override
        public Object evaluate(TransactionContext context) throws RuleEvaluationException {
            Object leftResult = left.evaluate(context);
            if (isTrue(leftResult)) {
                return true; // Short-circuit evaluation
            }

            Object rightResult = right.evaluate(context);
            return isTrue(rightResult);
        }

        @Override
        public NodeType getNodeType() {
            return NodeType.OR;
        }

        @Override
        public String toExpressionString() {
            return "(" + left.toExpressionString() + " OR " + right.toExpressionString() + ")";
        }
    }

    // Logical NOT node
    public static final class NotNode extends UnaryOperatorNode {
        public NotNode(ASTNode operand) {
            super(operand);
        }

        @Override
        public Object evaluate(TransactionContext context) throws RuleEvaluationException {
            Object result = operand.evaluate(context);
            return !isTrue(result);
        }

        @Override
        public NodeType getNodeType() {
            return NodeType.NOT;
        }

        @Override
        public String toExpressionString() {
            return "NOT " + operand.toExpressionString();
        }
    }

    // Equality node
    public static final class EqualsNode extends BinaryOperatorNode {
        public EqualsNode(ASTNode left, ASTNode right) {
            super(left, right);
        }

        @Override
        public Object evaluate(TransactionContext context) throws RuleEvaluationException {
            Object leftValue = left.evaluate(context);
            Object rightValue = right.evaluate(context);

            return Objects.equals(leftValue, rightValue);
        }

        @Override
        public NodeType getNodeType() {
            return NodeType.EQUALS;
        }

        @Override
        public String toExpressionString() {
            return left.toExpressionString() + " == " + right.toExpressionString();
        }
    }

    // Greater than node
    public static final class GreaterThanNode extends BinaryOperatorNode {
        public GreaterThanNode(ASTNode left, ASTNode right) {
            super(left, right);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object evaluate(TransactionContext context) throws RuleEvaluationException {
            Object leftValue = left.evaluate(context);
            Object rightValue = right.evaluate(context);

            if (leftValue instanceof Comparable && rightValue instanceof Comparable) {
                try {
                    return ((Comparable<Object>) leftValue).compareTo(rightValue) > 0;
                } catch (ClassCastException e) {
                    throw new RuleEvaluationException("Cannot compare " + leftValue.getClass().getSimpleName() +
                            " with " + rightValue.getClass().getSimpleName(), e);
                }
            }

            throw new RuleEvaluationException("Operands must be comparable for > operation");
        }

        @Override
        public NodeType getNodeType() {
            return NodeType.GREATER_THAN;
        }

        @Override
        public String toExpressionString() {
            return left.toExpressionString() + " > " + right.toExpressionString();
        }
    }

    // Field reference node
    public static final class FieldNode extends ASTNode {
        private final String fieldName;

        public FieldNode(String fieldName) {
            this.fieldName = Objects.requireNonNull(fieldName, "Field name cannot be null");
        }

        @Override
        public Object evaluate(TransactionContext context) throws RuleEvaluationException {
            switch (fieldName.toLowerCase()) {
                case "amount":
                    return context.getAmount();
                case "currency":
                    return context.getCurrency();
                case "mcc":
                    return context.getMccCategory();
                case "country":
                    return context.getMerchantCountry().orElse(null);
                case "city":
                    return context.getMerchantCity().orElse(null);
                case "hour":
                    return context.getHourOfDay();
                case "day":
                    return context.getDayOfWeek();
                case "customer_age":
                    return context.getCustomerAge().orElse(null);
                case "customer_segment":
                    return context.getCustomerSegment().orElse(null);
                case "account_balance":
                    return context.getAccountBalance().orElse(null);
                case "monthly_spending":
                    return context.getMonthlySpending().orElse(null);
                case "channel":
                    return context.getChannel().orElse(null);
                case "transaction_type":
                    return context.getTransactionType();

                default:
                    throw new RuleEvaluationException("Unknown field: " + fieldName);
            }
        }

        @Override
        public int getConditionCount() {
            return 0; // Field access is not a condition
        }

        @Override
        public NodeType getNodeType() {
            return NodeType.FIELD;
        }

        @Override
        public String toExpressionString() {
            return fieldName;
        }

        public String getFieldName() {
            return fieldName;
        }
    }

    // Number literal node
    public static final class NumberNode extends ASTNode {
        private final BigDecimal value;

        public NumberNode(BigDecimal value) {
            this.value = Objects.requireNonNull(value, "Number value cannot be null");
        }

        public NumberNode(String value) {
            this.value = new BigDecimal(value);
        }

        public NumberNode(double value) {
            this.value = BigDecimal.valueOf(value);
        }

        public NumberNode(int value) {
            this.value = BigDecimal.valueOf(value);
        }

        @Override
        public Object evaluate(TransactionContext context) {
            return value;
        }

        @Override
        public int getConditionCount() {
            return 0; // Literal is not a condition
        }

        @Override
        public NodeType getNodeType() {
            return NodeType.NUMBER;
        }

        @Override
        public String toExpressionString() {
            return value.toString();
        }

        public BigDecimal getValue() {
            return value;
        }
    }

    // String literal node
    public static final class StringNode extends ASTNode {
        private final String value;

        public StringNode(String value) {
            this.value = Objects.requireNonNull(value, "String value cannot be null");
        }

        @Override
        public Object evaluate(TransactionContext context) {
            return value;
        }

        @Override
        public int getConditionCount() {
            return 0;
        }

        @Override
        public NodeType getNodeType() {
            return NodeType.STRING;
        }

        @Override
        public String toExpressionString() {
            return "'" + value + "'";
        }

        public String getValue() {
            return value;
        }
    }

    // Enum literal node (for MCC, DayOfWeek, etc.)
    public static final class EnumNode extends ASTNode {
        private final String enumName;
        private final Object enumValue;

        public EnumNode(String enumName) {
            this.enumName = Objects.requireNonNull(enumName, "Enum name cannot be null");
            this.enumValue = parseEnumValue(enumName);
        }

        private Object parseEnumValue(String name) {
            // MCC categories
            try {
                return TransactionContext.MccCategory.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Ignore and try next
            }

            // Day of week
            try {
                return DayOfWeek.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Ignore and try next
            }

            // Transaction type
            try {
                return TransactionContext.TransactionType.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Return as string if no enum match
                return name;
            }
        }

        @Override
        public Object evaluate(TransactionContext context) {
            return enumValue;
        }

        @Override
        public int getConditionCount() {
            return 0;
        }

        @Override
        public NodeType getNodeType() {
            return NodeType.STRING; // Treat enum as string-like
        }

        @Override
        public String toExpressionString() {
            return enumName;
        }

        public String getEnumName() {
            return enumName;
        }

        public Object getEnumValue() {
            return enumValue;
        }
    }

    // IN operator node (membership test)
    public static final class InNode extends BinaryOperatorNode {
        public InNode(ASTNode left, ASTNode right) {
            super(left, right);
        }

        @Override
        public Object evaluate(TransactionContext context) throws RuleEvaluationException {
            Object leftValue = left.evaluate(context);
            Object rightValue = right.evaluate(context);

            if (rightValue instanceof Collection) {
                return ((Collection<?>) rightValue).contains(leftValue);
            }

            if (rightValue instanceof Object[]) {
                return Arrays.asList((Object[]) rightValue).contains(leftValue);
            }

            throw new RuleEvaluationException("Right operand of 'in' must be a collection or array");
        }

        @Override
        public NodeType getNodeType() {
            return NodeType.IN;
        }

        @Override
        public String toExpressionString() {
            return left.toExpressionString() + " IN " + right.toExpressionString();
        }
    }

    // List node for IN operations
    public static final class ListNode extends ASTNode {
        private final List<ASTNode> elements;

        public ListNode(List<ASTNode> elements) {
            this.elements = new ArrayList<>(Objects.requireNonNull(elements, "Elements cannot be null"));
        }

        @Override
        public Object evaluate(TransactionContext context) throws RuleEvaluationException {
            List<Object> values = new ArrayList<>();
            for (ASTNode element : elements) {
                values.add(element.evaluate(context));
            }
            return values;
        }

        @Override
        public int getConditionCount() {
            return 0; // List itself is not a condition
        }

        @Override
        public NodeType getNodeType() {
            return NodeType.LIST;
        }

        @Override
        public String toExpressionString() {
            return "[" + elements.stream()
                    .map(ASTNode::toExpressionString)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("") + "]";
        }

        public List<ASTNode> getElements() {
            return new ArrayList<>(elements);
        }
    }

    // Helper method for boolean evaluation
    protected static boolean isTrue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0.0;
        }
        if (value instanceof String) {
            return !((String) value).isEmpty();
        }
        return true; // Non-null objects are considered true
    }
}