package nz.kiwifinance.movement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nz.kiwifinance.account.AccountService;
import nz.kiwifinance.account.AccountType;
import nz.kiwifinance.engine.time.NzTime;
import nz.kiwifinance.support.IntegrationTestBase;
import nz.kiwifinance.transaction.ExternalTransaction;
import nz.kiwifinance.transaction.TransactionService;
import nz.kiwifinance.transaction.TransactionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

class MoneyMovementIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    private TestUser user;
    private UUID everyday;
    private UUID savings;

    @BeforeEach
    void createAccounts() throws Exception {
        user = register("Hemi");
        everyday = account("Everyday", "EVERYDAY", 200_000);
        savings = account("Rainy day", "SAVINGS", 1_000_000);
        putAs(user, "/api/v1/emergency-fund/account", Map.of("accountId", savings.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void transferOutOfTheEmergencyFundMovesBothBalances() throws Exception {
        record(Map.of(
                        "type",
                        "TRANSFER",
                        "fromAccountId",
                        savings.toString(),
                        "toAccountId",
                        everyday.toString(),
                        "amountCents",
                        250_000,
                        "note",
                        "Car repair"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromAccountName").value("Rainy day"))
                .andExpect(jsonPath("$.toAccountName").value("Everyday"));

        getAs(user, "/api/v1/emergency-fund")
                .andExpect(jsonPath("$.current.cents").value(750_000));
        JsonNode accounts = bodyOf(getAs(user, "/api/v1/accounts"));
        assertThat(balanceOf(accounts, everyday)).isEqualTo(450_000);
        assertThat(balanceOf(accounts, savings)).isEqualTo(750_000);

        getAs(user, "/api/v1/transactions?accountId=" + savings)
                .andExpect(jsonPath("$.items[0].amount.cents").value(-250_000))
                .andExpect(jsonPath("$.items[0].description").value("Transfer to Everyday"))
                .andExpect(jsonPath("$.items[0].transfer").value(true));
        getAs(user, "/api/v1/money-movements?accountId=" + savings)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].note").value("Car repair"));
    }

    @Test
    void withdrawalCountsAsSpendingAndDepositAddsMoney() throws Exception {
        String eatingOut =
                jdbc.queryForObject("select id::text from categories where slug = 'eating-out'", String.class);
        Map<String, Object> withdrawal = new HashMap<>();
        withdrawal.put("type", "WITHDRAWAL");
        withdrawal.put("fromAccountId", everyday.toString());
        withdrawal.put("amountCents", 5_000);
        withdrawal.put("categoryId", eatingOut);
        record(withdrawal).andExpect(status().isCreated());
        record(Map.of("type", "DEPOSIT", "toAccountId", savings.toString(), "amountCents", 20_000))
                .andExpect(status().isCreated());

        getAs(user, "/api/v1/transactions?accountId=" + everyday)
                .andExpect(jsonPath("$.items[0].transfer").value(false))
                .andExpect(jsonPath("$.items[0].category.slug").value("eating-out"));
        getAs(user, "/api/v1/emergency-fund")
                .andExpect(jsonPath("$.current.cents").value(1_020_000));
    }

    @Test
    void rejectsMovesThatDoNotAddUp() throws Exception {
        record(Map.of(
                        "type",
                        "TRANSFER",
                        "fromAccountId",
                        savings.toString(),
                        "toAccountId",
                        savings.toString(),
                        "amountCents",
                        100))
                .andExpect(status().isBadRequest());
        record(Map.of("type", "DEPOSIT", "fromAccountId", savings.toString(), "amountCents", 100))
                .andExpect(status().isBadRequest());
        record(Map.of("type", "WITHDRAWAL", "fromAccountId", savings.toString(), "amountCents", 0))
                .andExpect(status().isBadRequest());
        record(Map.of(
                        "type",
                        "WITHDRAWAL",
                        "fromAccountId",
                        savings.toString(),
                        "amountCents",
                        100,
                        "movedOn",
                        LocalDate.now(NzTime.ZONE).plusDays(2).toString()))
                .andExpect(status().isBadRequest());
        TestUser stranger = register("Stranger");
        postAs(
                        stranger,
                        "/api/v1/money-movements",
                        Map.of("type", "WITHDRAWAL", "fromAccountId", savings.toString(), "amountCents", 100))
                .andExpect(status().isNotFound());
    }

    @Test
    void theBanksCopyReplacesARecordedMoveOnABankFedAccount() throws Exception {
        UUID fed = accountService
                .createForBankFeed(user.id(), "Bank savings", AccountType.SAVINGS, "ANZ", 300_000)
                .getId();
        LocalDate today = LocalDate.now(NzTime.ZONE);
        record(Map.of(
                        "type", "TRANSFER",
                        "fromAccountId", fed.toString(),
                        "toAccountId", everyday.toString(),
                        "amountCents", 50_000,
                        "movedOn", today.minusDays(1).toString()))
                .andExpect(status().isCreated());
        getAs(user, "/api/v1/transactions?accountId=" + fed)
                .andExpect(jsonPath("$.items.length()").value(1));

        transactionService.importTransactions(
                user.id(),
                fed,
                TransactionSource.BANK_FEED,
                List.of(
                        new ExternalTransaction("bank-1", today, -50_000, "TFR TO EVERYDAY", null, null, false),
                        new ExternalTransaction("bank-2", today, -1_200, "BANK FEE", null, null, false)));

        JsonNode items =
                bodyOf(getAs(user, "/api/v1/transactions?accountId=" + fed)).get("items");
        assertThat(items).hasSize(2);
        assertThat(items.findValuesAsString("source")).containsOnly("BANK_FEED");
        JsonNode transfer = null;
        for (JsonNode item : items) {
            if (item.get("amount").get("cents").asLong() == -50_000) {
                transfer = item;
            }
        }
        assertThat(transfer).isNotNull();
        assertThat(transfer.get("transfer").asBoolean()).isTrue();
    }

    private ResultActions record(Map<String, Object> body) {
        return postAs(user, "/api/v1/money-movements", body);
    }

    private UUID account(String name, String type, long balance) throws Exception {
        return idOf(
                postAs(user, "/api/v1/accounts", Map.of("name", name, "type", type, "currentBalanceCents", balance)));
    }

    private static long balanceOf(JsonNode accounts, UUID id) {
        for (JsonNode account : accounts) {
            if (account.get("id").asString().equals(id.toString())) {
                return account.get("balance").get("cents").asLong();
            }
        }
        throw new AssertionError("No account " + id);
    }
}
