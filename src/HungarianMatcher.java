// HungarianMatcher.java - Hungarian Algorithm for optimal Auth-Presentment matching
import java.util.*;

/**
 * Hungarian Algorithm implementation for solving the assignment problem
 * Finds optimal matching between authorizations and presentments
 * Time Complexity: O(n³) where n = max(auths.size(), presentments.size())
 */
public class HungarianMatcher {

    private static final double EPSILON = 1e-10;
    private static final double NEGATIVE_INFINITY = -1e6;

    /**
     * Find optimal matching between auths and presentments
     * @param auths List of authorizations
     * @param presentments List of presentments
     * @return Optimal matching result
     */
    public MatchingResult findOptimalMatching(List<Auth> auths, List<Presentment> presentments) {
        Objects.requireNonNull(auths, "Auths cannot be null");
        Objects.requireNonNull(presentments, "Presentments cannot be null");

        if (auths.isEmpty() && presentments.isEmpty()) {
            return MatchingResult.empty();
        }

        long startTime = System.nanoTime();

        try {
            // Step 1: Build cost matrix (convert scores to costs)
            CostMatrix costMatrix = buildCostMatrix(auths, presentments);

            // Step 2: Apply Hungarian algorithm
            int[] assignment = hungarianAlgorithm(costMatrix);

            // Step 3: Convert assignment to matching result
            MatchingResult result = buildMatchingResult(auths, presentments, costMatrix, assignment);

            long elapsedTime = System.nanoTime() - startTime;
            return result.withExecutionTime(elapsedTime);

        } catch (Exception e) {
            throw new MatchingException("Hungarian algorithm failed", e);
        }
    }

    /**
     * Build cost matrix from matching scores
     * Higher matching score = Lower cost (since Hungarian finds minimum cost)
     */
    private CostMatrix buildCostMatrix(List<Auth> auths, List<Presentment> presentments) {
        int n = Math.max(auths.size(), presentments.size());
        double[][] matrix = new double[n][n];

        // Initialize with high cost (low compatibility)
        for (int i = 0; i < n; i++) {
            Arrays.fill(matrix[i], NEGATIVE_INFINITY);
        }

        // Fill actual matching scores (convert score to cost: cost = 100 - score)
        for (int i = 0; i < auths.size(); i++) {
            Auth auth = auths.get(i);

            for (int j = 0; j < presentments.size(); j++) {
                Presentment presentment = presentments.get(j);

                double matchingScore = auth.calculateMatchingScore(presentment);

                // Convert score to cost (Hungarian finds minimum, we want maximum score)
                double cost = 100.0 - matchingScore;

                // Only allow matching if cards match and both can be matched
                if (matchingScore > 0 && canBeMatched(auth, presentment)) {
                    matrix[i][j] = cost;
                }
            }
        }

        return new CostMatrix(matrix, auths.size(), presentments.size());
    }

    /**
     * Core Hungarian Algorithm implementation
     * Returns assignment array where assignment[i] = j means auth[i] -> presentment[j]
     */
    private int[] hungarianAlgorithm(CostMatrix costMatrix) {
        double[][] matrix = copyMatrix(costMatrix.matrix);
        int n = matrix.length;

        // Step 1: Subtract row minimums
        subtractRowMinimums(matrix);

        // Step 2: Subtract column minimums
        subtractColumnMinimums(matrix);

        // Step 3: Main Hungarian loop
        while (true) {
            // Find minimum number of lines to cover all zeros
            LineCover lineCover = findMinimumLineCover(matrix);

            if (lineCover.lineCount >= n) {
                // Found optimal assignment
                break;
            }

            // Step 4: Adjust matrix and continue
            adjustMatrix(matrix, lineCover);
        }

        // Step 5: Find the assignment
        return findAssignment(matrix, costMatrix.numAuths, costMatrix.numPresentments);
    }

    /**
     * Subtract minimum value from each row
     */
    private void subtractRowMinimums(double[][] matrix) {
        int n = matrix.length;

        for (int i = 0; i < n; i++) {
            double min = Arrays.stream(matrix[i])
                    .filter(val -> val > NEGATIVE_INFINITY)
                    .min()
                    .orElse(0.0);

            for (int j = 0; j < n; j++) {
                if (matrix[i][j] > NEGATIVE_INFINITY) {
                    matrix[i][j] -= min;
                }
            }
        }
    }

    /**
     * Subtract minimum value from each column
     */
    private void subtractColumnMinimums(double[][] matrix) {
        int n = matrix.length;

        for (int j = 0; j < n; j++) {
            double min = Double.MAX_VALUE;

            for (int i = 0; i < n; i++) {
                if (matrix[i][j] > NEGATIVE_INFINITY && matrix[i][j] < min) {
                    min = matrix[i][j];
                }
            }

            if (min != Double.MAX_VALUE) {
                for (int i = 0; i < n; i++) {
                    if (matrix[i][j] > NEGATIVE_INFINITY) {
                        matrix[i][j] -= min;
                    }
                }
            }
        }
    }

    /**
     * Find minimum number of lines (rows + columns) to cover all zeros
     * Uses König's theorem: minimum vertex cover = maximum matching in bipartite graph
     */
    private LineCover findMinimumLineCover(double[][] matrix) {
        int n = matrix.length;
        boolean[] rowCovered = new boolean[n];
        boolean[] colCovered = new boolean[n];

        // Build bipartite graph of zeros
        List<List<Integer>> graph = buildZeroGraph(matrix);

        // Find maximum matching using augmenting paths
        int[] matching = findMaximumMatching(graph, n);

        // Convert matching to minimum vertex cover (line cover)
        return convertMatchingToLineCover(graph, matching, n);
    }

    /**
     * Build graph where edges represent zero entries in cost matrix
     */
    private List<List<Integer>> buildZeroGraph(double[][] matrix) {
        int n = matrix.length;
        List<List<Integer>> graph = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());

            for (int j = 0; j < n; j++) {
                if (Math.abs(matrix[i][j]) < EPSILON && matrix[i][j] > NEGATIVE_INFINITY) {
                    graph.get(i).add(j);
                }
            }
        }

        return graph;
    }

    /**
     * Find maximum matching in bipartite graph using augmenting paths
     */
    private int[] findMaximumMatching(List<List<Integer>> graph, int n) {
        int[] matching = new int[n];
        Arrays.fill(matching, -1);

        for (int u = 0; u < n; u++) {
            boolean[] visited = new boolean[n];
            findAugmentingPath(graph, u, matching, visited);
        }

        return matching;
    }

    /**
     * DFS to find augmenting path
     */
    private boolean findAugmentingPath(List<List<Integer>> graph, int u, int[] matching, boolean[] visited) {
        for (int v : graph.get(u)) {
            if (visited[v]) continue;
            visited[v] = true;

            // If v is unmatched or we can find augmenting path from match[v]
            if (matching[v] == -1 || findAugmentingPath(graph, matching[v], matching, visited)) {
                matching[v] = u;
                return true;
            }
        }

        return false;
    }

    /**
     * Convert maximum matching to minimum line cover
     */
    private LineCover convertMatchingToLineCover(List<List<Integer>> graph, int[] matching, int n) {
        boolean[] rowCovered = new boolean[n];
        boolean[] colCovered = new boolean[n];

        // Mark unmatched rows
        Set<Integer> unmatchedRows = new HashSet<>();
        for (int i = 0; i < n; i++) {
            boolean isMatched = false;
            for (int j = 0; j < n; j++) {
                if (matching[j] == i) {
                    isMatched = true;
                    break;
                }
            }
            if (!isMatched) {
                unmatchedRows.add(i);
            }
        }

        // DFS from unmatched rows following alternating paths
        Set<Integer> reachableRows = new HashSet<>(unmatchedRows);
        Set<Integer> reachableCols = new HashSet<>();

        boolean changed = true;
        while (changed) {
            changed = false;

            // From reachable rows, mark reachable columns (via zeros)
            for (int row : new ArrayList<>(reachableRows)) {
                for (int col : graph.get(row)) {
                    if (!reachableCols.contains(col)) {
                        reachableCols.add(col);
                        changed = true;
                    }
                }
            }

            // From reachable columns, mark reachable rows (via matching)
            for (int col : new ArrayList<>(reachableCols)) {
                if (matching[col] != -1 && !reachableRows.contains(matching[col])) {
                    reachableRows.add(matching[col]);
                    changed = true;
                }
            }
        }

        // Minimum vertex cover: non-reachable rows + reachable columns
        int lineCount = 0;

        for (int i = 0; i < n; i++) {
            if (!reachableRows.contains(i)) {
                rowCovered[i] = true;
                lineCount++;
            }
        }

        for (int j : reachableCols) {
            colCovered[j] = true;
            lineCount++;
        }

        return new LineCover(rowCovered, colCovered, lineCount);
    }

    /**
     * Adjust matrix when line cover is insufficient
     */
    private void adjustMatrix(double[][] matrix, LineCover lineCover) {
        int n = matrix.length;

        // Find minimum uncovered value
        double min = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (!lineCover.rowCovered[i] && !lineCover.colCovered[j] &&
                        matrix[i][j] > NEGATIVE_INFINITY && matrix[i][j] < min) {
                    min = matrix[i][j];
                }
            }
        }

        if (min == Double.MAX_VALUE) {
            return; // No adjustment needed
        }

        // Subtract min from uncovered elements, add to doubly covered elements
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (matrix[i][j] > NEGATIVE_INFINITY) {
                    if (!lineCover.rowCovered[i] && !lineCover.colCovered[j]) {
                        // Uncovered: subtract min
                        matrix[i][j] -= min;
                    } else if (lineCover.rowCovered[i] && lineCover.colCovered[j]) {
                        // Doubly covered: add min
                        matrix[i][j] += min;
                    }
                    // Singly covered: no change
                }
            }
        }
    }

    /**
     * Find final assignment from processed matrix
     */
    private int[] findAssignment(double[][] matrix, int numAuths, int numPresentments) {
        int n = matrix.length;
        int[] assignment = new int[numAuths];
        Arrays.fill(assignment, -1);

        boolean[] rowUsed = new boolean[n];
        boolean[] colUsed = new boolean[n];

        // Find assignment greedily (zeros in processed matrix represent optimal assignment)
        for (int i = 0; i < numAuths; i++) {
            for (int j = 0; j < numPresentments; j++) {
                if (!rowUsed[i] && !colUsed[j] &&
                        Math.abs(matrix[i][j]) < EPSILON && matrix[i][j] > NEGATIVE_INFINITY) {
                    assignment[i] = j;
                    rowUsed[i] = true;
                    colUsed[j] = true;
                    break;
                }
            }
        }

        return assignment;
    }

    /**
     * Build final matching result from assignment
     */
    private MatchingResult buildMatchingResult(List<Auth> auths, List<Presentment> presentments,
                                               CostMatrix costMatrix, int[] assignment) {
        List<AuthPresentmentMatch> matches = new ArrayList<>();
        List<Auth> unmatchedAuths = new ArrayList<>();
        List<Presentment> unmatchedPresentments = new ArrayList<>(presentments);

        double totalScore = 0.0;

        // Process assignments
        for (int i = 0; i < assignment.length; i++) {
            int j = assignment[i];

            if (j >= 0 && j < presentments.size()) {
                Auth auth = auths.get(i);
                Presentment presentment = presentments.get(j);
                double score = auth.calculateMatchingScore(presentment);

                if (score > 0) {
                    matches.add(AuthPresentmentMatch.withScore(auth, presentment, score));
                    unmatchedPresentments.remove(presentment);
                    totalScore += score;
                } else {
                    unmatchedAuths.add(auth);
                }
            } else {
                unmatchedAuths.add(auths.get(i));
            }
        }

        // Add remaining unmatched auths
        for (int i = assignment.length; i < auths.size(); i++) {
            unmatchedAuths.add(auths.get(i));
        }

        return MatchingResult.builder()
                .matches(matches)
                .unmatchedAuths(unmatchedAuths)
                .unmatchedPresentments(unmatchedPresentments)
                .totalScore(totalScore)
                .algorithm("Hungarian")
                .build();
    }

    // Helper methods
    private boolean canBeMatched(Auth auth, Presentment presentment) {
        return auth.canBeMatched() &&
                presentment.canBeMatched() &&
                auth.getCardNumber().equals(presentment.getCardNumber());
    }

    private double[][] copyMatrix(double[][] original) {
        int n = original.length;
        double[][] copy = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(original[i], 0, copy[i], 0, n);
        }
        return copy;
    }

    // Inner classes for algorithm state
    private static class CostMatrix {
        final double[][] matrix;
        final int numAuths;
        final int numPresentments;

        CostMatrix(double[][] matrix, int numAuths, int numPresentments) {
            this.matrix = matrix;
            this.numAuths = numAuths;
            this.numPresentments = numPresentments;
        }
    }

    private static class LineCover {
        final boolean[] rowCovered;
        final boolean[] colCovered;
        final int lineCount;

        LineCover(boolean[] rowCovered, boolean[] colCovered, int lineCount) {
            this.rowCovered = rowCovered;
            this.colCovered = colCovered;
            this.lineCount = lineCount;
        }
    }
}

// Supporting exception class
class MatchingException extends RuntimeException {
    public MatchingException(String message, Throwable cause) {
        super(message, cause);
    }
}