
import java.util.Objects;
import java.io.Serializable;

public final class BinRangeConflict implements Serializable {

    private static final long serialVersionUID = 1L;
    private final BinRange newRange;
    private final BinRange existingRange;
    private final String resolution;

    public BinRangeConflict(BinRange newRange, BinRange existingRange, String resolution) {
        this.newRange = Objects.requireNonNull(newRange);
        this.existingRange = Objects.requireNonNull(existingRange);
        this.resolution = Objects.requireNonNull(resolution);
    }

    public BinRange getNewRange() {
        return newRange;
    }

    public BinRange getExistingRange() {
        return existingRange;
    }

    public String getResolution() {
        return resolution;
    }

    @Override
    public String toString() {
        return String.format("Conflict: %s overlaps with %s - %s", newRange, existingRange, resolution);
    }
}