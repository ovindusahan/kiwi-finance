package nz.kiwifinance.emergencyfund;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.account.Account;
import nz.kiwifinance.account.AccountService;
import nz.kiwifinance.account.AccountType;
import nz.kiwifinance.account.ManagedBy;
import nz.kiwifinance.analysis.FinancialSnapshot;
import nz.kiwifinance.analysis.FinancialSnapshotService;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.engine.emergencyfund.EmergencyFundCalculator;
import nz.kiwifinance.engine.emergencyfund.EmergencyFundPlan;
import nz.kiwifinance.engine.emergencyfund.EmergencyFundRequest;
import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.time.PayFrequency;
import nz.kiwifinance.preferences.PreferencesService;
import nz.kiwifinance.user.UserProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmergencyFundService {

    private final FinancialSnapshotService snapshots;
    private final AccountService accounts;
    private final PreferencesService preferences;
    private final EmergencyFundCalculator calculator = new EmergencyFundCalculator();

    @Transactional(readOnly = true)
    public EmergencyFundResponse forUser(UUID userId) {
        return forSnapshot(snapshots.snapshot(userId));
    }

    @Transactional
    public EmergencyFundResponse chooseAccount(UUID userId, UUID accountId) {
        accounts.setEmergencyFundAccount(userId, accountId);
        return forUser(userId);
    }

    public EmergencyFundPlan plan(FinancialSnapshot snapshot) {
        UserProfile profile = snapshot.profile();
        return calculator.plan(new EmergencyFundRequest(
                snapshot.monthlyEssentials(),
                snapshot.emergencyFundBalance(),
                snapshot.monthlySurplus(),
                snapshot.cashflow().hasVariableIncome(),
                profile.getEmploymentType().hasIrregularWork(),
                profile.getDependants() > 0,
                profile.isSingleIncomeHousehold(),
                snapshot.monthsOfData()));
    }

    public EmergencyFundResponse forSnapshot(FinancialSnapshot snapshot) {
        EmergencyFundPlan plan = plan(snapshot);
        Optional<Account> fundAccount = snapshot.accounts().stream()
                .filter(Account::isIncludedInEmergencyFund)
                .findFirst();
        Optional<Account> suggested = fundAccount.isPresent() ? Optional.empty() : suggestAccount(snapshot.accounts());
        PayFrequency frequency = snapshot.profile().getPayFrequency();
        return new EmergencyFundResponse(
                MoneyResponse.of(plan.monthlyEssentialSpending()),
                plan.targetMonths(),
                MoneyResponse.of(plan.target()),
                MoneyResponse.of(plan.current()),
                MoneyResponse.of(plan.shortfall()),
                plan.progress(),
                plan.monthsCovered(),
                plan.status(),
                MoneyResponse.of(plan.suggestedMonthlyContribution()),
                MoneyResponse.of(PayFrequency.MONTHLY.convert(plan.suggestedMonthlyContribution(), frequency)),
                frequency.periodName(),
                plan.monthsToTarget(),
                plan.milestones().stream()
                        .map(m -> new EmergencyFundResponse.Milestone(
                                m.label(), MoneyResponse.of(m.amount()), m.reached()))
                        .toList(),
                plan.reasons(),
                fundAccount.map(EmergencyFundService::view).orElse(null),
                suggested.map(EmergencyFundService::view).orElse(null),
                fundAccount.isEmpty() && preferences.remindAboutEmergencyFund(snapshot.userId()),
                new Explanation(
                        plan.explanation().summary(),
                        plan.explanation().steps(),
                        Stream.concat(plan.explanation().assumptions().stream(), snapshot.assumptions().stream())
                                .toList()));
    }

    /**
     * The best home for an emergency fund among the person's open accounts: a savings account they
     * can reach quickly, preferring the one that already holds the most.
     */
    static Optional<Account> suggestAccount(List<Account> open) {
        return open.stream()
                .filter(Account::isLiquid)
                .filter(account -> account.getType() == AccountType.SAVINGS)
                .max(Comparator.comparingLong(Account::getCurrentBalanceCents))
                .or(() -> open.stream()
                        .filter(Account::isLiquid)
                        .filter(account -> account.getType() != AccountType.CREDIT_CARD)
                        .max(Comparator.comparingLong(Account::getCurrentBalanceCents)));
    }

    private static EmergencyFundResponse.FundAccount view(Account account) {
        return new EmergencyFundResponse.FundAccount(
                account.getId(),
                account.getName(),
                account.getInstitution(),
                MoneyResponse.of(account.getCurrentBalanceCents()),
                account.getManagedBy() == ManagedBy.BANK_FEED);
    }
}
