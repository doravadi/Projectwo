// AuthStatus.java - Authorization status enum
public enum AuthStatus {

    PENDING("Pending", "Authorization request sent, awaiting response"),
    APPROVED("Approved", "Authorization approved, funds reserved"),
    DECLINED("Declined", "Authorization declined by issuer"),
    EXPIRED("Expired", "Authorization expired without presentment"),
    CAPTURED("Captured", "Authorization captured/settled"),
    PARTIALLY_CAPTURED("Partially Captured", "Authorization partially captured"),
    REVERSED("Reversed", "Authorization reversed/voided"),
    TIMEOUT("Timeout", "Authorization timed out");

    private static final long serialVersionUID = 1L;

    private final String displayName;
    private final String description;

    AuthStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if authorization is in final state (cannot be changed)
     */
    public boolean isFinalState() {
        return this == CAPTURED ||
                this == EXPIRED ||
                this == REVERSED ||
                this == TIMEOUT;
    }

    /**
     * Check if authorization can be matched with presentment
     */
    public boolean canBeMatched() {
        return this == APPROVED;
    }

    /**
     * Check if authorization can be captured
     */
    public boolean canBeCaptured() {
        return this == APPROVED || this == PARTIALLY_CAPTURED;
    }

    /**
     * Check if authorization can be reversed
     */
    public boolean canBeReversed() {
        return this == APPROVED ||
                this == PARTIALLY_CAPTURED ||
                this == PENDING;
    }

    /**
     * Get next possible states from current state
     */
    public AuthStatus[] getNextPossibleStates() {
        return switch (this) {
            case PENDING -> new AuthStatus[]{APPROVED, DECLINED, TIMEOUT};
            case APPROVED -> new AuthStatus[]{CAPTURED, PARTIALLY_CAPTURED, REVERSED, EXPIRED};
            case PARTIALLY_CAPTURED -> new AuthStatus[]{CAPTURED, REVERSED};
            case DECLINED, EXPIRED, CAPTURED, REVERSED, TIMEOUT -> new AuthStatus[]{}; // Final states
        };
    }

    /**
     * Parse status from string (case-insensitive)
     */
    public static AuthStatus fromString(String status) {
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Auth status cannot be null or empty");
        }

        String normalizedStatus = status.trim().toUpperCase().replace(" ", "_");

        try {
            return AuthStatus.valueOf(normalizedStatus);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown auth status: " + status);
        }
    }

    /**
     * Check if status transition is valid
     */
    public boolean canTransitionTo(AuthStatus newStatus) {
        AuthStatus[] possibleStates = getNextPossibleStates();

        for (AuthStatus possibleState : possibleStates) {
            if (possibleState == newStatus) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return displayName;
    }
}