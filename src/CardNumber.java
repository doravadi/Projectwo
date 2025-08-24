
import java.util.Objects;
import java.io.Serializable;

public final class CardNumber implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String number;
    private final long bin;

    private CardNumber(String number) {
        this.number = validateAndClean(number);
        this.bin = extractBin(this.number);
    }

    public static CardNumber of(String number) {
        return new CardNumber(number);
    }

    private String validateAndClean(String number) {
        if (number == null || number.trim().isEmpty()) {
            throw new IllegalArgumentException("Card number cannot be null or empty");
        }

        
        String cleaned = number.replaceAll("[\\s-]", "");

        
        if (!cleaned.matches("\\d+")) {
            throw new IllegalArgumentException("Card number must contain only digits");
        }

        
        if (cleaned.length() < 13 || cleaned.length() > 19) {
            throw new IllegalArgumentException(
                    String.format("Card number must be 13-19 digits, got %d", cleaned.length()));
        }

        
        if (!isValidLuhn(cleaned)) {
            throw new IllegalArgumentException("Invalid card number (Luhn check failed)");
        }

        return cleaned;
    }

    private long extractBin(String cardNumber) {
        
        int binLength = Math.min(8, cardNumber.length() - 4); 
        binLength = Math.max(6, binLength); 

        return Long.parseLong(cardNumber.substring(0, binLength));
    }

    
    private boolean isValidLuhn(String number) {
        int sum = 0;
        boolean isEven = false;

        
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(number.charAt(i));

            if (isEven) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }

            sum += digit;
            isEven = !isEven;
        }

        return sum % 10 == 0;
    }

    public String getNumber() {
        return number;
    }

    public long getBin() {
        return bin;
    }

    public String getMaskedNumber() {
        if (number.length() < 10) {
            return number; 
        }

        return number.substring(0, 6) +
                "*".repeat(number.length() - 10) +
                number.substring(number.length() - 4);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        CardNumber that = (CardNumber) obj;
        return Objects.equals(number, that.number);
    }

    @Override
    public int hashCode() {
        return Objects.hash(number);
    }

    @Override
    public String toString() {
        return getMaskedNumber();
    }
}