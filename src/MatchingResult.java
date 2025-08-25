
import java.time.LocalDateTime;
import java.util.*;
import java.io.Serializable;


public final class MatchingResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<AuthPresentmentMatch> matches;
    private final List<Auth> unmatchedAuths;
    private final List<Presentment> unmatchedPresentments;
    private final double totalScore;
    private final double averageScore;
    private final String algorithm;
    private final LocalDateTime executionTime;
    private final long executionTimeNanos;
    private final MatchingStatistics statistics;


    private MatchingResult(Builder builder) {
        this.matches = Collections.unmodifiableList(new ArrayList<>(builder.matches));
        this.unmatchedAuths = Collections.unmodifiableList(new ArrayList<>(builder.unmatchedAuths));
        this.unmatchedPresentments = Collections.unmodifiableList(new ArrayList<>(builder.unmatchedPresentments));
        this.totalScore = builder.totalScore;
        this.averageScore = matches.isEmpty() ? 0.0 : totalScore / matches.size();
        this.algorithm = Objects.requireNonNull(builder.algorithm, "Algorithm cannot be null");
        this.executionTime = builder.executionTime != null ? builder.executionTime : LocalDateTime.now();
        this.executionTimeNanos = builder.executionTimeNanos;
        this.statistics = calculateStatistics();
    }

    public static Builder builder() {
        return new Builder();
    }


    public static MatchingResult empty() {
        return builder()
                .matches(Collections.emptyList())
                .unmatchedAuths(Collections.emptyList())
                .unmatchedPresentments(Collections.emptyList())
                .totalScore(0.0)
                .algorithm("None")
                .build();
    }


    public List<AuthPresentmentMatch> getMatches() {
        return matches;
    }

    public List<Auth> getUnmatchedAuths() {
        return unmatchedAuths;
    }

    public List<Presentment> getUnmatchedPresentments() {
        return unmatchedPresentments;
    }

    public double getTotalScore() {
        return totalScore;
    }

    public double getAverageScore() {
        return averageScore;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public LocalDateTime getExecutionTime() {
        return executionTime;
    }

    public long getExecutionTimeNanos() {
        return executionTimeNanos;
    }

    public MatchingStatistics getStatistics() {
        return statistics;
    }


    public int getMatchCount() {
        return matches.size();
    }

    public int getUnmatchedAuthCount() {
        return unmatchedAuths.size();
    }

    public int getUnmatchedPresentmentCount() {
        return unmatchedPresentments.size();
    }

    public int getTotalAuthCount() {
        return matches.size() + unmatchedAuths.size();
    }

    public int getTotalPresentmentCount() {
        return matches.size() + unmatchedPresentments.size();
    }

    public double getMatchingRate() {
        int totalAuths = getTotalAuthCount();
        return totalAuths == 0 ? 0.0 : (double) matches.size() / totalAuths;
    }

    public double getPresentmentCoverageRate() {
        int totalPresentments = getTotalPresentmentCount();
        return totalPresentments == 0 ? 0.0 : (double) matches.size() / totalPresentments;
    }

    public boolean isFullyMatched() {
        return unmatchedAuths.isEmpty() && unmatchedPresentments.isEmpty();
    }

    public boolean hasUnmatchedItems() {
        return !unmatchedAuths.isEmpty() || !unmatchedPresentments.isEmpty();
    }

    public double getExecutionTimeMs() {
        return executionTimeNanos / 1_000_000.0;
    }


    public Optional<AuthPresentmentMatch> findMatchForAuth(Auth auth) {
        return matches.stream()
                .filter(match -> match.getAuth().equals(auth))
                .findFirst();
    }


    public Optional<AuthPresentmentMatch> findMatchForPresentment(Presentment presentment) {
        return matches.stream()
                .filter(match -> match.getPresentment().equals(presentment))
                .findFirst();
    }


    public List<AuthPresentmentMatch> getMatchesSortedByScore() {
        return matches.stream()
                .sorted((m1, m2) -> Double.compare(m2.getScore(), m1.getScore()))
                .toList();
    }


    public List<AuthPresentmentMatch> getLowQualityMatches(double threshold) {
        return matches.stream()
                .filter(match -> match.getScore() < threshold)
                .toList();
    }


    public Money getTotalAmountMatched() {
        if (matches.isEmpty()) {
            return Money.zero(Currency.USD);
        }


        Currency currency = matches.get(0).getAuth().getAmount().getCurrency();
        Money total = Money.zero(currency);

        for (AuthPresentmentMatch match : matches) {
            total = total.add(match.getAuth().getAmount());
        }

        return total;
    }


    public Money getTotalUnmatchedAuthAmount() {
        if (unmatchedAuths.isEmpty()) {
            return Money.zero(Currency.USD);
        }

        Currency currency = unmatchedAuths.get(0).getAmount().getCurrency();
        Money total = Money.zero(currency);

        for (Auth auth : unmatchedAuths) {
            total = total.add(auth.getAmount());
        }

        return total;
    }


    public MatchingResult withExecutionTime(long executionTimeNanos) {
        return builder()
                .matches(this.matches)
                .unmatchedAuths(this.unmatchedAuths)
                .unmatchedPresentments(this.unmatchedPresentments)
                .totalScore(this.totalScore)
                .algorithm(this.algorithm)
                .executionTime(this.executionTime)
                .executionTimeNanos(executionTimeNanos)
                .build();
    }


    public MatchingComparison compareTo(MatchingResult other) {
        Objects.requireNonNull(other, "Other result cannot be null");

        return MatchingComparison.builder()
                .result1(this)
                .result2(other)
                .scoreDifference(this.totalScore - other.totalScore)
                .matchCountDifference(this.getMatchCount() - other.getMatchCount())
                .executionTimeDifference(this.executionTimeNanos - other.executionTimeNanos)
                .qualityImprovement(calculateQualityImprovement(other))
                .build();
    }


    public MatchingReport generateReport() {
        return MatchingReport.builder()
                .result(this)
                .timestamp(LocalDateTime.now())
                .summary(generateSummary())
                .recommendations(generateRecommendations())
                .build();
    }


    private MatchingStatistics calculateStatistics() {
        if (matches.isEmpty()) {
            return MatchingStatistics.empty();
        }

        double[] scores = matches.stream()
                .mapToDouble(AuthPresentmentMatch::getScore)
                .toArray();

        Arrays.sort(scores);

        double min = scores[0];
        double max = scores[scores.length - 1];
        double median = calculateMedian(scores);
        double stdDev = calculateStandardDeviation(scores, averageScore);


        int excellent = 0, good = 0, fair = 0, poor = 0;
        for (double score : scores) {
            if (score >= 90) excellent++;
            else if (score >= 75) good++;
            else if (score >= 50) fair++;
            else poor++;
        }

        return MatchingStatistics.builder()
                .minScore(min)
                .maxScore(max)
                .averageScore(averageScore)
                .medianScore(median)
                .standardDeviation(stdDev)
                .excellentMatches(excellent)
                .goodMatches(good)
                .fairMatches(fair)
                .poorMatches(poor)
                .build();
    }

    private double calculateMedian(double[] sortedScores) {
        int length = sortedScores.length;
        if (length % 2 == 0) {
            return (sortedScores[length / 2 - 1] + sortedScores[length / 2]) / 2.0;
        } else {
            return sortedScores[length / 2];
        }
    }

    private double calculateStandardDeviation(double[] scores, double mean) {
        double sumSquaredDiffs = 0.0;
        for (double score : scores) {
            double diff = score - mean;
            sumSquaredDiffs += diff * diff;
        }
        return Math.sqrt(sumSquaredDiffs / scores.length);
    }

    private double calculateQualityImprovement(MatchingResult other) {
        if (other.getTotalScore() == 0) {
            return this.totalScore > 0 ? Double.POSITIVE_INFINITY : 0.0;
        }
        return (this.totalScore - other.totalScore) / other.getTotalScore() * 100.0;
    }

    private String generateSummary() {
        return String.format(
                "Matching completed using %s algorithm. " +
                        "Matched %d pairs with average score %.1f. " +
                        "%.1f%% auth coverage, %.1f%% presentment coverage. " +
                        "Execution time: %.2fms",
                algorithm, matches.size(), averageScore,
                getMatchingRate() * 100, getPresentmentCoverageRate() * 100,
                getExecutionTimeMs()
        );
    }

    private List<String> generateRecommendations() {
        List<String> recommendations = new ArrayList<>();

        if (getMatchingRate() < 0.8) {
            recommendations.add("Low matching rate detected. Consider reviewing auth expiry policies.");
        }

        if (averageScore < 70) {
            recommendations.add("Low average matching quality. Review scoring algorithm parameters.");
        }

        if (getExecutionTimeMs() > 1000) {
            recommendations.add("High execution time. Consider optimizing for large datasets.");
        }

        int poorMatches = statistics.getPoorMatches();
        if (poorMatches > 0) {
            recommendations.add(String.format("Found %d poor quality matches. Manual review recommended.", poorMatches));
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Matching performance looks good. No specific recommendations.");
        }

        return recommendations;
    }


    public static class Builder {
        private List<AuthPresentmentMatch> matches = new ArrayList<>();
        private List<Auth> unmatchedAuths = new ArrayList<>();
        private List<Presentment> unmatchedPresentments = new ArrayList<>();
        private double totalScore = 0.0;
        private String algorithm = "Unknown";
        private LocalDateTime executionTime;
        private long executionTimeNanos = 0L;

        public Builder matches(List<AuthPresentmentMatch> matches) {
            this.matches = matches;
            return this;
        }

        public Builder unmatchedAuths(List<Auth> unmatchedAuths) {
            this.unmatchedAuths = unmatchedAuths;
            return this;
        }

        public Builder unmatchedPresentments(List<Presentment> unmatchedPresentments) {
            this.unmatchedPresentments = unmatchedPresentments;
            return this;
        }

        public Builder totalScore(double totalScore) {
            this.totalScore = totalScore;
            return this;
        }

        public Builder algorithm(String algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        public Builder executionTime(LocalDateTime executionTime) {
            this.executionTime = executionTime;
            return this;
        }

        public Builder executionTimeNanos(long executionTimeNanos) {
            this.executionTimeNanos = executionTimeNanos;
            return this;
        }

        public MatchingResult build() {
            return new MatchingResult(this);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        MatchingResult that = (MatchingResult) obj;
        return Double.compare(that.totalScore, totalScore) == 0 &&
                Objects.equals(matches, that.matches) &&
                Objects.equals(unmatchedAuths, that.unmatchedAuths) &&
                Objects.equals(unmatchedPresentments, that.unmatchedPresentments) &&
                Objects.equals(algorithm, that.algorithm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matches, unmatchedAuths, unmatchedPresentments, totalScore, algorithm);
    }

    @Override
    public String toString() {
        return String.format("MatchingResult[algorithm=%s, matches=%d, unmatched_auths=%d, " +
                        "unmatched_presentments=%d, total_score=%.1f, avg_score=%.1f, execution_time=%.2fms]",
                algorithm, matches.size(), unmatchedAuths.size(),
                unmatchedPresentments.size(), totalScore, averageScore, getExecutionTimeMs());
    }
}