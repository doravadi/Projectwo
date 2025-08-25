

import java.util.Map;


public interface Rule {


    String getRuleId();


    int getPriority();


    String getDescription();


    boolean isActive();


    boolean isApplicable(TransactionContext context);


    RuleResult execute(TransactionContext context) throws RuleEvaluationException;


    int getConditionCount();


    RuleType getRuleType();


    enum RuleType {
        REWARD("Puan/ödül kuralları"),
        FRAUD_CHECK("Fraud tespit kuralları"),
        DISCOUNT("İndirim kuralları"),
        ALERT("Uyarı kuralları"),
        LIMIT_CHECK("Limit kontrolü"),
        MCC_ROUTING("MCC bazlı yönlendirme"),
        GEOGRAPHIC("Coğrafi kurallar"),
        TIME_BASED("Zaman bazlı kurallar");

        private final String description;

        RuleType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}