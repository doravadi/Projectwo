

import java.util.Map;

/**
 * Banking kurallarını temsil eden ana interface.
 *
 * DSL Rule Engine'in temel yapı taşı. Her kural:
 * - ID ve öncelik sahibi
 * - Belirli transaction context'inde çalıştırılabilir
 * - Sonuç (puan, indirim, uyarı, vb.) üretir
 *
 * Örnek kurallar:
 * - "MCC = GROCERY and amount > 100 then points = amount * 0.02"
 * - "hour between 22 and 06 then risk_score = +50"
 * - "country != TR then alert = 'FOREIGN_TRANSACTION'"
 */
public interface Rule {

    /**
     * Kuralın benzersiz kimliği
     */
    String getRuleId();

    /**
     * Kural önceliği (yüksek sayı = önce çalışır)
     * Çakışma durumunda kullanılır
     */
    int getPriority();

    /**
     * Kural açıklaması (business'e yönelik)
     */
    String getDescription();

    /**
     * Kuralın aktif olup olmadığı
     */
    boolean isActive();

    /**
     * Bu kural verilen transaction context'inde uygulanabilir mi?
     *
     * @param context Transaction ve müşteri bilgileri
     * @return true ise kural çalıştırılabilir
     */
    boolean isApplicable(TransactionContext context);

    /**
     * Kuralı çalıştır ve sonuç üret
     *
     * @param context Transaction context
     * @return Kural sonucu (puanlar, uyarılar, indirimler vb.)
     * @throws RuleEvaluationException Çalışma zamanı hatası
     */
    RuleResult execute(TransactionContext context) throws RuleEvaluationException;

    /**
     * Kuralın sahip olduğu koşul sayısı (complexity metriği)
     */
    int getConditionCount();

    /**
     * Kural türü (REWARD, FRAUD_CHECK, DISCOUNT, ALERT, vb.)
     */
    RuleType getRuleType();

    /**
     * Kural kategorileri
     */
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