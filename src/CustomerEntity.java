import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Objects;
import java.util.regex.Pattern;


public final class CustomerEntity {

    private final String customerId;
    private final String nationalId; // TC Kimlik No
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phoneNumber;
    private final LocalDate dateOfBirth;
    private final Gender gender;
    private final CustomerType customerType;
    private final CustomerStatus status;
    private final CustomerSegment segment;
    private final String address;
    private final String city;
    private final String country;
    private final String postalCode;
    private final String occupation;
    private final String employerName;
    private final LocalDate customerSince;
    private final String branchCode;
    private final String relationshipManager;
    private final String riskProfile; // LOW, MEDIUM, HIGH
    private final String kycStatus; // KYC verification status
    private final LocalDateTime lastLoginDate;
    private final String preferredLanguage;
    private final boolean marketingConsent;
    private final String notes;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+?[0-9\\s\\-\\(\\)]{10,15}$"
    );
    private static final Pattern NATIONAL_ID_PATTERN = Pattern.compile("^[0-9]{11}$"); // Turkish TC

    private CustomerEntity(Builder builder) {

        this.customerId = validateRequired(builder.customerId, "Customer ID");
        this.nationalId = validateNationalId(builder.nationalId);
        this.firstName = validateRequired(builder.firstName, "First name");
        this.lastName = validateRequired(builder.lastName, "Last name");
        this.email = validateEmail(builder.email);
        this.phoneNumber = validatePhoneNumber(builder.phoneNumber);
        this.dateOfBirth = validateDateOfBirth(builder.dateOfBirth);
        this.customerType = Objects.requireNonNull(builder.customerType, "Customer type cannot be null");
        this.status = Objects.requireNonNull(builder.status, "Customer status cannot be null");
        this.segment = Objects.requireNonNull(builder.segment, "Customer segment cannot be null");
        this.country = validateRequired(builder.country, "Country");
        this.customerSince = Objects.requireNonNull(builder.customerSince, "Customer since date cannot be null");

        this.gender = builder.gender;
        this.address = builder.address;
        this.city = builder.city;
        this.postalCode = builder.postalCode;
        this.occupation = builder.occupation;
        this.employerName = builder.employerName;
        this.branchCode = builder.branchCode;
        this.relationshipManager = builder.relationshipManager;
        this.riskProfile = builder.riskProfile != null ? builder.riskProfile : "MEDIUM";
        this.kycStatus = builder.kycStatus != null ? builder.kycStatus : "PENDING";
        this.lastLoginDate = builder.lastLoginDate;
        this.preferredLanguage = builder.preferredLanguage != null ? builder.preferredLanguage : "TR";
        this.marketingConsent = builder.marketingConsent;
        this.notes = builder.notes;
        this.createdAt = builder.createdAt != null ? builder.createdAt : LocalDateTime.now();
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : this.createdAt;

        validateBusinessRules();
    }

    private String validateRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
        return value.trim();
    }

    private String validateNationalId(String nationalId) {
        if (nationalId == null || !NATIONAL_ID_PATTERN.matcher(nationalId).matches()) {
            throw new IllegalArgumentException("Invalid national ID format");
        }

        if (!isValidTurkishNationalId(nationalId)) {
            throw new IllegalArgumentException("Invalid Turkish national ID");
        }
        return nationalId;
    }

    private String validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
        return email.toLowerCase().trim();
    }

    private String validatePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || !PHONE_PATTERN.matcher(phoneNumber).matches()) {
            throw new IllegalArgumentException("Invalid phone number format");
        }
        return phoneNumber.replaceAll("\\s+", ""); // Remove spaces
    }

    private LocalDate validateDateOfBirth(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            throw new IllegalArgumentException("Date of birth cannot be null");
        }
        if (dateOfBirth.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Date of birth cannot be in future");
        }

        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();
        if (age < 18) {
            throw new IllegalArgumentException("Customer must be at least 18 years old");
        }
        if (age > 120) {
            throw new IllegalArgumentException("Invalid age: customer cannot be over 120 years old");
        }

        return dateOfBirth;
    }

    private void validateBusinessRules() {

        if (customerSince.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Customer since date cannot be in future");
        }

        int age = getAge();
        if (customerType == CustomerType.CORPORATE && age < 25) {

        }

        if (customerType == CustomerType.INDIVIDUAL && segment == CustomerSegment.CORPORATE) {
            throw new IllegalArgumentException("Individual customer cannot have corporate segment");
        }

        if (customerType == CustomerType.CORPORATE && segment == CustomerSegment.STUDENT) {
            throw new IllegalArgumentException("Corporate customer cannot have student segment");
        }
    }

    private boolean isValidTurkishNationalId(String nationalId) {

        if (nationalId.length() != 11) return false;
        if (nationalId.charAt(0) == '0') return false;

        try {
            int[] digits = nationalId.chars().map(c -> c - '0').toArray();

            int sumOdd = digits[0] + digits[2] + digits[4] + digits[6] + digits[8];
            int sumEven = digits[1] + digits[3] + digits[5] + digits[7];

            int checkDigit1 = ((sumOdd * 7) - sumEven) % 10;
            if (checkDigit1 != digits[9]) return false;

            int sumAll = 0;
            for (int i = 0; i < 10; i++) {
                sumAll += digits[i];
            }

            int checkDigit2 = sumAll % 10;
            return checkDigit2 == digits[10];

        } catch (Exception e) {
            return false;
        }
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getNationalId() {
        return nationalId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public CustomerType getCustomerType() {
        return customerType;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public CustomerSegment getSegment() {
        return segment;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getOccupation() {
        return occupation;
    }

    public String getEmployerName() {
        return employerName;
    }

    public LocalDate getCustomerSince() {
        return customerSince;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public String getRelationshipManager() {
        return relationshipManager;
    }

    public String getRiskProfile() {
        return riskProfile;
    }

    public String getKycStatus() {
        return kycStatus;
    }

    public LocalDateTime getLastLoginDate() {
        return lastLoginDate;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public boolean isMarketingConsent() {
        return marketingConsent;
    }

    public String getNotes() {
        return notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return status == CustomerStatus.ACTIVE;
    }

    public boolean isSuspended() {
        return status == CustomerStatus.SUSPENDED || status == CustomerStatus.BLACKLISTED;
    }

    public boolean isIndividual() {
        return customerType == CustomerType.INDIVIDUAL;
    }

    public boolean isCorporate() {
        return customerType == CustomerType.CORPORATE;
    }

    public boolean isVip() {
        return segment == CustomerSegment.VIP || segment == CustomerSegment.PRIVATE_BANKING;
    }

    public boolean isHighRisk() {
        return "HIGH".equals(riskProfile);
    }

    public boolean isKycCompleted() {
        return "COMPLETED".equals(kycStatus);
    }

    public int getAge() {
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    public int getCustomerTenure() {
        return Period.between(customerSince, LocalDate.now()).getYears();
    }

    public boolean isNewCustomer(int monthsThreshold) {
        return Period.between(customerSince, LocalDate.now()).toTotalMonths() <= monthsThreshold;
    }

    public boolean isLongTermCustomer(int yearsThreshold) {
        return getCustomerTenure() >= yearsThreshold;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getInitials() {
        return String.valueOf(firstName.charAt(0)) + String.valueOf(lastName.charAt(0));
    }

    public String getMaskedNationalId() {
        if (nationalId == null || nationalId.length() < 4) {
            return "***********";
        }
        return "*".repeat(nationalId.length() - 4) + nationalId.substring(nationalId.length() - 4);
    }

    public String getMaskedEmail() {
        if (email == null) return null;

        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return email;

        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);

        if (localPart.length() <= 2) {
            return localPart.charAt(0) + "*" + domainPart;
        }

        return localPart.charAt(0) + "*".repeat(localPart.length() - 2) +
                localPart.charAt(localPart.length() - 1) + domainPart;
    }

    public String getMaskedPhoneNumber() {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "***********";
        }
        return "*".repeat(phoneNumber.length() - 4) + phoneNumber.substring(phoneNumber.length() - 4);
    }

    public boolean hasRecentActivity(int days) {
        if (lastLoginDate == null) return false;
        return lastLoginDate.isAfter(LocalDateTime.now().minusDays(days));
    }

    public String getAgeGroup() {
        int age = getAge();
        if (age < 25) return "18-24";
        if (age < 35) return "25-34";
        if (age < 45) return "35-44";
        if (age < 55) return "45-54";
        if (age < 65) return "55-64";
        return "65+";
    }

    public static CustomerEntity newIndividualCustomer(String customerId, String nationalId,
                                                       String firstName, String lastName,
                                                       String email, String phoneNumber,
                                                       LocalDate dateOfBirth) {
        return builder()
                .customerId(customerId)
                .nationalId(nationalId)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phoneNumber(phoneNumber)
                .dateOfBirth(dateOfBirth)
                .customerType(CustomerType.INDIVIDUAL)
                .status(CustomerStatus.ACTIVE)
                .segment(CustomerSegment.STANDARD)
                .country("TR")
                .customerSince(LocalDate.now())
                .preferredLanguage("TR")
                .build();
    }

    public static CustomerEntity newCorporateCustomer(String customerId, String nationalId,
                                                      String companyName, String contactPerson,
                                                      String email, String phoneNumber) {
        return builder()
                .customerId(customerId)
                .nationalId(nationalId)
                .firstName(companyName)
                .lastName(contactPerson)
                .email(email)
                .phoneNumber(phoneNumber)
                .dateOfBirth(LocalDate.of(1990, 1, 1)) // Dummy date for corporate
                .customerType(CustomerType.CORPORATE)
                .status(CustomerStatus.ACTIVE)
                .segment(CustomerSegment.CORPORATE)
                .country("TR")
                .customerSince(LocalDate.now())
                .preferredLanguage("TR")
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String customerId;
        private String nationalId;
        private String firstName;
        private String lastName;
        private String email;
        private String phoneNumber;
        private LocalDate dateOfBirth;
        private Gender gender;
        private CustomerType customerType;
        private CustomerStatus status;
        private CustomerSegment segment;
        private String address;
        private String city;
        private String country;
        private String postalCode;
        private String occupation;
        private String employerName;
        private LocalDate customerSince;
        private String branchCode;
        private String relationshipManager;
        private String riskProfile;
        private String kycStatus;
        private LocalDateTime lastLoginDate;
        private String preferredLanguage;
        private boolean marketingConsent;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        private Builder() {
        }

        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder nationalId(String nationalId) {
            this.nationalId = nationalId;
            return this;
        }

        public Builder firstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public Builder lastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder phoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public Builder dateOfBirth(LocalDate dateOfBirth) {
            this.dateOfBirth = dateOfBirth;
            return this;
        }

        public Builder gender(Gender gender) {
            this.gender = gender;
            return this;
        }

        public Builder customerType(CustomerType customerType) {
            this.customerType = customerType;
            return this;
        }

        public Builder status(CustomerStatus status) {
            this.status = status;
            return this;
        }

        public Builder segment(CustomerSegment segment) {
            this.segment = segment;
            return this;
        }

        public Builder address(String address) {
            this.address = address;
            return this;
        }

        public Builder city(String city) {
            this.city = city;
            return this;
        }

        public Builder country(String country) {
            this.country = country;
            return this;
        }

        public Builder postalCode(String postalCode) {
            this.postalCode = postalCode;
            return this;
        }

        public Builder occupation(String occupation) {
            this.occupation = occupation;
            return this;
        }

        public Builder employerName(String employerName) {
            this.employerName = employerName;
            return this;
        }

        public Builder customerSince(LocalDate customerSince) {
            this.customerSince = customerSince;
            return this;
        }

        public Builder branchCode(String branchCode) {
            this.branchCode = branchCode;
            return this;
        }

        public Builder relationshipManager(String relationshipManager) {
            this.relationshipManager = relationshipManager;
            return this;
        }

        public Builder riskProfile(String riskProfile) {
            this.riskProfile = riskProfile;
            return this;
        }

        public Builder kycStatus(String kycStatus) {
            this.kycStatus = kycStatus;
            return this;
        }

        public Builder lastLoginDate(LocalDateTime lastLoginDate) {
            this.lastLoginDate = lastLoginDate;
            return this;
        }

        public Builder preferredLanguage(String preferredLanguage) {
            this.preferredLanguage = preferredLanguage;
            return this;
        }

        public Builder marketingConsent(boolean marketingConsent) {
            this.marketingConsent = marketingConsent;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public CustomerEntity build() {
            return new CustomerEntity(this);
        }
    }

    public enum Gender {
        MALE("Male"),
        FEMALE("Female"),
        OTHER("Other"),
        PREFER_NOT_TO_SAY("Prefer not to say");

        private final String description;

        Gender(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum CustomerType {
        INDIVIDUAL("Individual Customer"),
        CORPORATE("Corporate Customer"),
        SMALL_BUSINESS("Small Business Customer"),
        GOVERNMENT("Government Entity"),
        NON_PROFIT("Non-Profit Organization");

        private final String description;

        CustomerType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public boolean isBusinessCustomer() {
            return this == CORPORATE || this == SMALL_BUSINESS ||
                    this == GOVERNMENT || this == NON_PROFIT;
        }
    }

    public enum CustomerStatus {
        ACTIVE("Active"),
        INACTIVE("Inactive"),
        SUSPENDED("Suspended"),
        BLACKLISTED("Blacklisted"),
        PENDING_VERIFICATION("Pending Verification"),
        CLOSED("Closed"),
        DORMANT("Dormant");

        private final String description;

        CustomerStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public boolean canPerformTransactions() {
            return this == ACTIVE;
        }

        public boolean requiresAction() {
            return this == PENDING_VERIFICATION || this == SUSPENDED;
        }

        public boolean isFinalStatus() {
            return this == CLOSED || this == BLACKLISTED;
        }
    }

    public enum CustomerSegment {
        STANDARD("Standard"),
        PREMIUM("Premium"),
        VIP("VIP"),
        PRIVATE_BANKING("Private Banking"),
        STUDENT("Student"),
        SENIOR("Senior"),
        CORPORATE("Corporate"),
        SME("Small & Medium Enterprise"),
        STARTUP("Startup");

        private final String description;

        CustomerSegment(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public boolean isHighValue() {
            return this == VIP || this == PRIVATE_BANKING || this == PREMIUM;
        }

        public boolean hasSpecialTreatment() {
            return isHighValue() || this == STUDENT || this == SENIOR;
        }
    }

    public CustomerEntity withStatus(CustomerStatus newStatus) {
        return new Builder()
                .customerId(this.customerId)
                .nationalId(this.nationalId)
                .firstName(this.firstName)
                .lastName(this.lastName)
                .email(this.email)
                .phoneNumber(this.phoneNumber)
                .dateOfBirth(this.dateOfBirth)
                .gender(this.gender)
                .customerType(this.customerType)
                .status(newStatus)
                .segment(this.segment)
                .address(this.address)
                .city(this.city)
                .country(this.country)
                .postalCode(this.postalCode)
                .occupation(this.occupation)
                .employerName(this.employerName)
                .customerSince(this.customerSince)
                .branchCode(this.branchCode)
                .relationshipManager(this.relationshipManager)
                .riskProfile(this.riskProfile)
                .kycStatus(this.kycStatus)
                .lastLoginDate(this.lastLoginDate)
                .preferredLanguage(this.preferredLanguage)
                .marketingConsent(this.marketingConsent)
                .notes(this.notes)
                .createdAt(this.createdAt)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public CustomerEntity withLastLogin(LocalDateTime loginTime) {
        return new Builder()
                .customerId(this.customerId)
                .nationalId(this.nationalId)
                .firstName(this.firstName)
                .lastName(this.lastName)
                .email(this.email)
                .phoneNumber(this.phoneNumber)
                .dateOfBirth(this.dateOfBirth)
                .gender(this.gender)
                .customerType(this.customerType)
                .status(this.status)
                .segment(this.segment)
                .address(this.address)
                .city(this.city)
                .country(this.country)
                .postalCode(this.postalCode)
                .occupation(this.occupation)
                .employerName(this.employerName)
                .customerSince(this.customerSince)
                .branchCode(this.branchCode)
                .relationshipManager(this.relationshipManager)
                .riskProfile(this.riskProfile)
                .kycStatus(this.kycStatus)
                .lastLoginDate(loginTime)
                .preferredLanguage(this.preferredLanguage)
                .marketingConsent(this.marketingConsent)
                .notes(this.notes)
                .createdAt(this.createdAt)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public CustomerEntity withSegment(CustomerSegment newSegment) {
        return new Builder()
                .customerId(this.customerId)
                .nationalId(this.nationalId)
                .firstName(this.firstName)
                .lastName(this.lastName)
                .email(this.email)
                .phoneNumber(this.phoneNumber)
                .dateOfBirth(this.dateOfBirth)
                .gender(this.gender)
                .customerType(this.customerType)
                .status(this.status)
                .segment(newSegment)
                .address(this.address)
                .city(this.city)
                .country(this.country)
                .postalCode(this.postalCode)
                .occupation(this.occupation)
                .employerName(this.employerName)
                .customerSince(this.customerSince)
                .branchCode(this.branchCode)
                .relationshipManager(this.relationshipManager)
                .riskProfile(this.riskProfile)
                .kycStatus(this.kycStatus)
                .lastLoginDate(this.lastLoginDate)
                .preferredLanguage(this.preferredLanguage)
                .marketingConsent(this.marketingConsent)
                .notes(this.notes)
                .createdAt(this.createdAt)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CustomerEntity that = (CustomerEntity) o;
        return Objects.equals(customerId, that.customerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId);
    }

    @Override
    public String toString() {
        return "CustomerEntity{" +
                "customerId='" + customerId + '\'' +
                ", nationalId='" + getMaskedNationalId() + '\'' +
                ", fullName='" + getFullName() + '\'' +
                ", email='" + getMaskedEmail() + '\'' +
                ", phone='" + getMaskedPhoneNumber() + '\'' +
                ", type=" + customerType +
                ", status=" + status +
                ", segment=" + segment +
                ", age=" + getAge() +
                '}';
    }


    public String toAuditString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CUST[").append(customerId).append("] ");
        sb.append(getFullName()).append(" ");
        sb.append(customerType).append(" ");
        sb.append(status).append(" ");
        sb.append(segment).append(" ");
        sb.append("Age:").append(getAge());
        if (isVip()) {
            sb.append(" VIP");
        }
        if (isHighRisk()) {
            sb.append(" HIGH-RISK");
        }
        return sb.toString();
    }


    public String getProfileSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(getFullName()).append("\n");
        sb.append(customerType.getDescription()).append(" - ").append(segment.getDescription()).append("\n");
        sb.append("Age: ").append(getAge()).append(", Customer since: ").append(customerSince).append("\n");
        sb.append("Status: ").append(status.getDescription()).append("\n");
        if (city != null) {
            sb.append("Location: ").append(city).append(", ").append(country).append("\n");
        }
        if (occupation != null) {
            sb.append("Occupation: ").append(occupation).append("\n");
        }
        sb.append("Risk Profile: ").append(riskProfile).append(", KYC: ").append(kycStatus);
        return sb.toString();
    }
}