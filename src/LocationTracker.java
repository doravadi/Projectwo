import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;


public final class LocationTracker {


    private static final double EARTH_RADIUS_KM = 6371.0;


    private static final double MAX_REASONABLE_SPEED_KMH = 1000.0;
    private static final double CITY_CHANGE_THRESHOLD_KM = 50.0;
    private static final int SUSPICIOUS_WINDOW_MINUTES = 10;
    private static final double LONG_DISTANCE_THRESHOLD_KM = 500.0;

    private final Deque<LocationEntry> locationHistory;
    private final int maxHistorySize;

    public LocationTracker(int maxHistorySize) {
        if (maxHistorySize <= 0) {
            throw new IllegalArgumentException("Max history size must be positive");
        }
        this.maxHistorySize = maxHistorySize;
        this.locationHistory = new ArrayDeque<>();
    }

    public static LocationTracker createDefault() {
        return new LocationTracker(1000);
    }


    public LocationAnalysis recordLocation(String transactionId, String cardNumber,
                                           double latitude, double longitude,
                                           String cityName, String countryCode,
                                           LocalDateTime timestamp) {

        Objects.requireNonNull(transactionId, "Transaction ID cannot be null");
        Objects.requireNonNull(cardNumber, "Card number cannot be null");
        Objects.requireNonNull(cityName, "City name cannot be null");
        Objects.requireNonNull(countryCode, "Country code cannot be null");
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");

        validateCoordinates(latitude, longitude);

        LocationEntry newEntry = new LocationEntry(transactionId, cardNumber, latitude, longitude,
                cityName, countryCode, timestamp);


        LocationAnalysis analysis = analyzeLocation(newEntry, cardNumber);


        locationHistory.addLast(newEntry);


        while (locationHistory.size() > maxHistorySize) {
            locationHistory.removeFirst();
        }

        return analysis;
    }


    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {

        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);


        double deltaLat = lat2Rad - lat1Rad;
        double deltaLon = lon2Rad - lon1Rad;

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }


    public static double calculateSpeed(LocationEntry from, LocationEntry to) {
        if (from.getTimestamp().isAfter(to.getTimestamp())) {
            throw new IllegalArgumentException("From timestamp must be before to timestamp");
        }

        double distance = calculateDistance(from.getLatitude(), from.getLongitude(),
                to.getLatitude(), to.getLongitude());

        long minutes = ChronoUnit.MINUTES.between(from.getTimestamp(), to.getTimestamp());
        if (minutes == 0) {
            return Double.MAX_VALUE;
        }

        double hours = minutes / 60.0;
        return distance / hours;
    }


    public List<LocationEntry> getLocationHistory(String cardNumber, int maxEntries) {
        Objects.requireNonNull(cardNumber, "Card number cannot be null");

        return locationHistory.stream()
                .filter(entry -> entry.getCardNumber().equals(cardNumber))
                .sorted(Comparator.comparing(LocationEntry::getTimestamp).reversed())
                .limit(maxEntries)
                .toList();
    }


    public int getCityCountInWindow(String cardNumber, LocalDateTime windowStart, LocalDateTime windowEnd) {
        Objects.requireNonNull(cardNumber, "Card number cannot be null");
        Objects.requireNonNull(windowStart, "Window start cannot be null");
        Objects.requireNonNull(windowEnd, "Window end cannot be null");

        Set<String> cities = new HashSet<>();

        locationHistory.stream()
                .filter(entry -> entry.getCardNumber().equals(cardNumber))
                .filter(entry -> !entry.getTimestamp().isBefore(windowStart) &&
                        !entry.getTimestamp().isAfter(windowEnd))
                .forEach(entry -> cities.add(entry.getCityName()));

        return cities.size();
    }


    private LocationAnalysis analyzeLocation(LocationEntry newEntry, String cardNumber) {
        List<LocationEntry> recentLocations = getLocationHistory(cardNumber, 10);

        if (recentLocations.isEmpty()) {
            return LocationAnalysis.normal(newEntry);
        }

        LocationEntry previousEntry = recentLocations.get(0);


        double distance = calculateDistance(previousEntry.getLatitude(), previousEntry.getLongitude(),
                newEntry.getLatitude(), newEntry.getLongitude());

        long minutesBetween = ChronoUnit.MINUTES.between(previousEntry.getTimestamp(),
                newEntry.getTimestamp());

        double speed = minutesBetween > 0 ? (distance / (minutesBetween / 60.0)) : Double.MAX_VALUE;


        Set<LocationAnomaly> anomalies = new HashSet<>();


        if (speed > MAX_REASONABLE_SPEED_KMH) {
            anomalies.add(LocationAnomaly.IMPOSSIBLE_SPEED);
        }


        LocalDateTime tenMinutesAgo = newEntry.getTimestamp().minusMinutes(SUSPICIOUS_WINDOW_MINUTES);
        int cityCount = getCityCountInWindow(cardNumber, tenMinutesAgo, newEntry.getTimestamp());
        if (cityCount >= 3) {
            anomalies.add(LocationAnomaly.RAPID_CITY_CHANGES);
        }


        LocalDateTime thirtyMinutesAgo = newEntry.getTimestamp().minusMinutes(30);
        double maxDistanceIn30Min = calculateMaxDistanceInWindow(cardNumber, thirtyMinutesAgo,
                newEntry.getTimestamp());
        if (maxDistanceIn30Min > LONG_DISTANCE_THRESHOLD_KM) {
            anomalies.add(LocationAnomaly.LONG_DISTANCE_SHORT_TIME);
        }


        if (!previousEntry.getCountryCode().equals(newEntry.getCountryCode())) {
            anomalies.add(LocationAnomaly.INTERNATIONAL_TRAVEL);
        }


        long sameLocationCount = recentLocations.stream()
                .filter(entry -> isSameLocation(entry, newEntry))
                .count();
        if (sameLocationCount >= 5) {
            anomalies.add(LocationAnomaly.REPEATED_LOCATION);
        }

        return new LocationAnalysis(newEntry, previousEntry, distance, speed,
                minutesBetween, anomalies);
    }

    private double calculateMaxDistanceInWindow(String cardNumber, LocalDateTime windowStart,
                                                LocalDateTime windowEnd) {
        List<LocationEntry> windowEntries = locationHistory.stream()
                .filter(entry -> entry.getCardNumber().equals(cardNumber))
                .filter(entry -> !entry.getTimestamp().isBefore(windowStart) &&
                        !entry.getTimestamp().isAfter(windowEnd))
                .sorted(Comparator.comparing(LocationEntry::getTimestamp))
                .toList();

        if (windowEntries.size() < 2) {
            return 0.0;
        }

        double maxDistance = 0.0;
        for (int i = 0; i < windowEntries.size() - 1; i++) {
            LocationEntry from = windowEntries.get(i);
            LocationEntry to = windowEntries.get(i + 1);
            double distance = calculateDistance(from.getLatitude(), from.getLongitude(),
                    to.getLatitude(), to.getLongitude());
            maxDistance = Math.max(maxDistance, distance);
        }

        return maxDistance;
    }

    private boolean isSameLocation(LocationEntry entry1, LocationEntry entry2) {
        double distance = calculateDistance(entry1.getLatitude(), entry1.getLongitude(),
                entry2.getLatitude(), entry2.getLongitude());
        return distance < 1.0;
    }

    private void validateCoordinates(double latitude, double longitude) {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90 degrees");
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180 degrees");
        }
    }


    public TrackerStatistics getStatistics() {
        Map<String, Integer> cardLocationCounts = new HashMap<>();
        Map<String, Integer> countryCounts = new HashMap<>();
        Set<String> uniqueCities = new HashSet<>();

        for (LocationEntry entry : locationHistory) {
            cardLocationCounts.merge(entry.getCardNumber(), 1, Integer::sum);
            countryCounts.merge(entry.getCountryCode(), 1, Integer::sum);
            uniqueCities.add(entry.getCityName());
        }

        return new TrackerStatistics(locationHistory.size(), cardLocationCounts.size(),
                uniqueCities.size(), countryCounts);
    }


    public int getHistorySize() {
        return locationHistory.size();
    }

    public int getMaxHistorySize() {
        return maxHistorySize;
    }


    public enum LocationAnomaly {
        IMPOSSIBLE_SPEED("İmkansız seyahat hızı"),
        RAPID_CITY_CHANGES("Hızlı şehir değişimleri"),
        LONG_DISTANCE_SHORT_TIME("Kısa sürede uzun mesafe"),
        INTERNATIONAL_TRAVEL("Uluslararası seyahat"),
        REPEATED_LOCATION("Tekrarlanan lokasyon");

        private final String description;

        LocationAnomaly(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public static final class LocationEntry {
        private final String transactionId;
        private final String cardNumber;
        private final double latitude;
        private final double longitude;
        private final String cityName;
        private final String countryCode;
        private final LocalDateTime timestamp;

        public LocationEntry(String transactionId, String cardNumber, double latitude, double longitude,
                             String cityName, String countryCode, LocalDateTime timestamp) {
            this.transactionId = transactionId;
            this.cardNumber = cardNumber;
            this.latitude = latitude;
            this.longitude = longitude;
            this.cityName = cityName;
            this.countryCode = countryCode;
            this.timestamp = timestamp;
        }


        public String getTransactionId() {
            return transactionId;
        }

        public String getCardNumber() {
            return cardNumber;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public String getCityName() {
            return cityName;
        }

        public String getCountryCode() {
            return countryCode;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public String getLocation() {
            return cityName + ", " + countryCode;
        }

        @Override
        public String toString() {
            return String.format("LocationEntry{id=%s, location=%s, coords=(%.4f,%.4f), time=%s}",
                    transactionId, getLocation(), latitude, longitude, timestamp);
        }
    }

    public static final class LocationAnalysis {
        private final LocationEntry currentLocation;
        private final LocationEntry previousLocation;
        private final double distanceKm;
        private final double speedKmh;
        private final long timeDifferenceMinutes;
        private final Set<LocationAnomaly> anomalies;

        public LocationAnalysis(LocationEntry currentLocation, LocationEntry previousLocation,
                                double distanceKm, double speedKmh, long timeDifferenceMinutes,
                                Set<LocationAnomaly> anomalies) {
            this.currentLocation = currentLocation;
            this.previousLocation = previousLocation;
            this.distanceKm = distanceKm;
            this.speedKmh = speedKmh;
            this.timeDifferenceMinutes = timeDifferenceMinutes;
            this.anomalies = EnumSet.copyOf(Objects.requireNonNull(anomalies, "Anomalies cannot be null"));
        }

        public static LocationAnalysis normal(LocationEntry location) {
            return new LocationAnalysis(location, null, 0.0, 0.0, 0L, EnumSet.noneOf(LocationAnomaly.class));
        }


        public LocationEntry getCurrentLocation() {
            return currentLocation;
        }

        public LocationEntry getPreviousLocation() {
            return previousLocation;
        }

        public double getDistanceKm() {
            return distanceKm;
        }

        public double getSpeedKmh() {
            return speedKmh;
        }

        public long getTimeDifferenceMinutes() {
            return timeDifferenceMinutes;
        }

        public Set<LocationAnomaly> getAnomalies() {
            return EnumSet.copyOf(anomalies);
        }

        public boolean hasSuspiciousActivity() {
            return !anomalies.isEmpty();
        }

        public boolean isHighRisk() {
            return anomalies.contains(LocationAnomaly.IMPOSSIBLE_SPEED) ||
                    anomalies.contains(LocationAnomaly.RAPID_CITY_CHANGES) ||
                    anomalies.size() >= 3;
        }

        public int getRiskScore() {
            int score = 0;
            for (LocationAnomaly anomaly : anomalies) {
                score += switch (anomaly) {
                    case IMPOSSIBLE_SPEED -> 40;
                    case RAPID_CITY_CHANGES -> 35;
                    case LONG_DISTANCE_SHORT_TIME -> 30;
                    case INTERNATIONAL_TRAVEL -> 15;
                    case REPEATED_LOCATION -> 10;
                };
            }
            return Math.min(score, 100);
        }

        @Override
        public String toString() {
            return String.format("LocationAnalysis{distance=%.1fkm, speed=%.1fkmh, anomalies=%d, risk=%d}",
                    distanceKm, speedKmh, anomalies.size(), getRiskScore());
        }
    }

    public static final class TrackerStatistics {
        private final int totalEntries;
        private final int uniqueCards;
        private final int uniqueCities;
        private final Map<String, Integer> countryCounts;

        public TrackerStatistics(int totalEntries, int uniqueCards, int uniqueCities,
                                 Map<String, Integer> countryCounts) {
            this.totalEntries = totalEntries;
            this.uniqueCards = uniqueCards;
            this.uniqueCities = uniqueCities;
            this.countryCounts = new HashMap<>(countryCounts);
        }

        public int getTotalEntries() {
            return totalEntries;
        }

        public int getUniqueCards() {
            return uniqueCards;
        }

        public int getUniqueCities() {
            return uniqueCities;
        }

        public Map<String, Integer> getCountryCounts() {
            return new HashMap<>(countryCounts);
        }

        @Override
        public String toString() {
            return String.format("TrackerStats{entries=%d, cards=%d, cities=%d, countries=%d}",
                    totalEntries, uniqueCards, uniqueCities, countryCounts.size());
        }
    }

    @Override
    public String toString() {
        return String.format("LocationTracker{entries=%d/%d}", locationHistory.size(), maxHistorySize);
    }
}