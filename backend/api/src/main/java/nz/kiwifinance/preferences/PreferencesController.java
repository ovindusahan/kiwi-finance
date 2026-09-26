package nz.kiwifinance.preferences;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.web.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/preferences")
@RequiredArgsConstructor
class PreferencesController {

    private final PreferencesService preferencesService;

    @GetMapping
    PreferencesResponse get(CurrentUser user) {
        return preferencesService.get(user.id());
    }

    @PatchMapping
    PreferencesResponse update(CurrentUser user, @Valid @RequestBody PreferencesRequests.Update request) {
        return preferencesService.update(user.id(), request);
    }

    @PutMapping("/home-layout")
    PreferencesResponse saveHomeLayout(CurrentUser user, @Valid @RequestBody PreferencesRequests.HomeLayout request) {
        return preferencesService.saveHomeLayout(user.id(), request.widgets());
    }

    @PostMapping("/emergency-fund-reminder/snooze")
    PreferencesResponse snoozeEmergencyFundReminder(CurrentUser user) {
        return preferencesService.snoozeEmergencyFundReminder(user.id());
    }
}
