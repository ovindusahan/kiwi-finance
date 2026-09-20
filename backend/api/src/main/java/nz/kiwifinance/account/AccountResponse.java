package nz.kiwifinance.account;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import nz.kiwifinance.common.web.MoneyResponse;
import org.jspecify.annotations.Nullable;

@Schema(name = "Account")
public record AccountResponse(
        UUID id,
        String name,
        AccountType type,
        @Nullable String institution,
        MoneyResponse balance,
        boolean liquid,
        boolean includeInEmergencyFund,
        ManagedBy managedBy,
        boolean archived,
        Instant createdAt,
        Instant updatedAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getInstitution(),
                MoneyResponse.of(account.getCurrentBalanceCents()),
                account.isLiquid(),
                account.isIncludedInEmergencyFund(),
                account.getManagedBy(),
                account.isArchived(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
