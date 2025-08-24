
public enum Currency {
    TRY("Turkish Lira", 2),
    USD("US Dollar", 2),
    EUR("Euro", 2),
    GBP("British Pound", 2),
    JPY("Japanese Yen", 0);

    private static final long serialVersionUID = 1L;

    private final String displayName;
    private final int decimalPlaces;

    Currency(String displayName, int decimalPlaces) {
        this.displayName = displayName;
        this.decimalPlaces = decimalPlaces;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDecimalPlaces() {
        return decimalPlaces;
    }
}