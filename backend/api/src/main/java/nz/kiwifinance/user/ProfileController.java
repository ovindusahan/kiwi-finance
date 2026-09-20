package nz.kiwifinance.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.web.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    ProfileResponse get(CurrentUser user) {
        return ProfileResponse.from(profileService.get(user.id()));
    }

    @PutMapping
    ProfileResponse update(CurrentUser user, @Valid @RequestBody ProfileRequest request) {
        return ProfileResponse.from(profileService.update(user.id(), request));
    }
}
