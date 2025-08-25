
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


    public boolean isFinalState() {
        return this == SETTLED ||
                this == REJECTED ||
                this == CANCELLED ||
                this == EXPIRED ||
                this == FAILED;
    }


    public boolean isSuccessfullyCompleted() {
        return this == SETTLED || this == PARTIAL_SETTLED;
    }


    public boolean canBeMatched() {
        return this == PENDING || this == PROCESSING;
    }


    public boolean canBeSettled() {
        return this == MATCHED || this == PARTIAL_SETTLED;
    }


    public boolean canBeCancelled() {
        return this == PENDING ||
                this == MATCHED ||
                this == PROCESSING;
    }


    public boolean canBeDisputed() {
        return this == SETTLED || this == PARTIAL_SETTLED;
    }


    public boolean hasFailedOrBeenRejected() {
        return this == REJECTED || this == FAILED || this == RETURNED;
    }


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

            case REJECTED, CANCELLED, EXPIRED, FAILED, RETURNED -> new PresentmentStatus[]{};
        };
    }


    public boolean canTransitionTo(PresentmentStatus newStatus) {
        if (this == newStatus) {
            return true;
        }

        PresentmentStatus[] possibleStates = getNextPossibleStates();

        for (PresentmentStatus possibleState : possibleStates) {
            if (possibleState == newStatus) {
                return true;
            }
        }

        return false;
    }


    public int getProcessingPriority() {
        return switch (this) {
            case DISPUTED -> 10;
            case RETURNED -> 8;
            case FAILED -> 8;
            case PROCESSING -> 6;
            case MATCHED -> 5;
            case PENDING -> 3;
            case PARTIAL_SETTLED -> 2;
            default -> 1;
        };
    }


    public boolean isErrorStatus() {
        return this == REJECTED ||
                this == FAILED ||
                this == RETURNED ||
                this == EXPIRED;
    }


    public boolean isActiveProcessing() {
        return this == PROCESSING ||
                this == PENDING ||
                this == MATCHED;
    }


    public int getTypicalProcessingTimeHours() {
        return switch (this) {
            case PENDING -> 2;
            case PROCESSING -> 1;
            case MATCHED -> 24;
            case PARTIAL_SETTLED -> 48;
            case DISPUTED -> 2160;
            default -> 0;
        };
    }


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


    public static PresentmentStatus[] getManualInterventionRequired() {
        return new PresentmentStatus[]{
                DISPUTED, FAILED, RETURNED, REJECTED
        };
    }


    public static PresentmentStatus[] getActiveStatuses() {
        return new PresentmentStatus[]{
                PENDING, PROCESSING, MATCHED, PARTIAL_SETTLED
        };
    }


    public double getSettlementImpact() {
        return switch (this) {
            case SETTLED -> 1.0;
            case PARTIAL_SETTLED -> 0.5;
            case DISPUTED -> -0.5;
            case RETURNED -> -1.0;
            case REJECTED -> -0.2;
            case FAILED -> -0.1;
            default -> 0.0;
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}