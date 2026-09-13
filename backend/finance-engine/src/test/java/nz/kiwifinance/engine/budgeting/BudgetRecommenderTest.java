package nz.kiwifinance.engine.budgeting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import nz.kiwifinance.engine.analysis.CategoryGroup;
import nz.kiwifinance.engine.analysis.CategoryRef;
import nz.kiwifinance.engine.analysis.CategorySpending;
import nz.kiwifinance.engine.money.Money;
import org.junit.jupiter.api.Test;

class BudgetRecommenderTest {

    private static final CategoryRef RENT = new CategoryRef("rent", "Rent", CategoryGroup.ESSENTIALS);
    private static final CategoryRef EATING_OUT = new CategoryRef("eating-out", "Eating out", CategoryGroup.LIFESTYLE);
    private static final CategoryRef INSURANCE = new CategoryRef("insurance", "Insurance", CategoryGroup.ESSENTIALS);

    private final BudgetRecommender recommender = new BudgetRecommender();

    @Test
    void budgetsEssentialsAtTypicalSpendingAndKeepsLifestyleWhenTargetIsMet() {
        var request = new BudgetRequest(
                Money.ofDollars(6_000),
                List.of(spending(RENT, 2_400, 2_400, 2_400), spending(EATING_OUT, 300, 250, 350)),
                3,
                BudgetRequest.DEFAULT_SAVINGS_RATE,
                Money.ZERO);

        var result = recommender.recommend(request);

        assertThat(result.lines())
                .extracting(BudgetRecommendation.Line::recommended)
                .containsExactly(Money.ofDollars(2_400), Money.ofDollars(300));
        assertThat(result.plannedSavings()).isEqualTo(Money.ofDollars(3_300));
        assertThat(result.meetsTarget()).isTrue();
    }

    @Test
    void trimsLifestyleTowardsLowerMonthsWhenSavingsFallShort() {
        var request = new BudgetRequest(
                Money.ofDollars(3_500),
                List.of(spending(RENT, 2_400, 2_400, 2_400, 2_400), spending(EATING_OUT, 200, 600, 650, 700)),
                4,
                BudgetRequest.DEFAULT_SAVINGS_RATE,
                Money.ZERO);

        var result = recommender.recommend(request);

        var eatingOut = result.lines().stream()
                .filter(l -> l.category() == EATING_OUT)
                .findFirst()
                .orElseThrow();
        assertThat(eatingOut.recommended()).isLessThan(Money.ofDollars(630));
        assertThat(eatingOut.recommended()).isGreaterThanOrEqualTo(Money.ofDollars(500));
        assertThat(eatingOut.isTrimmed()).isTrue();
        assertThat(eatingOut.rationale()).contains("lower-spending months");
        assertThat(result.explanation().summary()).isNotBlank();
    }

    @Test
    void spreadsIrregularCostsAcrossTheYear() {
        var request = new BudgetRequest(
                Money.ofDollars(6_000),
                List.of(spending(INSURANCE, 0, 0, 1_200, 0, 0, 0)),
                6,
                BudgetRequest.DEFAULT_SAVINGS_RATE,
                Money.ZERO);

        var line = recommender.recommend(request).lines().getFirst();

        assertThat(line.recommended()).isEqualTo(Money.ofDollars(200));
    }

    @Test
    void reservesEnoughForGoalContributions() {
        var request = new BudgetRequest(
                Money.ofDollars(4_000),
                List.of(spending(RENT, 2_000, 2_000, 2_000)),
                3,
                new BigDecimal("0.10"),
                Money.ofDollars(1_000));

        assertThat(recommender.recommend(request).targetSavings()).isEqualTo(Money.ofDollars(1_000));
    }

    private static CategorySpending spending(CategoryRef category, long... monthlyDollars) {
        List<CategorySpending.MonthAmount> monthly = new java.util.ArrayList<>();
        YearMonth month = YearMonth.of(2026, 9).minusMonths(monthlyDollars.length - 1L);
        for (long dollars : monthlyDollars) {
            monthly.add(new CategorySpending.MonthAmount(month, Money.ofDollars(dollars)));
            month = month.plusMonths(1);
        }
        List<Money> amounts =
                monthly.stream().map(CategorySpending.MonthAmount::amount).toList();
        return new CategorySpending(
                category,
                Money.sum(amounts),
                BigDecimal.ZERO,
                nz.kiwifinance.engine.analysis.Stats.median(amounts),
                nz.kiwifinance.engine.analysis.Stats.percentile(amounts, 25),
                monthly);
    }
}
