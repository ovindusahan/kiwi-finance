package nz.kiwifinance.movement;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param fromAccountId where the money left; required for transfers and withdrawals
 * @param toAccountId where the money arrived; required for transfers and deposits
 * @param movedOn the day it moved; defaults to today
 * @param categoryId what a withdrawal was spent on, or where a deposit came from; left out for
 *     transfers
 */
@Schema(name = "MoneyMovementRequest")
record MovementRequest(
        @NotNull MovementType type,
        UUID fromAccountId,
        UUID toAccountId,
        @NotNull @Positive Long amountCents,
        LocalDate movedOn,
        UUID categoryId,
        @Size(max = 200) String note) {}
