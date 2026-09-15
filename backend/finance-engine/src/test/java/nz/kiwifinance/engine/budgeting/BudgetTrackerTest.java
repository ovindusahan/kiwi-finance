package nz.kiwifinance.engine.budgeting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import nz.kiwifinance.engine.analysis.CategoryGroup;
import nz.kiwifinance.engine.analysis.CategoryRef;
import nz.kiwifinance.engine.analysis.TransactionRecord;
import nz.kiwifinance.engine.money.Money;
import org.junit.jupiter.api.Test;

class BudgetTrackerTest {

    private static final CategoryRef GROCERIES = new CategoryRef("groceries", "Groceries", CategoryGroup.ESSENTIALS);
    private static final CategoryRef EATING_OUT = new CategoryRef("eating-out", "Eating out", CategoryGroup.LIFESTYLE);
    private static final CategoryRef FUEL = new CategoryRef("fuel", "Fuel", CategoryGroup.ESSENTIALS);

    @Test
    void comparesSpendingWithLimitsAndPace() {
        LocalDate today = LocalDate.of(2026, 9, 10);
        List<TransactionRecord> transactions = List.of(
                spend(LocalDate.of(2026, 9, 2), "250", GROCERIES),
                spend(LocalDate.of(2026, 9, 5), "180", EATING_OUT),
                spend(LocalDate.of(2026, 9, 6), "130", FUEL),
                spend(LocalDate.of(2026, 8, 30), "500", GROCERIES));

        var progress = new BudgetTracker()
                .progress(
                        List.of(
                                new BudgetTracker.Limit(GROCERIES, Money.ofDollars(800)),
                                new BudgetTracker.Limit(EATING_OUT, Money.ofDollars(200)),
                                new BudgetTracker.Limit(FUEL, Money.ofDollars(120))),
                        transactions,
                        today);

        assertThat(progress.lines())
                .extracting(BudgetProgress.LineProgress::status)
                .containsExactly(
                        BudgetProgress.Status.ON_TRACK, BudgetProgress.Status.AT_RISK, BudgetProgress.Status.OVER);
        assertThat(progress.totalSpent()).isEqualTo(Money.ofDollars(560));
        assertThat(progress.totalRemaining()).isEqualTo(Money.ofDollars(560));
    }

    private static TransactionRecord spend(LocalDate date, String dollars, CategoryRef category) {
        return new TransactionRecord(date, Money.ofDollars(dollars).negate(), category, null, "Purchase", false);
    }
}
