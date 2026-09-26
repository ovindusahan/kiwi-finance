package nz.kiwifinance.movement;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;
import nz.kiwifinance.common.web.MoneyResponse;
import org.jspecify.annotations.Nullable;

@Schema(name = "MoneyMovement")
record MovementResponse(
        UUID id,
        MovementType type,
        @Nullable UUID fromAccountId,
        @Nullable String fromAccountName,
        @Nullable UUID toAccountId,
        @Nullable String toAccountName,
        MoneyResponse amount,
        LocalDate movedOn,
        @Nullable String note) {}
