
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Para hesaplamalarında yuvarlama politikası ihlallerinde fırlatılan runtime exception.
 *
 * Bu exception şu durumlarda fırlatılır:
 * - Yanlış RoundingMode kullanımı
 * - Hassasiyet kaybına neden olan işlemler
 * - Faiz hesaplamalarında tolerans aşımı (±0.01 TL)
 * - Sweep line vs brute force farkı çok büyük
 */
public final class RoundingPolicyViolation extends RuntimeException {

    private final BigDecimal expectedValue;
    private final BigDecimal actualValue;
    private final RoundingMode usedRoundingMode;
    private final String operation;

    public RoundingPolicyViolation(String message) {
        super(message);
        this.expectedValue = null;
        this.actualValue = null;
        this.usedRoundingMode = null;
        this.operation = null;
    }

    public RoundingPolicyViolation(String message, Throwable cause) {
        super(message, cause);
        this.expectedValue = null;
        this.actualValue = null;
        this.usedRoundingMode = null;
        this.operation = null;
    }

    public RoundingPolicyViolation(String message,
                                   BigDecimal expectedValue,
                                   BigDecimal actualValue,
                                   RoundingMode usedRoundingMode,
                                   String operation) {
        super(buildDetailedMessage(message, expectedValue, actualValue, usedRoundingMode, operation));
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
        this.usedRoundingMode = usedRoundingMode;
        this.operation = operation;
    }

    // Factory methods for common banking scenarios
    public static RoundingPolicyViolation toleranceExceeded(String operation,
                                                            BigDecimal expected,
                                                            BigDecimal actual,
                                                            BigDecimal tolerance) {
        String message = String.format("Tolerance exceeded in %s: expected=%s, actual=%s, tolerance=%s",
                operation, expected, actual, tolerance);
        return new RoundingPolicyViolation(message, expected, actual, null, operation);
    }

    public static RoundingPolicyViolation invalidRoundingMode(String operation,
                                                              RoundingMode problematicMode,
                                                              String reason) {
        String message = String.format("Invalid rounding mode %s in %s: %s",
                problematicMode, operation, reason);
        return new RoundingPolicyViolation(message, null, null, problematicMode, operation);
    }

    public static RoundingPolicyViolation precisionLoss(String operation,
                                                        BigDecimal originalValue,
                                                        BigDecimal roundedValue) {
        String message = String.format("Precision loss detected in %s", operation);
        return new RoundingPolicyViolation(message, originalValue, roundedValue, null, operation);
    }

    public static RoundingPolicyViolation interestDrift(String operation,
                                                        BigDecimal calculatedTotal,
                                                        BigDecimal sumOfParts,
                                                        BigDecimal allowedDrift) {
        String message = String.format("Interest calculation drift in %s exceeds allowed variance", operation);
        return new RoundingPolicyViolation(message, calculatedTotal, sumOfParts, null, operation);
    }

    public static RoundingPolicyViolation scaleViolation(String operation,
                                                         int requiredScale,
                                                         int actualScale) {
        String message = String.format("Scale violation in %s: required %d decimal places, got %d",
                operation, requiredScale, actualScale);
        return new RoundingPolicyViolation(message);
    }

    public static RoundingPolicyViolation sweepLineMismatch(BigDecimal sweepLineResult,
                                                            BigDecimal bruteForceResult,
                                                            BigDecimal tolerance) {
        String message = "Sweep line algorithm result differs from brute force validation";
        return new RoundingPolicyViolation(message, bruteForceResult, sweepLineResult, null,
                "interest_calculation_validation");
    }

    // Getters
    public BigDecimal getExpectedValue() {
        return expectedValue;
    }

    public BigDecimal getActualValue() {
        return actualValue;
    }

    public RoundingMode getUsedRoundingMode() {
        return usedRoundingMode;
    }

    public String getOperation() {
        return operation;
    }

    public boolean hasValueComparison() {
        return expectedValue != null && actualValue != null;
    }

    /**
     * Farkın mutlak değerini döndürür (varsa)
     */
    public BigDecimal getDifference() {
        if (!hasValueComparison()) {
            return null;
        }
        return actualValue.subtract(expectedValue).abs();
    }

    /**
     * Yüzde farkı hesaplar (varsa)
     */
    public BigDecimal getPercentageDifference() {
        if (!hasValueComparison() || expectedValue.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal difference = getDifference();
        return difference.divide(expectedValue.abs(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    /**
     * Tolerans dahilinde mi kontrol eder
     */
    public boolean isWithinTolerance(BigDecimal tolerance) {
        if (!hasValueComparison()) {
            return false;
        }
        return getDifference().compareTo(tolerance) <= 0;
    }

    private static String buildDetailedMessage(String message,
                                               BigDecimal expectedValue,
                                               BigDecimal actualValue,
                                               RoundingMode usedRoundingMode,
                                               String operation) {
        StringBuilder sb = new StringBuilder(message);

        if (operation != null) {
            sb.append(" [Operation: ").append(operation).append("]");
        }

        if (expectedValue != null && actualValue != null) {
            sb.append(" [Expected: ").append(expectedValue)
                    .append(", Actual: ").append(actualValue);

            BigDecimal diff = actualValue.subtract(expectedValue);
            sb.append(", Difference: ").append(diff).append("]");
        }

        if (usedRoundingMode != null) {
            sb.append(" [RoundingMode: ").append(usedRoundingMode).append("]");
        }

        return sb.toString();
    }

    /**
     * Recovery önerisi (debugging için)
     */
    public String getRecoveryAdvice() {
        if (usedRoundingMode == RoundingMode.UNNECESSARY) {
            return "Use explicit RoundingMode like HALF_UP or HALF_EVEN for monetary calculations";
        }

        if (hasValueComparison()) {
            BigDecimal percentDiff = getPercentageDifference();
            if (percentDiff != null && percentDiff.compareTo(new BigDecimal("0.01")) > 0) {
                return "Consider using higher precision MathContext or different calculation order";
            }
        }

        return "Review calculation logic and rounding strategy";
    }
}