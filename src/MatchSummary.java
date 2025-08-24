// MatchSummary.java - Match summary for reporting
import java.util.List;
import java.io.Serializable;

public final class MatchSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String authId;
    private final String presentmentId;
    private final CardId cardId;
    private final double score;
    private final AuthPresentmentMatch.MatchQuality quality;
    private final AuthPresentmentMatch.MatchConfidence confidence;
    private final Money settlementAmount;
    private final long timeDifferenceHours;
    private final boolean requiresReview;
    private final List<String> validationIssues;
    private final List<String> riskFactors;

    private MatchSummary(Builder builder) {
        this.authId = builder.authId;
        this.presentmentId = builder.presentmentId;
        this.cardId = builder.cardId;
        this.score = builder.score;
        this.quality = builder.quality;
        this.confidence = builder.confidence;
        this.settlementAmount = builder.settlementAmount;
        this.timeDifferenceHours = builder.timeDifferenceHours;
        this.requiresReview = builder.requiresReview;
        this.validationIssues = builder.validationIssues;
        this.riskFactors = builder.riskFactors;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getAuthId() { return authId; }
    public String getPresentmentId() { return presentmentId; }
    public CardId getCardId() { return cardId; }
    public double getScore() { return score; }
    public AuthPresentmentMatch.MatchQuality getQuality() { return quality; }
    public AuthPresentmentMatch.MatchConfidence getConfidence() { return confidence; }
    public Money getSettlementAmount() { return settlementAmount; }
    public long getTimeDifferenceHours() { return timeDifferenceHours; }
    public boolean requiresReview() { return requiresReview; }
    public List<String> getValidationIssues() { return validationIssues; }
    public List<String> getRiskFactors() { return riskFactors; }

    public static class Builder {
        private String authId;
        private String presentmentId;
        private CardId cardId;
        private double score;
        private AuthPresentmentMatch.MatchQuality quality;
        private AuthPresentmentMatch.MatchConfidence confidence;
        private Money settlementAmount;
        private long timeDifferenceHours;
        private boolean requiresReview;
        private List<String> validationIssues;
        private List<String> riskFactors;

        public Builder authId(String authId) { this.authId = authId; return this; }
        public Builder presentmentId(String presentmentId) { this.presentmentId = presentmentId; return this; }
        public Builder cardId(CardId cardId) { this.cardId = cardId; return this; }
        public Builder score(double score) { this.score = score; return this; }
        public Builder quality(AuthPresentmentMatch.MatchQuality quality) { this.quality = quality; return this; }
        public Builder confidence(AuthPresentmentMatch.MatchConfidence confidence) { this.confidence = confidence; return this; }
        public Builder settlementAmount(Money settlementAmount) { this.settlementAmount = settlementAmount; return this; }
        public Builder timeDifferenceHours(long timeDifferenceHours) { this.timeDifferenceHours = timeDifferenceHours; return this; }
        public Builder requiresReview(boolean requiresReview) { this.requiresReview = requiresReview; return this; }
        public Builder validationIssues(List<String> validationIssues) { this.validationIssues = validationIssues; return this; }
        public Builder riskFactors(List<String> riskFactors) { this.riskFactors = riskFactors; return this; }

        public MatchSummary build() {
            return new MatchSummary(this);
        }
    }
}