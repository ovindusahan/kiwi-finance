package nz.kiwifinance.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import nz.kiwifinance.category.CategoryResponse;
import nz.kiwifinance.common.web.MoneyResponse;
import org.jspecify.annotations.Nullable;

@Schema(name = "Transaction")
public record TransactionResponse(
        UUID id,
        UUID accountId,
        @Nullable String accountName,
        LocalDate postedOn,
        MoneyResponse amount,
        String description,
        @Nullable String merchant,
        @Nullable CategoryResponse category,
        @Nullable CategorySource categorySource,
        TransactionSource source,
        boolean transfer,
        @Nullable String notes,
        Instant createdAt) {}
