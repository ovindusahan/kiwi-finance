package nz.kiwifinance.income;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import nz.kiwifinance.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

class IncomeIntegrationTest extends IntegrationTestBase {

    @Test
    void calculatesTakeHomePayUsingProfileSettings() throws Exception {
        TestUser user = register("Ana");
        putAs(user, "/api/v1/profile", profile(true, "0.035"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboarded").value(true));

        postAs(
                        user,
                        "/api/v1/income-sources",
                        Map.of(
                                "name",
                                "Acme salary",
                                "type",
                                "SALARY",
                                "amountCents",
                                7_500_000,
                                "basis",
                                "GROSS",
                                "frequency",
                                "ANNUALLY"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.grossAnnual.cents").value(7_500_000))
                .andExpect(jsonPath("$.takeHomePerPeriod.cents").value(5_634_200));
        postAs(
                user,
                "/api/v1/income-sources",
                Map.of(
                        "name",
                        "Market stall",
                        "type",
                        "OTHER",
                        "amountCents",
                        20_000,
                        "basis",
                        "NET",
                        "frequency",
                        "WEEKLY"));

        getAs(user, "/api/v1/income-sources")
                .andExpect(jsonPath("$.sources.length()").value(2))
                .andExpect(jsonPath("$.expectedMonthlyTakeHome.cents").value(469_517 + 86_667));
    }

    @Test
    void validatesProfileKiwiSaverSettings() throws Exception {
        TestUser user = register("Ana");
        Map<String, Object> invalid = profile(true, null);

        putAs(user, "/api/v1/profile", invalid).andExpect(status().isBadRequest());
    }

    @Test
    void offersAPublicPayCalculator() throws Exception {
        postAs(
                        null,
                        "/api/v1/income/pay-calculator",
                        Map.of(
                                "amountCents",
                                625_000,
                                "basis",
                                "GROSS",
                                "frequency",
                                "MONTHLY",
                                "taxCode",
                                "M",
                                "kiwiSaverRate",
                                0.035,
                                "payDate",
                                "2026-10-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxYear").value("2026/27"))
                .andExpect(jsonPath("$.perPeriod.takeHome.cents").value(469_516))
                .andExpect(jsonPath("$.explanation.steps.length()").value(6));
    }

    @Test
    void hidesOtherPeoplesIncome() throws Exception {
        TestUser owner = register("Owner");
        TestUser other = register("Other");
        UUID id = idOf(postAs(
                owner,
                "/api/v1/income-sources",
                Map.of(
                        "name",
                        "Salary",
                        "type",
                        "SALARY",
                        "amountCents",
                        100_000,
                        "basis",
                        "NET",
                        "frequency",
                        "FORTNIGHTLY")));

        deleteAs(other, "/api/v1/income-sources/" + id).andExpect(status().isNotFound());
        getAs(other, "/api/v1/income-sources")
                .andExpect(jsonPath("$.sources.length()").value(0));
    }

    private static Map<String, Object> profile(boolean kiwiSaver, String rate) {
        Map<String, Object> body = new HashMap<>();
        body.put("region", "AUCKLAND");
        body.put("householdSize", 2);
        body.put("dependants", 0);
        body.put("employmentType", "EMPLOYEE");
        body.put("housingType", "RENTING");
        body.put("taxCode", "M");
        body.put("hasStudentLoan", false);
        body.put("kiwiSaverMember", kiwiSaver);
        body.put("kiwiSaverRate", rate == null ? null : Double.valueOf(rate));
        body.put("payFrequency", "FORTNIGHTLY");
        body.put("savingsInterestRate", 0.025);
        body.put("targetSavingsRate", 0.15);
        return body;
    }
}
