package nz.kiwifinance.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import nz.kiwifinance.common.web.MoneyResponse;
import org.jspecify.annotations.Nullable;

final class CashWithdrawals {

    private CashWithdrawals() {}

    /**
     * Cash taken out that we don't know the purpose of yet.
     *
     * @param amount the cash taken out, as a positive amount
     */
    @Schema(name = "CashWithdrawal")
    record View(
            UUID transactionId,
            UUID accountId,
            @Nullable String accountName,
            LocalDate postedOn,
            MoneyResponse amount,
            String description) {}

    /**
     * What the cash went on. Anything not covered by the lines stays in the person's cash wallet.
     */
    @Schema(name = "CashSpendingRequest")
    record Spending(@NotNull @Size(max = 20) List<@Valid Line> lines) {}

    @Schema(name = "CashSpendingLineRequest")
    record Line(
            @NotNull UUID categoryId,
            @NotNull @Positive Long amountCents,
            @Size(max = 100) String note) {}

    /**
     * @param walletBalance what is now in the person's cash wallet
     */
    @Schema(name = "CashSpendingResult")
    record Result(UUID walletAccountId, MoneyResponse walletBalance, int linesRecorded) {}
}
