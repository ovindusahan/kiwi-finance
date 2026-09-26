package nz.kiwifinance.bankfeed;

public enum ConnectionMethod {
    /** The person created their own app with the provider and entered its tokens. */
    PERSONAL_APP,
    /** The person granted access through the provider's consent screen. */
    OAUTH
}
