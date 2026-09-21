package nz.kiwifinance.bankfeed.akahu;

/**
 * The tokens needed to call Akahu for one person. OAuth connections store only the user token;
 * the app token comes from configuration so it can be rotated centrally.
 */
public record AkahuCredentials(String appToken, String userToken) {

    AkahuCredentials withAppToken(String fallback) {
        return appToken != null ? this : new AkahuCredentials(fallback, userToken);
    }

    @Override
    public String toString() {
        return "AkahuCredentials[redacted]";
    }
}
