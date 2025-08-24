
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.io.Serializable;


public final class AuthPresentmentMatch implements Comparable<AuthPresentmentMatch>, Serializable {

    private static final long serialVersionUID = 1L;

    private final Auth auth;
    private final Presentment presentment;
    private final double score;
    private final LocalDateTime matchTimestamp;
    private final MatchQuality quality;
    private final MatchValidation validation;
    private final MatchRisk risk;

    
    private AuthPresentmentMatch(Auth auth, Presentment presentment, double score) {
        this.auth = Objects.requireNonNull(auth, "Auth cannot be null");
        this.presentment = Objects.requireNonNull(presentment, "Presentment cannot be null");
        this.score = validateScore(score);
        this.matchTimestamp = LocalDateTime.now();
        this.quality = calculateQuality(score);
        this.validation = validateMatch(auth, presentment);
        this.risk = assessRisk(auth, presentment, score);

        
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid match: " + validation.getErrorMessage());
        }
    }

    
    public static AuthPresentmentMatch of(Auth auth, Presentment presentment) {
        double score = auth.calculateMatchingScore(presentment);
        return new AuthPresentmentMatch(auth, presentment, score);
    }

    
    public static AuthPresentmentMatch withScore(Auth auth, Presentment presentment, double score) {
        return new AuthPresentmentMatch(auth, presentment, score);
    }

    
    public Auth getAuth() { return auth; }
    public Presentment getPresentment() { return presentment; }
    public double getScore() { return score; }
    public LocalDateTime getMatchTimestamp() { return matchTimestamp; }
    public MatchQuality getQuality() { return quality; }
    public MatchValidation getValidation() { return validation; }
    public MatchRisk getRisk() { return risk; }

    
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

    
    public Money getSettlementAmount() {
        
        
        Money authAmount = auth.getAmount();
        Money presentmentAmount = presentment.getAmount();

        
        if (presentment.isRefund()) {
            return presentmentAmount;
        }

        
        if (presentmentAmount.getAmount().compareTo(authAmount.getAmount()) <= 0) {
            return presentmentAmount;
        } else {
            return authAmount; 
        }
    }

    
    public Money getAmountVariance() {
        return presentment.getAmount().subtract(auth.getAmount());
    }

    
    public Money getAbsoluteAmountVariance() {
        Money variance = getAmountVariance();
        if (variance.isNegative()) {
            return Money.of(variance.getAmount().negate(), variance.getCurrency());
        }
        return variance;
    }

    
    public Duration getTimeDifference() {
        return Duration.between(auth.getTimestamp(), presentment.getTimestamp());
    }

    
    public long getTimeDifferenceHours() {
        return getTimeDifference().toHours();
    }

    
    public boolean isSuspiciouslyFast() {
        return getTimeDifferenceHours() < 1 && score > 80;
    }

    
    public boolean isSuspiciouslySlow() {
        return getTimeDifferenceHours() > 168; 
    }

    
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

    
    public MatchSummary generateSummary() {
        return MatchSummary.builder()
                .authId(auth.getAuthId())
                .presentmentId(presentment.getPresentmentId())
                .cardId(CardId.fromCardNumber(auth.getCardNumber(), "12", "25")) 
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

        
        if (!auth.getCardNumber().equals(presentment.getCardNumber())) {
            builder.addError("Card number mismatch");
            return builder.build();
        }

        
        if (!auth.getAmount().getCurrency().equals(presentment.getAmount().getCurrency())) {
            builder.addError("Currency mismatch");
        }

        
        if (auth.isExpired()) {
            builder.addError("Authorization has expired");
        }

        
        Money authAmount = auth.getAmount();
        Money presAmount = presentment.getAmount();

        if (presentment.isSale() && presAmount.getAmount().compareTo(authAmount.getAmount()) > 0) {
            
            Money tipTolerance = authAmount.multiply(new java.math.BigDecimal("1.15"));
            if (presAmount.getAmount().compareTo(tipTolerance.getAmount()) > 0) {
                builder.addWarning("Presentment amount significantly exceeds auth amount");
            } else {
                builder.addInfo("Presentment includes tip within tolerance");
            }
        }

        
        if (presentment.getTimestamp().isBefore(auth.getTimestamp())) {
            builder.addWarning("Presentment timestamp before auth timestamp");
        }

        Duration timeDiff = Duration.between(auth.getTimestamp(), presentment.getTimestamp());
        if (timeDiff.toDays() > 30) {
            builder.addWarning("Very long time gap between auth and presentment");
        }

        
        if (!auth.getMerchantId().equals(presentment.getMerchantId())) {
            builder.addWarning("Merchant ID mismatch");
        }

        
        if (!auth.getMccCode().equals(presentment.getMccCode())) {
            builder.addInfo("MCC code mismatch (may be normal)");
        }

        return builder.build();
    }

    private MatchRisk assessRisk(Auth auth, Presentment presentment, double score) {
        MatchRisk.Builder builder = MatchRisk.builder();

        
        if (score < 50) {
            builder.addFactor("Very low matching score");
        } else if (score < 70) {
            builder.addFactor("Low matching score");
        }

        
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

        
        long hoursGap = getTimeDifferenceHours();
        if (hoursGap < 0) {
            builder.addFactor("Presentment before authorization");
        } else if (hoursGap > 720) { 
            builder.addFactor("Very delayed presentment");
        }

        
        

        return builder.build();
    }

    
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
        
        int scoreComparison = Double.compare(other.score, this.score);
        if (scoreComparison != 0) {
            return scoreComparison;
        }

        
        return other.auth.getTimestamp().compareTo(this.auth.getTimestamp());
    }

    @Override
    public String toString() {
        return String.format("AuthPresentmentMatch[auth=%s, presentment=%s, score=%.1f, quality=%s]",
                auth.getAuthId(), presentment.getPresentmentId(), score, quality);
    }

    
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