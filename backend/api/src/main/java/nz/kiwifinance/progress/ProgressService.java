package nz.kiwifinance.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.analysis.FinancialSnapshot;
import nz.kiwifinance.analysis.FinancialSnapshotService;
import nz.kiwifinance.bankfeed.BankConnectionService;
import nz.kiwifinance.bankfeed.BankFeedProvider;
import nz.kiwifinance.budget.BudgetResponses;
import nz.kiwifinance.budget.BudgetService;
import nz.kiwifinance.emergencyfund.EmergencyFundService;
import nz.kiwifinance.engine.analysis.MonthSummary;
import nz.kiwifinance.engine.analysis.TransactionRecord;
import nz.kiwifinance.engine.budgeting.BudgetProgress;
import nz.kiwifinance.engine.emergencyfund.EmergencyFundPlan;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.progress.AchievementEvaluator;
import nz.kiwifinance.engine.progress.AchievementFacts;
import nz.kiwifinance.engine.progress.StreakCalculator;
import nz.kiwifinance.engine.progress.Streaks;
import nz.kiwifinance.engine.score.KiwiScore;
import nz.kiwifinance.engine.score.KiwiScoreCalculator;
import nz.kiwifinance.engine.score.KiwiScoreRequest;
import nz.kiwifinance.goal.GoalService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The person's Kiwi Score, habit streaks and achievements, all derived from their current data.
 */
@Service
@RequiredArgsConstructor
public class ProgressService {

    private static final int MONTHS_ASSESSED = 3;

    private final FinancialSnapshotService snapshots;
    private final EmergencyFundService emergencyFund;
    private final BudgetService budgets;
    private final GoalService goals;
    private final BankConnectionService bankConnections;
    private final KiwiScoreCalculator scoreCalculator = new KiwiScoreCalculator();
    private final StreakCalculator streakCalculator = new StreakCalculator();
    private final AchievementEvaluator achievementEvaluator = new AchievementEvaluator();

    @Transactional(readOnly = true)
    public ProgressResponse forUser(UUID userId) {
        return forSnapshot(snapshots.snapshot(userId));
    }

    @Transactional(readOnly = true)
    public ProgressResponse forSnapshot(FinancialSnapshot snapshot) {
        UUID userId = snapshot.userId();
        EmergencyFundPlan fund = emergencyFund.plan(snapshot);
        Optional<BudgetResponses.Progress> lastMonth = budgets.lastMonth(userId, snapshot.today());
        GoalService.GoalStats goalStats = goals.stats(userId);
        BigDecimal savingsRate = savingsRate(snapshot);

        List<MonthSummary> recent = snapshot.cashflow().months().stream()
                .filter(MonthSummary::hasActivity)
                .toList();
        recent = recent.subList(Math.max(0, recent.size() - MONTHS_ASSESSED), recent.size());
        int monthsWithSurplus =
                (int) recent.stream().filter(m -> m.net().isPositive()).count();

        KiwiScore score = scoreCalculator.calculate(new KiwiScoreRequest(
                savingsRate,
                fund.progress(),
                monthsWithSurplus,
                recent.size(),
                lastMonth
                        .map(p -> (int) p.lines().stream()
                                .filter(l -> l.status() != BudgetProgress.Status.OVER)
                                .count())
                        .orElse(null),
                lastMonth.map(p -> p.lines().size()).orElse(null),
                snapshot.consumerDebt(),
                snapshot.monthlyIncome(),
                goalStats.active(),
                goalStats.onTrack()));

        Streaks streaks = streakCalculator.calculate(
                snapshot.history(),
                snapshot.cashflow(),
                budgets.lifestyleAllowance(userId).orElse(null),
                snapshot.today());

        int uncategorised = (int) snapshot.history().stream()
                .filter(t -> t.flow() == TransactionRecord.Flow.SPENDING && t.category() == null)
                .count();
        var achievements = achievementEvaluator
                .evaluate(new AchievementFacts(
                        !snapshot.history().isEmpty(),
                        bankConnections.current(userId, BankFeedProvider.AKAHU).isPresent(),
                        budgets.current(userId, null).isPresent(),
                        lastMonth
                                .map(p ->
                                        p.totalSpent().cents() <= p.totalLimit().cents())
                                .orElse(false),
                        fund.current(),
                        fund.monthlyEssentialSpending(),
                        fund.progress(),
                        goalStats.created(),
                        goalStats.achieved(),
                        savingsRate,
                        uncategorised,
                        streaks))
                .stream()
                .map(a -> new ProgressResponse.Achievement(
                        a.key(), a.title(), a.description(), a.icon(), a.unlocked(), a.progress()))
                .toList();

        return new ProgressResponse(
                ProgressResponse.Score.from(score),
                streaks,
                achievements,
                (int) achievements.stream()
                        .filter(ProgressResponse.Achievement::unlocked)
                        .count(),
                achievements.size());
    }

    static BigDecimal savingsRate(FinancialSnapshot snapshot) {
        Money income = snapshot.monthlyIncome();
        if (income.isPositive()) {
            return snapshot.monthlySurplus().ratioOf(income).setScale(4, RoundingMode.HALF_UP);
        }
        return snapshot.cashflow().typicalSpending().isPositive() ? BigDecimal.ONE.negate() : BigDecimal.ZERO;
    }
}
