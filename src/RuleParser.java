
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;


public final class RuleParser {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");


    private static final Set<String> KEYWORDS = Set.of(
            "and", "or", "not", "in", "not_in", "between", "then",
            "true", "false", "null"
    );


    private static final Set<String> VALID_FIELDS = Set.of(
            "amount", "currency", "mcc", "country", "city", "hour", "day",
            "customer_age", "customer_segment", "account_balance",
            "monthly_spending", "channel", "transaction_type"
    );

    private Tokenizer tokenizer;
    private String originalExpression;

    public RuleParser() {

    }


    public ASTNode parse(String expression) throws RuleSyntaxException {
        Objects.requireNonNull(expression, "Expression cannot be null");

        this.originalExpression = expression.trim();
        if (originalExpression.isEmpty()) {
            throw RuleSyntaxException.incompleteExpression("");
        }

        this.tokenizer = new Tokenizer(originalExpression);

        try {
            ASTNode ast = parseExpression();


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


    private ASTNode parseExpression() throws RuleSyntaxException {
        ASTNode condition = parseOrCondition();


        if (tokenizer.peek().getType() == TokenType.KEYWORD &&
                "then".equals(tokenizer.peek().getValue())) {
            tokenizer.consume();

            while (!tokenizer.isAtEnd()) {
                tokenizer.consume();
            }
        }

        return condition;
    }


    private ASTNode parseOrCondition() throws RuleSyntaxException {
        ASTNode left = parseAndCondition();

        while (tokenizer.peek().getType() == TokenType.KEYWORD &&
                "or".equals(tokenizer.peek().getValue())) {
            tokenizer.consume();
            ASTNode right = parseAndCondition();
            left = new ASTNode.OrNode(left, right);
        }

        return left;
    }


    private ASTNode parseAndCondition() throws RuleSyntaxException {
        ASTNode left = parseNotCondition();

        while (tokenizer.peek().getType() == TokenType.KEYWORD &&
                "and".equals(tokenizer.peek().getValue())) {
            tokenizer.consume();
            ASTNode right = parseNotCondition();
            left = new ASTNode.AndNode(left, right);
        }

        return left;
    }


    private ASTNode parseNotCondition() throws RuleSyntaxException {
        if (tokenizer.peek().getType() == TokenType.KEYWORD &&
                "not".equals(tokenizer.peek().getValue())) {
            tokenizer.consume();
            ASTNode operand = parseComparison();
            return new ASTNode.NotNode(operand);
        }

        return parseComparison();
    }


    private ASTNode parseComparison() throws RuleSyntaxException {
        ASTNode left = parseOperand();

        Token operator = tokenizer.peek();


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
                        return new ASTNode.GreaterThanNode(right, left);
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
                    tokenizer.consume();
                    ASTNode rightOperand = parseOperand();
                    return new ASTNode.InNode(left, rightOperand);
                }
                break;
        }

        return left;
    }


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

                String stringValue = token.getValue();
                if (stringValue.startsWith("'") && stringValue.endsWith("'")) {
                    stringValue = stringValue.substring(1, stringValue.length() - 1);
                }
                return new ASTNode.StringNode(stringValue);

            case KEYWORD:
                if ("true".equals(token.getValue()) || "false".equals(token.getValue())) {
                    tokenizer.consume();
                    return new ASTNode.StringNode(token.getValue());
                }

                tokenizer.consume();
                return new ASTNode.EnumNode(token.getValue());

            case LPAREN:
                tokenizer.consume();
                ASTNode expression = parseOrCondition();
                Token rparen = tokenizer.peek();
                if (rparen.getType() != TokenType.RPAREN) {
                    throw RuleSyntaxException.expectedToken(originalExpression,
                            rparen.getPosition(), ")", rparen.getValue());
                }
                tokenizer.consume();
                return expression;

            case LBRACKET:
                return parseList();

            default:
                throw RuleSyntaxException.unexpectedToken(originalExpression,
                        token.getPosition(), token.getValue());
        }
    }


    private ASTNode parseField() throws RuleSyntaxException {
        Token token = tokenizer.peek();
        if (token.getType() != TokenType.IDENTIFIER) {
            throw RuleSyntaxException.expectedToken(originalExpression,
                    token.getPosition(), "field name", token.getValue());
        }

        tokenizer.consume();
        String fieldName = token.getValue();


        if (!VALID_FIELDS.contains(fieldName.toLowerCase())) {
            throw RuleSyntaxException.invalidFieldName(originalExpression,
                    token.getPosition(), fieldName);
        }

        return new ASTNode.FieldNode(fieldName);
    }


    private ASTNode parseList() throws RuleSyntaxException {
        Token lbracket = tokenizer.peek();
        if (lbracket.getType() != TokenType.LBRACKET) {
            throw RuleSyntaxException.expectedToken(originalExpression,
                    lbracket.getPosition(), "[", lbracket.getValue());
        }
        tokenizer.consume();

        List<ASTNode> elements = new ArrayList<>();


        if (tokenizer.peek().getType() == TokenType.RBRACKET) {
            tokenizer.consume();
            return new ASTNode.ListNode(elements);
        }


        elements.add(parseListElement());


        while (tokenizer.peek().getType() == TokenType.COMMA) {
            tokenizer.consume();
            elements.add(parseListElement());
        }

        Token rbracket = tokenizer.peek();
        if (rbracket.getType() != TokenType.RBRACKET) {
            throw RuleSyntaxException.expectedToken(originalExpression,
                    rbracket.getPosition(), "]", rbracket.getValue());
        }
        tokenizer.consume();

        return new ASTNode.ListNode(elements);
    }


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


                if (Character.isWhitespace(c)) {
                    pos++;
                    continue;
                }


                if (Character.isDigit(c) || (c == '-' && pos + 1 < input.length() &&
                        Character.isDigit(input.charAt(pos + 1)))) {
                    int start = pos;
                    if (c == '-') pos++;
                    while (pos < input.length() && (Character.isDigit(input.charAt(pos)) ||
                            input.charAt(pos) == '.')) {
                        pos++;
                    }
                    result.add(new Token(TokenType.NUMBER, input.substring(start, pos), start));
                    continue;
                }


                if (c == '\'' || c == '"') {
                    int start = pos;
                    pos++;
                    while (pos < input.length() && input.charAt(pos) != c) {
                        pos++;
                    }
                    if (pos >= input.length()) {
                        throw new RuntimeException("Unterminated string at position " + start);
                    }
                    pos++;
                    result.add(new Token(TokenType.STRING, input.substring(start, pos), start));
                    continue;
                }


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
                return tokens.get(tokens.size() - 1);
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


    private static final class Token {
        private final TokenType type;
        private final String value;
        private final int position;

        public Token(TokenType type, String value, int position) {
            this.type = type;
            this.value = value;
            this.position = position;
        }

        public TokenType getType() {
            return type;
        }

        public String getValue() {
            return value;
        }

        public int getPosition() {
            return position;
        }

        @Override
        public String toString() {
            return type + "(" + value + ")";
        }
    }


    private enum TokenType {

        NUMBER, STRING, IDENTIFIER,


        KEYWORD,


        OPERATOR,


        LPAREN, RPAREN,
        LBRACKET, RBRACKET,
        COMMA, SEMICOLON,


        EOF
    }
}