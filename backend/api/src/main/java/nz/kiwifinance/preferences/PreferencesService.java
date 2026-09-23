package nz.kiwifinance.preferences;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.error.ApiException;
import nz.kiwifinance.common.error.ErrorCode;
import nz.kiwifinance.engine.time.NzTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class PreferencesService {

    /** How long "remind me later" hides the emergency fund reminder. */
    static final int SNOOZE_DAYS = 14;

    private static final TypeReference<List<HomeWidget>> WIDGETS = new TypeReference<>() {};

    private final UserPreferencesRepository preferences;
    private final JsonMapper json;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PreferencesResponse get(UUID userId) {
        return preferences
                .findByUserId(userId)
                .map(this::view)
                .orElseGet(() -> new PreferencesResponse(null, true, null));
    }

    @Transactional
    PreferencesResponse saveHomeLayout(UUID userId, List<HomeWidget> widgets) {
        UserPreferences saved = getOrCreate(userId);
        if (widgets == null) {
            saved.setHomeLayout(null);
        } else {
            Set<String> seen = new HashSet<>();
            for (HomeWidget widget : widgets) {
                if (!seen.add(widget.id())) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "Each widget can only appear once.");
                }
            }
            saved.setHomeLayout(json.writeValueAsString(widgets));
        }
        return view(saved);
    }

    @Transactional
    PreferencesResponse update(UUID userId, PreferencesRequests.Update request) {
        UserPreferences saved = getOrCreate(userId);
        if (request.emergencyFundReminders() != null) {
            saved.setEmergencyFundReminders(request.emergencyFundReminders());
            saved.setEmergencyFundReminderSnoozedUntil(null);
        }
        return view(saved);
    }

    @Transactional
    PreferencesResponse snoozeEmergencyFundReminder(UUID userId) {
        UserPreferences saved = getOrCreate(userId);
        saved.setEmergencyFundReminderSnoozedUntil(today().plusDays(SNOOZE_DAYS));
        return view(saved);
    }

    /**
     * Whether to remind the person to choose an emergency fund account today. Reminders are on until
     * the person turns them off, and "remind me later" pauses them for a fortnight.
     */
    @Transactional(readOnly = true)
    public boolean remindAboutEmergencyFund(UUID userId) {
        LocalDate today = today();
        return preferences
                .findByUserId(userId)
                .map(saved -> saved.isEmergencyFundReminders()
                        && (saved.getEmergencyFundReminderSnoozedUntil() == null
                                || !today.isBefore(saved.getEmergencyFundReminderSnoozedUntil())))
                .orElse(true);
    }

    private UserPreferences getOrCreate(UUID userId) {
        return preferences.findByUserId(userId).orElseGet(() -> preferences.save(new UserPreferences(userId)));
    }

    private PreferencesResponse view(UserPreferences saved) {
        List<HomeWidget> layout = saved.getHomeLayout() == null ? null : json.readValue(saved.getHomeLayout(), WIDGETS);
        return new PreferencesResponse(
                layout, saved.isEmergencyFundReminders(), saved.getEmergencyFundReminderSnoozedUntil());
    }

    private LocalDate today() {
        return NzTime.today(clock);
    }
}
