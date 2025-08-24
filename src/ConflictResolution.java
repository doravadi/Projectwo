
public enum ConflictResolution {
    SKIP_CONFLICTS("Skip conflicting ranges"),
    MERGE_RANGES("Merge overlapping ranges"),
    FAIL_ON_CONFLICT("Fail on first conflict");

    private static final long serialVersionUID = 1L;

    private final String description;

    ConflictResolution(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}