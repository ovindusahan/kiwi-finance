package nz.kiwifinance.preferences;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * @param homeLayout the saved home screen, or {@code null} when the person uses the default layout
 * @param emergencyFundReminderSnoozedUntil the reminder stays hidden until this date
 */
@Schema(name = "Preferences")
public record PreferencesResponse(
        @Nullable List<HomeWidget> homeLayout,
        boolean emergencyFundReminders,
        @Nullable LocalDate emergencyFundReminderSnoozedUntil) {}
