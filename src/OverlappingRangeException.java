public class OverlappingRangeException extends DomainException {

    private final BinRange existingRange;
    private final BinRange newRange;

    public OverlappingRangeException(BinRange existingRange, BinRange newRange) {
        super(String.format("BIN range overlap detected: existing %s conflicts with new %s",
                existingRange, newRange));
        this.existingRange = existingRange;
        this.newRange = newRange;
    }

    public BinRange getExistingRange() {
        return existingRange;
    }

    public BinRange getNewRange() {
        return newRange;
    }
}