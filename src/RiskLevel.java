// RiskLevel.java - Risk level enum (updated version)
public enum RiskLevel {
    LOW(1, "Low Risk", "Minimal risk, proceed normally"),
    MEDIUM(2, "Medium Risk", "Moderate risk, additional review recommended"),
    HIGH(3, "High Risk", "High risk, manual review required"),
    CRITICAL(4, "Critical Risk", "Critical risk, immediate attention required");

    private static final long serialVersionUID = 1L;

    private final int level;
    private final String displayName;
    private final String description;

    RiskLevel(int level, String displayName, String description) {
        this.level = level;
        this.displayName = displayName;
        this.description = description;
    }

    public int getLevel() { return level; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    public boolean isHigherThan(RiskLevel other) {
        return this.level > other.level;
    }

    public boolean isLowerThan(RiskLevel other) {
        return this.level < other.level;
    }

    @Override
    public String toString() {
        return displayName;
    }
}