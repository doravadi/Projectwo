// ValidationReport.java - Database validation result
import java.util.*;
import java.io.Serializable;

public final class ValidationReport implements Serializable {

    private static final long serialVersionUID = 1L;
    private final int totalRanges;
    private final List<Map.Entry<BinRange, BinRange>> overlappingRanges;
    private final List<String> warnings;
    private final List<String> errors;

    private ValidationReport(Builder builder) {
        this.totalRanges = builder.totalRanges;
        this.overlappingRanges = Collections.unmodifiableList(new ArrayList<>(builder.overlappingRanges));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(builder.warnings));
        this.errors = Collections.unmodifiableList(new ArrayList<>(builder.errors));
    }

    public static Builder builder() { return new Builder(); }

    public int getTotalRanges() { return totalRanges; }
    public List<Map.Entry<BinRange, BinRange>> getOverlappingRanges() { return overlappingRanges; }
    public List<String> getWarnings() { return warnings; }
    public List<String> getErrors() { return errors; }

    public boolean isValid() { return errors.isEmpty() && overlappingRanges.isEmpty(); }
    public boolean hasWarnings() { return !warnings.isEmpty(); }

    public static class Builder {
        private int totalRanges;
        private List<Map.Entry<BinRange, BinRange>> overlappingRanges = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private List<String> errors = new ArrayList<>();

        public Builder totalRanges(int totalRanges) { this.totalRanges = totalRanges; return this; }
        public Builder overlappingRanges(List<Map.Entry<BinRange, BinRange>> overlaps) {
            this.overlappingRanges = overlaps; return this;
        }
        public Builder addWarning(String warning) { this.warnings.add(warning); return this; }
        public Builder addError(String error) { this.errors.add(error); return this; }

        public ValidationReport build() { return new ValidationReport(this); }
    }

    @Override
    public String toString() {
        return String.format("ValidationReport[ranges=%d, overlaps=%d, warnings=%d, errors=%d]",
                totalRanges, overlappingRanges.size(), warnings.size(), errors.size());
    }
}