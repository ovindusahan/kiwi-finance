package nz.kiwifinance.preferences;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import nz.kiwifinance.common.persistence.AuditableEntity;

/**
 * Choices about how the app looks and behaves for a person, as opposed to facts about their money.
 */
@Entity
@Table(name = "user_preferences")
@Getter
@Setter(AccessLevel.PACKAGE)
public class UserPreferences extends AuditableEntity {

    @Setter(AccessLevel.NONE)
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** The home screen widgets as JSON, or {@code null} for the default layout. */
    @Column(name = "home_layout")
    private String homeLayout;

    @Column(name = "emergency_fund_reminders", nullable = false)
    private boolean emergencyFundReminders = true;

    @Column(name = "emergency_fund_reminder_snoozed_until")
    private LocalDate emergencyFundReminderSnoozedUntil;

    protected UserPreferences() {}

    UserPreferences(UUID userId) {
        this.userId = userId;
    }
}
