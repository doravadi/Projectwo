// MatchRisk.java - Match risk assessment
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.io.Serializable;

public final class MatchRisk implements Serializable {

    private static final long serialVersionUID = 1L;

    private final RiskLevel level;
    private final List<String> factors;

    private MatchRisk(Builder builder) {
        this.level = builder.level;
        this.factors = Collections.unmodifiableList(new ArrayList<>(builder.factors));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static MatchRisk low() {
        return builder().level(RiskLevel.LOW).build();
    }

    public static MatchRisk medium() {
        return builder().level(RiskLevel.MEDIUM).build();
    }

    public static MatchRisk high() {
        return builder().level(RiskLevel.HIGH).build();
    }

    // Getters
    public RiskLevel getLevel() { return level; }
    public List<String> getFactors() { return factors; }

    // Business logic
    public boolean isHighRisk() {
        return level == RiskLevel.HIGH || level == RiskLevel.CRITICAL;
    }

    public boolean isLowRisk() {
        return level == RiskLevel.LOW;
    }

    public int getRiskScore() {
        return switch (level) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case CRITICAL -> 4;
        };
    }

    public static class Builder {
        private RiskLevel level = RiskLevel.LOW;
        private final List<String> factors = new ArrayList<>();

        public Builder level(RiskLevel level) {
            this.level = level;
            return this;
        }

        public Builder addFactor(String factor) {
            this.factors.add(factor);
            // Auto-adjust risk level based on factors
            if (this.level == RiskLevel.LOW && !factors.isEmpty()) {
                this.level = RiskLevel.MEDIUM;
            }
            if (factors.size() > 2) {
                this.level = RiskLevel.HIGH;
            }
            return this;
        }

        public MatchRisk build() {
            return new MatchRisk(this);
        }
    }
}