

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;


public final class DebtBucket implements Comparable<DebtBucket> {

    private final String bucketId;
    private final BucketType type;
    private final BigDecimal currentBalance;
    private final BigDecimal interestRate;        
    private final BigDecimal minimumPayment;      
    private final LocalDate dueDate;              
    private final int priority;                   

    public enum BucketType {
        PURCHASE(1, "Alışveriş"),
        CASH_ADVANCE(2, "Nakit Avans"),
        INSTALLMENT(3, "Taksit"),
        FEES_INTEREST(4, "Faiz ve Komisyon"),
        OVERDUE(0, "Vadesi Geçmiş");  

        private final int defaultPriority;
        private final String description;

        BucketType(int defaultPriority, String description) {
            this.defaultPriority = defaultPriority;
            this.description = description;
        }

        public int getDefaultPriority() { return defaultPriority; }
        public String getDescription() { return description; }
    }

    public DebtBucket(String bucketId, BucketType type, BigDecimal currentBalance,
                      BigDecimal interestRate, BigDecimal minimumPayment,
                      LocalDate dueDate, int priority) {
        this.bucketId = Objects.requireNonNull(bucketId, "Bucket ID cannot be null");
        this.type = Objects.requireNonNull(type, "Bucket type cannot be null");
        this.currentBalance = Objects.requireNonNull(currentBalance, "Current balance cannot be null");
        this.interestRate = Objects.requireNonNull(interestRate, "Interest rate cannot be null");
        this.minimumPayment = Objects.requireNonNull(minimumPayment, "Minimum payment cannot be null");
        this.dueDate = Objects.requireNonNull(dueDate, "Due date cannot be null");
        this.priority = priority;

        validateBucket();
    }

    
    public static DebtBucket createPurchaseBucket(String bucketId, BigDecimal balance, LocalDate dueDate) {
        return new DebtBucket(bucketId, BucketType.PURCHASE, balance,
                new BigDecimal("0.18"), balance.multiply(new BigDecimal("0.05")),
                dueDate, BucketType.PURCHASE.getDefaultPriority());
    }

    public static DebtBucket createCashAdvanceBucket(String bucketId, BigDecimal balance, LocalDate dueDate) {
        return new DebtBucket(bucketId, BucketType.CASH_ADVANCE, balance,
                new BigDecimal("0.24"), balance.multiply(new BigDecimal("0.10")),
                dueDate, BucketType.CASH_ADVANCE.getDefaultPriority());
    }

    public static DebtBucket createOverdueBucket(String bucketId, BigDecimal balance, LocalDate dueDate) {
        return new DebtBucket(bucketId, BucketType.OVERDUE, balance,
                new BigDecimal("0.40"), balance, 
                dueDate, BucketType.OVERDUE.getDefaultPriority());
    }

    
    public String getBucketId() { return bucketId; }
    public BucketType getType() { return type; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public BigDecimal getInterestRate() { return interestRate; }
    public BigDecimal getMinimumPayment() { return minimumPayment; }
    public LocalDate getDueDate() { return dueDate; }
    public int getPriority() { return priority; }

    
    public boolean hasDebt() {
        return currentBalance.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isOverdue() {
        return type == BucketType.OVERDUE || dueDate.isBefore(LocalDate.now());
    }

    public BigDecimal calculateDailyInterest() {
        if (!hasDebt()) return BigDecimal.ZERO;

        
        return currentBalance.multiply(interestRate)
                .divide(new BigDecimal("365"), 6, BigDecimal.ROUND_HALF_UP);
    }

    public BigDecimal getAvailablePaymentCapacity() {
        
        return currentBalance.subtract(minimumPayment);
    }

    
    public BigDecimal getValueDensity() {
        if (currentBalance.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return interestRate.divide(currentBalance, 8, BigDecimal.ROUND_HALF_UP);
    }

    
    public DebtBucket withPayment(BigDecimal paymentAmount) {
        Objects.requireNonNull(paymentAmount, "Payment amount cannot be null");
        if (paymentAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Payment amount cannot be negative");
        }

        BigDecimal newBalance = currentBalance.subtract(paymentAmount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            newBalance = BigDecimal.ZERO;
        }

        
        BigDecimal newMinimum = newBalance.compareTo(BigDecimal.ZERO) == 0 ?
                BigDecimal.ZERO :
                minimumPayment.multiply(newBalance).divide(currentBalance, 2, BigDecimal.ROUND_HALF_UP);

        return new DebtBucket(bucketId, type, newBalance, interestRate,
                newMinimum, dueDate, priority);
    }

    public DebtBucket withPriority(int newPriority) {
        return new DebtBucket(bucketId, type, currentBalance, interestRate,
                minimumPayment, dueDate, newPriority);
    }

    private void validateBucket() {
        if (currentBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Current balance cannot be negative");
        }
        if (interestRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Interest rate cannot be negative");
        }
        if (minimumPayment.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Minimum payment cannot be negative");
        }
        if (minimumPayment.compareTo(currentBalance) > 0) {
            throw new IllegalArgumentException("Minimum payment cannot exceed current balance");
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        DebtBucket that = (DebtBucket) obj;
        return Objects.equals(bucketId, that.bucketId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bucketId);
    }

    @Override
    public int compareTo(DebtBucket other) {
        
        int priorityCompare = Integer.compare(priority, other.priority);
        if (priorityCompare != 0) {
            return priorityCompare;
        }

        
        int rateCompare = other.interestRate.compareTo(interestRate);
        if (rateCompare != 0) {
            return rateCompare;
        }

        
        return bucketId.compareTo(other.bucketId);
    }

    @Override
    public String toString() {
        return String.format("DebtBucket{id='%s', type=%s, balance=%s, rate=%s%%, priority=%d}",
                bucketId, type, currentBalance,
                interestRate.multiply(new BigDecimal("100")), priority);
    }
}