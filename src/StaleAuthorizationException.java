
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;


public class StaleAuthorizationException extends DomainException {

    private final Auth staleAuth;
    private final Presentment attemptedPresentment;
    private final LocalDateTime currentTime;
    private final Duration expiredDuration;
    private final List<String> businessImpacts;


    public StaleAuthorizationException(Auth staleAuth, Presentment attemptedPresentment) {
        super(buildErrorMessage(staleAuth, attemptedPresentment));

        this.staleAuth = Objects.requireNonNull(staleAuth, "Stale auth cannot be null");
        this.attemptedPresentment = Objects.requireNonNull(attemptedPresentment, "Attempted presentment cannot be null");
        this.currentTime = LocalDateTime.now();
        this.expiredDuration = Duration.between(staleAuth.getExpiryTime(), currentTime);
        this.businessImpacts = calculateBusinessImpacts();
    }


    public StaleAuthorizationException(Auth staleAuth, Presentment attemptedPresentment, String additionalContext) {
        super(buildErrorMessage(staleAuth, attemptedPresentment) + ". Additional context: " + additionalContext);

        this.staleAuth = staleAuth;
        this.attemptedPresentment = attemptedPresentment;
        this.currentTime = LocalDateTime.now();
        this.expiredDuration = Duration.between(staleAuth.getExpiryTime(), currentTime);
        this.businessImpacts = calculateBusinessImpacts();
    }


    public StaleAuthorizationException(Auth staleAuth, Presentment attemptedPresentment, Throwable cause) {
        super(buildErrorMessage(staleAuth, attemptedPresentment), cause);

        this.staleAuth = staleAuth;
        this.attemptedPresentment = attemptedPresentment;
        this.currentTime = LocalDateTime.now();
        this.expiredDuration = Duration.between(staleAuth.getExpiryTime(), currentTime);
        this.businessImpacts = calculateBusinessImpacts();
    }


    public Auth getStaleAuth() {
        return staleAuth;
    }

    public Presentment getAttemptedPresentment() {
        return attemptedPresentment;
    }

    public LocalDateTime getCurrentTime() {
        return currentTime;
    }

    public Duration getExpiredDuration() {
        return expiredDuration;
    }

    public List<String> getBusinessImpacts() {
        return new ArrayList<>(businessImpacts);
    }


    public boolean isRecentlyExpired() {
        return expiredDuration.toHours() <= 24;
    }

    public boolean isLongExpired() {
        return expiredDuration.toDays() > 7;
    }

    public boolean isCriticallyExpired() {
        return expiredDuration.toDays() > 30;
    }

    public String getExpirationSeverity() {
        if (isCriticallyExpired()) {
            return "CRITICAL";
        } else if (isLongExpired()) {
            return "HIGH";
        } else if (isRecentlyExpired()) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }


    public List<String> getRecommendedActions() {
        List<String> actions = new ArrayList<>();

        if (isRecentlyExpired()) {
            actions.add("Check if authorization can be extended");
            actions.add("Contact issuer for auth renewal");
            actions.add("Process as exception if high-value merchant");
        } else if (isLongExpired()) {
            actions.add("Reject presentment - authorization too old");
            actions.add("Request new authorization from merchant");
            actions.add("Flag merchant for process improvement");
        } else if (isCriticallyExpired()) {
            actions.add("Block settlement - unacceptable delay");
            actions.add("Escalate to risk management");
            actions.add("Review merchant settlement patterns");
            actions.add("Consider merchant penalties");
        }

        return actions;
    }


    public Money getFinancialImpact() {

        return attemptedPresentment.getAmount();
    }


    public StaleAuthRisk getRiskAssessment() {
        RiskLevel riskLevel;
        List<String> riskFactors = new ArrayList<>();


        if (isCriticallyExpired()) {
            riskLevel = RiskLevel.CRITICAL;
            riskFactors.add("Authorization expired over 30 days ago");
            riskFactors.add("High probability of issuer rejection");
        } else if (isLongExpired()) {
            riskLevel = RiskLevel.HIGH;
            riskFactors.add("Authorization expired over 7 days ago");
            riskFactors.add("Possible issuer rejection");
        } else if (isRecentlyExpired()) {
            riskLevel = RiskLevel.MEDIUM;
            riskFactors.add("Recently expired authorization");
            riskFactors.add("May still be processable");
        } else {
            riskLevel = RiskLevel.LOW;
            riskFactors.add("Authorization just expired");
        }


        Money amount = attemptedPresentment.getAmount();
        if (amount.getAmount().compareTo(new java.math.BigDecimal("10000")) > 0) {
            riskFactors.add("High-value transaction increases settlement risk");
        }


        if (staleAuth.getStatus() != AuthStatus.APPROVED) {
            riskFactors.add("Authorization status is not approved: " + staleAuth.getStatus());
        }

        return new StaleAuthRisk(riskLevel, riskFactors, getFinancialImpact());
    }


    public boolean requiresManualIntervention() {
        return isLongExpired() ||
                isCriticallyExpired() ||
                getFinancialImpact().getAmount().compareTo(new java.math.BigDecimal("1000")) > 0;
    }


    public StaleAuthExceptionReport generateReport() {
        return StaleAuthExceptionReport.builder()
                .authId(staleAuth.getAuthId())
                .presentmentId(attemptedPresentment.getPresentmentId())
                .authExpiredAt(staleAuth.getExpiryTime())
                .presentmentTimestamp(attemptedPresentment.getTimestamp())
                .expiredDuration(expiredDuration)
                .severity(getExpirationSeverity())
                .financialImpact(getFinancialImpact())
                .riskAssessment(getRiskAssessment())
                .businessImpacts(businessImpacts)
                .recommendedActions(getRecommendedActions())
                .requiresManualIntervention(requiresManualIntervention())
                .exceptionTimestamp(currentTime)
                .build();
    }


    private static String buildErrorMessage(Auth staleAuth, Presentment attemptedPresentment) {
        Duration expiredDuration = Duration.between(staleAuth.getExpiryTime(), LocalDateTime.now());

        return String.format(
                "Cannot match presentment %s with expired authorization %s. " +
                        "Auth expired %s ago on %s. Presentment timestamp: %s",
                attemptedPresentment.getPresentmentId(),
                staleAuth.getAuthId(),
                formatDuration(expiredDuration),
                staleAuth.getExpiryTime(),
                attemptedPresentment.getTimestamp()
        );
    }

    private static String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;

        if (days > 0) {
            return String.format("%d days, %d hours", days, hours);
        } else if (hours > 0) {
            return String.format("%d hours, %d minutes", hours, minutes);
        } else {
            return String.format("%d minutes", minutes);
        }
    }

    private List<String> calculateBusinessImpacts() {
        List<String> impacts = new ArrayList<>();

        impacts.add("Settlement delayed or blocked");
        impacts.add("Merchant cash flow impact");

        if (isLongExpired()) {
            impacts.add("Increased chargeback risk");
            impacts.add("Potential merchant relationship strain");
        }

        if (isCriticallyExpired()) {
            impacts.add("Regulatory compliance concerns");
            impacts.add("Audit trail complications");
            impacts.add("Risk management escalation required");
        }

        Money amount = getFinancialImpact();
        if (amount.getAmount().compareTo(new java.math.BigDecimal("5000")) > 0) {
            impacts.add("Significant financial exposure");
        }

        return impacts;
    }


    public static class StaleAuthRisk {
        private final RiskLevel level;
        private final List<String> factors;
        private final Money financialExposure;

        public StaleAuthRisk(RiskLevel level, List<String> factors, Money financialExposure) {
            this.level = level;
            this.factors = new ArrayList<>(factors);
            this.financialExposure = financialExposure;
        }

        public RiskLevel getLevel() {
            return level;
        }

        public List<String> getFactors() {
            return new ArrayList<>(factors);
        }

        public Money getFinancialExposure() {
            return financialExposure;
        }

        @Override
        public String toString() {
            return String.format("StaleAuthRisk[level=%s, factors=%d, exposure=%s]",
                    level, factors.size(), financialExposure);
        }
    }

    public static class StaleAuthExceptionReport {
        private final String authId;
        private final String presentmentId;
        private final LocalDateTime authExpiredAt;
        private final LocalDateTime presentmentTimestamp;
        private final Duration expiredDuration;
        private final String severity;
        private final Money financialImpact;
        private final StaleAuthRisk riskAssessment;
        private final List<String> businessImpacts;
        private final List<String> recommendedActions;
        private final boolean requiresManualIntervention;
        private final LocalDateTime exceptionTimestamp;

        private StaleAuthExceptionReport(Builder builder) {
            this.authId = builder.authId;
            this.presentmentId = builder.presentmentId;
            this.authExpiredAt = builder.authExpiredAt;
            this.presentmentTimestamp = builder.presentmentTimestamp;
            this.expiredDuration = builder.expiredDuration;
            this.severity = builder.severity;
            this.financialImpact = builder.financialImpact;
            this.riskAssessment = builder.riskAssessment;
            this.businessImpacts = builder.businessImpacts;
            this.recommendedActions = builder.recommendedActions;
            this.requiresManualIntervention = builder.requiresManualIntervention;
            this.exceptionTimestamp = builder.exceptionTimestamp;
        }

        public static Builder builder() {
            return new Builder();
        }


        public String getAuthId() {
            return authId;
        }

        public String getPresentmentId() {
            return presentmentId;
        }

        public LocalDateTime getAuthExpiredAt() {
            return authExpiredAt;
        }

        public LocalDateTime getPresentmentTimestamp() {
            return presentmentTimestamp;
        }

        public Duration getExpiredDuration() {
            return expiredDuration;
        }

        public String getSeverity() {
            return severity;
        }

        public Money getFinancialImpact() {
            return financialImpact;
        }

        public StaleAuthRisk getRiskAssessment() {
            return riskAssessment;
        }

        public List<String> getBusinessImpacts() {
            return businessImpacts;
        }

        public List<String> getRecommendedActions() {
            return recommendedActions;
        }

        public boolean requiresManualIntervention() {
            return requiresManualIntervention;
        }

        public LocalDateTime getExceptionTimestamp() {
            return exceptionTimestamp;
        }

        public static class Builder {
            private String authId;
            private String presentmentId;
            private LocalDateTime authExpiredAt;
            private LocalDateTime presentmentTimestamp;
            private Duration expiredDuration;
            private String severity;
            private Money financialImpact;
            private StaleAuthRisk riskAssessment;
            private List<String> businessImpacts;
            private List<String> recommendedActions;
            private boolean requiresManualIntervention;
            private LocalDateTime exceptionTimestamp;

            public Builder authId(String authId) {
                this.authId = authId;
                return this;
            }

            public Builder presentmentId(String presentmentId) {
                this.presentmentId = presentmentId;
                return this;
            }

            public Builder authExpiredAt(LocalDateTime authExpiredAt) {
                this.authExpiredAt = authExpiredAt;
                return this;
            }

            public Builder presentmentTimestamp(LocalDateTime presentmentTimestamp) {
                this.presentmentTimestamp = presentmentTimestamp;
                return this;
            }

            public Builder expiredDuration(Duration expiredDuration) {
                this.expiredDuration = expiredDuration;
                return this;
            }

            public Builder severity(String severity) {
                this.severity = severity;
                return this;
            }

            public Builder financialImpact(Money financialImpact) {
                this.financialImpact = financialImpact;
                return this;
            }

            public Builder riskAssessment(StaleAuthRisk riskAssessment) {
                this.riskAssessment = riskAssessment;
                return this;
            }

            public Builder businessImpacts(List<String> businessImpacts) {
                this.businessImpacts = businessImpacts;
                return this;
            }

            public Builder recommendedActions(List<String> recommendedActions) {
                this.recommendedActions = recommendedActions;
                return this;
            }

            public Builder requiresManualIntervention(boolean requiresManualIntervention) {
                this.requiresManualIntervention = requiresManualIntervention;
                return this;
            }

            public Builder exceptionTimestamp(LocalDateTime exceptionTimestamp) {
                this.exceptionTimestamp = exceptionTimestamp;
                return this;
            }

            public StaleAuthExceptionReport build() {
                return new StaleAuthExceptionReport(this);
            }
        }
    }
}