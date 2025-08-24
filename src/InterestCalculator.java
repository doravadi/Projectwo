

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Günlük ortalama bakiye üzerinden faiz hesaplama motoru.
 * Bucket bazında farklı faiz oranları destekler.
 */
public final class InterestCalculator {

    private final EnumMap<DailyBalance.BalanceBucket, BigDecimal> interestRates;
    private final MathContext mathContext;
    private final RoundingMode roundingMode;
    private final BigDecimal daysInYear;

    public InterestCalculator(EnumMap<DailyBalance.BalanceBucket, BigDecimal> interestRates,
                              MathContext mathContext,
                              RoundingMode roundingMode) {
        this.interestRates = new EnumMap<>(Objects.requireNonNull(interestRates));
        this.mathContext = Objects.requireNonNull(mathContext);
        this.roundingMode = Objects.requireNonNull(roundingMode);
        this.daysInYear = new BigDecimal("365");

        validateInterestRates();
    }

    // Default constructor with Turkish bank rates
    public static InterestCalculator createDefault() {
        EnumMap<DailyBalance.BalanceBucket, BigDecimal> defaultRates =
                new EnumMap<>(DailyBalance.BalanceBucket.class);

        defaultRates.put(DailyBalance.BalanceBucket.PURCHASE, new BigDecimal("0.18"));      // %18 yıllık
        defaultRates.put(DailyBalance.BalanceBucket.CASH_ADVANCE, new BigDecimal("0.24")); // %24 yıllık
        defaultRates.put(DailyBalance.BalanceBucket.INSTALLMENT, new BigDecimal("0.15"));  // %15 yıllık
        defaultRates.put(DailyBalance.BalanceBucket.FEES_INTEREST, new BigDecimal("0.30")); // %30 yıllık

        return new InterestCalculator(defaultRates,
                new MathContext(10, RoundingMode.HALF_UP),
                RoundingMode.HALF_UP);
    }

    /**
     * Dönemlik faiz hesabı - Ana method!
     */
    public InterestCalculationResult calculateInterest(SweepLineCalculator sweepLine,
                                                       DateRange period) {
        Objects.requireNonNull(sweepLine, "SweepLineCalculator cannot be null");
        Objects.requireNonNull(period, "Period cannot be null");

        // Ortalama günlük bakiyeleri al (sweep line optimizasyonu)
        EnumMap<DailyBalance.BalanceBucket, BigDecimal> averageBalances =
                sweepLine.calculateAverageBalances(period);

        // Dönem gün sayısı
        long periodDays = ChronoUnit.DAYS.between(period.getStartDate(),
                period.getEndDate().plusDays(1));
        BigDecimal periodDaysDecimal = new BigDecimal(periodDays);

        // Bucket bazında faiz hesapla
        EnumMap<DailyBalance.BalanceBucket, BigDecimal> bucketInterests =
                new EnumMap<>(DailyBalance.BalanceBucket.class);
        BigDecimal totalInterest = BigDecimal.ZERO;

        for (DailyBalance.BalanceBucket bucket : DailyBalance.BalanceBucket.values()) {
            BigDecimal averageBalance = averageBalances.get(bucket);
            BigDecimal annualRate = interestRates.get(bucket);

            // Dönemlik faiz = (ortalama_bakiye * yıllık_oran * gün_sayısı) / 365
            BigDecimal interest = averageBalance
                    .multiply(annualRate, mathContext)
                    .multiply(periodDaysDecimal, mathContext)
                    .divide(daysInYear, mathContext)
                    .setScale(2, roundingMode);

            bucketInterests.put(bucket, interest);
            totalInterest = totalInterest.add(interest);
        }

        return new InterestCalculationResult(period, averageBalances,
                bucketInterests, totalInterest, periodDays);
    }

    /**
     * Günlük faiz hesabı (detaylı raporlama için)
     */
    public List<DailyInterest> calculateDailyInterest(SweepLineCalculator sweepLine,
                                                      DateRange period) {
        List<DailyBalance> dailyBalances = sweepLine.calculateDailyBalances(period);
        List<DailyInterest> result = new ArrayList<>();

        for (DailyBalance daily : dailyBalances) {
            EnumMap<DailyBalance.BalanceBucket, BigDecimal> dailyInterests =
                    calculateDailyInterestForBalance(daily);

            BigDecimal totalDaily = dailyInterests.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            result.add(new DailyInterest(daily.getDate(), dailyInterests, totalDaily));
        }

        return result;
    }

    // Nested result classes
    public static final class InterestCalculationResult {
        private final DateRange period;
        private final EnumMap<DailyBalance.BalanceBucket, BigDecimal> averageBalances;
        private final EnumMap<DailyBalance.BalanceBucket, BigDecimal> bucketInterests;
        private final BigDecimal totalInterest;
        private final long periodDays;

        public InterestCalculationResult(DateRange period,
                                         EnumMap<DailyBalance.BalanceBucket, BigDecimal> averageBalances,
                                         EnumMap<DailyBalance.BalanceBucket, BigDecimal> bucketInterests,
                                         BigDecimal totalInterest,
                                         long periodDays) {
            this.period = period;
            this.averageBalances = new EnumMap<>(averageBalances);
            this.bucketInterests = new EnumMap<>(bucketInterests);
            this.totalInterest = totalInterest;
            this.periodDays = periodDays;
        }

        public DateRange getPeriod() { return period; }
        public EnumMap<DailyBalance.BalanceBucket, BigDecimal> getAverageBalances() {
            return new EnumMap<>(averageBalances);
        }
        public EnumMap<DailyBalance.BalanceBucket, BigDecimal> getBucketInterests() {
            return new EnumMap<>(bucketInterests);
        }
        public BigDecimal getTotalInterest() { return totalInterest; }
        public long getPeriodDays() { return periodDays; }

        @Override
        public String toString() {
            return String.format("InterestResult{period=%s, total=%s, days=%d}",
                    period, totalInterest, periodDays);
        }
    }

    public static final class DailyInterest {
        private final LocalDate date;
        private final EnumMap<DailyBalance.BalanceBucket, BigDecimal> bucketInterests;
        private final BigDecimal totalInterest;

        public DailyInterest(LocalDate date,
                             EnumMap<DailyBalance.BalanceBucket, BigDecimal> bucketInterests,
                             BigDecimal totalInterest) {
            this.date = date;
            this.bucketInterests = new EnumMap<>(bucketInterests);
            this.totalInterest = totalInterest;
        }

        public LocalDate getDate() { return date; }
        public EnumMap<DailyBalance.BalanceBucket, BigDecimal> getBucketInterests() {
            return new EnumMap<>(bucketInterests);
        }
        public BigDecimal getTotalInterest() { return totalInterest; }

        @Override
        public String toString() {
            return String.format("DailyInterest{date=%s, total=%s}", date, totalInterest);
        }
    }

    /**
     * Compound interest hesabı (taksit planları için)
     */
    public BigDecimal calculateCompoundInterest(BigDecimal principal,
                                                DailyBalance.BalanceBucket bucket,
                                                int months) {
        BigDecimal annualRate = interestRates.get(bucket);
        BigDecimal monthlyRate = annualRate.divide(new BigDecimal("12"), mathContext);

        // A = P * (1 + r)^n
        BigDecimal onePlusRate = BigDecimal.ONE.add(monthlyRate);
        BigDecimal compound = principal.multiply(
                onePlusRate.pow(months, mathContext), mathContext);

        return compound.setScale(2, roundingMode);
    }

    /**
     * Faiz oranını günceller (immutable)
     */
    public InterestCalculator withRate(DailyBalance.BalanceBucket bucket, BigDecimal newRate) {
        EnumMap<DailyBalance.BalanceBucket, BigDecimal> newRates =
                new EnumMap<>(interestRates);
        newRates.put(bucket, newRate);

        return new InterestCalculator(newRates, mathContext, roundingMode);
    }

    // Helper methods
    private EnumMap<DailyBalance.BalanceBucket, BigDecimal> calculateDailyInterestForBalance(
            DailyBalance balance) {

        EnumMap<DailyBalance.BalanceBucket, BigDecimal> interests =
                new EnumMap<>(DailyBalance.BalanceBucket.class);

        for (DailyBalance.BalanceBucket bucket : DailyBalance.BalanceBucket.values()) {
            BigDecimal bucketBalance = balance.getBalance(bucket);

            // Sadece pozitif bakiyeler için faiz hesapla
            if (bucketBalance.compareTo(BigDecimal.ZERO) <= 0) {
                interests.put(bucket, BigDecimal.ZERO);
                continue;
            }

            BigDecimal annualRate = interestRates.get(bucket);

            // Günlük faiz = bakiye * (yıllık_oran / 365)
            BigDecimal dailyRate = annualRate.divide(daysInYear, mathContext);
            BigDecimal dailyInterest = bucketBalance
                    .multiply(dailyRate, mathContext)
                    .setScale(2, roundingMode);

            interests.put(bucket, dailyInterest);
        }

        return interests;
    }

    private void validateInterestRates() {
        for (Map.Entry<DailyBalance.BalanceBucket, BigDecimal> entry : interestRates.entrySet()) {
            BigDecimal rate = entry.getValue();
            if (rate.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Interest rate cannot be negative: " +
                        entry.getKey() + " = " + rate);
            }
            if (rate.compareTo(new BigDecimal("2.0")) > 0) { // %200'ün üstünde uyarı
                System.err.println("Warning: Very high interest rate for " +
                        entry.getKey() + ": " + rate);
            }
        }
    }

    // Getters
    public BigDecimal getRate(DailyBalance.BalanceBucket bucket) {
        return interestRates.get(bucket);
    }

    public MathContext getMathContext() {
        return mathContext;
    }

    public RoundingMode getRoundingMode() {
        return roundingMode;
    }



    @Override
    public String toString() {
        return String.format("InterestCalculator{rates=%s, context=%s}",
                interestRates, mathContext);
    }
}