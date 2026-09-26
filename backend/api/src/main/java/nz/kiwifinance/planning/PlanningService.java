package nz.kiwifinance.planning;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.account.Account;
import nz.kiwifinance.account.AccountType;
import nz.kiwifinance.analysis.FinancialSnapshot;
import nz.kiwifinance.analysis.FinancialSnapshotService;
import nz.kiwifinance.common.error.ApiException;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.emergencyfund.EmergencyFundService;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.nzrules.NzRules;
import nz.kiwifinance.engine.planning.AffordabilityCalculator;
import nz.kiwifinance.engine.planning.AffordabilityRequest;
import nz.kiwifinance.engine.planning.AffordabilityResult;
import nz.kiwifinance.engine.planning.LoanCalculator;
import nz.kiwifinance.goal.GoalService;
import nz.kiwifinance.user.UserProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlanningService {

    private final FinancialSnapshotService snapshots;
    private final EmergencyFundService emergencyFund;
    private final GoalService goals;
    private final PurchasePlanRepository plans;
    private final AffordabilityCalculator affordability = new AffordabilityCalculator();
    private final LoanCalculator loans = new LoanCalculator();

    @Transactional(readOnly = true)
    public PlanningResponses.Affordability assess(UUID userId, PlanningRequests.Purchase purchase) {
        return assess(snapshots.snapshot(userId), purchase);
    }

    PlanningResponses.Loan loan(PlanningRequests.Loan request) {
        return PlanningResponses.Loan.from(loans.quote(
                Money.ofCents(request.amountCents()),
                request.annualRate(),
                request.termMonths(),
                Money.ofCents(request.feesCents() == null ? 0 : request.feesCents())));
    }

    @Transactional(readOnly = true)
    List<PlanningResponses.PurchasePlan> plans(UUID userId) {
        FinancialSnapshot snapshot = snapshots.snapshot(userId);
        return plans.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(plan -> view(plan, snapshot))
                .toList();
    }

    @Transactional(readOnly = true)
    PlanningResponses.PurchasePlan plan(UUID userId, UUID planId) {
        return view(find(userId, planId), snapshots.snapshot(userId));
    }

    @Transactional
    PlanningResponses.PurchasePlan save(UUID userId, UUID planId, PlanningRequests.Purchase request) {
        PurchasePlan plan = planId == null ? new PurchasePlan(userId) : find(userId, planId);
        plan.apply(request);
        plans.save(plan);
        return view(plan, snapshots.snapshot(userId));
    }

    @Transactional
    void delete(UUID userId, UUID planId) {
        plans.delete(find(userId, planId));
    }

    /**
     * Turns a plan into a savings goal for the amount that needs saving, contributing what the
     * plan says is needed each month.
     */
    @Transactional
    PlanningResponses.PurchasePlan startSaving(UUID userId, UUID planId) {
        PurchasePlan plan = find(userId, planId);
        FinancialSnapshot snapshot = snapshots.snapshot(userId);
        PlanningResponses.Affordability assessment = assess(snapshot, plan.toRequest());
        if (plan.getGoalId() == null) {
            long monthly = assessment.requiredMonthlySaving() != null
                    ? assessment.requiredMonthlySaving().cents()
                    : assessment.monthlySavingCapacity().cents();
            var goal = goals.createForPurchase(
                    userId,
                    plan.getItemName(),
                    plan.isFirstHome(),
                    assessment.target().cents(),
                    plan.getDesiredDate(),
                    monthly);
            plan.linkGoal(goal.id());
        }
        return view(plan, snapshot);
    }

    private PlanningResponses.PurchasePlan view(PurchasePlan plan, FinancialSnapshot snapshot) {
        return new PlanningResponses.PurchasePlan(
                plan.getId(),
                plan.getItemName(),
                MoneyResponse.of(plan.getPriceCents()),
                plan.getDesiredDate(),
                plan.getFunding(),
                plan.getDepositCents() == null ? null : MoneyResponse.of(plan.getDepositCents()),
                plan.getLoanRate(),
                plan.getLoanTermMonths(),
                plan.getLoanFeesCents() == null ? null : MoneyResponse.of(plan.getLoanFeesCents()),
                plan.isFirstHome(),
                plan.getGoalId(),
                assess(snapshot, plan.toRequest()),
                plan.getCreatedAt());
    }

    private PlanningResponses.Affordability assess(FinancialSnapshot snapshot, PlanningRequests.Purchase purchase) {
        UserProfile profile = snapshot.profile();
        AffordabilityRequest.Financing financing = purchase.funding() == Funding.FINANCE
                ? new AffordabilityRequest.Financing(
                        Money.ofCents(purchase.depositCents()),
                        purchase.loanRate(),
                        purchase.loanTermMonths(),
                        Money.ofCents(purchase.loanFeesCents() == null ? 0 : purchase.loanFeesCents()))
                : null;
        AffordabilityResult result = affordability.assess(new AffordabilityRequest(
                purchase.itemName(),
                Money.ofCents(purchase.priceCents()),
                purchase.desiredDate(),
                snapshot.today(),
                snapshot.liquidBalance(),
                emergencyFund.plan(snapshot).target(),
                goals.reservedInLiquidAccounts(snapshot.userId()),
                snapshot.monthlySurplus(),
                goals.monthlyCommitments(snapshot.userId()),
                profile.getSavingsInterestRate(),
                profile.getPayFrequency(),
                snapshot.lifestyleSpending(),
                purchase.firstHome() ? kiwiSaverFirstHome(snapshot) : Money.ZERO,
                financing,
                snapshot.monthsOfData()));
        return PlanningResponses.Affordability.from(result, snapshot.assumptions());
    }

    /**
     * KiwiSaver that could go towards a first home: members of at least three years can withdraw
     * everything except the required minimum balance.
     */
    private static Money kiwiSaverFirstHome(FinancialSnapshot snapshot) {
        UserProfile profile = snapshot.profile();
        if (!profile.isFirstHomeBuyer() || !profile.isKiwiSaverMember() || profile.getKiwiSaverJoinedOn() == null) {
            return Money.ZERO;
        }
        var rules = NzRules.standard().forDate(snapshot.today()).kiwiSaver();
        LocalDate eligibleFrom = profile.getKiwiSaverJoinedOn().plusYears(rules.firstHomeMinimumMembershipYears());
        if (eligibleFrom.isAfter(snapshot.today())) {
            return Money.ZERO;
        }
        long balance = snapshot.accounts().stream()
                .filter(a -> a.getType() == AccountType.KIWISAVER)
                .mapToLong(Account::getCurrentBalanceCents)
                .sum();
        if (balance == 0 && profile.getKiwiSaverBalanceCents() != null) {
            balance = profile.getKiwiSaverBalanceCents();
        }
        return Money.max(Money.ZERO, Money.ofCents(balance).minus(rules.firstHomeMinimumRemainingBalance()));
    }

    private PurchasePlan find(UUID userId, UUID planId) {
        return plans.findByIdAndUserId(planId, userId).orElseThrow(() -> ApiException.notFound("Purchase plan"));
    }
}
