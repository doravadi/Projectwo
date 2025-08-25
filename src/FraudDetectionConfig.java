import java.math.BigDecimal;


public final class FraudDetectionConfig {

    private final int criticalRiskThreshold;
    private final int moderateRiskThreshold;
    private final int duplicateAlertThreshold;
    private final BigDecimal highAmountThreshold;

    public FraudDetectionConfig(int criticalRiskThreshold, int moderateRiskThreshold,
                                int duplicateAlertThreshold, BigDecimal highAmountThreshold) {
        this.criticalRiskThreshold = criticalRiskThreshold;
        this.moderateRiskThreshold = moderateRiskThreshold;
        this.duplicateAlertThreshold = duplicateAlertThreshold;
        this.highAmountThreshold = highAmountThreshold;
    }

    public static FraudDetectionConfig createDefault() {
        return new FraudDetectionConfig(80, 40, 30, new BigDecimal("5000"));
    }

    public static FraudDetectionConfig createHighSecurity() {
        return new FraudDetectionConfig(60, 30, 20, new BigDecimal("2000"));
    }

    public int getCriticalRiskThreshold() {
        return criticalRiskThreshold;
    }

    public int getModerateRiskThreshold() {
        return moderateRiskThreshold;
    }

    public int getDuplicateAlertThreshold() {
        return duplicateAlertThreshold;
    }

    public BigDecimal getHighAmountThreshold() {
        return highAmountThreshold;
    }
}