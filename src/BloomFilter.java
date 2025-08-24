import java.util.BitSet;
import java.util.Objects;

/**
 * Custom Bloom Filter implementation - probabilistic data structure.
 *
 * Fraud detection için duplicate transaction detection:
 * - Memory efficient: BitSet instead of HashSet
 * - Fast lookups: O(k) where k=hash function count
 * - False positives possible, false negatives impossible
 * - 3 hash functions: MurmurHash variants
 */
public final class BloomFilter {

    private final BitSet bitSet;
    private final int bitArraySize;
    private final int hashFunctionCount;
    private int elementCount;

    // Hash function seeds for diversity
    private static final int[] HASH_SEEDS = {0x7ed55d16, 0xc761c23c, 0x165667b1};

    // Default parameters for fraud detection
    private static final int DEFAULT_BIT_SIZE = 1000000;  // 1M bits ≈ 125KB
    private static final int DEFAULT_HASH_COUNT = 3;

    public BloomFilter(int expectedElements, double falsePositiveRate) {
        if (expectedElements <= 0) {
            throw new IllegalArgumentException("Expected elements must be positive");
        }
        if (falsePositiveRate <= 0 || falsePositiveRate >= 1) {
            throw new IllegalArgumentException("False positive rate must be between 0 and 1");
        }

        // Optimal bit array size: m = -n*ln(p) / (ln(2)^2)
        this.bitArraySize = (int) Math.ceil(-expectedElements * Math.log(falsePositiveRate) /
                (Math.log(2) * Math.log(2)));

        // Optimal hash function count: k = (m/n) * ln(2)
        this.hashFunctionCount = Math.max(1, (int) Math.round(
                (double) bitArraySize / expectedElements * Math.log(2)));

        this.bitSet = new BitSet(bitArraySize);
        this.elementCount = 0;
    }

    public BloomFilter(int bitArraySize, int hashFunctionCount) {
        if (bitArraySize <= 0) {
            throw new IllegalArgumentException("Bit array size must be positive");
        }
        if (hashFunctionCount <= 0) {
            throw new IllegalArgumentException("Hash function count must be positive");
        }

        this.bitArraySize = bitArraySize;
        this.hashFunctionCount = Math.min(hashFunctionCount, HASH_SEEDS.length);
        this.bitSet = new BitSet(bitArraySize);
        this.elementCount = 0;
    }

    // Default constructor for fraud detection
    public static BloomFilter createForFraudDetection() {
        // Optimized for ~100K transactions with ~0.1% false positive rate
        return new BloomFilter(100000, 0.001);
    }

    // Compact version for memory-constrained environments
    public static BloomFilter createCompact() {
        return new BloomFilter(DEFAULT_BIT_SIZE, DEFAULT_HASH_COUNT);
    }

    /**
     * Element ekler - transaction ID, card+merchant combo vs.
     */
    public void add(String element) {
        Objects.requireNonNull(element, "Element cannot be null");

        int[] hashes = getHashValues(element);
        for (int hash : hashes) {
            int index = Math.abs(hash) % bitArraySize;
            bitSet.set(index);
        }

        elementCount++;
    }

    /**
     * Element var mı kontrol eder
     * False positive mümkün, false negative impossible
     */
    public boolean mightContain(String element) {
        Objects.requireNonNull(element, "Element cannot be null");

        int[] hashes = getHashValues(element);
        for (int hash : hashes) {
            int index = Math.abs(hash) % bitArraySize;
            if (!bitSet.get(index)) {
                return false; // Definitely not present
            }
        }

        return true; // Might be present (could be false positive)
    }

    /**
     * Birden fazla element ekler (batch operation)
     */
    public void addAll(Iterable<String> elements) {
        Objects.requireNonNull(elements, "Elements cannot be null");
        for (String element : elements) {
            add(element);
        }
    }

    /**
     * Birden fazla element kontrol eder
     */
    public boolean containsAny(Iterable<String> elements) {
        Objects.requireNonNull(elements, "Elements cannot be null");
        for (String element : elements) {
            if (mightContain(element)) {
                return true;
            }
        }
        return false;
    }

    /**
     * False positive rate'i hesaplar (approximation)
     */
    public double getEstimatedFalsePositiveRate() {
        if (elementCount == 0) return 0.0;

        // FPR ≈ (1 - e^(-kn/m))^k
        // k = hash functions, n = elements, m = bit array size
        double exponent = -(double) hashFunctionCount * elementCount / bitArraySize;
        double base = 1.0 - Math.exp(exponent);
        return Math.pow(base, hashFunctionCount);
    }

    /**
     * Memory utilization (percentage of bits set)
     */
    public double getMemoryUtilization() {
        return (double) bitSet.cardinality() / bitArraySize * 100.0;
    }

    /**
     * Bloom filter'ı temizler
     */
    public void clear() {
        bitSet.clear();
        elementCount = 0;
    }

    /**
     * İki bloom filter'ı birleştirir (union operation)
     */
    public BloomFilter union(BloomFilter other) {
        if (this.bitArraySize != other.bitArraySize ||
                this.hashFunctionCount != other.hashFunctionCount) {
            throw new IllegalArgumentException("Bloom filters must have same parameters for union");
        }

        BloomFilter result = new BloomFilter(bitArraySize, hashFunctionCount);
        result.bitSet.or(this.bitSet);
        result.bitSet.or(other.bitSet);
        result.elementCount = this.elementCount + other.elementCount; // Approximation

        return result;
    }

    /**
     * 3 hash fonksiyonu ile hash values üretir
     */
    private int[] getHashValues(String element) {
        int[] hashes = new int[Math.min(hashFunctionCount, HASH_SEEDS.length)];

        for (int i = 0; i < hashes.length; i++) {
            hashes[i] = murmurHash3(element, HASH_SEEDS[i]);
        }

        return hashes;
    }

    /**
     * MurmurHash3 implementation - fast non-cryptographic hash
     */
    private int murmurHash3(String input, int seed) {
        byte[] data = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return murmurHash3(data, seed);
    }

    private int murmurHash3(byte[] data, int seed) {
        final int c1 = 0xcc9e2d51;
        final int c2 = 0x1b873593;
        final int r1 = 15;
        final int r2 = 13;
        final int m = 5;
        final int n = 0xe6546b64;

        int hash = seed;

        // Process 4-byte chunks
        int length = data.length;
        int numChunks = length / 4;

        for (int i = 0; i < numChunks; i++) {
            int chunk = 0;
            for (int j = 0; j < 4; j++) {
                chunk |= (data[i * 4 + j] & 0xff) << (j * 8);
            }

            chunk *= c1;
            chunk = Integer.rotateLeft(chunk, r1);
            chunk *= c2;

            hash ^= chunk;
            hash = Integer.rotateLeft(hash, r2);
            hash = hash * m + n;
        }

        // Process remaining bytes
        int remaining = length % 4;
        if (remaining > 0) {
            int chunk = 0;
            for (int i = 0; i < remaining; i++) {
                chunk |= (data[numChunks * 4 + i] & 0xff) << (i * 8);
            }

            chunk *= c1;
            chunk = Integer.rotateLeft(chunk, r1);
            chunk *= c2;
            hash ^= chunk;
        }

        // Finalization
        hash ^= length;
        hash ^= (hash >>> 16);
        hash *= 0x85ebca6b;
        hash ^= (hash >>> 13);
        hash *= 0xc2b2ae35;
        hash ^= (hash >>> 16);

        return hash;
    }

    /**
     * Bloom filter statistics
     */
    public BloomFilterStatistics getStatistics() {
        return new BloomFilterStatistics(
                bitArraySize,
                hashFunctionCount,
                elementCount,
                bitSet.cardinality(),
                getMemoryUtilization(),
                getEstimatedFalsePositiveRate()
        );
    }

    // Getters
    public int getBitArraySize() { return bitArraySize; }
    public int getHashFunctionCount() { return hashFunctionCount; }
    public int getElementCount() { return elementCount; }
    public int getBitsSet() { return bitSet.cardinality(); }

    /**
     * Bloom filter statistics nested class
     */
    public static final class BloomFilterStatistics {
        private final int bitArraySize;
        private final int hashFunctionCount;
        private final int elementCount;
        private final int bitsSet;
        private final double memoryUtilization;
        private final double estimatedFPR;

        public BloomFilterStatistics(int bitArraySize, int hashFunctionCount, int elementCount,
                                     int bitsSet, double memoryUtilization, double estimatedFPR) {
            this.bitArraySize = bitArraySize;
            this.hashFunctionCount = hashFunctionCount;
            this.elementCount = elementCount;
            this.bitsSet = bitsSet;
            this.memoryUtilization = memoryUtilization;
            this.estimatedFPR = estimatedFPR;
        }

        public int getBitArraySize() { return bitArraySize; }
        public int getHashFunctionCount() { return hashFunctionCount; }
        public int getElementCount() { return elementCount; }
        public int getBitsSet() { return bitsSet; }
        public double getMemoryUtilization() { return memoryUtilization; }
        public double getEstimatedFPR() { return estimatedFPR; }

        public boolean isOptimal() {
            // Optimal when memory utilization is around 50-70%
            return memoryUtilization >= 45.0 && memoryUtilization <= 75.0 && estimatedFPR <= 0.05;
        }

        public String getPerformanceAssessment() {
            if (estimatedFPR > 0.1) return "HIGH_FPR";
            if (memoryUtilization > 90) return "OVERSATURATED";
            if (memoryUtilization < 10) return "UNDERUTILIZED";
            if (isOptimal()) return "OPTIMAL";
            return "ACCEPTABLE";
        }

        @Override
        public String toString() {
            return String.format("BloomStats{size=%d, hashes=%d, elements=%d, " +
                            "utilization=%.1f%%, FPR=%.3f%%, status=%s}",
                    bitArraySize, hashFunctionCount, elementCount,
                    memoryUtilization, estimatedFPR * 100, getPerformanceAssessment());
        }
    }

    @Override
    public String toString() {
        return String.format("BloomFilter{size=%d, hashes=%d, elements=%d, utilization=%.1f%%}",
                bitArraySize, hashFunctionCount, elementCount, getMemoryUtilization());
    }
}