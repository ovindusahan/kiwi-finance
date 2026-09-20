package nz.kiwifinance.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.Getter;
import nz.kiwifinance.common.persistence.AuditableEntity;

@Entity
@Table(name = "users")
@Getter
public class User extends AuditableEntity {

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    protected User() {}

    public User(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void recordFailedLogin(Instant now, int maxAttempts, Duration lockout) {
        failedLoginCount++;
        if (failedLoginCount >= maxAttempts) {
            lockedUntil = now.plus(lockout);
            failedLoginCount = 0;
        }
    }

    public void recordSuccessfulLogin() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

    public void rename(String displayName) {
        this.displayName = displayName;
    }

    public void changeEmail(String email) {
        this.email = email;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
