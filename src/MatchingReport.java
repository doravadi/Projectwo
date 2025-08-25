
import java.time.LocalDateTime;
import java.util.List;
import java.io.Serializable;

public final class MatchingReport implements Serializable {

    private static final long serialVersionUID = 1L;

    private final MatchingResult result;
    private final LocalDateTime timestamp;
    private final String summary;
    private final List<String> recommendations;

    private MatchingReport(Builder builder) {
        this.result = builder.result;
        this.timestamp = builder.timestamp;
        this.summary = builder.summary;
        this.recommendations = builder.recommendations;
    }

    public static Builder builder() {
        return new Builder();
    }


    public MatchingResult getResult() {
        return result;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }

    public static class Builder {
        private MatchingResult result;
        private LocalDateTime timestamp;
        private String summary;
        private List<String> recommendations;

        public Builder result(MatchingResult result) {
            this.result = result;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder recommendations(List<String> recommendations) {
            this.recommendations = recommendations;
            return this;
        }

        public MatchingReport build() {
            return new MatchingReport(this);
        }
    }
}