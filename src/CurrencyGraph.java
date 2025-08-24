import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Currency exchange graph - Bellman-Ford algoritması için.
 *
 * Graph representation:
 * - Vertices: Currency (TRY, USD, EUR, GBP, JPY)
 * - Edges: CurrencyPair with -log(rate) weights
 * - Negative cycle = Arbitrage opportunity
 */
public final class CurrencyGraph {

    private final Map<Currency, Integer> currencyToIndex;
    private final Map<Integer, Currency> indexToCurrency;
    private final List<List<Edge>> adjacencyList;
    private final Set<CurrencyPair> currencyPairs;
    private final int vertexCount;

    // Supported currencies - exactly 5
    private static final Currency[] SUPPORTED_CURRENCIES = {
            Currency.TRY, Currency.USD, Currency.EUR, Currency.GBP, Currency.JPY
    };

    public CurrencyGraph() {
        this.vertexCount = SUPPORTED_CURRENCIES.length;
        this.currencyToIndex = new HashMap<>();
        this.indexToCurrency = new HashMap<>();
        this.adjacencyList = new ArrayList<>();
        this.currencyPairs = new HashSet<>();

        initializeGraph();
    }

    /**
     * Currency pair ekler - otomatik olarak reverse pair'i de ekler
     */
    public void addCurrencyPair(CurrencyPair pair) {
        Objects.requireNonNull(pair, "Currency pair cannot be null");

        validateCurrencySupport(pair.getFromCurrency());
        validateCurrencySupport(pair.getToCurrency());

        currencyPairs.add(pair);

        // Forward edge: from → to
        int fromIndex = currencyToIndex.get(pair.getFromCurrency());
        int toIndex = currencyToIndex.get(pair.getToCurrency());

        Edge forwardEdge = new Edge(fromIndex, toIndex, pair.getLogWeight(), pair);
        adjacencyList.get(fromIndex).add(forwardEdge);

        // Reverse edge: to → from (automatically generated)
        try {
            CurrencyPair reversePair = pair.reverse();
            currencyPairs.add(reversePair);

            Edge reverseEdge = new Edge(toIndex, fromIndex, reversePair.getLogWeight(), reversePair);
            adjacencyList.get(toIndex).add(reverseEdge);

        } catch (IllegalStateException e) {
            // Skip reverse if rate is zero
            System.err.println("Warning: Cannot create reverse pair for " + pair.getPairId());
        }
    }

    /**
     * Birden fazla currency pair ekler
     */
    public void addCurrencyPairs(Collection<CurrencyPair> pairs) {
        Objects.requireNonNull(pairs, "Currency pairs cannot be null");
        pairs.forEach(this::addCurrencyPair);
    }

    /**
     * Graph'ta edge var mı kontrol eder
     */
    public boolean hasEdge(Currency from, Currency to) {
        if (!isCurrencySupported(from) || !isCurrencySupported(to)) {
            return false;
        }

        int fromIndex = currencyToIndex.get(from);
        int toIndex = currencyToIndex.get(to);

        return adjacencyList.get(fromIndex).stream()
                .anyMatch(edge -> edge.getToIndex() == toIndex);
    }

    /**
     * İki currency arasındaki en iyi rate'i bulur
     */
    public Optional<CurrencyPair> getBestRate(Currency from, Currency to) {
        return currencyPairs.stream()
                .filter(pair -> pair.getFromCurrency().equals(from) &&
                        pair.getToCurrency().equals(to))
                .min(Comparator.comparing(CurrencyPair::getLogWeight)); // En düşük log weight = en yüksek rate
    }

    /**
     * Adjacency list'i döndürür (Bellman-Ford için)
     */
    public List<Edge> getAllEdges() {
        return adjacencyList.stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * Belirli bir currency'den çıkan edge'ler
     */
    public List<Edge> getEdgesFrom(Currency currency) {
        validateCurrencySupport(currency);
        int index = currencyToIndex.get(currency);
        return new ArrayList<>(adjacencyList.get(index));
    }

    /**
     * Graph connectivity kontrolü
     */
    public boolean isConnected() {
        if (currencyPairs.isEmpty()) return false;

        // Simple DFS to check connectivity
        Set<Integer> visited = new HashSet<>();
        dfsVisit(0, visited);

        return visited.size() == vertexCount;
    }

    /**
     * Graph istatistikleri
     */
    public GraphStatistics getStatistics() {
        int totalEdges = getAllEdges().size();
        int totalPairs = currencyPairs.size();

        Map<Currency, Integer> outDegrees = new EnumMap<>(Currency.class);
        for (Currency currency : SUPPORTED_CURRENCIES) {
            int outDegree = adjacencyList.get(currencyToIndex.get(currency)).size();
            outDegrees.put(currency, outDegree);
        }

        double averageSpread = currencyPairs.stream()
                .mapToDouble(pair -> pair.getSpreadBps().doubleValue())
                .average().orElse(0.0);

        long staleRateCount = currencyPairs.stream()
                .mapToLong(pair -> pair.isStale(10) ? 1 : 0)
                .sum();

        return new GraphStatistics(totalEdges, totalPairs, outDegrees,
                isConnected(), averageSpread, staleRateCount);
    }

    /**
     * Graph'ı temizler
     */
    public void clear() {
        currencyPairs.clear();
        for (List<Edge> edges : adjacencyList) {
            edges.clear();
        }
    }

    // Helper methods
    private void initializeGraph() {
        // Currency indexing
        for (int i = 0; i < SUPPORTED_CURRENCIES.length; i++) {
            Currency currency = SUPPORTED_CURRENCIES[i];
            currencyToIndex.put(currency, i);
            indexToCurrency.put(i, currency);
            adjacencyList.add(new ArrayList<>());
        }
    }

    private void validateCurrencySupport(Currency currency) {
        if (!isCurrencySupported(currency)) {
            throw new IllegalArgumentException("Unsupported currency: " + currency +
                    ". Supported: " + Arrays.toString(SUPPORTED_CURRENCIES));
        }
    }

    private boolean isCurrencySupported(Currency currency) {
        return currencyToIndex.containsKey(currency);
    }

    private void dfsVisit(int nodeIndex, Set<Integer> visited) {
        visited.add(nodeIndex);

        for (Edge edge : adjacencyList.get(nodeIndex)) {
            if (!visited.contains(edge.getToIndex())) {
                dfsVisit(edge.getToIndex(), visited);
            }
        }
    }

    // Getters
    public int getVertexCount() { return vertexCount; }
    public Set<CurrencyPair> getCurrencyPairs() { return new HashSet<>(currencyPairs); }

    public Currency getCurrencyByIndex(int index) {
        return indexToCurrency.get(index);
    }

    public Integer getCurrencyIndex(Currency currency) {
        return currencyToIndex.get(currency);
    }

    public Currency[] getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES.clone();
    }

    // Nested classes
    /**
     * Graph edge - Bellman-Ford için
     */
    public static final class Edge {
        private final int fromIndex;
        private final int toIndex;
        private final double weight;        // -log(rate)
        private final CurrencyPair pair;

        public Edge(int fromIndex, int toIndex, double weight, CurrencyPair pair) {
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
            this.weight = weight;
            this.pair = Objects.requireNonNull(pair, "Currency pair cannot be null");
        }

        public int getFromIndex() { return fromIndex; }
        public int getToIndex() { return toIndex; }
        public double getWeight() { return weight; }
        public CurrencyPair getPair() { return pair; }

        @Override
        public String toString() {
            return String.format("Edge{%s→%s, weight=%.4f, rate=%s}",
                    fromIndex, toIndex, weight, pair.getExchangeRate());
        }
    }

    /**
     * Graph istatistikleri
     */
    public static final class GraphStatistics {
        private final int totalEdges;
        private final int totalPairs;
        private final Map<Currency, Integer> outDegrees;
        private final boolean connected;
        private final double averageSpread;
        private final long staleRateCount;

        public GraphStatistics(int totalEdges, int totalPairs, Map<Currency, Integer> outDegrees,
                               boolean connected, double averageSpread, long staleRateCount) {
            this.totalEdges = totalEdges;
            this.totalPairs = totalPairs;
            this.outDegrees = new EnumMap<>(outDegrees);
            this.connected = connected;
            this.averageSpread = averageSpread;
            this.staleRateCount = staleRateCount;
        }

        public int getTotalEdges() { return totalEdges; }
        public int getTotalPairs() { return totalPairs; }
        public Map<Currency, Integer> getOutDegrees() { return new EnumMap<>(outDegrees); }
        public boolean isConnected() { return connected; }
        public double getAverageSpread() { return averageSpread; }
        public long getStaleRateCount() { return staleRateCount; }

        @Override
        public String toString() {
            return String.format("GraphStats{edges=%d, pairs=%d, connected=%s, avgSpread=%.1f bps}",
                    totalEdges, totalPairs, connected, averageSpread);
        }
    }

    @Override
    public String toString() {
        return String.format("CurrencyGraph{vertices=%d, edges=%d, pairs=%d, connected=%s}",
                vertexCount, getAllEdges().size(), currencyPairs.size(), isConnected());
    }
}