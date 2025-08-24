import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Sliding window utility for fraud detection.
 *
 * Time-based sliding window implementation:
 * - Automatic cleanup of expired entries
 * - Transaction count and amount aggregation
 * - Velocity and pattern analysis
 * - Memory efficient Deque-based storage
 */
public final class RiskWindow {

    private final Deque<WindowEntry> entries;
    private final int windowSizeMinutes;
    private final int maxEntries;

    // Aggregated values (for efficiency)
    private BigDecimal totalAmount;
    private int transactionCount;

    // Window statistics
    private LocalDateTime lastCleanupTime;
    private final Map<String, Integer> merchantCounts;
    private final Map<String, Integer> locationCounts;

    public RiskWindow(int windowSizeMinutes, int maxEntries) {
        if (windowSizeMinutes <= 0) {
            throw new IllegalArgumentException("Window size must be positive");
        }
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("Max entries must be positive");
        }

        this.windowSizeMinutes = windowSizeMinutes;
        this.maxEntries = maxEntries;
        this.entries = new ArrayDeque<>();
        this.totalAmount = BigDecimal.ZERO;
        this.transactionCount = 0;
        this.lastCleanupTime = LocalDateTime.now();
        this.merchantCounts = new HashMap<>();
        this.locationCounts = new HashMap<>();
    }

    // Common window configurations
    public static RiskWindow createVelocityWindow() {
        return new RiskWindow(10, 100); // 10 dakika, max 100 işlem
    }

    public static RiskWindow createAmountWindow() {
        return new RiskWindow(60, 200); // 1 saat, max 200 işlem
    }

    public static RiskWindow createPatternWindow() {
        return new RiskWindow(1440, 1000); // 24 saat, max 1000 işlem
    }

    /**
     * Window'a yeni transaction ekler
     */
    public synchronized WindowAnalysis addTransaction(String transactionId, String cardNumber,
                                                      BigDecimal amount, String merchantName,
                                                      String location, LocalDateTime timestamp) {

        Objects.requireNonNull(transactionId, "Transaction ID cannot be null");
        Objects.requireNonNull(cardNumber, "Card number cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(merchantName, "Merchant name cannot be null");
        Objects.requireNonNull(location, "Location cannot be null");
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");

        // Expired entries'leri temizle
        cleanupExpiredEntries(timestamp);

        // Yeni entry ekle
        WindowEntry newEntry = new WindowEntry(transactionId, cardNumber, amount,
                merchantName, location, timestamp);
        entries.addLast(newEntry);

        // Aggregated values update
        totalAmount = totalAmount.add(amount);
        transactionCount++;
        merchantCounts.merge(merchantName, 1, Integer::sum);
        locationCounts.merge(location, 1, Integer::sum);

        // Max entries kontrolü
        while (entries.size() > maxEntries) {
            removeOldestEntry();
        }

        // Analysis yap ve döndür
        return analyzeWindow(newEntry, cardNumber, timestamp);
    }

    /**
     * Belirli kart için window analizi
     */
    public synchronized WindowAnalysis analyzeCard(String cardNumber, LocalDateTime currentTime) {
        Objects.requireNonNull(cardNumber, "Card number cannot be null");
        Objects.requireNonNull(currentTime, "Current time cannot be null");

        cleanupExpiredEntries(currentTime);

        return analyzeWindow(null, cardNumber, currentTime);
    }

    /**
     * Window'daki expired entries'leri temizler
     */
    private void cleanupExpiredEntries(LocalDateTime currentTime) {
        LocalDateTime cutoffTime = currentTime.minusMinutes(windowSizeMinutes);

        while (!entries.isEmpty() && entries.peekFirst().getTimestamp().isBefore(cutoffTime)) {
            removeOldestEntry();
        }

        lastCleanupTime = currentTime;
    }

    /**
     * En eski entry'yi kaldırır ve aggregates'i günceller
     */
    private void removeOldestEntry() {
        WindowEntry oldest = entries.removeFirst();
        if (oldest != null) {
            totalAmount = totalAmount.subtract(oldest.getAmount());
            transactionCount--;

            // Merchant count update
            String merchant = oldest.getMerchantName();
            merchantCounts.computeIfPresent(merchant, (k, v) -> v > 1 ? v - 1 : null);

            // Location count update
            String location = oldest.getLocation();
            locationCounts.computeIfPresent(location, (k, v) -> v > 1 ? v - 1 : null);
        }
    }

    /**
     * Window analizi yapar
     */
    private WindowAnalysis analyzeWindow(WindowEntry newEntry, String cardNumber,
                                         LocalDateTime currentTime) {

        // Card-specific filtering
        List<WindowEntry> cardEntries = entries.stream()
                .filter(entry -> entry.getCardNumber().equals(cardNumber))
                .sorted(Comparator.comparing(WindowEntry::getTimestamp))
                .toList();

        if (cardEntries.isEmpty()) {
            return WindowAnalysis.empty(cardNumber, currentTime);
        }

        // Card-specific aggregations
        BigDecimal cardTotalAmount = cardEntries.stream()
                .map(WindowEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int cardTransactionCount = cardEntries.size();

        // Unique merchants and locations for this card
        Set<String> uniqueMerchants = new HashSet<>();
        Set<String> uniqueLocations = new HashSet<>();
        Map<String, Integer> cardMerchantCounts = new HashMap<>();
        Map<String, Integer> cardLocationCounts = new HashMap<>();

        for (WindowEntry entry : cardEntries) {
            uniqueMerchants.add(entry.getMerchantName());
            uniqueLocations.add(entry.getLocation());
            cardMerchantCounts.merge(entry.getMerchantName(), 1, Integer::sum);
            cardLocationCounts.merge(entry.getLocation(), 1, Integer::sum);
        }

        // Velocity analysis
        double transactionsPerMinute = 0.0;
        if (!cardEntries.isEmpty() && cardEntries.size() > 1) {
            LocalDateTime firstTransaction = cardEntries.get(0).getTimestamp();
            LocalDateTime lastTransaction = cardEntries.get(cardEntries.size() - 1).getTimestamp();
            long minutesBetween = ChronoUnit.MINUTES.between(firstTransaction, lastTransaction);
            if (minutesBetween > 0) {
                transactionsPerMinute = (double) cardTransactionCount / minutesBetween;
            }
        }

        // Pattern analysis
        RiskPattern riskPattern = analyzeRiskPatterns(cardEntries, cardMerchantCounts,
                cardLocationCounts, transactionsPerMinute);

        return new WindowAnalysis(cardNumber, currentTime, windowSizeMinutes,
                cardTransactionCount, cardTotalAmount, uniqueMerchants.size(),
                uniqueLocations.size(), transactionsPerMinute, riskPattern,
                cardMerchantCounts, cardLocationCounts);
    }

    /**
     * Risk pattern analysis
     */
    private RiskPattern analyzeRiskPatterns(List<WindowEntry> entries,
                                            Map<String, Integer> merchantCounts,
                                            Map<String, Integer> locationCounts,
                                            double transactionsPerMinute) {

        Set<RiskPattern.PatternType> detectedPatterns = new HashSet<>();
        int riskScore = 0;

        // High velocity pattern
        if (transactionsPerMinute > 2.0) { // >2 işlem/dakika
            detectedPatterns.add(RiskPattern.PatternType.HIGH_VELOCITY);
            riskScore += 30;
        }

        // High frequency at same merchant
        for (int count : merchantCounts.values()) {
            if (count >= 10) { // Aynı merchant'ta 10+ işlem
                detectedPatterns.add(RiskPattern.PatternType.MERCHANT_HAMMERING);
                riskScore += 25;
                break;
            }
        }

        // Multiple locations rapidly
        if (locationCounts.size() >= 5 && entries.size() >= 10) {
            detectedPatterns.add(RiskPattern.PatternType.LOCATION_HOPPING);
            riskScore += 20;
        }

        // Round amount pattern (possible testing)
        long roundAmounts = entries.stream()
                .mapToLong(entry -> {
                    BigDecimal amount = entry.getAmount();
                    return amount.remainder(new BigDecimal("10")).compareTo(BigDecimal.ZERO) == 0 ? 1 : 0;
                })
                .sum();

        if (roundAmounts >= entries.size() * 0.8) { // %80'i round amount
            detectedPatterns.add(RiskPattern.PatternType.ROUND_AMOUNTS);
            riskScore += 15;
        }

        // Ascending/descending amount pattern
        if (hasSequentialAmountPattern(entries)) {
            detectedPatterns.add(RiskPattern.PatternType.SEQUENTIAL_AMOUNTS);
            riskScore += 20;
        }

        // Night activity pattern
        long nightTransactions = entries.stream()
                .mapToLong(entry -> {
                    int hour = entry.getTimestamp().getHour();
                    return (hour < 6 || hour > 22) ? 1 : 0;
                })
                .sum();

        if (nightTransactions >= entries.size() * 0.7) { // %70'i gece
            detectedPatterns.add(RiskPattern.PatternType.NIGHT_ACTIVITY);
            riskScore += 10;
        }

        return new RiskPattern(detectedPatterns, Math.min(100, riskScore));
    }

    /**
     * Sequential amount pattern detection
     */
    private boolean hasSequentialAmountPattern(List<WindowEntry> entries) {
        if (entries.size() < 3) return false;

        // Check for ascending pattern
        boolean ascending = true;
        boolean descending = true;

        for (int i = 1; i < entries.size(); i++) {
            BigDecimal prev = entries.get(i-1).getAmount();
            BigDecimal curr = entries.get(i).getAmount();

            if (curr.compareTo(prev) <= 0) ascending = false;
            if (curr.compareTo(prev) >= 0) descending = false;
        }

        return ascending || descending;
    }

    /**
     * Window'u temizler
     */
    public synchronized void clear() {
        entries.clear();
        totalAmount = BigDecimal.ZERO;
        transactionCount = 0;
        merchantCounts.clear();
        locationCounts.clear();
        lastCleanupTime = LocalDateTime.now();
    }

    /**
     * Window statistics
     */
    public synchronized WindowStatistics getStatistics() {
        return new WindowStatistics(entries.size(), transactionCount, totalAmount,
                merchantCounts.size(), locationCounts.size(),
                windowSizeMinutes, maxEntries, lastCleanupTime);
    }

    // Getters
    public int getWindowSizeMinutes() { return windowSizeMinutes; }
    public int getMaxEntries() { return maxEntries; }
    public int getCurrentSize() { return entries.size(); }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public int getTransactionCount() { return transactionCount; }

    // Nested classes
    public static final class WindowEntry {
        private final String transactionId;
        private final String cardNumber;
        private final BigDecimal amount;
        private final String merchantName;
        private final String location;
        private final LocalDateTime timestamp;

        public WindowEntry(String transactionId, String cardNumber, BigDecimal amount,
                           String merchantName, String location, LocalDateTime timestamp) {
            this.transactionId = transactionId;
            this.cardNumber = cardNumber;
            this.amount = amount;
            this.merchantName = merchantName;
            this.location = location;
            this.timestamp = timestamp;
        }

        // Getters
        public String getTransactionId() { return transactionId; }
        public String getCardNumber() { return cardNumber; }
        public BigDecimal getAmount() { return amount; }
        public String getMerchantName() { return merchantName; }
        public String getLocation() { return location; }
        public LocalDateTime getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("WindowEntry{id=%s, amount=%s, merchant=%s, time=%s}",
                    transactionId, amount, merchantName, timestamp);
        }
    }

    public static final class WindowAnalysis {
        private final String cardNumber;
        private final LocalDateTime analysisTime;
        private final int windowSizeMinutes;
        private final int transactionCount;
        private final BigDecimal totalAmount;
        private final int uniqueMerchants;
        private final int uniqueLocations;
        private final double transactionsPerMinute;
        private final RiskPattern riskPattern;
        private final Map<String, Integer> merchantCounts;
        private final Map<String, Integer> locationCounts;

        public WindowAnalysis(String cardNumber, LocalDateTime analysisTime, int windowSizeMinutes,
                              int transactionCount, BigDecimal totalAmount, int uniqueMerchants,
                              int uniqueLocations, double transactionsPerMinute, RiskPattern riskPattern,
                              Map<String, Integer> merchantCounts, Map<String, Integer> locationCounts) {
            this.cardNumber = cardNumber;
            this.analysisTime = analysisTime;
            this.windowSizeMinutes = windowSizeMinutes;
            this.transactionCount = transactionCount;
            this.totalAmount = totalAmount;
            this.uniqueMerchants = uniqueMerchants;
            this.uniqueLocations = uniqueLocations;
            this.transactionsPerMinute = transactionsPerMinute;
            this.riskPattern = riskPattern;
            this.merchantCounts = new HashMap<>(merchantCounts);
            this.locationCounts = new HashMap<>(locationCounts);
        }

        public static WindowAnalysis empty(String cardNumber, LocalDateTime analysisTime) {
            return new WindowAnalysis(cardNumber, analysisTime, 0, 0, BigDecimal.ZERO, 0, 0, 0.0,
                    new RiskPattern(new HashSet<>(), 0), new HashMap<>(), new HashMap<>());
        }

        // Getters
        public String getCardNumber() { return cardNumber; }
        public LocalDateTime getAnalysisTime() { return analysisTime; }
        public int getWindowSizeMinutes() { return windowSizeMinutes; }
        public int getTransactionCount() { return transactionCount; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public int getUniqueMerchants() { return uniqueMerchants; }
        public int getUniqueLocations() { return uniqueLocations; }
        public double getTransactionsPerMinute() { return transactionsPerMinute; }
        public RiskPattern getRiskPattern() { return riskPattern; }
        public Map<String, Integer> getMerchantCounts() { return new HashMap<>(merchantCounts); }
        public Map<String, Integer> getLocationCounts() { return new HashMap<>(locationCounts); }

        public boolean hasRiskPatterns() { return riskPattern.getRiskScore() > 0; }
        public boolean isHighRisk() { return riskPattern.getRiskScore() >= 60; }

        @Override
        public String toString() {
            return String.format("WindowAnalysis{card=%s, txns=%d, amount=%s, risk=%d, patterns=%d}",
                    cardNumber.substring(Math.max(0, cardNumber.length()-4)),
                    transactionCount, totalAmount, riskPattern.getRiskScore(),
                    riskPattern.getDetectedPatterns().size());
        }
    }

    public static final class RiskPattern {
        public enum PatternType {
            HIGH_VELOCITY("Yüksek işlem hızı"),
            MERCHANT_HAMMERING("Aynı merchant'ta çok işlem"),
            LOCATION_HOPPING("Hızlı lokasyon değişimi"),
            ROUND_AMOUNTS("Round tutar pattern'i"),
            SEQUENTIAL_AMOUNTS("Sıralı tutar pattern'i"),
            NIGHT_ACTIVITY("Gece aktivitesi"),
            WEEKEND_BURST("Hafta sonu patlaması");

            private final String description;
            PatternType(String description) { this.description = description; }
            public String getDescription() { return description; }
        }

        private final Set<PatternType> detectedPatterns;
        private final int riskScore;

        public RiskPattern(Set<PatternType> detectedPatterns, int riskScore) {
            this.detectedPatterns = EnumSet.copyOf(Objects.requireNonNull(detectedPatterns));
            this.riskScore = Math.max(0, Math.min(100, riskScore));
        }

        public Set<PatternType> getDetectedPatterns() { return EnumSet.copyOf(detectedPatterns); }
        public int getRiskScore() { return riskScore; }
        public boolean hasPattern(PatternType pattern) { return detectedPatterns.contains(pattern); }

        @Override
        public String toString() {
            return String.format("RiskPattern{score=%d, patterns=%s}", riskScore, detectedPatterns);
        }
    }

    public static final class WindowStatistics {
        private final int currentEntries;
        private final int totalTransactions;
        private final BigDecimal totalAmount;
        private final int uniqueMerchants;
        private final int uniqueLocations;
        private final int windowSizeMinutes;
        private final int maxEntries;
        private final LocalDateTime lastCleanupTime;

        public WindowStatistics(int currentEntries, int totalTransactions, BigDecimal totalAmount,
                                int uniqueMerchants, int uniqueLocations, int windowSizeMinutes,
                                int maxEntries, LocalDateTime lastCleanupTime) {
            this.currentEntries = currentEntries;
            this.totalTransactions = totalTransactions;
            this.totalAmount = totalAmount;
            this.uniqueMerchants = uniqueMerchants;
            this.uniqueLocations = uniqueLocations;
            this.windowSizeMinutes = windowSizeMinutes;
            this.maxEntries = maxEntries;
            this.lastCleanupTime = lastCleanupTime;
        }

        // Getters
        public int getCurrentEntries() { return currentEntries; }
        public int getTotalTransactions() { return totalTransactions; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public int getUniqueMerchants() { return uniqueMerchants; }
        public int getUniqueLocations() { return uniqueLocations; }
        public int getWindowSizeMinutes() { return windowSizeMinutes; }
        public int getMaxEntries() { return maxEntries; }
        public LocalDateTime getLastCleanupTime() { return lastCleanupTime; }

        public double getUtilizationPercentage() {
            return maxEntries > 0 ? (double) currentEntries / maxEntries * 100.0 : 0.0;
        }

        @Override
        public String toString() {
            return String.format("WindowStats{entries=%d/%d (%.1f%%), window=%dmin, merchants=%d}",
                    currentEntries, maxEntries, getUtilizationPercentage(),
                    windowSizeMinutes, uniqueMerchants);
        }
    }

    @Override
    public String toString() {
        return String.format("RiskWindow{size=%dmin, entries=%d/%d, amount=%s}",
                windowSizeMinutes, entries.size(), maxEntries, totalAmount);
    }
}