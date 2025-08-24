

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Immutable tarih aralığı value object.
 * Sweep line algoritmasında dönem tanımları için kullanılır.
 */
public final class DateRange implements Comparable<DateRange> {
    private final LocalDate startDate;
    private final LocalDate endDate;

    public DateRange(LocalDate startDate, LocalDate endDate) {
        this.startDate = Objects.requireNonNull(startDate, "Start date cannot be null");
        this.endDate = Objects.requireNonNull(endDate, "End date cannot be null");

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date: " +
                    startDate + " > " + endDate);
        }
    }

    // Factory methods
    public static DateRange of(LocalDate startDate, LocalDate endDate) {
        return new DateRange(startDate, endDate);
    }

    public static DateRange singleDay(LocalDate date) {
        return new DateRange(date, date);
    }

    public static DateRange thisMonth() {
        LocalDate now = LocalDate.now();
        return new DateRange(now.withDayOfMonth(1),
                now.withDayOfMonth(now.lengthOfMonth()));
    }

    public static DateRange lastNDays(int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1);
        return new DateRange(start, end);
    }

    // Getters
    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    // Query methods
    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date, "Date cannot be null");
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public boolean overlaps(DateRange other) {
        Objects.requireNonNull(other, "Other range cannot be null");
        return !endDate.isBefore(other.startDate) && !other.endDate.isBefore(startDate);
    }

    public long getDayCount() {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    public boolean isEmpty() {
        return startDate.isAfter(endDate);
    }

    public boolean isSingleDay() {
        return startDate.equals(endDate);
    }

    // Operations
    public DateRange extend(int days) {
        return new DateRange(startDate, endDate.plusDays(days));
    }

    public DateRange shrink(int days) {
        LocalDate newEnd = endDate.minusDays(days);
        if (newEnd.isBefore(startDate)) {
            throw new IllegalArgumentException("Cannot shrink range below zero days");
        }
        return new DateRange(startDate, newEnd);
    }

    public DateRange shift(int days) {
        return new DateRange(startDate.plusDays(days), endDate.plusDays(days));
    }

    public DateRange intersection(DateRange other) {
        Objects.requireNonNull(other, "Other range cannot be null");

        LocalDate maxStart = startDate.isAfter(other.startDate) ? startDate : other.startDate;
        LocalDate minEnd = endDate.isBefore(other.endDate) ? endDate : other.endDate;

        if (maxStart.isAfter(minEnd)) {
            throw new IllegalArgumentException("Ranges do not overlap");
        }

        return new DateRange(maxStart, minEnd);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        DateRange dateRange = (DateRange) obj;
        return Objects.equals(startDate, dateRange.startDate) &&
                Objects.equals(endDate, dateRange.endDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startDate, endDate);
    }

    @Override
    public int compareTo(DateRange other) {
        int startCompare = startDate.compareTo(other.startDate);
        if (startCompare != 0) {
            return startCompare;
        }
        return endDate.compareTo(other.endDate);
    }

    @Override
    public String toString() {
        if (isSingleDay()) {
            return startDate.toString();
        }
        return String.format("[%s to %s]", startDate, endDate);
    }
}