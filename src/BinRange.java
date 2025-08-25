
import java.io.Serializable;
import java.util.Objects;
import java.util.Objects;

public final class BinRange implements Comparable<BinRange>, Serializable {

    private final long startBin;
    private final long endBin;
    private final String bankName;
    private final String cardType;
    private final String country;


    private BinRange(long startBin, long endBin, String bankName, String cardType, String country) {
        if (startBin > endBin) {
            throw new IllegalArgumentException(
                    String.format("Start BIN (%d) cannot be greater than end BIN (%d)", startBin, endBin));
        }
        if (startBin < 100000L || endBin > 999999999999L) {
            throw new IllegalArgumentException(
                    String.format("BIN must be between 6-12 digits: start=%d, end=%d", startBin, endBin));
        }

        this.startBin = startBin;
        this.endBin = endBin;
        this.bankName = Objects.requireNonNull(bankName, "Bank name cannot be null");
        this.cardType = Objects.requireNonNull(cardType, "Card type cannot be null");
        this.country = Objects.requireNonNull(country, "Country cannot be null");
    }


    public static BinRange of(long startBin, long endBin, String bankName, String cardType, String country) {
        return new BinRange(startBin, endBin, bankName, cardType, country);
    }


    public long getStartBin() {
        return startBin;
    }

    public long getEndBin() {
        return endBin;
    }

    public String getBankName() {
        return bankName;
    }

    public String getCardType() {
        return cardType;
    }

    public String getCountry() {
        return country;
    }


    public boolean contains(long bin) {
        return bin >= startBin && bin <= endBin;
    }

    public boolean overlaps(BinRange other) {
        return this.startBin <= other.endBin && this.endBin >= other.startBin;
    }

    public long getRangeSize() {
        return endBin - startBin + 1;
    }


    public boolean isSingleBin() {
        return startBin == endBin;
    }

    public boolean isPrefix() {

        String startStr = String.valueOf(startBin);
        String endStr = String.valueOf(endBin);

        if (startStr.length() >= 6 && endStr.length() >= 6) {
            String prefix = startStr.substring(0, 6);
            return startStr.equals(prefix + "0".repeat(startStr.length() - 6)) &&
                    endStr.equals(prefix + "9".repeat(endStr.length() - 6));
        }
        return false;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        BinRange binRange = (BinRange) obj;
        return startBin == binRange.startBin &&
                endBin == binRange.endBin &&
                Objects.equals(bankName, binRange.bankName) &&
                Objects.equals(cardType, binRange.cardType) &&
                Objects.equals(country, binRange.country);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startBin, endBin, bankName, cardType, country);
    }

    @Override
    public int compareTo(BinRange other) {

        int startComparison = Long.compare(this.startBin, other.startBin);
        if (startComparison != 0) {
            return startComparison;
        }


        return Long.compare(this.endBin, other.endBin);
    }

    @Override
    public String toString() {
        if (isSingleBin()) {
            return String.format("BIN[%d] %s %s (%s)",
                    startBin, bankName, cardType, country);
        } else {
            return String.format("BIN[%d-%d] %s %s (%s)",
                    startBin, endBin, bankName, cardType, country);
        }
    }
}