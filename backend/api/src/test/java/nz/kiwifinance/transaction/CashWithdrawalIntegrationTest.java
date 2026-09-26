package nz.kiwifinance.transaction;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nz.kiwifinance.engine.time.NzTime;
import nz.kiwifinance.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CashWithdrawalIntegrationTest extends IntegrationTestBase {

    private TestUser user;
    private UUID everyday;
    private UUID withdrawal;

    @BeforeEach
    void withdrawCash() throws Exception {
        user = register("Hine");
        everyday = idOf(postAs(
                user,
                "/api/v1/accounts",
                Map.of("name", "Everyday", "type", "EVERYDAY", "currentBalanceCents", 100_000)));
        LocalDate today = LocalDate.now(NzTime.ZONE);
        withdrawal = idOf(postAs(
                user,
                "/api/v1/transactions",
                Map.of(
                        "accountId",
                        everyday.toString(),
                        "postedOn",
                        today.minusDays(2).toString(),
                        "amountCents",
                        -20_000,
                        "description",
                        "ATM WITHDRAWAL PONSONBY RD")));
        postAs(
                        user,
                        "/api/v1/transactions",
                        Map.of(
                                "accountId",
                                everyday.toString(),
                                "postedOn",
                                today.minusDays(1).toString(),
                                "amountCents",
                                -4_500,
                                "description",
                                "COUNTDOWN AUCKLAND"))
                .andExpect(status().isCreated());
    }

    @Test
    void asksWhatCashWasSpentOnAndKeepsTheRestInAWallet() throws Exception {
        getAs(user, "/api/v1/cash-withdrawals")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transactionId").value(withdrawal.toString()))
                .andExpect(jsonPath("$[0].amount.cents").value(20_000))
                .andExpect(jsonPath("$[0].accountName").value("Everyday"));

        postAs(
                        user,
                        "/api/v1/cash-withdrawals/" + withdrawal,
                        Map.of(
                                "lines",
                                List.of(
                                        Map.of(
                                                "categoryId",
                                                category("groceries"),
                                                "amountCents",
                                                12_000,
                                                "note",
                                                "Market"),
                                        Map.of("categoryId", category("takeaways"), "amountCents", 3_000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletBalance.cents").value(5_000))
                .andExpect(jsonPath("$.linesRecorded").value(2));

        getAs(user, "/api/v1/cash-withdrawals").andExpect(jsonPath("$.length()").value(0));
        getAs(user, "/api/v1/transactions/" + withdrawal)
                .andExpect(jsonPath("$.transfer").value(true));
        getAs(user, "/api/v1/accounts")
                .andExpect(jsonPath("$[?(@.name == 'Cash')].balance.cents").value(5_000));
        getAs(user, "/api/v1/transactions?search=Market")
                .andExpect(jsonPath("$.items[0].category.slug").value("groceries"))
                .andExpect(jsonPath("$.items[0].amount.cents").value(-12_000));
    }

    @Test
    void refusesSpendingMoreThanWasTakenOut() throws Exception {
        postAs(
                        user,
                        "/api/v1/cash-withdrawals/" + withdrawal,
                        Map.of("lines", List.of(Map.of("categoryId", category("groceries"), "amountCents", 25_000))))
                .andExpect(status().isBadRequest());
        postAs(user, "/api/v1/cash-withdrawals/" + withdrawal, Map.of("lines", List.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletBalance.cents").value(20_000));
        postAs(user, "/api/v1/cash-withdrawals/" + withdrawal, Map.of("lines", List.of()))
                .andExpect(status().isBadRequest());
        TestUser stranger = register("Stranger");
        postAs(stranger, "/api/v1/cash-withdrawals/" + withdrawal, Map.of("lines", List.of()))
                .andExpect(status().isNotFound());
    }

    private String category(String slug) {
        return jdbc.queryForObject("select id::text from categories where slug = ?", String.class, slug);
    }
}
