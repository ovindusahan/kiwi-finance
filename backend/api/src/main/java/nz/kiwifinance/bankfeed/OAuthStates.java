package nz.kiwifinance.bankfeed;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and redeems single-use OAuth state values for provider authorisation flows.
 */
@Component
@RequiredArgsConstructor
public class OAuthStates {

    private final OAuthStateRepository states;
    private final Clock clock;

    @Transactional
    public void issue(UUID userId, BankFeedProvider provider, String stateHash, Instant expiresAt) {
        states.save(new OAuthState(userId, provider, stateHash, expiresAt));
    }

    /**
     * Marks the state as used. Returns false if it is unknown, expired, already used or belongs to
     * someone else.
     */
    @Transactional
    public boolean consume(UUID userId, BankFeedProvider provider, String stateHash) {
        Instant now = clock.instant();
        return states.findByStateHash(stateHash)
                .filter(state -> state.isUsableBy(userId, provider, now))
                .map(state -> {
                    state.consume(now);
                    return true;
                })
                .orElse(false);
    }
}
