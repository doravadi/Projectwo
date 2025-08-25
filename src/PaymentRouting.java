
import java.util.*;
import java.io.Serializable;

public final class PaymentRouting implements Serializable {

    private static final long serialVersionUID = 1L;
    private final String bankName;
    private final String country;
    private final String cardType;
    private final boolean domesticTransaction;
    private final RiskLevel riskLevel;

    private PaymentRouting(Builder builder) {
        this.bankName = Objects.requireNonNull(builder.bankName);
        this.country = Objects.requireNonNull(builder.country);
        this.cardType = Objects.requireNonNull(builder.cardType);
        this.domesticTransaction = builder.domesticTransaction;
        this.riskLevel = Objects.requireNonNull(builder.riskLevel);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getBankName() {
        return bankName;
    }

    public String getCountry() {
        return country;
    }

    public String getCardType() {
        return cardType;
    }

    public boolean isDomesticTransaction() {
        return domesticTransaction;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public static class Builder {
        private String bankName;
        private String country;
        private String cardType;
        private boolean domesticTransaction;
        private RiskLevel riskLevel;

        public Builder bankName(String bankName) {
            this.bankName = bankName;
            return this;
        }

        public Builder country(String country) {
            this.country = country;
            return this;
        }

        public Builder cardType(String cardType) {
            this.cardType = cardType;
            return this;
        }

        public Builder domesticTransaction(boolean domestic) {
            this.domesticTransaction = domestic;
            return this;
        }

        public Builder riskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
            return this;
        }

        public PaymentRouting build() {
            return new PaymentRouting(this);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PaymentRouting that = (PaymentRouting) obj;
        return domesticTransaction == that.domesticTransaction &&
                Objects.equals(bankName, that.bankName) &&
                Objects.equals(country, that.country) &&
                Objects.equals(cardType, that.cardType) &&
                riskLevel == that.riskLevel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bankName, country, cardType, domesticTransaction, riskLevel);
    }

    @Override
    public String toString() {
        return String.format("PaymentRouting[bank=%s, country=%s, type=%s, domestic=%s, risk=%s]",
                bankName, country, cardType, domesticTransaction, riskLevel);
    }
}