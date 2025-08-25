import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public final class SweepLineService {

    private final Map<String, SweepLineCalculator> accountCalculators;
    private final InterestCalculator interestCalculator;
    private final MathContext mathContext;
    private final BigDecimal toleranceThreshold;

    public SweepLineService(InterestCalculator interestCalculator) {
        this.accountCalculators = new ConcurrentHashMap<>();
        this.interestCalculator = Objects.requireNonNull(interestCalculator);
        this.mathContext = new MathContext(10, RoundingMode.HALF_UP);
        this.toleranceThreshold = new BigDecimal("0.01");
    }

    public static SweepLineService createDefault() {
        return new SweepLineService(InterestCalculator.createDefault());
    }


    public void addBalanceChange(String accountId, BalanceChange change,
                                 DailyBalance.BalanceBucket bucket) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(change, "Balance change cannot be null");
        Objects.requireNonNull(bucket, "Bucket cannot be null");

        SweepLineCalculator calculator = getOrCreateCalculator(accountId);
        calculator.addBalanceChange(change, bucket);
    }


    public void addBalanceChanges(String accountId,
                                  List<BalanceChange> changes,
                                  Map<BalanceChange, DailyBalance.BalanceBucket> bucketMapping) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(changes, "Changes cannot be null");
        Objects.requireNonNull(bucketMapping, "Bucket mapping cannot be null");

        SweepLineCalculator calculator = getOrCreateCalculator(accountId);
        calculator.addBalanceChanges(changes, bucketMapping);
    }


    public InterestCalculator.InterestCalculationResult calculateStatementInterest(
            String accountId, DateRange statementPeriod) {

        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(statementPeriod, "Statement period cannot be null");

        SweepLineCalculator calculator = getCalculator(accountId);
        InterestCalculator.InterestCalculationResult result =
                interestCalculator.calculateInterest(calculator, statementPeriod);


        validateResultAccuracy(calculator, statementPeriod, result);

        return result;
    }


    public List<InterestCalculator.DailyInterest> calculateDailyInterest(String accountId, DateRange period) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(period, "Period cannot be null");

        SweepLineCalculator calculator = getCalculator(accountId);
        return interestCalculator.calculateDailyInterest(calculator, period);
    }


    public List<DailyBalance> getDailyBalanceHistory(String accountId, DateRange period) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(period, "Period cannot be null");

        SweepLineCalculator calculator = getCalculator(accountId);
        return calculator.calculateDailyBalances(period);
    }


    public DailyBalance getBalanceAt(String accountId, LocalDate date) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(date, "Date cannot be null");

        SweepLineCalculator calculator = getCalculator(accountId);
        return calculator.getBalanceAt(date);
    }


    public BenchmarkResult benchmarkAccuracy(String accountId, DateRange period) {
        SweepLineCalculator calculator = getCalculator(accountId);

        long sweepStartTime = System.nanoTime();
        InterestCalculator.InterestCalculationResult sweepResult =
                interestCalculator.calculateInterest(calculator, period);
        long sweepDuration = System.nanoTime() - sweepStartTime;

        long bruteStartTime = System.nanoTime();
        BigDecimal bruteForceResult = calculateBruteForceInterest(calculator, period);
        long bruteDuration = System.nanoTime() - bruteStartTime;

        BigDecimal difference = sweepResult.getTotalInterest().subtract(bruteForceResult).abs();
        boolean withinTolerance = difference.compareTo(toleranceThreshold) <= 0;

        return new BenchmarkResult(sweepResult.getTotalInterest(), bruteForceResult,
                difference, withinTolerance, sweepDuration, bruteDuration);
    }


    public boolean removeAccount(String accountId) {
        return accountCalculators.remove(accountId) != null;
    }


    public Map<String, Object> getSystemStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeAccounts", accountCalculators.size());
        stats.put("toleranceThreshold", toleranceThreshold);
        stats.put("mathContext", mathContext);


        Map<String, Object> accountStats = new HashMap<>();
        for (Map.Entry<String, SweepLineCalculator> entry : accountCalculators.entrySet()) {
            accountStats.put(entry.getKey(), entry.getValue().getStatistics());
        }
        stats.put("accountStatistics", accountStats);

        return stats;
    }


    private SweepLineCalculator getOrCreateCalculator(String accountId) {
        return accountCalculators.computeIfAbsent(accountId,
                k -> new SweepLineCalculator());
    }

    private SweepLineCalculator getCalculator(String accountId) {
        SweepLineCalculator calculator = accountCalculators.get(accountId);
        if (calculator == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        return calculator;
    }


    private void validateResultAccuracy(SweepLineCalculator calculator,
                                        DateRange period,
                                        InterestCalculator.InterestCalculationResult result) {

        BigDecimal bruteForceTotal = calculateBruteForceInterest(calculator, period);
        BigDecimal sweepLineTotal = result.getTotalInterest();
        BigDecimal difference = sweepLineTotal.subtract(bruteForceTotal).abs();

        if (difference.compareTo(toleranceThreshold) > 0) {
            throw RoundingPolicyViolation.sweepLineMismatch(sweepLineTotal, bruteForceTotal, toleranceThreshold);
        }
    }


    private BigDecimal calculateBruteForceInterest(SweepLineCalculator calculator, DateRange period) {
        List<DailyBalance> dailyBalances = calculator.calculateDailyBalances(period);
        BigDecimal totalInterest = BigDecimal.ZERO;

        for (DailyBalance daily : dailyBalances) {

            for (DailyBalance.BalanceBucket bucket : DailyBalance.BalanceBucket.values()) {
                BigDecimal balance = daily.getBalance(bucket);
                if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal rate = interestCalculator.getRate(bucket);


                BigDecimal dailyInterest = balance
                        .multiply(rate, mathContext)
                        .divide(new BigDecimal("365"), mathContext)
                        .setScale(2, RoundingMode.HALF_UP);

                totalInterest = totalInterest.add(dailyInterest);
            }
        }

        return totalInterest;
    }


    public static final class BenchmarkResult {
        private final BigDecimal sweepLineResult;
        private final BigDecimal bruteForceResult;
        private final BigDecimal difference;
        private final boolean withinTolerance;
        private final long sweepLineDuration;
        private final long bruteForceDuration;

        public BenchmarkResult(BigDecimal sweepLineResult, BigDecimal bruteForceResult,
                               BigDecimal difference, boolean withinTolerance,
                               long sweepLineDuration, long bruteForceDuration) {
            this.sweepLineResult = sweepLineResult;
            this.bruteForceResult = bruteForceResult;
            this.difference = difference;
            this.withinTolerance = withinTolerance;
            this.sweepLineDuration = sweepLineDuration;
            this.bruteForceDuration = bruteForceDuration;
        }

        public BigDecimal getSweepLineResult() {
            return sweepLineResult;
        }

        public BigDecimal getBruteForceResult() {
            return bruteForceResult;
        }

        public BigDecimal getDifference() {
            return difference;
        }

        public boolean isWithinTolerance() {
            return withinTolerance;
        }

        public long getSweepLineDuration() {
            return sweepLineDuration;
        }

        public long getBruteForceDuration() {
            return bruteForceDuration;
        }

        public double getSpeedupRatio() {
            return (double) bruteForceDuration / sweepLineDuration;
        }

        @Override
        public String toString() {
            return String.format("Benchmark{sweep=%s, brute=%s, diff=%s, tolerance=%s, speedup=%.2fx}",
                    sweepLineResult, bruteForceResult, difference,
                    withinTolerance, getSpeedupRatio());
        }
    }

    @Override
    public String toString() {
        return String.format("SweepLineService{accounts=%d, tolerance=%s}",
                accountCalculators.size(), toleranceThreshold);
    }
}