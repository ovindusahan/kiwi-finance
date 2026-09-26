package nz.kiwifinance.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import nz.kiwifinance.common.persistence.AuditableEntity;
import nz.kiwifinance.engine.nzrules.TaxCode;
import nz.kiwifinance.engine.time.PayFrequency;

@Entity
@Table(name = "user_profiles")
@Getter
public class UserProfile extends AuditableEntity {

    public static final BigDecimal DEFAULT_SAVINGS_INTEREST_RATE = new BigDecimal("0.025");
    public static final BigDecimal DEFAULT_TARGET_SAVINGS_RATE = new BigDecimal("0.15");

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Region region;

    @Column(name = "household_size", nullable = false)
    private int householdSize = 1;

    @Column(nullable = false)
    private int dependants;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false)
    private EmploymentType employmentType = EmploymentType.EMPLOYEE;

    @Enumerated(EnumType.STRING)
    @Column(name = "housing_type")
    private HousingType housingType;

    @Column(name = "single_income_household", nullable = false)
    private boolean singleIncomeHousehold;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_code", nullable = false)
    private TaxCode taxCode = TaxCode.M;

    @Column(name = "has_student_loan", nullable = false)
    private boolean studentLoan;

    @Column(name = "kiwisaver_member", nullable = false)
    private boolean kiwiSaverMember;

    @Column(name = "kiwisaver_rate", precision = 5, scale = 4)
    private BigDecimal kiwiSaverRate;

    @Column(name = "kiwisaver_joined_on")
    private LocalDate kiwiSaverJoinedOn;

    @Column(name = "kiwisaver_balance_cents")
    private Long kiwiSaverBalanceCents;

    @Column(name = "first_home_buyer", nullable = false)
    private boolean firstHomeBuyer;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_frequency", nullable = false)
    private PayFrequency payFrequency = PayFrequency.FORTNIGHTLY;

    @Column(name = "savings_interest_rate", nullable = false, precision = 6, scale = 5)
    private BigDecimal savingsInterestRate = DEFAULT_SAVINGS_INTEREST_RATE;

    @Column(name = "target_savings_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal targetSavingsRate = DEFAULT_TARGET_SAVINGS_RATE;

    @Column(name = "onboarded_at")
    private Instant onboardedAt;

    protected UserProfile() {}

    public UserProfile(UUID userId) {
        this.userId = userId;
    }

    public void update(ProfileRequest request, Instant now) {
        dateOfBirth = request.dateOfBirth();
        region = request.region();
        householdSize = request.householdSize();
        dependants = request.dependants();
        employmentType = request.employmentType();
        housingType = request.housingType();
        singleIncomeHousehold = request.singleIncomeHousehold();
        taxCode = request.taxCode();
        studentLoan = request.hasStudentLoan();
        kiwiSaverMember = request.kiwiSaverMember();
        kiwiSaverRate = request.kiwiSaverMember() ? request.kiwiSaverRate() : null;
        kiwiSaverJoinedOn = request.kiwiSaverMember() ? request.kiwiSaverJoinedOn() : null;
        kiwiSaverBalanceCents = request.kiwiSaverMember() ? request.kiwiSaverBalanceCents() : null;
        firstHomeBuyer = request.firstHomeBuyer();
        payFrequency = request.payFrequency();
        savingsInterestRate = request.savingsInterestRate();
        targetSavingsRate = request.targetSavingsRate();
        if (onboardedAt == null) {
            onboardedAt = now;
        }
    }

    public boolean hasStudentLoan() {
        return studentLoan;
    }
}
