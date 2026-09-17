package nz.kiwifinance.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class AccountRequests {

    private AccountRequests() {}

    /**
     * @param liquid whether the money is available at short notice; defaults from the type
     */
    @Schema(name = "CreateAccountRequest")
    record Create(
            @NotBlank @Size(max = 100) String name,
            @NotNull AccountType type,
            @Size(max = 100) String institution,
            Long currentBalanceCents,
            Boolean liquid) {}

    /**
     * Omitted fields are left unchanged. Send an empty institution to clear it.
     */
    @Schema(name = "UpdateAccountRequest")
    record Update(
            @Size(min = 1, max = 100) String name,
            AccountType type,
            @Size(max = 100) String institution,
            Long currentBalanceCents,
            Boolean liquid,
            Boolean archived) {}
}
