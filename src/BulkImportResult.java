// BulkImportResult.java - Bulk operation result
import java.util.*;
import java.io.Serializable;

public final class BulkImportResult implements Serializable {

    private static final long serialVersionUID = 1L;
    private final List<BinRange> successful;
    private final List<BinRangeConflict> conflicts;
    private final List<ValidationError> errors;

    public BulkImportResult(List<BinRange> successful, List<BinRangeConflict> conflicts, List<ValidationError> errors) {
        this.successful = Collections.unmodifiableList(new ArrayList<>(successful));
        this.conflicts = Collections.unmodifiableList(new ArrayList<>(conflicts));
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public List<BinRange> getSuccessful() { return successful; }
    public List<BinRangeConflict> getConflicts() { return conflicts; }
    public List<ValidationError> getErrors() { return errors; }

    public int getSuccessCount() { return successful.size(); }
    public int getConflictCount() { return conflicts.size(); }
    public int getErrorCount() { return errors.size(); }
    public int getTotalProcessed() { return getSuccessCount() + getConflictCount() + getErrorCount(); }

    public boolean hasErrors() { return !errors.isEmpty(); }
    public boolean hasConflicts() { return !conflicts.isEmpty(); }
    public boolean isFullySuccessful() { return !hasErrors() && !hasConflicts(); }

    @Override
    public String toString() {
        return String.format("BulkImportResult[success=%d, conflicts=%d, errors=%d]",
                getSuccessCount(), getConflictCount(), getErrorCount());
    }
}