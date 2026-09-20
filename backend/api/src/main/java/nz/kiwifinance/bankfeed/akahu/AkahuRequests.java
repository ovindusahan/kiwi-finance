package nz.kiwifinance.bankfeed.akahu;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

final class AkahuRequests {

    private AkahuRequests() {}

    @Schema(name = "AkahuPersonalTokensRequest")
    record PersonalTokens(
            @NotBlank
            @Size(max = 200)
            @Pattern(regexp = "^app_token_[A-Za-z0-9_-]+$", message = "should start with app_token_")
            String appToken,

            @NotBlank
            @Size(max = 200)
            @Pattern(regexp = "^user_token_[A-Za-z0-9_-]+$", message = "should start with user_token_")
            String userToken) {

        @Override
        public String toString() {
            return "PersonalTokens[redacted]";
        }
    }

    @Schema(name = "AkahuOAuthCallbackRequest")
    record OAuthCallback(
            @NotBlank @Size(max = 2000) String code,
            @NotBlank @Size(max = 200) String state) {}
}
