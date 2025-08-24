
import java.util.Objects;
import java.io.Serializable;


public final class CardId implements Comparable<CardId>, Serializable {

    private static final long serialVersionUID = 1L;

    private final String hashedPan;    
    private final String tokenId;      
    private final long binNumber;      
    private final String lastFourDigits; 
    private final String expiryMonth;  
    private final String expiryYear;   

    
    private CardId(String hashedPan, String tokenId, long binNumber,
                   String lastFourDigits, String expiryMonth, String expiryYear) {
        this.hashedPan = Objects.requireNonNull(hashedPan, "Hashed PAN cannot be null");
        this.tokenId = tokenId; 
        this.binNumber = binNumber;
        this.lastFourDigits = Objects.requireNonNull(lastFourDigits, "Last four digits cannot be null");
        this.expiryMonth = Objects.requireNonNull(expiryMonth, "Expiry month cannot be null");
        this.expiryYear = Objects.requireNonNull(expiryYear, "Expiry year cannot be null");

        validateCardId();
    }

    
    public static CardId fromCardNumber(CardNumber cardNumber, String expiryMonth, String expiryYear) {
        Objects.requireNonNull(cardNumber, "Card number cannot be null");

        String hashedPan = hashPan(cardNumber.getNumber());
        String lastFour = extractLastFour(cardNumber.getNumber());
        long bin = cardNumber.getBin();

        return new CardId(hashedPan, null, bin, lastFour, expiryMonth, expiryYear);
    }

    
    public static CardId fromTokenizedCard(String tokenId, long binNumber,
                                           String lastFourDigits, String expiryMonth, String expiryYear) {
        
        String hashedPan = hashToken(tokenId);

        return new CardId(hashedPan, tokenId, binNumber, lastFourDigits, expiryMonth, expiryYear);
    }

    
    public static CardId of(String hashedPan, long binNumber, String lastFourDigits,
                            String expiryMonth, String expiryYear) {
        return new CardId(hashedPan, null, binNumber, lastFourDigits, expiryMonth, expiryYear);
    }

    
    public String getHashedPan() { return hashedPan; }
    public String getTokenId() { return tokenId; }
    public long getBinNumber() { return binNumber; }
    public String getLastFourDigits() { return lastFourDigits; }
    public String getExpiryMonth() { return expiryMonth; }
    public String getExpiryYear() { return expiryYear; }

    
    public boolean isTokenized() {
        return tokenId != null && !tokenId.trim().isEmpty();
    }

    public boolean isExpired() {
        return isExpired(java.time.LocalDate.now());
    }

    public boolean isExpired(java.time.LocalDate referenceDate) {
        try {
            int month = Integer.parseInt(expiryMonth);
            int year = 2000 + Integer.parseInt(expiryYear); 

            
            java.time.LocalDate expiryDate = java.time.LocalDate.of(year, month, 1)
                    .plusMonths(1)
                    .minusDays(1);

            return referenceDate.isAfter(expiryDate);

        } catch (Exception e) {
            throw new IllegalStateException("Invalid expiry date format", e);
        }
    }

    public boolean isNearExpiry() {
        return isNearExpiry(java.time.LocalDate.now(), 3); 
    }

    public boolean isNearExpiry(java.time.LocalDate referenceDate, int monthsThreshold) {
        try {
            int month = Integer.parseInt(expiryMonth);
            int year = 2000 + Integer.parseInt(expiryYear);

            java.time.LocalDate expiryDate = java.time.LocalDate.of(year, month, 1)
                    .plusMonths(1)
                    .minusDays(1);

            java.time.LocalDate thresholdDate = referenceDate.plusMonths(monthsThreshold);

            return !expiryDate.isAfter(thresholdDate) && expiryDate.isAfter(referenceDate);

        } catch (Exception e) {
            throw new IllegalStateException("Invalid expiry date format", e);
        }
    }

    
    public String getMaskedDisplay() {
        String binStr = String.valueOf(binNumber);
        if (binStr.length() >= 6) {
            return binStr.substring(0, 6) + "******" + lastFourDigits;
        } else {
            return "****" + lastFourDigits;
        }
    }

    
    public String getDisplayWithExpiry() {
        return getMaskedDisplay() + " (" + expiryMonth + "/" + expiryYear + ")";
    }

    
    public boolean matchesCard(CardId other) {
        if (other == null) return false;

        
        if (this.hashedPan.equals(other.hashedPan)) {
            return true;
        }

        
        if (this.isTokenized() && other.isTokenized()) {
            return this.tokenId.equals(other.tokenId);
        }

        
        return this.binNumber == other.binNumber &&
                this.lastFourDigits.equals(other.lastFourDigits) &&
                this.expiryMonth.equals(other.expiryMonth) &&
                this.expiryYear.equals(other.expiryYear);
    }

    
    public double calculateSimilarity(CardId other) {
        if (other == null) return 0.0;

        double score = 0.0;

        
        if (this.hashedPan.equals(other.hashedPan)) {
            score += 50.0;
        }

        
        if (this.binNumber == other.binNumber) {
            score += 20.0;
        }

        
        if (this.lastFourDigits.equals(other.lastFourDigits)) {
            score += 20.0;
        }

        
        if (this.expiryMonth.equals(other.expiryMonth) &&
                this.expiryYear.equals(other.expiryYear)) {
            score += 10.0;
        }

        
        if (this.isTokenized() && other.isTokenized() &&
                this.tokenId.equals(other.tokenId)) {
            score += 10.0; 
        }

        return Math.min(100.0, score);
    }

    
    private void validateCardId() {
        if (hashedPan.trim().isEmpty()) {
            throw new IllegalArgumentException("Hashed PAN cannot be empty");
        }

        if (lastFourDigits.length() != 4 || !lastFourDigits.matches("\\d{4}")) {
            throw new IllegalArgumentException("Last four digits must be exactly 4 digits: " + lastFourDigits);
        }

        if (expiryMonth.length() != 2 || !expiryMonth.matches("\\d{2}")) {
            throw new IllegalArgumentException("Expiry month must be 2 digits (MM): " + expiryMonth);
        }

        int month = Integer.parseInt(expiryMonth);
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Expiry month must be 01-12: " + expiryMonth);
        }

        if (expiryYear.length() != 2 || !expiryYear.matches("\\d{2}")) {
            throw new IllegalArgumentException("Expiry year must be 2 digits (YY): " + expiryYear);
        }

        if (binNumber < 100000L || binNumber > 999999999999L) {
            throw new IllegalArgumentException("BIN number out of valid range: " + binNumber);
        }
    }

    private static String hashPan(String pan) {
        
        
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest((pan + "SALT_2024").getBytes("UTF-8"));

            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to hash PAN", e);
        }
    }

    private static String hashToken(String token) {
        
        return hashPan(token + "_TOKEN");
    }

    private static String extractLastFour(String pan) {
        if (pan.length() < 4) {
            throw new IllegalArgumentException("PAN too short to extract last 4 digits");
        }
        return pan.substring(pan.length() - 4);
    }

    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        CardId cardId = (CardId) obj;
        return binNumber == cardId.binNumber &&
                Objects.equals(hashedPan, cardId.hashedPan) &&
                Objects.equals(tokenId, cardId.tokenId) &&
                Objects.equals(lastFourDigits, cardId.lastFourDigits) &&
                Objects.equals(expiryMonth, cardId.expiryMonth) &&
                Objects.equals(expiryYear, cardId.expiryYear);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hashedPan, tokenId, binNumber, lastFourDigits, expiryMonth, expiryYear);
    }

    @Override
    public int compareTo(CardId other) {
        
        int binComparison = Long.compare(this.binNumber, other.binNumber);
        if (binComparison != 0) {
            return binComparison;
        }

        
        int panComparison = this.hashedPan.compareTo(other.hashedPan);
        if (panComparison != 0) {
            return panComparison;
        }

        
        int yearComparison = other.expiryYear.compareTo(this.expiryYear);
        if (yearComparison != 0) {
            return yearComparison;
        }

        return other.expiryMonth.compareTo(this.expiryMonth);
    }

    @Override
    public String toString() {
        return String.format("CardId[%s, %s/%s]", getMaskedDisplay(), expiryMonth, expiryYear);
    }
}