package nz.kiwifinance.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class AuthRequests {

    private AuthRequests() {}

    @Schema(name = "RegisterRequest")
    record Register(
            @NotBlank @Email @Size(max = 320) String email,

            @NotBlank @Size(min = 12, max = 128, message = "must be between 12 and 128 characters")
            String password,

            @NotBlank @Size(max = 100) String displayName) {}

    @Schema(name = "LoginRequest")
    record Login(@NotBlank String email, @NotBlank String password) {}

    @Schema(name = "RefreshRequest")
    record Refresh(@NotBlank String refreshToken) {}

    @Schema(name = "LogoutRequest")
    record Logout(@NotBlank String refreshToken) {}

    /** Changes to the signed-in user's details. Changing the email needs the current password. */
    @Schema(name = "UpdateAccountRequest")
    record UpdateAccount(
            @Size(min = 1, max = 100) String displayName,
            @Email @Size(max = 320) String email,
            String currentPassword) {}

    @Schema(name = "ChangePasswordRequest")
    record ChangePassword(
            @NotBlank String currentPassword,

            @NotBlank @Size(min = 12, max = 128, message = "must be between 12 and 128 characters")
            String newPassword) {}

    @Schema(name = "DeleteAccountRequest")
    record DeleteAccount(@NotBlank String password) {}
}
