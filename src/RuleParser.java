
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * DSL Rule Parser - Recursive descent parser
 *
 * DSL string'lerini AST'ye çevirir. Grammar:
 *
 * expression := condition ["then" action]
 * condition := or_condition
 * or_condition := and_condition ("or" and_condition)*
 * and_condition := not_condition ("and" not_condition)*
 * not_condition := ["not"] comparison
 * comparison := operand (operator operand)?
 * operand := field | literal | "(" condition ")"
 * operator := "==" | "!=" | ">" | "<" | ">=" | "<=" | "in" | "not_in" | "between"
 * field := identifier
 * literal := number | string | enum | list
 * list := "[" (literal ("," literal)*)? "]"
 *
 * Örnekler:
 * - "MCC in [GROCERY, FUEL] and amount > 500"
 * - "hour between 22 and 06 or day in {SAT, SUN}"
 * - "not (country == 'TR' and amount < 100)"
 */
public final class RuleParser {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    // Reserved keywords
    private static final Set<String> KEYWORDS = Set.of(
            "and", "or", "not", "in", "not_in", "between", "then",
            "true", "false", "null"
    );

    // Field names validation
    private static final Set<String> VALID_FIELDS = Set.of(
            "amount", "currency", "mcc", "country", "city", "hour", "day",
            "customer_age", "customer_segment", "account_balance",
            "monthly_spending", "channel", "transaction_type"
    );

    private Tokenizer tokenizer;
    private String originalExpression;

    public RuleParser() {
        // Stateless parser
    }

    /**
     * DSL string'ini parse eder ve AST döner
     *
     * @param expression DSL ifadesi
     * @return AST root node
     * @throws RuleSyntaxException Parse hatası
     */
    public ASTNode parse(String expression) throws RuleSyntaxException {
        Objects.requireNonNull(expression, "Expression cannot be null");

        this.originalExpression = expression.trim();
        if (originalExpression.isEmpty()) {
            throw RuleSyntaxException.incompleteExpression("");
        }

        this.tokenizer = new Tokenizer(originalExpression);

        try {
            ASTNode ast = parseExpression();

            // Tüm token'lar tüketilmiş olmalı
            if (!tokenizer.isAtEnd()) {
                Token unexpected = tokenizer.peek();
                throw RuleSyntaxException.unexpectedToken(originalExpression,
                        unexpected.getPosition(), unexpected.getValue());
            }

            return ast;

        } catch (RuntimeException e) {
            throw new RuleSyntaxException("Parse error: " + e.getMessage(), e);
        }
    }

    /**
     * expression := condition ["then" action]
     */
    private ASTNode parseExpression() throws RuleSyntaxException {
        ASTNode condition = parseOrCondition();

        // "then" clause şimdilik ignore edilir (action parsing gelecek versiyonlarda)
        if (tokenizer.peek().getType() == TokenType.KEYWORD &&
                "then".equals(tokenizer.peek().getValue())) {
            tokenizer.consume(); // "then" token'ını tüket
            // Action parsing burada olacak ama şimdilik skip
            while (!tokenizer.isAtEnd()) {
                tokenizer.consume(); // Rest of tokens
            }
        }

        return condition;
    }

    /**
     * or_condition := and_condition ("or" and_condition)*
     */
    private ASTNode parseOrCondition() throws RuleSyntaxException {
        ASTNode left = parseAndCondition();

        while (tokenizer.peek().getType() == TokenType.KEYWORD &&
                "or".equals(tokenizer.peek().getValue())) {
            tokenizer.consume(); // "or" token
            ASTNode right = parseAndCondition();
            left = new ASTNode.OrNode(left, right);
        }

        return left;
    }

    /**
     * and_condition := not_condition ("and" not_condition)*
     */
    private ASTNode parseAndCondition() throws RuleSyntaxException {
        ASTNode left = parseNotCondition();

        while (tokenizer.peek().getType() == TokenType.KEYWORD &&
                "and".equals(tokenizer.peek().getValue())) {
            tokenizer.consume(); // "and" token
            ASTNode right = parseNotCondition();
            left = new ASTNode.AndNode(left, right);
        }

        return left;
    }

    /**
     * not_condition := ["not"] comparison
     */
    private ASTNode parseNotCondition() throws RuleSyntaxException {
        if (tokenizer.peek().getType() == TokenType.KEYWORD &&
                "not".equals(tokenizer.peek().getValue())) {
            tokenizer.consume(); // "not" token
            ASTNode operand = parseComparison();
            return new ASTNode.NotNode(operand);
        }

        return parseComparison();
    }

    /**
     * comparison := operand (operator operand)?
     */
    private ASTNode parseComparison() throws RuleSyntaxException {
        ASTNode left = parseOperand();

        Token operator = tokenizer.peek();

        // Comparison operators
        switch (operator.getType()) {
            case OPERATOR:
                String op = operator.getValue();
                tokenizer.consume();
                ASTNode right = parseOperand();

                switch (op) {
                    case "==":
                        return new ASTNode.EqualsNode(left, right);
                    case "!=":
                        return new ASTNode.NotNode(new ASTNode.EqualsNode(left, right));
                    case ">":
                        return new ASTNode.GreaterThanNode(left, right);
                    case "<":
                        return new ASTNode.GreaterThanNode(right, left); // Reverse for <
                    case ">=":
                        return new ASTNode.OrNode(
                                new ASTNode.GreaterThanNode(left, right),
                                new ASTNode.EqualsNode(left, right)
                        );
                    case "<=":
                        return new ASTNode.OrNode(
                                new ASTNode.GreaterThanNode(right, left),
                                new ASTNode.EqualsNode(left, right)
                        );
                    default:
                        throw RuleSyntaxException.invalidOperator(originalExpression,
                                operator.getPosition(), op);
                }

            case KEYWORD:
                if ("in".equals(operator.getValue())) {
                    tokenizer.consume(); // "in"
                    ASTNode rightOperand = parseOperand();
                    return new ASTNode.InNode(left, rightOperand);
                }
                break;
        }

        return left; // No operator found, return operand as-is
    }

    /**
     * operand := field | literal | "(" condition ")"
     */
    private ASTNode parseOperand() throws RuleSyntaxException {
        Token token = tokenizer.peek();

        switch (token.getType()) {
            case IDENTIFIER:
                return parseField();

            case NUMBER:
                tokenizer.consume();
                return new ASTNode.NumberNode(token.getValue());

            case STRING:
                tokenizer.consume();
                // Remove quotes
                String stringValue = token.getValue();
                if (stringValue.startsWith("'") && stringValue.endsWith("'")) {
                    stringValue = stringValue.substring(1, stringValue.length() - 1);
                }
                return new ASTNode.StringNode(stringValue);

            case KEYWORD:
                if ("true".equals(token.getValue()) || "false".equals(token.getValue())) {
                    tokenizer.consume();
                    return new ASTNode.StringNode(token.getValue()); // Boolean as string for now
                }
                // Treat as enum
                tokenizer.consume();
                return new ASTNode.EnumNode(token.getValue());

            case LPAREN:
                tokenizer.consume(); // "("
                ASTNode expression = parseOrCondition();
                Token rparen = tokenizer.peek();
                if (rparen.getType() != TokenType.RPAREN) {
                    throw RuleSyntaxException.expectedToken(originalExpression,
                            rparen.getPosition(), ")", rparen.getValue());
                }
                tokenizer.consume(); // ")"
                return expression;

            case LBRACKET:
                return parseList();

            default:
                throw RuleSyntaxException.unexpectedToken(originalExpression,
                        token.getPosition(), token.getValue());
        }
    }

    /**
     * field := identifier
     */
    private ASTNode parseField() throws RuleSyntaxException {
        Token token = tokenizer.peek();
        if (token.getType() != TokenType.IDENTIFIER) {
            throw RuleSyntaxException.expectedToken(originalExpression,
                    token.getPosition(), "field name", token.getValue());
        }

        tokenizer.consume();
        String fieldName = token.getValue();

        // Validate field name
        if (!VALID_FIELDS.contains(fieldName.toLowerCase())) {
            throw RuleSyntaxException.invalidFieldName(originalExpression,
                    token.getPosition(), fieldName);
        }

        return new ASTNode.FieldNode(fieldName);
    }

    /**
     * list := "[" (literal ("," literal)*)? "]"
     */
    private ASTNode parseList() throws RuleSyntaxException {
        Token lbracket = tokenizer.peek();
        if (lbracket.getType() != TokenType.LBRACKET) {
            throw RuleSyntaxException.expectedToken(originalExpression,
                    lbracket.getPosition(), "[", lbracket.getValue());
        }
        tokenizer.consume(); // "["

        List<ASTNode> elements = new ArrayList<>();

        // Empty list case
        if (tokenizer.peek().getType() == TokenType.RBRACKET) {
            tokenizer.consume(); // "]"
            return new ASTNode.ListNode(elements);
        }

        // Parse first element
        elements.add(parseListElement());

        // Parse remaining elements
        while (tokenizer.peek().getType() == TokenType.COMMA) {
            tokenizer.consume(); // ","
            elements.add(parseListElement());
        }

        Token rbracket = tokenizer.peek();
        if (rbracket.getType() != TokenType.RBRACKET) {
            throw RuleSyntaxException.expectedToken(originalExpression,
                    rbracket.getPosition(), "]", rbracket.getValue());
        }
        tokenizer.consume(); // "]"

        return new ASTNode.ListNode(elements);
    }

    /**
     * List element parser (number, string, enum)
     */
    private ASTNode parseListElement() throws RuleSyntaxException {
        Token token = tokenizer.peek();

        switch (token.getType()) {
            case NUMBER:
                tokenizer.consume();
                return new ASTNode.NumberNode(token.getValue());

            case STRING:
                tokenizer.consume();
                String stringValue = token.getValue();
                if (stringValue.startsWith("'") && stringValue.endsWith("'")) {
                    stringValue = stringValue.substring(1, stringValue.length() - 1);
                }
                return new ASTNode.StringNode(stringValue);

            case IDENTIFIER:
            case KEYWORD:
                tokenizer.consume();
                return new ASTNode.EnumNode(token.getValue());

            default:
                throw RuleSyntaxException.unexpectedToken(originalExpression,
                        token.getPosition(), token.getValue());
        }
    }

    // Tokenizer inner class
    private static final class Tokenizer {
        private final String input;
        private final List<Token> tokens;
        private int position;

        public Tokenizer(String input) {
            this.input = input;
            this.tokens = tokenize(input);
            this.position = 0;
        }

        private List<Token> tokenize(String input) {
            List<Token> result = new ArrayList<>();
            int pos = 0;

            while (pos < input.length()) {
                char c = input.charAt(pos);

                // Skip whitespace
                if (Character.isWhitespace(c)) {
                    pos++;
                    continue;
                }

                // Numbers
                if (Character.isDigit(c) || (c == '-' && pos + 1 < input.length() &&
                        Character.isDigit(input.charAt(pos + 1)))) {
                    int start = pos;
                    if (c == '-') pos++; // Skip negative sign
                    while (pos < input.length() && (Character.isDigit(input.charAt(pos)) ||
                            input.charAt(pos) == '.')) {
                        pos++;
                    }
                    result.add(new Token(TokenType.NUMBER, input.substring(start, pos), start));
                    continue;
                }

                // Strings
                if (c == '\'' || c == '"') {
                    int start = pos;
                    pos++; // Skip opening quote
                    while (pos < input.length() && input.charAt(pos) != c) {
                        pos++;
                    }
                    if (pos >= input.length()) {
                        throw new RuntimeException("Unterminated string at position " + start);
                    }
                    pos++; // Skip closing quote
                    result.add(new Token(TokenType.STRING, input.substring(start, pos), start));
                    continue;
                }

                // Identifiers and keywords
                if (Character.isLetter(c) || c == '_') {
                    int start = pos;
                    while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)) ||
                            input.charAt(pos) == '_')) {
                        pos++;
                    }
                    String value = input.substring(start, pos);
                    TokenType type = KEYWORDS.contains(value.toLowerCase()) ? TokenType.KEYWORD : TokenType.IDENTIFIER;
                    result.add(new Token(type, value, start));
                    continue;
                }

                // Operators
                String twoChar = pos + 1 < input.length() ? input.substring(pos, pos + 2) : "";
                if (twoChar.equals("==") || twoChar.equals("!=") || twoChar.equals(">=") ||
                        twoChar.equals("<=")) {
                    result.add(new Token(TokenType.OPERATOR, twoChar, pos));
                    pos += 2;
                    continue;
                }

                if (c == '>' || c == '<' || c == '=') {
                    result.add(new Token(TokenType.OPERATOR, String.valueOf(c), pos));
                    pos++;
                    continue;
                }

                // Special characters
                switch (c) {
                    case '(':
                        result.add(new Token(TokenType.LPAREN, "(", pos));
                        break;
                    case ')':
                        result.add(new Token(TokenType.RPAREN, ")", pos));
                        break;
                    case '[':
                        result.add(new Token(TokenType.LBRACKET, "[", pos));
                        break;
                    case ']':
                        result.add(new Token(TokenType.RBRACKET, "]", pos));
                        break;
                    case ',':
                        result.add(new Token(TokenType.COMMA, ",", pos));
                        break;
                    case ';':
                        result.add(new Token(TokenType.SEMICOLON, ";", pos));
                        break;
                    default:
                        throw new RuntimeException("Unexpected character '" + c + "' at position " + pos);
                }
                pos++;
            }

            result.add(new Token(TokenType.EOF, "", pos));
            return result;
        }

        public Token peek() {
            if (position >= tokens.size()) {
                return tokens.get(tokens.size() - 1); // EOF token
            }
            return tokens.get(position);
        }

        public Token consume() {
            Token token = peek();
            if (token.getType() != TokenType.EOF) {
                position++;
            }
            return token;
        }

        public boolean isAtEnd() {
            return peek().getType() == TokenType.EOF;
        }
    }

    // Token class
    private static final class Token {
        private final TokenType type;
        private final String value;
        private final int position;

        public Token(TokenType type, String value, int position) {
            this.type = type;
            this.value = value;
            this.position = position;
        }

        public TokenType getType() { return type; }
        public String getValue() { return value; }
        public int getPosition() { return position; }

        @Override
        public String toString() {
            return type + "(" + value + ")";
        }
    }

    // Token types
    private enum TokenType {
        // Literals
        NUMBER, STRING, IDENTIFIER,

        // Keywords
        KEYWORD, // and, or, not, in, then, true, false, null

        // Operators
        OPERATOR, // ==, !=, >, <, >=, <=

        // Punctuation
        LPAREN, RPAREN,    // ( )
        LBRACKET, RBRACKET, // [ ]
        COMMA, SEMICOLON,   // , ;

        // End of file
        EOF
    }
}