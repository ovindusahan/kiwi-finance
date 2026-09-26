package nz.kiwifinance.engine.insights;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import nz.kiwifinance.engine.analysis.CashflowAnalysis;
import nz.kiwifinance.engine.analysis.CategoryGroup;
import nz.kiwifinance.engine.analysis.CategoryRef;
import nz.kiwifinance.engine.analysis.MonthSummary;
import nz.kiwifinance.engine.analysis.RecurrenceInterval;
import nz.kiwifinance.engine.analysis.RecurringPayment;
import nz.kiwifinance.engine.emergencyfund.EmergencyFundCalculator;
import nz.kiwifinance.engine.emergencyfund.EmergencyFundPlan;
import nz.kiwifinance.engine.emergencyfund.EmergencyFundRequest;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.planning.GoalPlan;
import nz.kiwifinance.engine.time.MonthRange;
import org.junit.jupiter.api.Test;

class PersonalInsightsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private static final CategoryRef TAKEAWAYS = new CategoryRef("takeaways", "Takeaways", CategoryGroup.LIFESTYLE);
    private static final CategoryRef GROCERIES = new CategoryRef("groceries", "Groceries", CategoryGroup.ESSENTIALS);
    private static final CategoryRef RENT = new CategoryRef("rent", "Rent", CategoryGroup.ESSENTIALS);
    private static final CashflowAnalysis CASHFLOW = new CashflowAnalysis(
            MonthRange.endingWith(YearMonth.of(2026, 9), 6),
            List.of(),
            6,
            Money.ofDollars(5_000),
            Money.ofDollars(4_000),
            Money.ofDollars(3_000),
            Money.ofDollars(1_000),
            Money.ofDollars(1_000),
            BigDecimal.ZERO);
    private static final RecurringPayment RENT_DUE = new RecurringPayment(
            "Rent",
            RENT,
            RecurrenceInterval.FORTNIGHTLY,
            Money.ofDollars(900),
            false,
            12,
            TODAY.minusDays(10),
            TODAY.plusDays(4),
            false);

    private final InsightGenerator generator = new InsightGenerator();

    @Test
    void speaksToTheBudgetBillsAndGoals() {
        var budget = new InsightContext.Budget(
                20,
                31,
                Money.ofDollars(2_000),
                Money.ofDollars(1_500),
                List.of(
                        new InsightContext.Line(TAKEAWAYS, Money.ofDollars(100), Money.ofDollars(160)),
                        new InsightContext.Line(GROCERIES, Money.ofDollars(600), Money.ofDollars(560))));
        var goal = new InsightContext.GoalNote(
                "House deposit",
                GoalPlan.Kind.EXTEND_DATE,
                GoalPlan.Urgency.LATER,
                "Move the date to March 2031",
                "Finishing on time needs more than you have spare.");

        var insights = generate(context(Money.ofDollars(400), Money.ZERO, budget, List.of(goal)), fund(0));

        assertThat(insights)
                .extracting(Insight::key)
                .contains("bills_exceed_balance", "budget_over", "budget_running_ahead", "goal_extend_date");
        assertThat(insights)
                .filteredOn(insight -> insight.key().equals("bills_exceed_balance"))
                .singleElement()
                .satisfies(insight -> {
                    assertThat(insight.amount()).isEqualTo(Money.ofDollars(500));
                    assertThat(insight.action()).isEqualTo(Insight.Action.MOVE_MONEY);
                });
        assertThat(insights)
                .filteredOn(insight -> insight.key().equals("budget_over"))
                .singleElement()
                .satisfies(insight -> {
                    assertThat(insight.title()).isEqualTo("You're over your Takeaways budget");
                    assertThat(insight.category()).isEqualTo(TAKEAWAYS);
                });
        assertThat(insights)
                .filteredOn(insight -> insight.key().equals("goal_extend_date"))
                .singleElement()
                .satisfies(
                        insight -> assertThat(insight.title()).isEqualTo("House deposit: move the date to March 2031"));
    }

    @Test
    void adaptsWhenMoneyMoves() {
        var before = generate(context(Money.ofDollars(400), Money.ZERO, null, List.of()), fund(9_000));
        var after = generate(context(Money.ofDollars(2_000), Money.ofDollars(1_600), null, List.of()), fund(7_400));

        assertThat(before)
                .extracting(Insight::key)
                .contains("bills_exceed_balance")
                .doesNotContain("emergency_fund_used");
        assertThat(after)
                .extracting(Insight::key)
                .contains("emergency_fund_used")
                .doesNotContain("bills_exceed_balance");
        assertThat(after)
                .filteredOn(insight -> insight.key().equals("emergency_fund_used"))
                .singleElement()
                .satisfies(insight -> assertThat(insight.message()).contains("$1,600"));
    }

    @Test
    void suggestsMovingSpareCashAndClearingDebt() {
        var context = new InsightContext(
                TODAY,
                month(Money.ofDollars(2_000)),
                Money.ofDollars(12_000),
                Money.ofDollars(1_500),
                true,
                Money.ZERO,
                null,
                List.of());

        var insights = generate(context, fund(2_000));

        assertThat(insights).extracting(Insight::key).contains("spare_cash", "debt_clearable", "budget_missing");
        assertThat(insights)
                .filteredOn(insight -> insight.key().equals("spare_cash"))
                .singleElement()
                .satisfies(insight -> assertThat(insight.amount()).isEqualTo(Money.ofDollars(6_000)));
    }

    @Test
    void flagsSpendingAheadOfTheUsualPace() {
        var context = new InsightContext(
                TODAY,
                month(Money.ofDollars(3_600)),
                Money.ofDollars(5_000),
                Money.ZERO,
                true,
                Money.ZERO,
                null,
                List.of());

        assertThat(generate(context, fund(9_000))).extracting(Insight::key).contains("spending_pace_high");
    }

    private List<Insight> generate(InsightContext context, EmergencyFundPlan fund) {
        return generator.generate(new InsightRequest(CASHFLOW, List.of(), List.of(RENT_DUE), fund, context));
    }

    private static InsightContext context(
            Money spendable, Money withdrawn, InsightContext.Budget budget, List<InsightContext.GoalNote> goals) {
        return new InsightContext(
                TODAY, month(Money.ofDollars(1_500)), spendable, Money.ZERO, true, withdrawn, budget, goals);
    }

    private static MonthSummary month(Money spending) {
        return new MonthSummary(
                YearMonth.from(TODAY),
                Money.ofDollars(5_000),
                spending,
                spending,
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                30);
    }

    private static EmergencyFundPlan fund(long balance) {
        return new EmergencyFundCalculator()
                .plan(new EmergencyFundRequest(
                        Money.ofDollars(3_000),
                        Money.ofDollars(balance),
                        Money.ofDollars(1_000),
                        false,
                        false,
                        false,
                        false,
                        6));
    }
}
