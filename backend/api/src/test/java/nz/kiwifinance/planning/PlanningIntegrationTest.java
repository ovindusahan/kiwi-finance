package nz.kiwifinance.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nz.kiwifinance.engine.time.NzTime;
import nz.kiwifinance.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Exercises analysis and planning on six months of steady finances: $6,000 a month in, $2,400
 * rent, $800 groceries, about $300 eating out and a Netflix subscription.
 */
class PlanningIntegrationTest extends IntegrationTestBase {

    private TestUser user;
    private UUID everyday;
    private UUID savings;

    @BeforeEach
    void createFinances() throws Exception {
        user = register("Mere");
        everyday = idOf(postAs(
                user,
                "/api/v1/accounts",
                Map.of("name", "Everyday", "type", "EVERYDAY", "currentBalanceCents", 500_000)));
        savings = idOf(postAs(
                user,
                "/api/v1/accounts",
                Map.of("name", "Rainy day", "type", "SAVINGS", "currentBalanceCents", 1_000_000)));
        putAs(user, "/api/v1/emergency-fund/account", Map.of("accountId", savings.toString()))
                .andExpect(status().isOk());
        YearMonth current = YearMonth.from(LocalDate.now(NzTime.ZONE));
        int[] eatingOut = {250, 300, 350, 280, 320, 300};
        for (int i = 1; i <= 6; i++) {
            YearMonth month = current.minusMonths(i);
            add(month.atDay(1), 600_000, "ACME SALARY", "salary");
            add(month.atDay(2), -240_000, "RENT", "rent");
            add(month.atDay(10), -80_000, "COUNTDOWN", "groceries");
            add(month.atDay(12), -2_299, "NETFLIX.COM", "subscriptions");
            add(month.atDay(15), -eatingOut[i - 1] * 100L, "CAFE", "eating-out");
        }
    }

    @Test
    void sizesTheEmergencyFundFromEssentialSpending() throws Exception {
        getAs(user, "/api/v1/emergency-fund")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyEssentials.cents").value(320_000))
                .andExpect(jsonPath("$.targetMonths").value(3))
                .andExpect(jsonPath("$.target.cents").value(960_000))
                .andExpect(jsonPath("$.status").value("FUNDED"))
                .andExpect(jsonPath("$.account.name").value("Rainy day"))
                .andExpect(jsonPath("$.explanation.steps.length()").isNumber());
    }

    @Test
    void suggestsHowToShareSavingsAcrossGoals() throws Exception {
        LocalDate soon = LocalDate.now(NzTime.ZONE).plusMonths(4);
        UUID laptop = idOf(postAs(
                user,
                "/api/v1/goals",
                Map.of("name", "Laptop", "type", "PURCHASE", "targetCents", 400_000, "targetDate", soon.toString())));

        getAs(user, "/api/v1/goals/plan")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emergencyFund.cents").value(0))
                .andExpect(jsonPath("$.available.cents").isNumber())
                .andExpect(jsonPath("$.advice[0].goalId").value(laptop.toString()))
                .andExpect(jsonPath("$.advice[0].urgency").value("SOON"))
                .andExpect(jsonPath("$.advice[0].kind").value("INCREASE_CONTRIBUTION"))
                .andExpect(jsonPath("$.advice[0].suggestedMonthly.cents").value(100_000))
                .andExpect(jsonPath("$.summary").isString());
    }

    @Test
    void weighsUpAHomeAgainstRealRentAndSavings() throws Exception {
        postAs(
                        user,
                        "/api/v1/planning/purchase-impact",
                        Map.of(
                                "kind",
                                "HOUSE",
                                "priceCents",
                                60_000_000,
                                "depositCents",
                                12_000_000,
                                "firstHome",
                                true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income.cents").value(600_000))
                .andExpect(jsonPath("$.loan.principal.cents").value(48_000_000))
                .andExpect(jsonPath("$.monthlyCosts[?(@.label == \"Rent you'd stop paying\")].amount.cents")
                        .value(-240_000))
                .andExpect(jsonPath("$.monthlyCosts[0].category.slug").value("mortgage"))
                .andExpect(jsonPath("$.cashAvailable.cents").value(500_000))
                .andExpect(jsonPath("$.options.length()").value(3))
                .andExpect(jsonPath("$.verdict").isString())
                .andExpect(jsonPath("$.headline").isString());
    }

    @Test
    void showsWhichGoalsACarWouldDelay() throws Exception {
        LocalDate soon = LocalDate.now(NzTime.ZONE).plusMonths(10);
        postAs(
                        user,
                        "/api/v1/goals",
                        Map.of(
                                "name",
                                "Holiday",
                                "type",
                                "TRAVEL",
                                "targetCents",
                                2_000_000,
                                "targetDate",
                                soon.toString()))
                .andExpect(status().isCreated());

        postAs(user, "/api/v1/planning/purchase-impact", Map.of("kind", "CAR", "priceCents", 4_000_000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deposit.cents").value(800_000))
                .andExpect(jsonPath("$.loan.termMonths").value(60))
                .andExpect(jsonPath("$.goalsAffected[0].name").value("Holiday"));
        postAs(user, "/api/v1/planning/purchase-impact", Map.of("kind", "CAR", "priceCents", 0))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recommendsAndTracksABudget() throws Exception {
        JsonNode recommendation = bodyOf(getAs(user, "/api/v1/budgets/recommendation")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyIncome.cents").value(600_000))
                .andExpect(jsonPath("$.meetsTarget").value(true)));
        List<Map<String, Object>> lines = new ArrayList<>();
        for (JsonNode line : recommendation.get("lines")) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("categoryId", line.get("category").get("id").asString());
            entry.put("limitCents", line.get("recommended").get("cents").asLong());
            lines.add(entry);
        }
        assertThat(lines).hasSize(4);

        getAs(user, "/api/v1/budgets/current").andExpect(status().isNotFound());
        postAs(user, "/api/v1/budgets", Map.of("lines", lines)).andExpect(status().isCreated());
        getAs(user, "/api/v1/budgets/current")
                .andExpect(jsonPath("$.name").value("My budget"))
                .andExpect(jsonPath("$.totalLimit.cents").value(353_000))
                .andExpect(jsonPath("$.progress.lines.length()").value(4));
        String lastMonth =
                YearMonth.from(LocalDate.now(NzTime.ZONE)).minusMonths(1).toString();
        getAs(user, "/api/v1/budgets/current?month=" + lastMonth)
                .andExpect(jsonPath("$.progress.linesOver").value(0))
                .andExpect(jsonPath("$.progress.totalSpent.cents").value(347_299));
    }

    @Test
    void tracksGoalsAndKeepsThemPrivate() throws Exception {
        LocalDate inAYear = LocalDate.now(NzTime.ZONE).plusYears(1);
        UUID goal = idOf(postAs(
                        user,
                        "/api/v1/goals",
                        Map.of(
                                "name",
                                "Japan",
                                "type",
                                "TRAVEL",
                                "targetCents",
                                600_000,
                                "targetDate",
                                inAYear.toString(),
                                "monthlyContributionCents",
                                50_000,
                                "startingAmountCents",
                                100_000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saved.cents").value(100_000))
                .andExpect(jsonPath("$.projection.status").value("ON_TRACK"))
                .andExpect(jsonPath("$.daysLeft").isNumber()));

        postAs(user, "/api/v1/goals/" + goal + "/contributions", Map.of("amountCents", 500_000))
                .andExpect(jsonPath("$.status").value("ACHIEVED"))
                .andExpect(jsonPath("$.progress").value(1.0));

        UUID linked = idOf(postAs(
                user,
                "/api/v1/goals",
                Map.of(
                        "name",
                        "Buffer",
                        "type",
                        "CUSTOM",
                        "targetCents",
                        2_000_000,
                        "linkedAccountId",
                        savings.toString())));
        getAs(user, "/api/v1/goals/" + linked)
                .andExpect(jsonPath("$.saved.cents").value(1_000_000));
        postAs(user, "/api/v1/goals/" + linked + "/contributions", Map.of("amountCents", 100))
                .andExpect(status().isConflict());

        TestUser other = register("Other");
        getAs(other, "/api/v1/goals/" + goal).andExpect(status().isNotFound());
        postAs(
                        other,
                        "/api/v1/goals",
                        Map.of(
                                "name",
                                "Steal",
                                "type",
                                "CUSTOM",
                                "targetCents",
                                100,
                                "linkedAccountId",
                                savings.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void answersCanIAffordIt() throws Exception {
        postAs(
                        user,
                        "/api/v1/planning/affordability",
                        Map.of("itemName", "a laptop", "priceCents", 200_000, "funding", "CASH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("AFFORDABLE_NOW"))
                .andExpect(jsonPath("$.availableNow.cents").value(540_000));

        LocalDate inAYear = LocalDate.now(NzTime.ZONE).plusYears(1);
        postAs(
                        user,
                        "/api/v1/planning/affordability",
                        Map.of(
                                "itemName",
                                "a car",
                                "priceCents",
                                3_000_000,
                                "desiredDate",
                                inAYear.toString(),
                                "funding",
                                "CASH"))
                .andExpect(jsonPath("$.verdict").value("ON_TRACK"))
                .andExpect(jsonPath("$.payPeriod").value("fortnight"))
                .andExpect(jsonPath("$.explanation.assumptions[?(@.key == 'income_basis')]")
                        .exists());

        postAs(
                        user,
                        "/api/v1/planning/affordability",
                        Map.of("itemName", "a car", "priceCents", 3_000_000, "funding", "FINANCE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void turnsPurchasePlansIntoGoals() throws Exception {
        UUID plan = idOf(postAs(
                        user,
                        "/api/v1/planning/purchase-plans",
                        Map.of("itemName", "an e-bike", "priceCents", 800_000, "funding", "CASH"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assessment.verdict").exists()));

        JsonNode started = bodyOf(postAs(user, "/api/v1/planning/purchase-plans/" + plan + "/goal", null)
                .andExpect(status().isOk()));
        String goalId = started.get("goalId").asString();
        getAs(user, "/api/v1/goals/" + goalId)
                .andExpect(jsonPath("$.name").value("an e-bike"))
                .andExpect(jsonPath("$.type").value("PURCHASE"));
        postAs(user, "/api/v1/planning/purchase-plans/" + plan + "/goal", null)
                .andExpect(jsonPath("$.goalId").value(goalId));
    }

    @Test
    void summarisesEverythingOnTheDashboard() throws Exception {
        getAs(user, "/api/v1/dashboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Mere"))
                .andExpect(jsonPath("$.typical.income.cents").value(600_000))
                .andExpect(jsonPath("$.typical.incomeBasis").value("TRANSACTIONS"))
                .andExpect(jsonPath("$.netWorth.cents").value(1_500_000))
                .andExpect(jsonPath("$.emergencyFund.status").value("FUNDED"))
                .andExpect(jsonPath("$.score.score").isNumber())
                .andExpect(jsonPath("$.setup.length()").value(6))
                .andExpect(
                        jsonPath("$.setup[?(@.key == 'emergency-fund')].done").value(true))
                .andExpect(jsonPath("$.setup[?(@.key == 'budget')].done").value(false));

        getAs(user, "/api/v1/progress")
                .andExpect(jsonPath("$.achievements[?(@.key == 'safety_net')].unlocked")
                        .value(true))
                .andExpect(jsonPath("$.streaks.surplusMonths").value(6));
    }

    @Test
    void analysesSpendingAndDetectsRecurringPayments() throws Exception {
        getAs(user, "/api/v1/analysis/cashflow?months=7")
                .andExpect(jsonPath("$.months.length()").value(7))
                .andExpect(jsonPath("$.months[6].complete").value(false));
        getAs(user, "/api/v1/analysis/spending?months=3")
                .andExpect(jsonPath("$.categories[0].category.slug").value("rent"))
                .andExpect(jsonPath("$.categories[0].change").value(0.0));
        getAs(user, "/api/v1/analysis/recurring")
                .andExpect(jsonPath("$.payments[?(@.name == 'Netflix.com')].interval")
                        .value("MONTHLY"))
                .andExpect(jsonPath("$.monthlySubscriptions.cents").value(2_299));
        getAs(user, "/api/v1/insights").andExpect(jsonPath("$[0].key").isString());
    }

    @Test
    void servesEducationAndLoanMathsWithoutSigningIn() throws Exception {
        perform(get("/api/v1/education/guides"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
        perform(get("/api/v1/education/guides/kiwisaver-basics"), null, null)
                .andExpect(jsonPath("$.sections.length()").value(6));
        perform(get("/api/v1/education/guides/nope"), null, null).andExpect(status().isNotFound());
        postAs(null, "/api/v1/planning/loan", Map.of("amountCents", 2_000_000, "annualRate", 0.099, "termMonths", 60))
                .andExpect(jsonPath("$.monthlyRepayment.cents").value(42_396));
    }

    private void add(LocalDate date, long cents, String description, String categorySlug) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("accountId", everyday.toString());
        body.put("postedOn", date.toString());
        body.put("amountCents", cents);
        body.put("description", description);
        body.put(
                "categoryId",
                jdbc.queryForObject("select id::text from categories where slug = ?", String.class, categorySlug));
        postAs(user, "/api/v1/transactions", body).andExpect(status().isCreated());
    }
}
