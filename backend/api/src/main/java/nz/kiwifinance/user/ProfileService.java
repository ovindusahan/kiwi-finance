package nz.kiwifinance.user;

import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserProfileRepository profiles;
    private final Clock clock;

    @Transactional
    public void createDefault(UUID userId) {
        profiles.save(new UserProfile(userId));
    }

    @Transactional(readOnly = true)
    public UserProfile get(UUID userId) {
        return profiles.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Profile"));
    }

    @Transactional
    public UserProfile update(UUID userId, ProfileRequest request) {
        UserProfile profile = get(userId);
        profile.update(request, clock.instant());
        return profile;
    }
}
