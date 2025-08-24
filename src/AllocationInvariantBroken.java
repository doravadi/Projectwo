import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;


public final class AllocationInvariantBroken extends RuntimeException {

    private final String bucketId;
    private final BigDecimal expectedBalance;
    private final BigDecimal actualBalance;
    private final InvariantType violationType;
    private final Map<String, Object> context;

    public enum InvariantType {
        NEGATIVE_BALANCE("Negatif bakiye oluştu"),
        MINIMUM_PAYMENT_VIOLATION("Asgari ödeme karşılanmadı"),
        ALLOCATION_OVERFLOW("Tahsis tutarı aşımı"),
        DP_INCONSISTENCY("DP algoritması tutarsızlığı"),
        BUCKET_CAPACITY_EXCEEDED("Bucket kapasitesi aşıldı"),
        TOTAL_MISMATCH("Toplam tahsis uyumsuzluğu");

        private final String description;

        InvariantType(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    public AllocationInvariantBroken(String message) {
        super(message);
        this.bucketId = null;
        this.expectedBalance = null;
        this.actualBalance = null;
        this.violationType = null;
        this.context = null;
    }

    public AllocationInvariantBroken(String message, Throwable cause) {
        super(message, cause);
        this.bucketId = null;
        this.expectedBalance = null;
        this.actualBalance = null;
        this.violationType = null;
        this.context = null;
    }

    public AllocationInvariantBroken(String message, String bucketId,
                                     BigDecimal expectedBalance, BigDecimal actualBalance,
                                     InvariantType violationType, Map<String, Object> context) {
        super(buildDetailedMessage(message, bucketId, expectedBalance, actualBalance, violationType));
        this.bucketId = bucketId;
        this.expectedBalance = expectedBalance;
        this.actualBalance = actualBalance;
        this.violationType = violationType;
        this.context = context != null ? Map.copyOf(context) : null;
    }

    
    public static AllocationInvariantBroken negativeBalance(String bucketId,
                                                            BigDecimal expectedBalance,
                                                            BigDecimal actualBalance,
                                                            BigDecimal allocationAmount) {
        String message = String.format("Allocation resulted in negative balance for bucket %s", bucketId);
        Map<String, Object> context = Map.of("allocationAmount", allocationAmount);
        return new AllocationInvariantBroken(message, bucketId, expectedBalance, actualBalance,
                InvariantType.NEGATIVE_BALANCE, context);
    }

    public static AllocationInvariantBroken minimumPaymentViolation(String bucketId,
                                                                    BigDecimal requiredMinimum,
                                                                    BigDecimal allocatedAmount) {
        String message = String.format("Minimum payment requirement not met for bucket %s", bucketId);
        Map<String, Object> context = Map.of(
                "requiredMinimum", requiredMinimum,
                "allocatedAmount", allocatedAmount
        );
        return new AllocationInvariantBroken(message, bucketId, requiredMinimum, allocatedAmount,
                InvariantType.MINIMUM_PAYMENT_VIOLATION, context);
    }

    public static AllocationInvariantBroken allocationOverflow(String bucketId,
                                                               BigDecimal bucketCapacity,
                                                               BigDecimal attemptedAllocation) {
        String message = String.format("Allocation exceeds bucket capacity for %s", bucketId);
        Map<String, Object> context = Map.of("attemptedAllocation", attemptedAllocation);
        return new AllocationInvariantBroken(message, bucketId, bucketCapacity, attemptedAllocation,
                InvariantType.BUCKET_CAPACITY_EXCEEDED, context);
    }

    public static AllocationInvariantBroken dpInconsistency(String operation,
                                                            BigDecimal expectedResult,
                                                            BigDecimal actualResult,
                                                            Map<String, Object> dpContext) {
        String message = String.format("DP algorithm inconsistency in %s", operation);
        return new AllocationInvariantBroken(message, null, expectedResult, actualResult,
                InvariantType.DP_INCONSISTENCY, dpContext);
    }

    public static AllocationInvariantBroken totalMismatch(BigDecimal totalPayment,
                                                          BigDecimal totalAllocated,
                                                          Map<String, BigDecimal> allocations) {
        String message = "Total allocation amount does not match payment amount";
        Map<String, Object> context = Map.of(
                "totalPayment", totalPayment,
                "totalAllocated", totalAllocated,
                "allocationCount", allocations.size(),
                "allocations", allocations
        );
        return new AllocationInvariantBroken(message, null, totalPayment, totalAllocated,
                InvariantType.TOTAL_MISMATCH, context);
    }

    
    public String getBucketId() { return bucketId; }
    public BigDecimal getExpectedBalance() { return expectedBalance; }
    public BigDecimal getActualBalance() { return actualBalance; }
    public InvariantType getViolationType() { return violationType; }
    public Map<String, Object> getContext() {
        return context != null ? Map.copyOf(context) : null;
    }

    public boolean hasBalanceComparison() {
        return expectedBalance != null && actualBalance != null;
    }

    
    public BigDecimal getBalanceDifference() {
        if (!hasBalanceComparison()) {
            return null;
        }
        return actualBalance.subtract(expectedBalance).abs();
    }

    
    public int getSeverityLevel() {
        if (violationType == null) return 0;

        return switch (violationType) {
            case NEGATIVE_BALANCE -> 5;           
            case MINIMUM_PAYMENT_VIOLATION -> 4; 
            case ALLOCATION_OVERFLOW -> 4;       
            case DP_INCONSISTENCY -> 3;         
            case BUCKET_CAPACITY_EXCEEDED -> 3;  
            case TOTAL_MISMATCH -> 2;            
        };
    }

    
    public String getRecoveryAdvice() {
        if (violationType == null) {
            return "Review allocation logic and validate inputs";
        }

        return switch (violationType) {
            case NEGATIVE_BALANCE -> "Validate allocation amount before applying to bucket. " +
                    "Ensure allocation <= bucket balance";
            case MINIMUM_PAYMENT_VIOLATION -> "Check minimum payment requirements before allocation. " +
                    "Prioritize minimum payments across all buckets";
            case ALLOCATION_OVERFLOW -> "Implement capacity checks in allocation algorithm. " +
                    "Use bucket.getCurrentBalance() as maximum allocation";
            case DP_INCONSISTENCY -> "Review DP algorithm implementation. " +
                    "Add consistency checks in backtracking phase";
            case BUCKET_CAPACITY_EXCEEDED -> "Add pre-allocation validation. " +
                    "Use available capacity calculation";
            case TOTAL_MISMATCH -> "Implement running total validation during allocation. " +
                    "Add tolerance-based comparison for BigDecimal arithmetic";
        };
    }

    
    public String getContextSummary() {
        if (context == null || context.isEmpty()) {
            return "No context available";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append(", ");
        }

        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2); 
        }

        return sb.toString();
    }

    private static String buildDetailedMessage(String message, String bucketId,
                                               BigDecimal expectedBalance, BigDecimal actualBalance,
                                               InvariantType violationType) {
        StringBuilder sb = new StringBuilder(message);

        if (violationType != null) {
            sb.append(" [Type: ").append(violationType.getDescription()).append("]");
        }

        if (bucketId != null) {
            sb.append(" [BucketId: ").append(bucketId).append("]");
        }

        if (expectedBalance != null && actualBalance != null) {
            sb.append(" [Expected: ").append(expectedBalance)
                    .append(", Actual: ").append(actualBalance);

            BigDecimal diff = actualBalance.subtract(expectedBalance);
            sb.append(", Difference: ").append(diff).append("]");
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("AllocationInvariantBroken{type=%s, bucket=%s, severity=%d}",
                violationType, bucketId, getSeverityLevel());
    }
}