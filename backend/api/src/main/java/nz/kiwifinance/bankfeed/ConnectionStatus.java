package nz.kiwifinance.bankfeed;

public enum ConnectionStatus {
    ACTIVE,
    /** The provider stopped accepting the stored credentials; the person must reconnect. */
    REAUTH_REQUIRED,
    DISCONNECTED
}
