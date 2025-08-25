
import java.util.*;
import java.io.Serializable;

public final class DatabaseStatistics implements Serializable {

    private static final long serialVersionUID = 1L;
    private final int totalRanges;
    private final Map<String, Long> bankDistribution;
    private final Map<String, Long> countryDistribution;
    private final Map<String, Long> cardTypeDistribution;
    private final long totalBinCoverage;
    private final BinRange largestRange;
    private final BinRange smallestRange;

    private DatabaseStatistics(Builder builder) {
        this.totalRanges = builder.totalRanges;
        this.bankDistribution = Collections.unmodifiableMap(new HashMap<>(builder.bankDistribution));
        this.countryDistribution = Collections.unmodifiableMap(new HashMap<>(builder.countryDistribution));
        this.cardTypeDistribution = Collections.unmodifiableMap(new HashMap<>(builder.cardTypeDistribution));
        this.totalBinCoverage = builder.totalBinCoverage;
        this.largestRange = builder.largestRange;
        this.smallestRange = builder.smallestRange;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getTotalRanges() {
        return totalRanges;
    }

    public Map<String, Long> getBankDistribution() {
        return bankDistribution;
    }

    public Map<String, Long> getCountryDistribution() {
        return countryDistribution;
    }

    public Map<String, Long> getCardTypeDistribution() {
        return cardTypeDistribution;
    }

    public long getTotalBinCoverage() {
        return totalBinCoverage;
    }

    public BinRange getLargestRange() {
        return largestRange;
    }

    public BinRange getSmallestRange() {
        return smallestRange;
    }

    public static class Builder {
        private int totalRanges;
        private Map<String, Long> bankDistribution = new HashMap<>();
        private Map<String, Long> countryDistribution = new HashMap<>();
        private Map<String, Long> cardTypeDistribution = new HashMap<>();
        private long totalBinCoverage;
        private BinRange largestRange;
        private BinRange smallestRange;

        public Builder totalRanges(int totalRanges) {
            this.totalRanges = totalRanges;
            return this;
        }

        public Builder bankDistribution(Map<String, Long> distribution) {
            this.bankDistribution = distribution;
            return this;
        }

        public Builder countryDistribution(Map<String, Long> distribution) {
            this.countryDistribution = distribution;
            return this;
        }

        public Builder cardTypeDistribution(Map<String, Long> distribution) {
            this.cardTypeDistribution = distribution;
            return this;
        }

        public Builder totalBinCoverage(long coverage) {
            this.totalBinCoverage = coverage;
            return this;
        }

        public Builder largestRange(BinRange range) {
            this.largestRange = range;
            return this;
        }

        public Builder smallestRange(BinRange range) {
            this.smallestRange = range;
            return this;
        }

        public DatabaseStatistics build() {
            return new DatabaseStatistics(this);
        }
    }
}