package nz.kiwifinance.analysis;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.account.Account;
import nz.kiwifinance.account.AccountService;
import nz.kiwifinance.account.AccountType;
import nz.kiwifinance.engine.analysis.CashflowAnalysis;
import nz.kiwifinance.engine.analysis.CashflowAnalyzer;
import nz.kiwifinance.engine.analysis.CategorySpending;
import nz.kiwifinance.engine.analysis.MonthSummary;
import nz.kiwifinance.engine.analysis.RecurringPayment;
import nz.kiwifinance.engine.analysis.RecurringPaymentDetector;
import nz.kiwifinance.engine.analysis.SpendingAnalyzer;
import nz.kiwifinance.engine.analysis.TransactionRecord;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.MonthRange;
import nz.kiwifinance.engine.time.NzTime;
import nz.kiwifinance.income.IncomeService;
import nz.kiwifinance.transaction.CashWithdrawalService;
import nz.kiwifinance.transaction.TransactionService;
import nz.kiwifinance.user.ProfileService;
import nz.kiwifinance.user.UserProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinancialSnapshotService {

    /**
     * Six complete months is long enough to smooth out one-off spending, and short enough to reflect
     * recent changes in income or rent.
     */
    public static final int WINDOW_MONTHS = 6;

    private static final int RECURRING_LOOKBACK_MONTHS = 13;

    private final TransactionService transactions;
    private final AccountService accounts;
    private final ProfileService profiles;
    private final IncomeService income;
    private final Clock clock;
    private final CashflowAnalyzer cashflowAnalyzer = new CashflowAnalyzer();
    private final SpendingAnalyzer spendingAnalyzer = new SpendingAnalyzer();
    private final RecurringPaymentDetector recurringDetector = new RecurringPaymentDetector();

    @Transactional(readOnly = true)
    public FinancialSnapshot snapshot(UUID userId) {
        LocalDate today = NzTime.today(clock);
        YearMonth current = YearMonth.from(today);
        MonthRange window = MonthRange.completeMonthsBefore(current, WINDOW_MONTHS);
        LocalDate historyStart = current.minusMonths(RECURRING_LOOKBACK_MONTHS).atDay(1);
        List<TransactionRecord> history = transactions.records(userId, historyStart, today);

        CashflowAnalysis cashflow = cashflowAnalyzer.analyse(history, window);
        MonthSummary thisMonth = cashflowAnalyzer
                .analyse(history, new MonthRange(current, current))
                .months()
                .getFirst();
        List<CategorySpending> spending = spendingAnalyzer.byCategory(history, window);
        // Cash withdrawals repeat, but they aren't bills: what the cash bought is asked about separately.
        List<RecurringPayment> recurring = recurringDetector.detect(history, today).stream()
                .filter(payment -> !CashWithdrawalService.looksLikeCash(payment.name(), null))
                .toList();

        Money expected = income.expectedMonthlyTakeHome(userId);
        IncomeBasis basis;
        Money monthlyIncome;
        Money surplus;
        if (cashflow.monthsOfData() > 0 && cashflow.typicalIncome().isPositive()) {
            basis = IncomeBasis.TRANSACTIONS;
            monthlyIncome = cashflow.typicalIncome();
            surplus = cashflow.typicalSurplus();
        } else if (expected.isPositive()) {
            basis = IncomeBasis.INCOME_SOURCES;
            monthlyIncome = expected;
            surplus = expected.minus(cashflow.typicalSpending());
        } else {
            basis = IncomeBasis.NONE;
            monthlyIncome = Money.ZERO;
            surplus = cashflow.typicalSpending().negate();
        }

        // Without categories we can't tell essentials apart, so treat all spending as essential
        // rather than understate how much an emergency fund needs to cover.
        Money essentials = cashflow.typicalEssentialSpending().isPositive()
                ? cashflow.typicalEssentialSpending()
                : cashflow.typicalSpending();

        UserProfile profile = profiles.get(userId);
        List<Account> open = accounts.list(userId, false);
        Money liquid = sum(open.stream().filter(Account::isLiquid).toList());
        Money emergencyFund =
                sum(open.stream().filter(Account::isIncludedInEmergencyFund).toList());
        Money debt = sum(open.stream()
                        .filter(a -> a.getType() == AccountType.CREDIT_CARD || a.getType() == AccountType.LOAN)
                        .filter(a -> a.getCurrentBalanceCents() < 0)
                        .toList())
                .negate();
        Money netWorth = sum(open);
        boolean hasKiwiSaverAccount = open.stream().anyMatch(a -> a.getType() == AccountType.KIWISAVER);
        if (!hasKiwiSaverAccount && profile.getKiwiSaverBalanceCents() != null) {
            netWorth = netWorth.plus(Money.ofCents(profile.getKiwiSaverBalanceCents()));
        }

        return new FinancialSnapshot(
                userId,
                today,
                window,
                history,
                cashflow,
                thisMonth,
                spending,
                recurring,
                monthlyIncome,
                basis,
                surplus,
                essentials,
                open,
                Money.max(Money.ZERO, liquid),
                Money.max(Money.ZERO, emergencyFund),
                debt,
                netWorth,
                profile);
    }

    private static Money sum(List<Account> accounts) {
        return Money.ofCents(
                accounts.stream().mapToLong(Account::getCurrentBalanceCents).sum());
    }
}
