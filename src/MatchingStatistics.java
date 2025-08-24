// MatchingStatistics.java - Statistical analysis of matching results
import java.io.Serializable;

public final class MatchingStatistics implements Serializable {

    private static final long serialVersionUID = 1L;

    private final double minScore;
    private final double maxScore;
    private final double averageScore;
    private final double medianScore;
    private final double standardDeviation;
    private final int excellentMatches;
    private final int goodMatches;
    private final int fairMatches;
    private final int poorMatches;

    private MatchingStatistics(Builder builder) {
        this.minScore = builder.minScore;
        this.maxScore = builder.maxScore;
        this.averageScore = builder.averageScore;
        this.medianScore = builder.medianScore;
        this.standardDeviation = builder.standardDeviation;
        this.excellentMatches = builder.excellentMatches;
        this.goodMatches = builder.goodMatches;
        this.fairMatches = builder.fairMatches;
        this.poorMatches = builder.poorMatches;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static MatchingStatistics empty() {
        return builder().build();
    }

    // Getters
    public double getMinScore() { return minScore; }
    public double getMaxScore() { return maxScore; }
    public double getAverageScore() { return averageScore; }
    public double getMedianScore() { return medianScore; }
    public double getStandardDeviation() { return standardDeviation; }
    public int getExcellentMatches() { return excellentMatches; }
    public int getGoodMatches() { return goodMatches; }
    public int getFairMatches() { return fairMatches; }
    public int getPoorMatches() { return poorMatches; }

    public int getTotalMatches() {
        return excellentMatches + goodMatches + fairMatches + poorMatches;
    }

    public double getQualityScore() {
        int total = getTotalMatches();
        if (total == 0) return 0.0;

        // Weighted quality score
        return (excellentMatches * 4.0 + goodMatches * 3.0 + fairMatches * 2.0 + poorMatches * 1.0) / total;
    }

    public static class Builder {
        private double minScore = 0.0;
        private double maxScore = 0.0;
        private double averageScore = 0.0;
        private double medianScore = 0.0;
        private double standardDeviation = 0.0;
        private int excellentMatches = 0;
        private int goodMatches = 0;
        private int fairMatches = 0;
        private int poorMatches = 0;

        public Builder minScore(double minScore) { this.minScore = minScore; return this; }
        public Builder maxScore(double maxScore) { this.maxScore = maxScore; return this; }
        public Builder averageScore(double averageScore) { this.averageScore = averageScore; return this; }
        public Builder medianScore(double medianScore) { this.medianScore = medianScore; return this; }
        public Builder standardDeviation(double standardDeviation) { this.standardDeviation = standardDeviation; return this; }
        public Builder excellentMatches(int excellentMatches) { this.excellentMatches = excellentMatches; return this; }
        public Builder goodMatches(int goodMatches) { this.goodMatches = goodMatches; return this; }
        public Builder fairMatches(int fairMatches) { this.fairMatches = fairMatches; return this; }
        public Builder poorMatches(int poorMatches) { this.poorMatches = poorMatches; return this; }

        public MatchingStatistics build() {
            return new MatchingStatistics(this);
        }
    }
}