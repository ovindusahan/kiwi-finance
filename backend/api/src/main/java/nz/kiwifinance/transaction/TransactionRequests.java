package nz.kiwifinance.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class TransactionRequests {

    private TransactionRequests() {}

    /**
     * @param amountCents negative for money out, positive for money in
     */
    @Schema(name = "CreateTransactionRequest")
    record Create(
            @NotNull UUID accountId,
            @NotNull LocalDate postedOn,
            @NotNull Long amountCents,
            @NotBlank @Size(max = 500) String description,
            @Size(max = 200) String merchant,
            UUID categoryId,
            boolean transfer,
            @Size(max = 1000) String notes) {}

    /**
     * Omitted fields are left unchanged; an empty merchant or notes clears it. Date, amount,
     * description and merchant can only be changed on transactions entered by hand. Categories
     * are changed through {@link SetCategory}.
     */
    @Schema(name = "UpdateTransactionRequest")
    record Update(
            LocalDate postedOn,
            Long amountCents,
            @Size(min = 1, max = 500) String description,
            @Size(max = 200) String merchant,
            Boolean transfer,
            @Size(max = 1000) String notes) {

        boolean changesDetails() {
            return postedOn != null || amountCents != null || description != null || merchant != null;
        }
    }

    /**
     * @param categoryId the new category, or {@code null} to leave the transaction uncategorised
     */
    @Schema(name = "SetTransactionCategoryRequest")
    record SetCategory(UUID categoryId) {}

    @Schema(name = "CategoriseTransactionsRequest")
    record Categorise(@NotEmpty @Size(max = 500) List<@NotNull UUID> transactionIds, UUID categoryId) {}
}
