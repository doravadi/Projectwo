import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;


public final class ArbitrageDetector {

    private static final double EPSILON = 1e-8;
    private static final int MAX_ITERATIONS = 1000;
    private static final MathContext MATH_CONTEXT = new MathContext(10, RoundingMode.HALF_UP);


    public List<ArbitrageOpportunity> detectArbitrage(CurrencyGraph graph) throws DisconnectedCurrencyGraph {
        Objects.requireNonNull(graph, "Currency graph cannot be null");

        if (!graph.isConnected()) {
            throw new DisconnectedCurrencyGraph("Currency graph is not connected - " +
                    "cannot detect arbitrage in disconnected components");
        }

        List<ArbitrageOpportunity> opportunities = new ArrayList<>();
        Currency[] currencies = graph.getSupportedCurrencies();


        for (Currency sourceCurrency : currencies) {
            BellmanFordResult result = runBellmanFord(graph, sourceCurrency);

            if (result.hasNegativeCycle()) {
                ArbitrageOpportunity opportunity = reconstructArbitrageOpportunity(
                        graph, result, sourceCurrency);

                if (opportunity != null && !isDuplicate(opportunities, opportunity)) {
                    opportunities.add(opportunity);
                }
            }
        }


        opportunities.sort(Comparator.comparing(ArbitrageOpportunity::getProfitPercentage).reversed());

        return opportunities;
    }


    public Optional<ArbitrageOpportunity> detectArbitrageFrom(CurrencyGraph graph, Currency startCurrency) throws DisconnectedCurrencyGraph {
        Objects.requireNonNull(graph, "Currency graph cannot be null");
        Objects.requireNonNull(startCurrency, "Start currency cannot be null");

        if (!graph.isConnected()) {
            throw new DisconnectedCurrencyGraph("Currency graph is not connected - " +
                    "cannot detect arbitrage from " + startCurrency);
        }

        BellmanFordResult result = runBellmanFord(graph, startCurrency);

        if (result.hasNegativeCycle()) {
            ArbitrageOpportunity opportunity = reconstructArbitrageOpportunity(
                    graph, result, startCurrency);
            return Optional.ofNullable(opportunity);
        }

        return Optional.empty();
    }


    private BellmanFordResult runBellmanFord(CurrencyGraph graph, Currency source) {
        int vertexCount = graph.getVertexCount();
        Integer sourceIndex = graph.getCurrencyIndex(source);

        if (sourceIndex == null) {
            throw new IllegalArgumentException("Source currency not found in graph: " + source);
        }


        double[] distances = new double[vertexCount];
        int[] predecessors = new int[vertexCount];
        Arrays.fill(distances, Double.POSITIVE_INFINITY);
        Arrays.fill(predecessors, -1);
        distances[sourceIndex] = 0.0;

        List<CurrencyGraph.Edge> edges = graph.getAllEdges();


        for (int iteration = 0; iteration < vertexCount - 1; iteration++) {
            boolean hasUpdate = false;

            for (CurrencyGraph.Edge edge : edges) {
                int from = edge.getFromIndex();
                int to = edge.getToIndex();
                double weight = edge.getWeight();

                if (distances[from] != Double.POSITIVE_INFINITY &&
                        distances[from] + weight < distances[to] - EPSILON) {

                    distances[to] = distances[from] + weight;
                    predecessors[to] = from;
                    hasUpdate = true;
                }
            }

            if (!hasUpdate) {
                break;
            }
        }


        List<Integer> negativeCycleNodes = new ArrayList<>();
        for (CurrencyGraph.Edge edge : edges) {
            int from = edge.getFromIndex();
            int to = edge.getToIndex();
            double weight = edge.getWeight();

            if (distances[from] != Double.POSITIVE_INFINITY &&
                    distances[from] + weight < distances[to] - EPSILON) {

                negativeCycleNodes.add(to);
            }
        }

        return new BellmanFordResult(distances, predecessors, negativeCycleNodes);
    }


    private ArbitrageOpportunity reconstructArbitrageOpportunity(CurrencyGraph graph,
                                                                 BellmanFordResult result,
                                                                 Currency source) {
        if (result.getNegativeCycleNodes().isEmpty()) {
            return null;
        }


        int startNode = result.getNegativeCycleNodes().get(0);
        List<Integer> cycle = findNegativeCycle(result.getPredecessors(), startNode);

        if (cycle.size() < 2) {
            return null;
        }


        List<Currency> currencyPath = new ArrayList<>();
        List<CurrencyPair> pairPath = new ArrayList<>();
        BigDecimal totalRate = BigDecimal.ONE;

        for (int i = 0; i < cycle.size(); i++) {
            int fromIndex = cycle.get(i);
            int toIndex = cycle.get((i + 1) % cycle.size());

            Currency fromCurrency = graph.getCurrencyByIndex(fromIndex);
            Currency toCurrency = graph.getCurrencyByIndex(toIndex);

            currencyPath.add(fromCurrency);


            Optional<CurrencyPair> pairOpt = graph.getBestRate(fromCurrency, toCurrency);
            if (pairOpt.isPresent()) {
                CurrencyPair pair = pairOpt.get();
                pairPath.add(pair);
                totalRate = totalRate.multiply(pair.getExchangeRate(), MATH_CONTEXT);
            }
        }


        BigDecimal profitRatio = totalRate.subtract(BigDecimal.ONE);
        BigDecimal profitPercentage = profitRatio.multiply(new BigDecimal("100"), MATH_CONTEXT);

        return new ArbitrageOpportunity(currencyPath, pairPath, totalRate,
                profitPercentage, source);
    }


    private List<Integer> findNegativeCycle(int[] predecessors, int startNode) {
        Set<Integer> visited = new HashSet<>();
        List<Integer> path = new ArrayList<>();

        int current = startNode;


        while (!visited.contains(current) && current != -1) {
            visited.add(current);
            path.add(current);
            current = predecessors[current];
        }

        if (current == -1) {
            return new ArrayList<>();
        }


        int cycleStart = path.indexOf(current);
        if (cycleStart == -1) {
            return new ArrayList<>();
        }


        return path.subList(cycleStart, path.size());
    }


    private boolean isDuplicate(List<ArbitrageOpportunity> opportunities,
                                ArbitrageOpportunity newOpportunity) {
        return opportunities.stream()
                .anyMatch(existing -> existing.hasSamePath(newOpportunity));
    }


    private static final class BellmanFordResult {
        private final double[] distances;
        private final int[] predecessors;
        private final List<Integer> negativeCycleNodes;

        public BellmanFordResult(double[] distances, int[] predecessors,
                                 List<Integer> negativeCycleNodes) {
            this.distances = distances.clone();
            this.predecessors = predecessors.clone();
            this.negativeCycleNodes = new ArrayList<>(negativeCycleNodes);
        }

        public boolean hasNegativeCycle() {
            return !negativeCycleNodes.isEmpty();
        }

        public double[] getDistances() {
            return distances.clone();
        }

        public int[] getPredecessors() {
            return predecessors.clone();
        }

        public List<Integer> getNegativeCycleNodes() {
            return new ArrayList<>(negativeCycleNodes);
        }
    }


    public DetectionStatistics getDetectionStatistics(CurrencyGraph graph) throws DisconnectedCurrencyGraph {
        Objects.requireNonNull(graph, "Currency graph cannot be null");

        long startTime = System.currentTimeMillis();

        List<ArbitrageOpportunity> opportunities = detectArbitrage(graph);

        long detectionTime = System.currentTimeMillis() - startTime;
        int totalOpportunities = opportunities.size();

        double maxProfit = opportunities.stream()
                .mapToDouble(opp -> opp.getProfitPercentage().doubleValue())
                .max().orElse(0.0);

        double avgProfit = opportunities.stream()
                .mapToDouble(opp -> opp.getProfitPercentage().doubleValue())
                .average().orElse(0.0);

        Map<Integer, Integer> pathLengthDistribution = new HashMap<>();
        for (ArbitrageOpportunity opp : opportunities) {
            int pathLength = opp.getCurrencyPath().size();
            pathLengthDistribution.merge(pathLength, 1, Integer::sum);
        }

        return new DetectionStatistics(totalOpportunities, maxProfit, avgProfit,
                detectionTime, pathLengthDistribution);
    }


    public static final class DetectionStatistics {
        private final int totalOpportunities;
        private final double maxProfit;
        private final double avgProfit;
        private final long detectionTimeMs;
        private final Map<Integer, Integer> pathLengthDistribution;

        public DetectionStatistics(int totalOpportunities, double maxProfit, double avgProfit,
                                   long detectionTimeMs, Map<Integer, Integer> pathLengthDistribution) {
            this.totalOpportunities = totalOpportunities;
            this.maxProfit = maxProfit;
            this.avgProfit = avgProfit;
            this.detectionTimeMs = detectionTimeMs;
            this.pathLengthDistribution = new HashMap<>(pathLengthDistribution);
        }

        public int getTotalOpportunities() {
            return totalOpportunities;
        }

        public double getMaxProfit() {
            return maxProfit;
        }

        public double getAvgProfit() {
            return avgProfit;
        }

        public long getDetectionTimeMs() {
            return detectionTimeMs;
        }

        public Map<Integer, Integer> getPathLengthDistribution() {
            return new HashMap<>(pathLengthDistribution);
        }

        @Override
        public String toString() {
            return String.format("DetectionStats{opportunities=%d, maxProfit=%.3f%%, " +
                            "avgProfit=%.3f%%, time=%dms}",
                    totalOpportunities, maxProfit, avgProfit, detectionTimeMs);
        }
    }

    @Override
    public String toString() {
        return "ArbitrageDetector{algorithm=Bellman-Ford, epsilon=" + EPSILON + "}";
    }
}