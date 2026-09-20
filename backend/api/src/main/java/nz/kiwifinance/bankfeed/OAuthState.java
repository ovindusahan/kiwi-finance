package nz.kiwifinance.bankfeed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import nz.kiwifinance.common.persistence.AuditableEntity;

/**
 * A single-use OAuth state value, stored hashed, that ties an authorisation callback to the
 * person who started it.
 */
@Entity
@Table(name = "oauth_states")
class OAuthState extends AuditableEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private BankFeedProvider provider;

    @Column(name = "state_hash", nullable = false, updatable = false)
    private String stateHash;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected OAuthState() {}

    OAuthState(UUID userId, BankFeedProvider provider, String stateHash, Instant expiresAt) {
        this.userId = userId;
        this.provider = provider;
        this.stateHash = stateHash;
        this.expiresAt = expiresAt;
    }

    boolean isUsableBy(UUID userId, BankFeedProvider provider, Instant now) {
        return this.userId.equals(userId) && this.provider == provider && consumedAt == null && expiresAt.isAfter(now);
    }

    void consume(Instant now) {
        consumedAt = now;
    }
}
