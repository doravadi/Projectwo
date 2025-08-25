

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;


public final class BalanceChange implements Comparable<BalanceChange> {
    private final LocalDate date;
    private final BigDecimal amount;
    private final String description;
    private final ChangeType type;

    public enum ChangeType {
        PURCHASE,
        PAYMENT,
        INTEREST,
        FEE,
        REFUND,
        ADJUSTMENT
    }

    public BalanceChange(LocalDate date, BigDecimal amount, String description, ChangeType type) {
        this.date = Objects.requireNonNull(date, "Date cannot be null");
        this.amount = Objects.requireNonNull(amount, "Amount cannot be null");
        this.description = Objects.requireNonNull(description, "Description cannot be null");
        this.type = Objects.requireNonNull(type, "ChangeType cannot be null");
    }


    public static BalanceChange purchase(LocalDate date, BigDecimal amount, String description) {
        return new BalanceChange(date, amount.negate(), description, ChangeType.PURCHASE);
    }

    public static BalanceChange payment(LocalDate date, BigDecimal amount, String description) {
        return new BalanceChange(date, amount, description, ChangeType.PAYMENT);
    }

    public static BalanceChange interest(LocalDate date, BigDecimal amount, String description) {
        return new BalanceChange(date, amount.negate(), description, ChangeType.INTEREST);
    }

    public static BalanceChange fee(LocalDate date, BigDecimal amount, String description) {
        return new BalanceChange(date, amount.negate(), description, ChangeType.FEE);
    }

    public static BalanceChange refund(LocalDate date, BigDecimal amount, String description) {
        return new BalanceChange(date, amount, description, ChangeType.REFUND);
    }

    public static BalanceChange adjustment(LocalDate date, BigDecimal amount, String description) {
        return new BalanceChange(date, amount, description, ChangeType.ADJUSTMENT);
    }


    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public ChangeType getType() {
        return type;
    }


    public boolean isCredit() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isDebit() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public BigDecimal getAbsoluteAmount() {
        return amount.abs();
    }


    public BalanceChange withAmount(BigDecimal newAmount) {
        return new BalanceChange(date, newAmount, description, type);
    }

    public BalanceChange withDate(LocalDate newDate) {
        return new BalanceChange(newDate, amount, description, type);
    }

    public BalanceChange withDescription(String newDescription) {
        return new BalanceChange(date, amount, newDescription, type);
    }

    public BalanceChange negate() {
        return new BalanceChange(date, amount.negate(), description, type);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        BalanceChange that = (BalanceChange) obj;
        return Objects.equals(date, that.date) &&
                Objects.equals(amount, that.amount) &&
                Objects.equals(description, that.description) &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, amount, description, type);
    }

    @Override
    public int compareTo(BalanceChange other) {

        int dateCompare = date.compareTo(other.date);
        if (dateCompare != 0) {
            return dateCompare;
        }


        int typeCompare = Integer.compare(getTypeOrder(), other.getTypeOrder());
        if (typeCompare != 0) {
            return typeCompare;
        }


        return other.amount.compareTo(amount);
    }

    private int getTypeOrder() {
        return switch (type) {
            case PAYMENT -> 1;
            case REFUND -> 2;
            case ADJUSTMENT -> 3;
            case PURCHASE -> 4;
            case FEE -> 5;
            case INTEREST -> 6;
        };
    }

    @Override
    public String toString() {
        return String.format("BalanceChange{%s: %s %s (%s)}",
                date,
                isCredit() ? "+" : "",
                amount,
                type);
    }
}