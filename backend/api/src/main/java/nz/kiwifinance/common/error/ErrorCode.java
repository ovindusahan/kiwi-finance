package nz.kiwifinance.common.error;

import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Every error the API can return. The lower-case name is sent to clients as the stable
 * {@code code} property of the problem details response.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "Invalid cursor"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid refresh token"),
    WRONG_PASSWORD(HttpStatus.UNPROCESSABLE_CONTENT, "Password is not correct"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Email already registered"),
    ACCOUNT_MANAGED_BY_BANK_FEED(HttpStatus.CONFLICT, "Account managed by bank feed"),
    ACCOUNT_ARCHIVED(HttpStatus.CONFLICT, "Account archived"),
    CATEGORY_READ_ONLY(HttpStatus.CONFLICT, "Category is read-only"),
    CATEGORY_NAME_TAKEN(HttpStatus.CONFLICT, "Category name already used"),
    TRANSACTION_MANAGED_BY_BANK_FEED(HttpStatus.CONFLICT, "Transaction managed by bank feed"),
    BANK_CONNECTION_EXISTS(HttpStatus.CONFLICT, "Bank connection already exists"),
    BANK_CONNECTION_NOT_ACTIVE(HttpStatus.CONFLICT, "Bank connection is not active"),
    BANK_CONNECTION_DIFFERENT_USER(HttpStatus.CONFLICT, "Different Akahu user"),
    BANK_FEED_ACCOUNT_LINK_INVALID(HttpStatus.CONFLICT, "Account cannot be linked"),
    SYNC_IN_PROGRESS(HttpStatus.CONFLICT, "Sync already in progress"),
    AKAHU_TOKENS_REJECTED(HttpStatus.UNPROCESSABLE_CONTENT, "Akahu rejected the tokens"),
    AKAHU_OAUTH_NOT_CONFIGURED(HttpStatus.CONFLICT, "Akahu OAuth is not configured"),
    OAUTH_STATE_INVALID(HttpStatus.BAD_REQUEST, "Invalid or expired authorisation"),
    AKAHU_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "Akahu is unavailable"),
    IMPORT_FORMAT_UNRECOGNISED(HttpStatus.UNPROCESSABLE_CONTENT, "File format not recognised"),
    IMPORT_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "File too large"),
    GOAL_NOT_ACTIVE(HttpStatus.CONFLICT, "Goal is not active"),
    TOO_MANY_LOGIN_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
