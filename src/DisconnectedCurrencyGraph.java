import java.util.*;


public final class DisconnectedCurrencyGraph extends Exception {

    private final Set<Currency> disconnectedCurrencies;
    private final Set<Currency> connectedCurrencies;
    private final int totalVertices;
    private final int connectedVertices;
    private final GraphConnectivityAnalysis analysis;

    public DisconnectedCurrencyGraph(String message) {
        super(message);
        this.disconnectedCurrencies = null;
        this.connectedCurrencies = null;
        this.totalVertices = 0;
        this.connectedVertices = 0;
        this.analysis = null;
    }

    public DisconnectedCurrencyGraph(String message, Throwable cause) {
        super(message, cause);
        this.disconnectedCurrencies = null;
        this.connectedCurrencies = null;
        this.totalVertices = 0;
        this.connectedVertices = 0;
        this.analysis = null;
    }

    public DisconnectedCurrencyGraph(String message,
                                     Set<Currency> disconnectedCurrencies,
                                     Set<Currency> connectedCurrencies,
                                     GraphConnectivityAnalysis analysis) {
        super(buildDetailedMessage(message, disconnectedCurrencies, connectedCurrencies, analysis));
        this.disconnectedCurrencies = disconnectedCurrencies != null && !disconnectedCurrencies.isEmpty() ?
                EnumSet.copyOf(disconnectedCurrencies) : null;
        this.connectedCurrencies = connectedCurrencies != null && !connectedCurrencies.isEmpty() ?
                EnumSet.copyOf(connectedCurrencies) : null;
        this.totalVertices = (disconnectedCurrencies != null ? disconnectedCurrencies.size() : 0) +
                (connectedCurrencies != null ? connectedCurrencies.size() : 0);
        this.connectedVertices = connectedCurrencies != null ? connectedCurrencies.size() : 0;
        this.analysis = analysis;
    }

    
    public static DisconnectedCurrencyGraph missingCurrencyPairs(Set<Currency> isolatedCurrencies,
                                                                 Set<Currency> connectedCurrencies) {
        String message = "Currency graph has isolated currencies - missing exchange rate pairs";
        GraphConnectivityAnalysis analysis = new GraphConnectivityAnalysis(
                isolatedCurrencies.size() + connectedCurrencies.size(),
                connectedCurrencies.size(),
                calculateMissingPairCount(isolatedCurrencies, connectedCurrencies),
                identifyMissingPairs(isolatedCurrencies, connectedCurrencies)
        );

        return new DisconnectedCurrencyGraph(message, isolatedCurrencies, connectedCurrencies, analysis);
    }

    public static DisconnectedCurrencyGraph insufficientConnectivity(Currency sourceCurrency,
                                                                     Set<Currency> unreachableCurrencies) {
        String message = String.format("Insufficient connectivity from %s - cannot reach %d currencies",
                sourceCurrency, unreachableCurrencies.size());
        Set<Currency> connected = new HashSet<>(java.util.Arrays.asList(Currency.values()));
        connected.removeAll(unreachableCurrencies);
        connected.remove(sourceCurrency); 

        GraphConnectivityAnalysis analysis = new GraphConnectivityAnalysis(
                Currency.values().length,
                connected.size(),
                unreachableCurrencies.size(),
                identifyMissingPairs(unreachableCurrencies, connected)
        );

        return new DisconnectedCurrencyGraph(message, unreachableCurrencies, connected, analysis);
    }

    public static DisconnectedCurrencyGraph noArbitragePathExists(Currency startCurrency) {
        String message = String.format("No arbitrage path exists from %s - graph connectivity insufficient",
                startCurrency);
        return new DisconnectedCurrencyGraph(message);
    }

    public static DisconnectedCurrencyGraph emptyGraph() {
        String message = "Currency graph is empty - no currency pairs defined";
        GraphConnectivityAnalysis analysis = new GraphConnectivityAnalysis(0, 0, 0, new HashSet<>());
        return new DisconnectedCurrencyGraph(message, new HashSet<>(), new HashSet<>(), analysis);
    }

    
    public Set<Currency> getDisconnectedCurrencies() {
        return disconnectedCurrencies != null ?
                new HashSet<>(disconnectedCurrencies) : new HashSet<>();
    }

    public Set<Currency> getConnectedCurrencies() {
        return connectedCurrencies != null ?
                new HashSet<>(connectedCurrencies) : new HashSet<>();
    }

    public int getTotalVertices() { return totalVertices; }
    public int getConnectedVertices() { return connectedVertices; }
    public int getDisconnectedVertices() { return totalVertices - connectedVertices; }

    public GraphConnectivityAnalysis getAnalysis() { return analysis; }

    public boolean hasConnectivityAnalysis() {
        return analysis != null;
    }

    
    public double getConnectivityPercentage() {
        if (totalVertices == 0) return 0.0;
        return (double) connectedVertices / totalVertices * 100.0;
    }

    
    public List<String> getRecoverySuggestions() {
        List<String> suggestions = new ArrayList<>();

        if (disconnectedCurrencies != null && !disconnectedCurrencies.isEmpty()) {
            suggestions.add("Add missing currency pairs for isolated currencies: " +
                    disconnectedCurrencies);
        }

        if (hasConnectivityAnalysis() && analysis.getMissingPairCount() > 0) {
            suggestions.add(String.format("Add %d missing currency pairs to improve connectivity",
                    analysis.getMissingPairCount()));

            if (analysis.getMissingPairs().size() <= 3) {
                suggestions.add("Priority pairs to add: " + analysis.getMissingPairs());
            }
        }

        if (getConnectivityPercentage() < 50.0) {
            suggestions.add("Graph connectivity is very low - consider adding major currency pairs (USD/EUR, USD/GBP, etc.)");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("Check currency pair data source and ensure all expected pairs are loaded");
        }

        return suggestions;
    }

    
    public Set<String> getCriticalMissingPairs() {
        if (analysis == null || disconnectedCurrencies == null) {
            return new HashSet<>();
        }

        Set<String> critical = new HashSet<>();

        
        for (Currency disconnected : disconnectedCurrencies) {
            if (!disconnected.equals(Currency.USD)) {
                critical.add(disconnected + "/USD");
                critical.add("USD/" + disconnected);
            }
        }

        return critical;
    }

    
    private static String buildDetailedMessage(String message,
                                               Set<Currency> disconnectedCurrencies,
                                               Set<Currency> connectedCurrencies,
                                               GraphConnectivityAnalysis analysis) {
        StringBuilder sb = new StringBuilder(message);

        if (disconnectedCurrencies != null && !disconnectedCurrencies.isEmpty()) {
            sb.append(" [Disconnected: ").append(disconnectedCurrencies).append("]");
        }

        if (connectedCurrencies != null && !connectedCurrencies.isEmpty()) {
            sb.append(" [Connected: ").append(connectedCurrencies).append("]");
        }

        if (analysis != null) {
            sb.append(" [Missing pairs: ").append(analysis.getMissingPairCount()).append("]");
        }

        return sb.toString();
    }

    private static int calculateMissingPairCount(Set<Currency> disconnected, Set<Currency> connected) {
        int totalCurrencies = disconnected.size() + connected.size();
        int maxPossiblePairs = totalCurrencies * (totalCurrencies - 1); 

        
        int connectedPairs = connected.size() * (connected.size() - 1);
        int disconnectedPairs = 0; 
        int crossPairs = 0; 

        int actualPairs = connectedPairs + disconnectedPairs + crossPairs;
        return maxPossiblePairs - actualPairs;
    }

    private static Set<String> identifyMissingPairs(Set<Currency> disconnected, Set<Currency> connected) {
        Set<String> missing = new HashSet<>();

        
        for (Currency disc : disconnected) {
            for (Currency conn : connected) {
                missing.add(disc + "/" + conn);
                missing.add(conn + "/" + disc);
            }
        }

        
        Currency[] discArray = disconnected.toArray(new Currency[0]);
        for (int i = 0; i < discArray.length; i++) {
            for (int j = i + 1; j < discArray.length; j++) {
                missing.add(discArray[i] + "/" + discArray[j]);
                missing.add(discArray[j] + "/" + discArray[i]);
            }
        }

        return missing;
    }

    
    public static final class GraphConnectivityAnalysis {
        private final int totalVertices;
        private final int connectedVertices;
        private final int missingPairCount;
        private final Set<String> missingPairs;

        public GraphConnectivityAnalysis(int totalVertices, int connectedVertices,
                                         int missingPairCount, Set<String> missingPairs) {
            this.totalVertices = totalVertices;
            this.connectedVertices = connectedVertices;
            this.missingPairCount = missingPairCount;
            this.missingPairs = new HashSet<>(Objects.requireNonNull(missingPairs,
                    "Missing pairs cannot be null"));
        }

        public int getTotalVertices() { return totalVertices; }
        public int getConnectedVertices() { return connectedVertices; }
        public int getDisconnectedVertices() { return totalVertices - connectedVertices; }
        public int getMissingPairCount() { return missingPairCount; }
        public Set<String> getMissingPairs() { return new HashSet<>(missingPairs); }

        public double getConnectivityRatio() {
            return totalVertices > 0 ? (double) connectedVertices / totalVertices : 0.0;
        }

        public boolean isFullyConnected() {
            return connectedVertices == totalVertices && totalVertices > 0;
        }

        public boolean isPartiallyConnected() {
            return connectedVertices > 0 && connectedVertices < totalVertices;
        }

        public boolean isCompletelyDisconnected() {
            return connectedVertices == 0;
        }

        @Override
        public String toString() {
            return String.format("ConnectivityAnalysis{connected=%d/%d (%.1f%%), missing=%d pairs}",
                    connectedVertices, totalVertices, getConnectivityRatio() * 100,
                    missingPairCount);
        }
    }

    @Override
    public String toString() {
        return String.format("DisconnectedCurrencyGraph{connectivity=%.1f%%, disconnected=%d, connected=%d}",
                getConnectivityPercentage(), getDisconnectedVertices(), connectedVertices);
    }
}