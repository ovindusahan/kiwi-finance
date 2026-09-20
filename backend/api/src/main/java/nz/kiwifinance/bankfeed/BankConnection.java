package nz.kiwifinance.bankfeed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import nz.kiwifinance.common.crypto.EncryptedValue;
import nz.kiwifinance.common.persistence.AuditableEntity;

@Entity
@Table(name = "bank_connections")
@Getter
public class BankConnection extends AuditableEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private BankFeedProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionStatus status;

    @Column(name = "credentials")
    private byte[] credentials;

    @Column(name = "credentials_key_id")
    private String credentialsKeyId;

    @Column(name = "external_user_id", nullable = false, updatable = false)
    private String externalUserId;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "sync_locked_until")
    private Instant syncLockedUntil;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    protected BankConnection() {}

    BankConnection(UUID userId, BankFeedProvider provider, ConnectionMethod method, String externalUserId) {
        this.userId = userId;
        this.provider = provider;
        this.method = method;
        this.externalUserId = externalUserId;
        this.status = ConnectionStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == ConnectionStatus.ACTIVE;
    }

    EncryptedValue encryptedCredentials() {
        return credentials == null ? null : new EncryptedValue(credentialsKeyId, credentials);
    }

    /**
     * Stores new credentials and reactivates the connection, for example after reconnecting.
     */
    void activate(ConnectionMethod method, EncryptedValue encrypted) {
        this.method = method;
        this.credentials = encrypted.ciphertext();
        this.credentialsKeyId = encrypted.keyId();
        this.status = ConnectionStatus.ACTIVE;
        this.disconnectedAt = null;
    }

    void requireReauthorisation() {
        if (status == ConnectionStatus.ACTIVE) {
            status = ConnectionStatus.REAUTH_REQUIRED;
        }
    }

    void disconnect(Instant now) {
        status = ConnectionStatus.DISCONNECTED;
        credentials = null;
        credentialsKeyId = null;
        disconnectedAt = now;
        syncLockedUntil = null;
    }

    void markSynced(Instant now) {
        lastSyncedAt = now;
    }
}
