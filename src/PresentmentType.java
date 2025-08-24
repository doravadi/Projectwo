// PresentmentType.java - Presentment type enum
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
    private final boolean increasesBalance; // true if adds to merchant balance, false if deducts

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

    /**
     * Check if this presentment type requires a matching authorization
     */
    public boolean requiresAuthorization() {
        return switch (this) {
            case SALE, PARTIAL_CAPTURE, CASH_ADVANCE, QUASI_CASH -> true;
            case REFUND, ADJUSTMENT, REVERSAL -> false; // May have auth but not required
            case CHARGEBACK, CHARGEBACK_REVERSAL, REPRESENTMENT -> true;
        };
    }

    /**
     * Check if this presentment type can be matched with auths
     */
    public boolean canBeMatched() {
        return switch (this) {
            case SALE, PARTIAL_CAPTURE, CASH_ADVANCE, QUASI_CASH, REPRESENTMENT -> true;
            case REFUND, ADJUSTMENT, REVERSAL, CHARGEBACK, CHARGEBACK_REVERSAL -> false;
        };
    }

    /**
     * Get expected amount relationship with authorization
     */
    public AmountRelationship getExpectedAmountRelationship() {
        return switch (this) {
            case SALE, CASH_ADVANCE, QUASI_CASH -> AmountRelationship.EQUAL_OR_SLIGHTLY_MORE; // Tips allowed
            case PARTIAL_CAPTURE -> AmountRelationship.LESS_THAN_OR_EQUAL;
            case REFUND, REVERSAL -> AmountRelationship.NEGATIVE;
            case ADJUSTMENT -> AmountRelationship.ANY; // Can be positive or negative
            case CHARGEBACK -> AmountRelationship.NEGATIVE;
            case CHARGEBACK_REVERSAL, REPRESENTMENT -> AmountRelationship.POSITIVE;
        };
    }

    /**
     * Get settlement timeframe (in hours after auth)
     */
    public int getTypicalSettlementHours() {
        return switch (this) {
            case SALE, PARTIAL_CAPTURE -> 24; // Next business day
            case CASH_ADVANCE, QUASI_CASH -> 2; // Almost immediate
            case REFUND -> 72; // 3 days typical
            case ADJUSTMENT -> 168; // Within a week
            case REVERSAL -> 1; // Same day
            case CHARGEBACK -> 8760; // Can be months later (365 days)
            case CHARGEBACK_REVERSAL, REPRESENTMENT -> 720; // 30 days
        };
    }

    /**
     * Check if presentment type is a dispute-related transaction
     */
    public boolean isDisputeRelated() {
        return switch (this) {
            case CHARGEBACK, CHARGEBACK_REVERSAL, REPRESENTMENT -> true;
            default -> false;
        };
    }

    /**
     * Check if presentment type affects merchant risk score
     */
    public boolean affectsRiskScore() {
        return switch (this) {
            case CHARGEBACK -> true; // Increases risk
            case CHARGEBACK_REVERSAL, REPRESENTMENT -> true; // Decreases risk
            case REFUND -> true; // May increase risk if frequent
            default -> false;
        };
    }

    /**
     * Get risk impact (-1 = decreases risk, 0 = neutral, 1 = increases risk)
     */
    public int getRiskImpact() {
        return switch (this) {
            case CHARGEBACK -> 1; // Bad for merchant
            case CHARGEBACK_REVERSAL, REPRESENTMENT -> -1; // Good for merchant
            case REFUND -> 1; // Slightly bad if frequent
            default -> 0; // Neutral
        };
    }

    /**
     * Parse presentment type from string (case-insensitive)
     */
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

    /**
     * Get all types that increase merchant balance
     */
    public static PresentmentType[] getBalanceIncreasingTypes() {
        return new PresentmentType[]{
                SALE, PARTIAL_CAPTURE, ADJUSTMENT, CHARGEBACK_REVERSAL,
                REPRESENTMENT, CASH_ADVANCE, QUASI_CASH
        };
    }

    /**
     * Get all types that decrease merchant balance
     */
    public static PresentmentType[] getBalanceDecreasingTypes() {
        return new PresentmentType[]{
                REFUND, REVERSAL, CHARGEBACK
        };
    }

    @Override
    public String toString() {
        return displayName;
    }

    // Inner enum for amount relationships
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