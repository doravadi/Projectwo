
public enum PresentmentType {

    SALE("Sale", "Regular sale transaction", true),
    REFUND("Refund", "Refund/return transaction", false),
    PARTIAL_CAPTURE("Partial Capture", "Partial capture of authorization", true),
    ADJUSTMENT("Adjustment", "Transaction adjustment", true),
    REVERSAL("Reversal", "Transaction reversal", false),
    CHARGEBACK("Chargeback", "Chargeback presentment", false),
    CHARGEBACK_REVERSAL("Chargeback Reversal", "Reversal of chargeback", true),
    REPRESENTMENT("Representment", "Re-presentment after chargeback", true),
    CASH_ADVANCE("Cash Advance", "Cash advance transaction", true),
    QUASI_CASH("Quasi Cash", "Quasi-cash transaction", true);

    private static final long serialVersionUID = 1L;

    private final String displayName;
    private final String description;
    private final boolean increasesBalance;

    PresentmentType(String displayName, String description, boolean increasesBalance) {
        this.displayName = displayName;
        this.description = description;
        this.increasesBalance = increasesBalance;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean increasesBalance() {
        return increasesBalance;
    }

    public boolean decreasesBalance() {
        return !increasesBalance;
    }


    public boolean requiresAuthorization() {
        return switch (this) {
            case SALE, PARTIAL_CAPTURE, CASH_ADVANCE, QUASI_CASH -> true;
            case REFUND, ADJUSTMENT, REVERSAL -> false;
            case CHARGEBACK, CHARGEBACK_REVERSAL, REPRESENTMENT -> true;
        };
    }


    public boolean canBeMatched() {
        return switch (this) {
            case SALE, PARTIAL_CAPTURE, CASH_ADVANCE, QUASI_CASH, REPRESENTMENT -> true;
            case REFUND, ADJUSTMENT, REVERSAL, CHARGEBACK, CHARGEBACK_REVERSAL -> false;
        };
    }


    public AmountRelationship getExpectedAmountRelationship() {
        return switch (this) {
            case SALE, CASH_ADVANCE, QUASI_CASH -> AmountRelationship.EQUAL_OR_SLIGHTLY_MORE;
            case PARTIAL_CAPTURE -> AmountRelationship.LESS_THAN_OR_EQUAL;
            case REFUND, REVERSAL -> AmountRelationship.NEGATIVE;
            case ADJUSTMENT -> AmountRelationship.ANY;
            case CHARGEBACK -> AmountRelationship.NEGATIVE;
            case CHARGEBACK_REVERSAL, REPRESENTMENT -> AmountRelationship.POSITIVE;
        };
    }


    public int getTypicalSettlementHours() {
        return switch (this) {
            case SALE, PARTIAL_CAPTURE -> 24;
            case CASH_ADVANCE, QUASI_CASH -> 2;
            case REFUND -> 72;
            case ADJUSTMENT -> 168;
            case REVERSAL -> 1;
            case CHARGEBACK -> 8760;
            case CHARGEBACK_REVERSAL, REPRESENTMENT -> 720;
        };
    }


    public boolean isDisputeRelated() {
        return switch (this) {
            case CHARGEBACK, CHARGEBACK_REVERSAL, REPRESENTMENT -> true;
            default -> false;
        };
    }


    public boolean affectsRiskScore() {
        return switch (this) {
            case CHARGEBACK -> true;
            case CHARGEBACK_REVERSAL, REPRESENTMENT -> true;
            case REFUND -> true;
            default -> false;
        };
    }


    public int getRiskImpact() {
        return switch (this) {
            case CHARGEBACK -> 1;
            case CHARGEBACK_REVERSAL, REPRESENTMENT -> -1;
            case REFUND -> 1;
            default -> 0;
        };
    }


    public static PresentmentType fromString(String type) {
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Presentment type cannot be null or empty");
        }

        String normalizedType = type.trim().toUpperCase().replace(" ", "_").replace("-", "_");

        try {
            return PresentmentType.valueOf(normalizedType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown presentment type: " + type);
        }
    }


    public static PresentmentType[] getBalanceIncreasingTypes() {
        return new PresentmentType[]{
                SALE, PARTIAL_CAPTURE, ADJUSTMENT, CHARGEBACK_REVERSAL,
                REPRESENTMENT, CASH_ADVANCE, QUASI_CASH
        };
    }


    public static PresentmentType[] getBalanceDecreasingTypes() {
        return new PresentmentType[]{
                REFUND, REVERSAL, CHARGEBACK
        };
    }

    @Override
    public String toString() {
        return displayName;
    }


    public enum AmountRelationship {
        EQUAL_OR_SLIGHTLY_MORE("Equal or slightly more than auth"),
        LESS_THAN_OR_EQUAL("Less than or equal to auth"),
        NEGATIVE("Negative amount (credit)"),
        POSITIVE("Positive amount (debit)"),
        ANY("Any amount relationship");

        private final String description;

        AmountRelationship(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}