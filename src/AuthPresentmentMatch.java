// AuthPresentmentMatch.java - Individual auth-presentment match pair
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.io.Serializable;

/**
 * Immutable representation of a matched auth-presentment pair
 * Contains the match details, scoring, and business validation
 */
public final class AuthPresentmentMatch implements Comparable<AuthPresentmentMatch>, Serializable {

    private static final long serialVersionUID = 1L;

    private final Auth auth;
    private final Presentment presentment;
    private final double score;
    private final LocalDateTime matchTimestamp;
    private final MatchQuality quality;
    private final MatchValidation validation;
    private final MatchRisk risk;

    // Private constructor - use factory methods
    private AuthPresentmentMatch(Auth auth, Presentment presentment, double score) {
        this.auth = Objects.requireNonNull(auth, "Auth cannot be null");
        this.presentment = Objects.requireNonNull(presentment, "Presentment cannot be null");
        this.score = validateScore(score);
        this.matchTimestamp = LocalDateTime.now();
        this.quality = calculateQuality(score);
        this.validation = validateMatch(auth, presentment);
        this.risk = assessRisk(auth, presentment, score);

        // Business rule validation
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid match: " + validation.getErrorMessage());
        }
    }

    /**
     * Create match with calculated score
     */
    public static AuthPresentmentMatch of(Auth auth, Presentment presentment) {
        double score = auth.calculateMatchingScore(presentment);
        return new AuthPresentmentMatch(auth, presentment, score);
    }

    /**
     * Create match with explicit score (for algorithm results)
     */
    public static AuthPresentmentMatch withScore(Auth auth, Presentment presentment, double score) {
        return new AuthPresentmentMatch(auth, presentment, score);
    }

    // Getters
    public Auth getAuth() { return auth; }
    public Presentment getPresentment() { return presentment; }
    public double getScore() { return score; }
    public LocalDateTime getMatchTimestamp() { return matchTimestamp; }
    public MatchQuality getQuality() { return quality; }
    public MatchValidation getValidation() { return validation; }
    public MatchRisk getRisk() { return risk; }

    // Business logic methods
    public boolean isHighQuality() {
        return quality == MatchQuality.EXCELLENT || quality == MatchQuality.GOOD;
    }

    public boolean isLowQuality() {
        return quality == MatchQuality.POOR;
    }

    public boolean requiresManualReview() {
        return isLowQuality() || risk.getLevel() == RiskLevel.HIGH || risk.getLevel() == RiskLevel.CRITICAL || !validation.isFullyValid();
    }

    public boolean isValid() {
        return validation.isValid();
    }

    public boolean canBeSettled() {
        return isValid() && auth.canBeMatched() && presentment.canBeMatched();
    }

    /**
     * Calculate potential settlement amount
     */
    public Money getSettlementAmount() {
        // Settlement amount is typically the presentment amount
        // but cannot exceed the auth amount
        Money authAmount = auth.getAmount();
        Money presentmentAmount = presentment.getAmount();

        // For refunds, use presentment amount directly
        if (presentment.isRefund()) {
            return presentmentAmount;
        }

        // For sales, use minimum of auth and presentment (prevent overcharging)
        if (presentmentAmount.getAmount().compareTo(authAmount.getAmount()) <= 0) {
            return presentmentAmount;
        } else {
            return authAmount; // Cap at authorized amount
        }
    }

    /**
     * Calculate amount variance (presentment vs auth)
     */
    public Money getAmountVariance() {
        return presentment.getAmount().subtract(auth.getAmount());
    }

    /**
     * Get absolute amount variance
     */
    public Money getAbsoluteAmountVariance() {
        Money variance = getAmountVariance();
        if (variance.isNegative()) {
            return Money.of(variance.getAmount().negate(), variance.getCurrency());
        }
        return variance;
    }

    /**
     * Calculate time gap between auth and presentment
     */
    public Duration getTimeDifference() {
        return Duration.between(auth.getTimestamp(), presentment.getTimestamp());
    }

    /**
     * Get time difference in hours
     */
    public long getTimeDifferenceHours() {
        return getTimeDifference().toHours();
    }

    /**
     * Check if presentment came unusually fast after auth
     */
    public boolean isSuspiciouslyFast() {
        return getTimeDifferenceHours() < 1 && score > 80;
    }

    /**
     * Check if presentment came unusually slow after auth
     */
    public boolean isSuspiciouslySlow() {
        return getTimeDifferenceHours() > 168; // More than 1 week
    }

    /**
     * Get match confidence level
     */
    public MatchConfidence getConfidence() {
        if (score >= 95 && validation.isFullyValid()) {
            return MatchConfidence.VERY_HIGH;
        } else if (score >= 85 && validation.isValid()) {
            return MatchConfidence.HIGH;
        } else if (score >= 70 && validation.isValid()) {
            return MatchConfidence.MEDIUM;
        } else if (score >= 50) {
            return MatchConfidence.LOW;
        } else {
            return MatchConfidence.VERY_LOW;
        }
    }

    /**
     * Generate match summary for reporting
     */
    public MatchSummary generateSummary() {
        return MatchSummary.builder()
                .authId(auth.getAuthId())
                .presentmentId(presentment.getPresentmentId())
                .cardId(CardId.fromCardNumber(auth.getCardNumber(), "12", "25")) // Simplified
                .score(score)
                .quality(quality)
                .confidence(getConfidence())
                .settlementAmount(getSettlementAmount())
                .timeDifferenceHours(getTimeDifferenceHours())
                .requiresReview(requiresManualReview())
                .validationIssues(validation.getIssues())
                .riskFactors(risk.getFactors())
                .build();
    }

    // Private helper methods
    private double validateScore(double score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Score must be between 0 and 100: " + score);
        }
        return score;
    }

    private MatchQuality calculateQuality(double score) {
        if (score >= 90) return MatchQuality.EXCELLENT;
        if (score >= 75) return MatchQuality.GOOD;
        if (score >= 50) return MatchQuality.FAIR;
        return MatchQuality.POOR;
    }

    private MatchValidation validateMatch(Auth auth, Presentment presentment) {
        MatchValidation.Builder builder = MatchValidation.builder();

        // Card number must match
        if (!auth.getCardNumber().equals(presentment.getCardNumber())) {
            builder.addError("Card number mismatch");
            return builder.build();
        }

        // Currency must match
        if (!auth.getAmount().getCurrency().equals(presentment.getAmount().getCurrency())) {
            builder.addError("Currency mismatch");
        }

        // Auth must not be expired
        if (auth.isExpired()) {
            builder.addError("Authorization has expired");
        }

        // Presentment amount validations
        Money authAmount = auth.getAmount();
        Money presAmount = presentment.getAmount();

        if (presentment.isSale() && presAmount.getAmount().compareTo(authAmount.getAmount()) > 0) {
            // Check if it's within tip tolerance (15%)
            Money tipTolerance = authAmount.multiply(new java.math.BigDecimal("1.15"));
            if (presAmount.getAmount().compareTo(tipTolerance.getAmount()) > 0) {
                builder.addWarning("Presentment amount significantly exceeds auth amount");
            } else {
                builder.addInfo("Presentment includes tip within tolerance");
            }
        }

        // Time validations
        if (presentment.getTimestamp().isBefore(auth.getTimestamp())) {
            builder.addWarning("Presentment timestamp before auth timestamp");
        }

        Duration timeDiff = Duration.between(auth.getTimestamp(), presentment.getTimestamp());
        if (timeDiff.toDays() > 30) {
            builder.addWarning("Very long time gap between auth and presentment");
        }

        // Merchant validations
        if (!auth.getMerchantId().equals(presentment.getMerchantId())) {
            builder.addWarning("Merchant ID mismatch");
        }

        // MCC validations
        if (!auth.getMccCode().equals(presentment.getMccCode())) {
            builder.addInfo("MCC code mismatch (may be normal)");
        }

        return builder.build();
    }

    private MatchRisk assessRisk(Auth auth, Presentment presentment, double score) {
        MatchRisk.Builder builder = MatchRisk.builder();

        // Low score = high risk
        if (score < 50) {
            builder.addFactor("Very low matching score");
        } else if (score < 70) {
            builder.addFactor("Low matching score");
        }

        // Amount risks
        Money variance = getAbsoluteAmountVariance();
        Money authAmount = auth.getAmount();

        if (authAmount.isPositive()) {
            double variancePercent = variance.getAmount()
                    .divide(authAmount.getAmount(), Money.MONEY_CONTEXT)
                    .doubleValue() * 100;

            if (variancePercent > 20) {
                builder.addFactor("High amount variance: " + String.format("%.1f%%", variancePercent));
            }
        }

        // Time risks
        long hoursGap = getTimeDifferenceHours();
        if (hoursGap < 0) {
            builder.addFactor("Presentment before authorization");
        } else if (hoursGap > 720) { // 30 days
            builder.addFactor("Very delayed presentment");
        }

        // Cross-border risks
        // (This would need country code extraction from merchant data)

        return builder.build();
    }

    // Object contract methods
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        AuthPresentmentMatch that = (AuthPresentmentMatch) obj;
        return Double.compare(that.score, score) == 0 &&
                Objects.equals(auth, that.auth) &&
                Objects.equals(presentment, that.presentment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(auth, presentment, score);
    }

    @Override
    public int compareTo(AuthPresentmentMatch other) {
        // Sort by score descending (best matches first)
        int scoreComparison = Double.compare(other.score, this.score);
        if (scoreComparison != 0) {
            return scoreComparison;
        }

        // Then by auth timestamp (newer first)
        return other.auth.getTimestamp().compareTo(this.auth.getTimestamp());
    }

    @Override
    public String toString() {
        return String.format("AuthPresentmentMatch[auth=%s, presentment=%s, score=%.1f, quality=%s]",
                auth.getAuthId(), presentment.getPresentmentId(), score, quality);
    }

    // Enums for match properties
    public enum MatchQuality {
        EXCELLENT("Excellent", 90, 100),
        GOOD("Good", 75, 89),
        FAIR("Fair", 50, 74),
        POOR("Poor", 0, 49);

        private final String displayName;
        private final double minScore;
        private final double maxScore;

        MatchQuality(String displayName, double minScore, double maxScore) {
            this.displayName = displayName;
            this.minScore = minScore;
            this.maxScore = maxScore;
        }

        public String getDisplayName() { return displayName; }
        public double getMinScore() { return minScore; }
        public double getMaxScore() { return maxScore; }

        @Override
        public String toString() { return displayName; }
    }

    public enum MatchConfidence {
        VERY_HIGH("Very High", 0.95),
        HIGH("High", 0.85),
        MEDIUM("Medium", 0.70),
        LOW("Low", 0.50),
        VERY_LOW("Very Low", 0.0);

        private final String displayName;
        private final double threshold;

        MatchConfidence(String displayName, double threshold) {
            this.displayName = displayName;
            this.threshold = threshold;
        }

        public String getDisplayName() { return displayName; }
        public double getThreshold() { return threshold; }

        @Override
        public String toString() { return displayName; }
    }
}