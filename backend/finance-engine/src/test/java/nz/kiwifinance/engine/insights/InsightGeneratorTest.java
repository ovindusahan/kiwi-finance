package nz.kiwifinance.engine.insights;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import nz.kiwifinance.engine.analysis.CashflowAnalysis;
import nz.kiwifinance.engine.analysis.CategoryGroup;
import nz.kiwifinance.engine.analysis.CategoryRef;
import nz.kiwifinance.engine.analysis.CategorySpending;
import nz.kiwifinance.engine.analysis.RecurrenceInterval;
import nz.kiwifinance.engine.analysis.RecurringPayment;
import nz.kiwifinance.engine.emergencyfund.EmergencyFundCalculator;
import nz.kiwifinance.engine.emergencyfund.EmergencyFundRequest;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.MonthRange;
import org.junit.jupiter.api.Test;

class InsightGeneratorTest {

    private static final CategoryRef SUBSCRIPTIONS =
            new CategoryRef("subscriptions", "Subscriptions", CategoryGroup.LIFESTYLE);
    private static final CategoryRef EATING_OUT = new CategoryRef("eating-out", "Eating out", CategoryGroup.LIFESTYLE);
    private static final MonthRange RANGE = MonthRange.endingWith(YearMonth.of(2026, 9), 3);

    private final InsightGenerator generator = new InsightGenerator();

    @Test
    void putsWarningsFirst() {
        var cashflow = cashflow(Money.ofDollars(4_000), Money.ofDollars(-200));
        var emergencyFund = new EmergencyFundCalculator()
                .plan(new EmergencyFundRequest(
                        Money.ofDollars(3_000), Money.ZERO, Money.ZERO, false, false, false, false, 3));
        var netflix = new RecurringPayment(
                "Netflix",
                SUBSCRIPTIONS,
                RecurrenceInterval.MONTHLY,
                Money.ofDollars("22.99"),
                false,
                5,
                LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 10, 15),
                true);
        var spotify = new RecurringPayment(
                "Spotify",
                SUBSCRIPTIONS,
                RecurrenceInterval.MONTHLY,
                Money.ofDollars("16.99"),
                false,
                5,
                LocalDate.of(2026, 9, 20),
                LocalDate.of(2026, 10, 20),
                true);

        var insights = generator.generate(
                new InsightRequest(cashflow, List.of(eatingOutSpike()), List.of(netflix, spotify), emergencyFund));

        assertThat(insights)
                .extracting(Insight::key)
                .startsWith("spending_exceeds_income")
                .contains("category_spike", "subscriptions", "emergency_fund_missing", "top_lifestyle_category");
        assertThat(insights)
                .filteredOn(i -> i.key().equals("subscriptions"))
                .singleElement()
                .satisfies(i -> assertThat(i.title()).isEqualTo("2 subscriptions cost $480 a year"));
        assertThat(insights)
                .filteredOn(i -> i.key().equals("category_spike"))
                .singleElement()
                .satisfies(i -> assertThat(i.message()).contains("$300 more than a typical month"));
    }

    @Test
    void celebratesAStrongSavingsRate() {
        var insights = generator.generate(new InsightRequest(
                cashflow(Money.ofDollars(5_000), Money.ofDollars(1_500)), List.of(), List.of(), null));

        assertThat(insights).extracting(Insight::key).containsExactly("strong_savings_rate");
        assertThat(insights.getFirst().title()).isEqualTo("You keep 30% of your income");
    }

    @Test
    void promptsNewUsersToConnectTheirBank() {
        var empty = new CashflowAnalysis(
                RANGE, List.of(), 0, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, BigDecimal.ZERO);

        assertThat(generator.generate(new InsightRequest(empty, List.of(), List.of(), null)))
                .extracting(Insight::key)
                .containsExactly("getting_started");
    }

    private static CashflowAnalysis cashflow(Money income, Money surplus) {
        return new CashflowAnalysis(
                RANGE, List.of(), 3, income, income.minus(surplus), Money.ZERO, Money.ZERO, surplus, BigDecimal.ZERO);
    }

    private static CategorySpending eatingOutSpike() {
        var months = RANGE.months();
        return new CategorySpending(
                EATING_OUT,
                Money.ofDollars(1_000),
                new BigDecimal("0.1"),
                Money.ofDollars(300),
                Money.ofDollars(250),
                List.of(
                        new CategorySpending.MonthAmount(months.get(0), Money.ofDollars(300)),
                        new CategorySpending.MonthAmount(months.get(1), Money.ofDollars(100)),
                        new CategorySpending.MonthAmount(months.get(2), Money.ofDollars(600))));
    }
}
