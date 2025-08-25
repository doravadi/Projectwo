
import java.util.Objects;
import java.io.Serializable;

public final class ValidationError implements Serializable {

    private static final long serialVersionUID = 1L;
    private final BinRange range;
    private final String errorMessage;

    public ValidationError(BinRange range, String errorMessage) {
        this.range = Objects.requireNonNull(range);
        this.errorMessage = Objects.requireNonNull(errorMessage);
    }

    public BinRange getRange() {
        return range;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return String.format("ValidationError: %s - %s", range, errorMessage);
    }
}