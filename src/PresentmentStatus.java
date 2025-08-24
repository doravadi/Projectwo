// PresentmentStatus.java - Presentment status enum
public enum PresentmentStatus {

    PENDING("Pending", "Presentment received, awaiting processing"),
    MATCHED("Matched", "Successfully matched with authorization"),
    SETTLED("Settled", "Funds transferred to merchant account"),
    REJECTED("Rejected", "Presentment rejected due to validation failure"),
    PARTIAL_SETTLED("Partial Settled", "Partially settled amount"),
    DISPUTED("Disputed", "Under chargeback dispute"),
    RETURNED("Returned", "Returned due to insufficient funds or other issue"),
    CANCELLED("Cancelled", "Cancelled by merchant or system"),
    EXPIRED("Expired", "Expired without processing"),
    PROCESSING("Processing", "Currently being processed"),
    FAILED("Failed", "Processing failed due to system error");

    private static final long serialVersionUID = 1L;

    private final String displayName;
    private final String description;

    PresentmentStatus(String displayName, String description) {
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
     * Check if presentment is in a final state (cannot be changed)
     */
    public boolean isFinalState() {
        return this == SETTLED ||
                this == REJECTED ||
                this == CANCELLED ||
                this == EXPIRED ||
                this == FAILED;
    }

    /**
     * Check if presentment is in a successful completion state
     */
    public boolean isSuccessfullyCompleted() {
        return this == SETTLED || this == PARTIAL_SETTLED;
    }

    /**
     * Check if presentment can be matched with authorization
     */
    public boolean canBeMatched() {
        return this == PENDING || this == PROCESSING;
    }

    /**
     * Check if presentment can be settled
     */
    public boolean canBeSettled() {
        return this == MATCHED || this == PARTIAL_SETTLED;
    }

    /**
     * Check if presentment can be cancelled
     */
    public boolean canBeCancelled() {
        return this == PENDING ||
                this == MATCHED ||
                this == PROCESSING;
    }

    /**
     * Check if presentment can be disputed
     */
    public boolean canBeDisputed() {
        return this == SETTLED || this == PARTIAL_SETTLED;
    }

    /**
     * Check if presentment has failed or been rejected
     */
    public boolean hasFailedOrBeenRejected() {
        return this == REJECTED || this == FAILED || this == RETURNED;
    }

    /**
     * Get next possible states from current state
     */
    public PresentmentStatus[] getNextPossibleStates() {
        return switch (this) {
            case PENDING -> new PresentmentStatus[]{
                    MATCHED, PROCESSING, REJECTED, CANCELLED, EXPIRED
            };
            case PROCESSING -> new PresentmentStatus[]{
                    MATCHED, SETTLED, REJECTED, FAILED
            };
            case MATCHED -> new PresentmentStatus[]{
                    SETTLED, PARTIAL_SETTLED, CANCELLED, FAILED
            };
            case PARTIAL_SETTLED -> new PresentmentStatus[]{
                    SETTLED, DISPUTED, CANCELLED
            };
            case SETTLED -> new PresentmentStatus[]{
                    DISPUTED, RETURNED
            };
            case DISPUTED -> new PresentmentStatus[]{
                    SETTLED, RETURNED, CANCELLED
            };
            // Final states have no transitions
            case REJECTED, CANCELLED, EXPIRED, FAILED, RETURNED -> new PresentmentStatus[]{};
        };
    }

    /**
     * Check if status transition is valid
     */
    public boolean canTransitionTo(PresentmentStatus newStatus) {
        if (this == newStatus) {
            return true; // Same state transition always allowed
        }

        PresentmentStatus[] possibleStates = getNextPossibleStates();

        for (PresentmentStatus possibleState : possibleStates) {
            if (possibleState == newStatus) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get processing priority (higher number = higher priority)
     */
    public int getProcessingPriority() {
        return switch (this) {
            case DISPUTED -> 10; // Highest priority - disputes need immediate attention
            case RETURNED -> 8;  // High priority - failures need resolution
            case FAILED -> 8;    // High priority - system issues
            case PROCESSING -> 6; // Medium-high priority - active processing
            case MATCHED -> 5;   // Medium priority - ready for settlement
            case PENDING -> 3;   // Lower priority - awaiting matching
            case PARTIAL_SETTLED -> 2; // Low priority - partially complete
            default -> 1;        // Lowest priority - final states
        };
    }

    /**
     * Check if status indicates an error condition
     */
    public boolean isErrorStatus() {
        return this == REJECTED ||
                this == FAILED ||
                this == RETURNED ||
                this == EXPIRED;
    }

    /**
     * Check if status indicates active processing
     */
    public boolean isActiveProcessing() {
        return this == PROCESSING ||
                this == PENDING ||
                this == MATCHED;
    }

    /**
     * Get typical processing time in hours for this status
     */
    public int getTypicalProcessingTimeHours() {
        return switch (this) {
            case PENDING -> 2;        // Usually matched within 2 hours
            case PROCESSING -> 1;     // Processing should complete in 1 hour
            case MATCHED -> 24;       // Settlement within 24 hours
            case PARTIAL_SETTLED -> 48; // Complete settlement within 48 hours
            case DISPUTED -> 2160;    // 90 days for dispute resolution
            default -> 0;             // Final states don't process further
        };
    }

    /**
     * Parse status from string (case-insensitive)
     */
    public static PresentmentStatus fromString(String status) {
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Presentment status cannot be null or empty");
        }

        String normalizedStatus = status.trim().toUpperCase().replace(" ", "_");

        try {
            return PresentmentStatus.valueOf(normalizedStatus);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown presentment status: " + status);
        }
    }

    /**
     * Get all statuses that require manual intervention
     */
    public static PresentmentStatus[] getManualInterventionRequired() {
        return new PresentmentStatus[]{
                DISPUTED, FAILED, RETURNED, REJECTED
        };
    }

    /**
     * Get all statuses that are actively being processed
     */
    public static PresentmentStatus[] getActiveStatuses() {
        return new PresentmentStatus[]{
                PENDING, PROCESSING, MATCHED, PARTIAL_SETTLED
        };
    }

    /**
     * Get settlement impact on merchant account
     * 1.0 = full positive impact, 0.0 = no impact, -1.0 = full negative impact
     */
    public double getSettlementImpact() {
        return switch (this) {
            case SETTLED -> 1.0;           // Full positive settlement
            case PARTIAL_SETTLED -> 0.5;   // Partial positive settlement
            case DISPUTED -> -0.5;         // Potential negative impact
            case RETURNED -> -1.0;         // Full negative impact
            case REJECTED -> -0.2;         // Small negative impact (fees)
            case FAILED -> -0.1;           // Minimal negative impact
            default -> 0.0;                // No financial impact
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}