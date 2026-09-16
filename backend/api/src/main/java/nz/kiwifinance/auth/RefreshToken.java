package nz.kiwifinance.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import nz.kiwifinance.common.persistence.AuditableEntity;

@Entity
@Table(name = "refresh_tokens")
class RefreshToken extends AuditableEntity {

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    protected RefreshToken() {}

    RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    boolean isRevoked() {
        return revokedAt != null;
    }

    boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    void rotate(UUID replacement, Instant now) {
        revokedAt = now;
        replacedById = replacement;
    }
}
