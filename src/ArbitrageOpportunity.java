import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Arbitrage opportunity sonucunu temsil eden immutable value object.
 *
 * Örnek: TRY → USD → EUR → TRY cycle'ında:
 * - Currency path: [TRY, USD, EUR, TRY] 
 * - Pair path: [TRY/USD, USD/EUR, EUR/TRY]
 * - Total rate: 1.0347 (3.47% profit)
 */
public final class ArbitrageOpportunity implements Comparable<ArbitrageOpportunity> {

    private final List<Currency> currencyPath;
    private final List<CurrencyPair> pairPath;
    private final BigDecimal totalExchangeRate;
    private final BigDecimal profitPercentage;
    private final Currency startCurrency;
    private final LocalDateTime detectionTime;
    private final String opportunityId;

    // Calculated fields
    private final int pathLength;
    private final BigDecimal minInvestment;
    private final OpportunityQuality quality;

    private static final MathContext MATH_CONTEXT = new MathContext(10, RoundingMode.HALF_UP);

    public ArbitrageOpportunity(List<Currency> currencyPath, List<CurrencyPair> pairPath,
                                BigDecimal totalExchangeRate, BigDecimal profitPercentage,
                                Currency startCurrency) {
        this.currencyPath = List.copyOf(Objects.requireNonNull(currencyPath, "Currency path cannot be null"));
        this.pairPath = List.copyOf(Objects.requireNonNull(pairPath, "Pair path cannot be null"));
        this.totalExchangeRate = Objects.requireNonNull(totalExchangeRate, "Total exchange rate cannot be null");
        this.profitPercentage = Objects.requireNonNull(profitPercentage, "Profit percentage cannot be null");
        this.startCurrency = Objects.requireNonNull(startCurrency, "Start currency cannot be null");
        this.detectionTime = LocalDateTime.now();
        this.opportunityId = generateOpportunityId();

        this.pathLength = currencyPath.size();
        this.minInvestment = calculateMinInvestment();
        this.quality = assessQuality();

        validateOpportunity();
    }

    // Getters
    public List<Currency> getCurrencyPath() { return currencyPath; }
    public List<CurrencyPair> getPairPath() { return pairPath; }
    public BigDecimal getTotalExchangeRate() { return totalExchangeRate; }
    public BigDecimal getProfitPercentage() { return profitPercentage; }
    public Currency getStartCurrency() { return startCurrency; }
    public LocalDateTime getDetectionTime() { return detectionTime; }
    public String getOpportunityId() { return opportunityId; }
    public int getPathLength() { return pathLength; }
    public BigDecimal getMinInvestment() { return minInvestment; }
    public OpportunityQuality getQuality() { return quality; }

    // Business methods
    public boolean isCycle() {
        return currencyPath.size() > 1 &&
                currencyPath.get(0).equals(currencyPath.get(currencyPath.size() - 1));
    }

    public boolean isProfitable() {
        return profitPercentage.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasSamePath(ArbitrageOpportunity other) {
        if (other == null || this.pathLength != other.pathLength) {
            return false;
        }

        // Normalize paths for comparison (cycles can start at different points)
        List<Currency> thisNormalized = normalizeCyclePath(this.currencyPath);
        List<Currency> otherNormalized = normalizeCyclePath(other.currencyPath);

        return thisNormalized.equals(otherNormalized) ||
                thisNormalized.equals(reverseList(otherNormalized));
    }

    /**
     * Belirli yatırım tutarı için kar hesaplama
     */
    public BigDecimal calculateProfit(BigDecimal investmentAmount) {
        Objects.requireNonNull(investmentAmount, "Investment amount cannot be null");
        if (investmentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Investment amount must be positive");
        }

        BigDecimal finalAmount = investmentAmount.multiply(totalExchangeRate, MATH_CONTEXT);
        return finalAmount.subtract(investmentAmount);
    }

    /**
     * Execution simulation - spread'leri dikkate alarak gerçekçi kar
     */
    public ExecutionResult simulateExecution(BigDecimal investmentAmount) {
        Objects.requireNonNull(investmentAmount, "Investment amount cannot be null");

        BigDecimal currentAmount = investmentAmount;
        BigDecimal totalSpread = BigDecimal.ZERO;
        List<ExecutionStep> steps = new ArrayList<>();

        for (CurrencyPair pair : pairPath) {
            // Bid price kullan (daha konservatif)
            BigDecimal beforeAmount = currentAmount;
            currentAmount = pair.convertWithBid(currentAmount);
            BigDecimal spread = pair.getSpread().multiply(beforeAmount, MATH_CONTEXT)
                    .divide(pair.getExchangeRate(), MATH_CONTEXT);
            totalSpread = totalSpread.add(spread);

            steps.add(new ExecutionStep(pair, beforeAmount, currentAmount, spread));
        }

        BigDecimal realizedProfit = currentAmount.subtract(investmentAmount);
        BigDecimal realizedProfitPercentage = realizedProfit.divide(investmentAmount, MATH_CONTEXT)
                .multiply(new BigDecimal("100"));

        return new ExecutionResult(investmentAmount, currentAmount, realizedProfit,
                realizedProfitPercentage, totalSpread, steps);
    }

    /**
     * Opportunity expiry - rate'lerin yaşı
     */
    public boolean isStale(int maxAgeMinutes) {
        return pairPath.stream().anyMatch(pair -> pair.isStale(maxAgeMinutes));
    }

    /**
     * Risk assessment
     */
    public RiskAssessment assessRisk() {
        // Path length risk (longer = riskier)
        int pathRisk = Math.min(pathLength - 2, 5) * 20; // 0-100 scale

        // Spread risk (wider spreads = riskier)
        double avgSpreadBps = pairPath.stream()
                .mapToDouble(pair -> pair.getSpreadBps().doubleValue())
                .average().orElse(0.0);
        int spreadRisk = (int) Math.min(avgSpreadBps / 2, 50); // 0-50 scale

        // Age risk (stale rates = riskier)  
        int ageRisk = isStale(5) ? 30 : 0; // 0 or 30

        // Quality risk
        int qualityRisk = switch (quality) {
            case EXCELLENT -> 0;
            case GOOD -> 10;
            case FAIR -> 20;
            case POOR -> 40;
        };

        int totalRisk = Math.min(pathRisk + spreadRisk + ageRisk + qualityRisk, 100);

        return new RiskAssessment(totalRisk, pathRisk, spreadRisk, ageRisk, qualityRisk);
    }

    // Helper methods
    private String generateOpportunityId() {
        String pathStr = currencyPath.stream()
                .map(Currency::name)
                .reduce((a, b) -> a + "-" + b)
                .orElse("UNKNOWN");

        return "ARB_" + pathStr + "_" + System.currentTimeMillis();
    }

    private BigDecimal calculateMinInvestment() {
        // Minimum investment to overcome spreads
        BigDecimal totalSpreadCost = pairPath.stream()
                .map(CurrencyPair::getSpread)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Minimum için spread cost'u profit rate'e böl
        if (profitPercentage.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("10000"); // High minimum for non-profitable
        }

        BigDecimal profitRate = profitPercentage.divide(new BigDecimal("100"), MATH_CONTEXT);
        return totalSpreadCost.divide(profitRate, MATH_CONTEXT).multiply(new BigDecimal("2"));
    }

    private OpportunityQuality assessQuality() {
        double profitValue = profitPercentage.doubleValue();
        int pathLen = pathLength;
        double avgSpread = pairPath.stream()
                .mapToDouble(pair -> pair.getSpreadBps().doubleValue())
                .average().orElse(100.0);

        // Quality scoring
        int score = 100;

        // Profit bonus/penalty
        if (profitValue >= 2.0) score += 20;
        else if (profitValue >= 1.0) score += 10;
        else if (profitValue <= 0.1) score -= 30;

        // Path length penalty
        score -= (pathLen - 3) * 10;

        // Spread penalty
        score -= (int) (avgSpread / 5);

        if (score >= 80) return OpportunityQuality.EXCELLENT;
        if (score >= 60) return OpportunityQuality.GOOD;
        if (score >= 40) return OpportunityQuality.FAIR;
        return OpportunityQuality.POOR;
    }

    private List<Currency> normalizeCyclePath(List<Currency> path) {
        if (path.size() <= 2) return new ArrayList<>(path);

        // Find minimum currency as starting point for normalization
        Currency minCurrency = path.stream().min(Currency::compareTo).orElse(path.get(0));
        int startIndex = path.indexOf(minCurrency);

        List<Currency> normalized = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            normalized.add(path.get((startIndex + i) % path.size()));
        }

        return normalized;
    }

    private static <T> List<T> reverseList(List<T> list) {
        List<T> reversed = new ArrayList<>(list);
        Collections.reverse(reversed);
        return reversed;
    }

    private void validateOpportunity() {
        if (currencyPath.isEmpty()) {
            throw new IllegalArgumentException("Currency path cannot be empty");
        }

        if (pairPath.size() != currencyPath.size() - 1 && pairPath.size() != currencyPath.size()) {
            throw new IllegalArgumentException("Pair path size inconsistent with currency path");
        }

        if (totalExchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total exchange rate must be positive");
        }
    }

    // Enums
    public enum OpportunityQuality {
        EXCELLENT, GOOD, FAIR, POOR
    }

    // Nested classes
    public static final class ExecutionStep {
        private final CurrencyPair pair;
        private final BigDecimal inputAmount;
        private final BigDecimal outputAmount;
        private final BigDecimal spreadCost;

        public ExecutionStep(CurrencyPair pair, BigDecimal inputAmount,
                             BigDecimal outputAmount, BigDecimal spreadCost) {
            this.pair = pair;
            this.inputAmount = inputAmount;
            this.outputAmount = outputAmount;
            this.spreadCost = spreadCost;
        }

        public CurrencyPair getPair() { return pair; }
        public BigDecimal getInputAmount() { return inputAmount; }
        public BigDecimal getOutputAmount() { return outputAmount; }
        public BigDecimal getSpreadCost() { return spreadCost; }

        @Override
        public String toString() {
            return String.format("%s: %.2f → %.2f (spread: %.2f)",
                    pair.getPairId(), inputAmount, outputAmount, spreadCost);
        }
    }

    public static final class ExecutionResult {
        private final BigDecimal investmentAmount;
        private final BigDecimal finalAmount;
        private final BigDecimal realizedProfit;
        private final BigDecimal realizedProfitPercentage;
        private final BigDecimal totalSpreadCost;
        private final List<ExecutionStep> steps;

        public ExecutionResult(BigDecimal investmentAmount, BigDecimal finalAmount,
                               BigDecimal realizedProfit, BigDecimal realizedProfitPercentage,
                               BigDecimal totalSpreadCost, List<ExecutionStep> steps) {
            this.investmentAmount = investmentAmount;
            this.finalAmount = finalAmount;
            this.realizedProfit = realizedProfit;
            this.realizedProfitPercentage = realizedProfitPercentage;
            this.totalSpreadCost = totalSpreadCost;
            this.steps = List.copyOf(steps);
        }

        public BigDecimal getInvestmentAmount() { return investmentAmount; }
        public BigDecimal getFinalAmount() { return finalAmount; }
        public BigDecimal getRealizedProfit() { return realizedProfit; }
        public BigDecimal getRealizedProfitPercentage() { return realizedProfitPercentage; }
        public BigDecimal getTotalSpreadCost() { return totalSpreadCost; }
        public List<ExecutionStep> getSteps() { return steps; }

        @Override
        public String toString() {
            return String.format("ExecutionResult{investment=%.2f, final=%.2f, profit=%.2f (%.3f%%)}",
                    investmentAmount, finalAmount, realizedProfit, realizedProfitPercentage);
        }
    }

    public static final class RiskAssessment {
        private final int totalRisk;        // 0-100
        private final int pathRisk;
        private final int spreadRisk;
        private final int ageRisk;
        private final int qualityRisk;

        public RiskAssessment(int totalRisk, int pathRisk, int spreadRisk,
                              int ageRisk, int qualityRisk) {
            this.totalRisk = totalRisk;
            this.pathRisk = pathRisk;
            this.spreadRisk = spreadRisk;
            this.ageRisk = ageRisk;
            this.qualityRisk = qualityRisk;
        }

        public int getTotalRisk() { return totalRisk; }
        public int getPathRisk() { return pathRisk; }
        public int getSpreadRisk() { return spreadRisk; }
        public int getAgeRisk() { return ageRisk; }
        public int getQualityRisk() { return qualityRisk; }

        public String getRiskLevel() {
            if (totalRisk <= 20) return "LOW";
            if (totalRisk <= 40) return "MEDIUM";
            if (totalRisk <= 70) return "HIGH";
            return "VERY_HIGH";
        }

        @Override
        public String toString() {
            return String.format("Risk{total=%d (%s), path=%d, spread=%d, age=%d, quality=%d}",
                    totalRisk, getRiskLevel(), pathRisk, spreadRisk, ageRisk, qualityRisk);
        }
    }

    @Override
    public int compareTo(ArbitrageOpportunity other) {
        // Primary: profit percentage (higher first)
        int profitCompare = other.profitPercentage.compareTo(this.profitPercentage);
        if (profitCompare != 0) return profitCompare;

        // Secondary: path length (shorter first) 
        int pathCompare = Integer.compare(this.pathLength, other.pathLength);
        if (pathCompare != 0) return pathCompare;

        // Tertiary: detection time (newer first)
        return other.detectionTime.compareTo(this.detectionTime);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        ArbitrageOpportunity that = (ArbitrageOpportunity) obj;
        return Objects.equals(opportunityId, that.opportunityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(opportunityId);
    }

    @Override
    public String toString() {
        String pathStr = currencyPath.stream()
                .map(Currency::name)
                .reduce((a, b) -> a + "→" + b)
                .orElse("");

        return String.format("ArbitrageOpportunity{path=%s, profit=%.3f%%, rate=%.6f, quality=%s}",
                pathStr, profitPercentage, totalExchangeRate, quality);
    }
}