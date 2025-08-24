
import java.io.Serializable;

public final class MatchingComparison implements Serializable {

    private static final long serialVersionUID = 1L;

    private final MatchingResult result1;
    private final MatchingResult result2;
    private final double scoreDifference;
    private final int matchCountDifference;
    private final long executionTimeDifference;
    private final double qualityImprovement;

    private MatchingComparison(Builder builder) {
        this.result1 = builder.result1;
        this.result2 = builder.result2;
        this.scoreDifference = builder.scoreDifference;
        this.matchCountDifference = builder.matchCountDifference;
        this.executionTimeDifference = builder.executionTimeDifference;
        this.qualityImprovement = builder.qualityImprovement;
    }

    public static Builder builder() {
        return new Builder();
    }

    
    public MatchingResult getResult1() { return result1; }
    public MatchingResult getResult2() { return result2; }
    public double getScoreDifference() { return scoreDifference; }
    public int getMatchCountDifference() { return matchCountDifference; }
    public long getExecutionTimeDifference() { return executionTimeDifference; }
    public double getQualityImprovement() { return qualityImprovement; }

    public boolean isResult1Better() {
        return scoreDifference > 0;
    }

    public boolean isResult2Better() {
        return scoreDifference < 0;
    }

    public String getRecommendation() {
        if (Math.abs(scoreDifference) < 1.0) {
            return "Results are similar. Choose based on execution time preference.";
        } else if (isResult1Better()) {
            return String.format("%s produces %.1f%% better results",
                    result1.getAlgorithm(), qualityImprovement);
        } else {
            return String.format("%s produces %.1f%% better results",
                    result2.getAlgorithm(), Math.abs(qualityImprovement));
        }
    }

    public static class Builder {
        private MatchingResult result1;
        private MatchingResult result2;
        private double scoreDifference;
        private int matchCountDifference;
        private long executionTimeDifference;
        private double qualityImprovement;

        public Builder result1(MatchingResult result1) { this.result1 = result1; return this; }
        public Builder result2(MatchingResult result2) { this.result2 = result2; return this; }
        public Builder scoreDifference(double scoreDifference) { this.scoreDifference = scoreDifference; return this; }
        public Builder matchCountDifference(int matchCountDifference) { this.matchCountDifference = matchCountDifference; return this; }
        public Builder executionTimeDifference(long executionTimeDifference) { this.executionTimeDifference = executionTimeDifference; return this; }
        public Builder qualityImprovement(double qualityImprovement) { this.qualityImprovement = qualityImprovement; return this; }

        public MatchingComparison build() {
            return new MatchingComparison(this);
        }
    }
}