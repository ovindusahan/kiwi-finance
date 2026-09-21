package nz.kiwifinance.bankfeed;

/**
 * A failure talking to a bank feed provider, classified so callers can react without knowing
 * which provider it was.
 */
public class BankFeedException extends RuntimeException {

    public enum Reason {
        /** The provider rejected the credentials: they were revoked, expired or are wrong. */
        UNAUTHORISED,
        RATE_LIMITED,
        UNAVAILABLE
    }

    private final Reason reason;

    public BankFeedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public BankFeedException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public String errorCode() {
        return switch (reason) {
            case UNAUTHORISED -> "reauthorisation_required";
            case RATE_LIMITED -> "provider_rate_limited";
            case UNAVAILABLE -> "provider_unavailable";
        };
    }
}
