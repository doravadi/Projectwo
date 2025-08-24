import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Para çifti ve kur bilgisini temsil eden immutable value object.
 *
 * Arbitraj detection için kullanılır:
 * - Exchange rate: FROM currency → TO currency dönüşüm oranı
 * - Logarithmic weight: -log(rate) Bellman-Ford için
 * - Bid/Ask spread: Market maker spread
 */
public final class CurrencyPair implements Comparable<CurrencyPair> {

    private final Currency fromCurrency;
    private final Currency toCurrency;
    private final BigDecimal exchangeRate;
    private final BigDecimal bidPrice;        // Alış fiyatı
    private final BigDecimal askPrice;        // Satış fiyatı
    private final LocalDateTime timestamp;
    private final String source;              // Kur kaynağı (API, Bank, etc.)

    // Logarithmic weight cache (-log(rate))
    private final double logWeight;

    private static final MathContext MATH_CONTEXT = new MathContext(10, RoundingMode.HALF_UP);

    public CurrencyPair(Currency fromCurrency, Currency toCurrency, BigDecimal exchangeRate,
                        BigDecimal bidPrice, BigDecimal askPrice, String source) {
        this.fromCurrency = Objects.requireNonNull(fromCurrency, "From currency cannot be null");
        this.toCurrency = Objects.requireNonNull(toCurrency, "To currency cannot be null");
        this.exchangeRate = Objects.requireNonNull(exchangeRate, "Exchange rate cannot be null");
        this.bidPrice = Objects.requireNonNull(bidPrice, "Bid price cannot be null");
        this.askPrice = Objects.requireNonNull(askPrice, "Ask price cannot be null");
        this.source = Objects.requireNonNull(source, "Source cannot be null");
        this.timestamp = LocalDateTime.now();

        validateRates();

        // Pre-compute logarithmic weight for Bellman-Ford
        this.logWeight = -Math.log(exchangeRate.doubleValue());
    }

    // Factory methods
    public static CurrencyPair of(Currency from, Currency to, BigDecimal rate, String source) {
        // Default spread %0.1
        BigDecimal spread = rate.multiply(new BigDecimal("0.001"), MATH_CONTEXT);
        BigDecimal bid = rate.subtract(spread);
        BigDecimal ask = rate.add(spread);

        return new CurrencyPair(from, to, rate, bid, ask, source);
    }

    public static CurrencyPair ofMidRate(Currency from, Currency to, BigDecimal midRate,
                                         BigDecimal spreadBps, String source) {
        // Spread in basis points (100 bps = 1%)
        BigDecimal spreadFactor = spreadBps.divide(new BigDecimal("10000"), MATH_CONTEXT);
        BigDecimal spreadAmount = midRate.multiply(spreadFactor, MATH_CONTEXT);

        BigDecimal bid = midRate.subtract(spreadAmount);
        BigDecimal ask = midRate.add(spreadAmount);

        return new CurrencyPair(from, to, midRate, bid, ask, source);
    }

    // Getters
    public Currency getFromCurrency() { return fromCurrency; }
    public Currency getToCurrency() { return toCurrency; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public BigDecimal getBidPrice() { return bidPrice; }
    public BigDecimal getAskPrice() { return askPrice; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getSource() { return source; }
    public double getLogWeight() { return logWeight; }

    // Business methods
    public BigDecimal getSpread() {
        return askPrice.subtract(bidPrice);
    }

    public BigDecimal getSpreadBps() {
        if (exchangeRate.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getSpread().divide(exchangeRate, MATH_CONTEXT)
                .multiply(new BigDecimal("10000"));
    }

    public boolean isCross() {
        return !fromCurrency.equals(Currency.USD) && !toCurrency.equals(Currency.USD);
    }

    public boolean isMajorPair() {
        // Major pairs: TRY, USD, EUR, GBP, JPY (our supported currencies)
        Currency[] majors = {Currency.TRY, Currency.USD, Currency.EUR,
                Currency.GBP, Currency.JPY};

        boolean fromIsMajor = java.util.Arrays.asList(majors).contains(fromCurrency);
        boolean toIsMajor = java.util.Arrays.asList(majors).contains(toCurrency);

        return fromIsMajor && toIsMajor;
    }

    /**
     * Para çiftini tersine çevirir (USD/EUR → EUR/USD)
     */
    public CurrencyPair reverse() {
        if (exchangeRate.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException("Cannot reverse zero rate pair");
        }

        BigDecimal reverseRate = BigDecimal.ONE.divide(exchangeRate, MATH_CONTEXT);
        BigDecimal reverseBid = BigDecimal.ONE.divide(askPrice, MATH_CONTEXT);
        BigDecimal reverseAsk = BigDecimal.ONE.divide(bidPrice, MATH_CONTEXT);

        return new CurrencyPair(toCurrency, fromCurrency, reverseRate,
                reverseBid, reverseAsk, source + "_REVERSED");
    }

    /**
     * Belirtilen tutarı çevirir
     */
    public BigDecimal convert(BigDecimal amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }

        return amount.multiply(exchangeRate, MATH_CONTEXT);
    }

    /**
     * Bid fiyatı ile çevirir (daha konservatif)
     */
    public BigDecimal convertWithBid(BigDecimal amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        return amount.multiply(bidPrice, MATH_CONTEXT);
    }

    /**
     * Yaş kontrolü - stale rate detection
     */
    public boolean isStale(int maxAgeMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(maxAgeMinutes);
        return timestamp.isBefore(cutoff);
    }

    /**
     * Rate quality score (0-100)
     */
    public int getQualityScore() {
        int score = 100;

        // Spread penalty (wider spread = lower quality)
        BigDecimal spreadBps = getSpreadBps();
        if (spreadBps.compareTo(new BigDecimal("10")) > 0) {
            score -= spreadBps.subtract(new BigDecimal("10")).intValue();
        }

        // Age penalty
        long minutesOld = java.time.Duration.between(timestamp, LocalDateTime.now()).toMinutes();
        if (minutesOld > 5) {
            score -= (int) Math.min(minutesOld - 5, 50);
        }

        // Cross currency penalty
        if (isCross()) {
            score -= 10;
        }

        return Math.max(0, Math.min(100, score));
    }

    private void validateRates() {
        if (fromCurrency.equals(toCurrency)) {
            throw new IllegalArgumentException("From and to currencies cannot be the same");
        }

        if (exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be positive");
        }

        if (bidPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Bid price must be positive");
        }

        if (askPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Ask price must be positive");
        }

        if (bidPrice.compareTo(askPrice) > 0) {
            throw new IllegalArgumentException("Bid price cannot be higher than ask price");
        }

        // Sanity check: rates shouldn't differ too much
        BigDecimal maxDeviation = exchangeRate.multiply(new BigDecimal("0.1")); // 10% max
        if (bidPrice.subtract(exchangeRate).abs().compareTo(maxDeviation) > 0 ||
                askPrice.subtract(exchangeRate).abs().compareTo(maxDeviation) > 0) {
            throw new IllegalArgumentException("Bid/ask prices deviate too much from mid rate");
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        CurrencyPair that = (CurrencyPair) obj;
        return Objects.equals(fromCurrency, that.fromCurrency) &&
                Objects.equals(toCurrency, that.toCurrency) &&
                Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromCurrency, toCurrency, source);
    }

    @Override
    public int compareTo(CurrencyPair other) {
        // Primary: from currency
        int fromCompare = fromCurrency.compareTo(other.fromCurrency);
        if (fromCompare != 0) return fromCompare;

        // Secondary: to currency
        int toCompare = toCurrency.compareTo(other.toCurrency);
        if (toCompare != 0) return toCompare;

        // Tertiary: source
        int sourceCompare = source.compareTo(other.source);
        if (sourceCompare != 0) return sourceCompare;

        // Final: timestamp (newer first)
        return other.timestamp.compareTo(timestamp);
    }

    @Override
    public String toString() {
        return String.format("%s/%s @ %s (spread: %s bps, %s)",
                fromCurrency, toCurrency, exchangeRate,
                getSpreadBps(), source);
    }

    /**
     * Pair identifier string (USD/EUR)
     */
    public String getPairId() {
        return fromCurrency + "/" + toCurrency;
    }
}