
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.io.Serializable;

public final class MatchValidation implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<String> errors;
    private final List<String> warnings;
    private final List<String> infos;

    private MatchValidation(Builder builder) {
        this.errors = Collections.unmodifiableList(new ArrayList<>(builder.errors));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(builder.warnings));
        this.infos = Collections.unmodifiableList(new ArrayList<>(builder.infos));
    }

    public static Builder builder() {
        return new Builder();
    }

    
    public List<String> getErrors() { return errors; }
    public List<String> getWarnings() { return warnings; }
    public List<String> getInfos() { return infos; }

    
    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean isFullyValid() {
        return errors.isEmpty() && warnings.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public boolean hasInfos() {
        return !infos.isEmpty();
    }

    public List<String> getIssues() {
        List<String> allIssues = new ArrayList<>();
        allIssues.addAll(errors);
        allIssues.addAll(warnings);
        return allIssues;
    }

    public String getErrorMessage() {
        if (errors.isEmpty()) {
            return null;
        }
        return String.join("; ", errors);
    }

    public static class Builder {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> infos = new ArrayList<>();

        public Builder addError(String error) {
            this.errors.add(error);
            return this;
        }

        public Builder addWarning(String warning) {
            this.warnings.add(warning);
            return this;
        }

        public Builder addInfo(String info) {
            this.infos.add(info);
            return this;
        }

        public MatchValidation build() {
            return new MatchValidation(this);
        }
    }
}