package nz.kiwifinance.bankfeed;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

final class BankFeedRequests {

    private BankFeedRequests() {}

    /**
     * @param linkAccountId an existing account to receive this feed's transactions; when omitted a
     *     new account is created
     */
    @Schema(name = "UpdateFeedAccountRequest")
    record UpdateFeedAccount(@NotNull Boolean syncEnabled, UUID linkAccountId) {}
}
