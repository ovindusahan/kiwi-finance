package nz.kiwifinance.goal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.account.Account;
import nz.kiwifinance.account.AccountService;
import nz.kiwifinance.common.error.ApiException;
import nz.kiwifinance.common.error.ErrorCode;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.planning.GoalProjection;
import nz.kiwifinance.engine.planning.GoalProjector;
import nz.kiwifinance.engine.time.NzTime;
import nz.kiwifinance.user.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoalService {

    private static final int RECENT_MONTHS = 3;
    private static final String STARTING_AMOUNT = "Starting amount";

    private final GoalRepository goals;
    private final GoalContributionRepository contributions;
    private final AccountService accounts;
    private final ProfileService profiles;
    private final Clock clock;
    private final GoalProjector projector = new GoalProjector();

    @Transactional(readOnly = true)
    public List<GoalResponses.View> list(UUID userId) {
        Map<UUID, Account> accountById = accounts.byId(userId);
        BigDecimal rate = profiles.get(userId).getSavingsInterestRate();
        return goals.findByUserIdOrderByPriorityAscCreatedAtAsc(userId).stream()
                .filter(goal -> goal.getStatus() != GoalStatus.ARCHIVED)
                .map(goal -> view(goal, accountById, rate))
                .toList();
    }

    @Transactional(readOnly = true)
    GoalResponses.View get(UUID userId, UUID goalId) {
        return view(
                find(userId, goalId),
                accounts.byId(userId),
                profiles.get(userId).getSavingsInterestRate());
    }

    @Transactional
    public GoalResponses.View create(UUID userId, GoalRequests.Save request) {
        validateLink(userId, request.linkedAccountId());
        Goal goal = new Goal(userId);
        goal.apply(request);
        goals.save(goal);
        if (request.linkedAccountId() == null
                && request.startingAmountCents() != null
                && request.startingAmountCents() > 0) {
            contributions.save(new GoalContribution(
                    goal.getId(), userId, request.startingAmountCents(), NzTime.today(clock), STARTING_AMOUNT));
        }
        goal.markAchievedIfReached(saved(goal, accounts.byId(userId)).cents(), clock.instant());
        return get(userId, goal.getId());
    }

    /**
     * Creates a goal to save for a planned purchase.
     */
    @Transactional
    public GoalResponses.View createForPurchase(
            UUID userId,
            String name,
            boolean firstHome,
            long targetCents,
            LocalDate targetDate,
            long monthlyContributionCents) {
        return create(
                userId,
                new GoalRequests.Save(
                        name,
                        firstHome ? GoalType.HOUSE_DEPOSIT : GoalType.PURCHASE,
                        targetCents,
                        targetDate,
                        2,
                        monthlyContributionCents,
                        null,
                        null));
    }

    @Transactional
    GoalResponses.View update(UUID userId, UUID goalId, GoalRequests.Save request) {
        validateLink(userId, request.linkedAccountId());
        Goal goal = find(userId, goalId);
        goal.apply(request);
        goal.markAchievedIfReached(saved(goal, accounts.byId(userId)).cents(), clock.instant());
        return get(userId, goalId);
    }

    @Transactional
    GoalResponses.View changeStatus(UUID userId, UUID goalId, GoalStatus status) {
        find(userId, goalId).changeStatus(status);
        return get(userId, goalId);
    }

    @Transactional
    void delete(UUID userId, UUID goalId) {
        goals.delete(find(userId, goalId));
    }

    @Transactional
    GoalResponses.View contribute(UUID userId, UUID goalId, GoalRequests.Contribute request) {
        Goal goal = find(userId, goalId);
        if (goal.getStatus() == GoalStatus.ARCHIVED) {
            throw new ApiException(ErrorCode.GOAL_NOT_ACTIVE, "Restore this goal before adding to it.");
        }
        if (goal.getLinkedAccountId() != null) {
            throw new ApiException(
                    ErrorCode.GOAL_NOT_ACTIVE,
                    "This goal follows its linked account. Move money into that account instead.");
        }
        if (request.amountCents() == 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Enter an amount to add or withdraw.");
        }
        LocalDate date = request.contributedOn() == null ? NzTime.today(clock) : request.contributedOn();
        contributions.save(new GoalContribution(goalId, userId, request.amountCents(), date, request.note()));
        goal.markAchievedIfReached(contributions.totalFor(goalId), clock.instant());
        return get(userId, goalId);
    }

    @Transactional(readOnly = true)
    List<GoalResponses.Contribution> contributions(UUID userId, UUID goalId) {
        find(userId, goalId);
        return contributions.findByGoalIdOrderByContributedOnDescCreatedAtDesc(goalId).stream()
                .map(c -> new GoalResponses.Contribution(
                        c.getId(), MoneyResponse.of(c.getAmountCents()), c.getContributedOn(), c.getNote()))
                .toList();
    }

    /**
     * What active goals expect to receive each month, which planning treats as already spoken for.
     */
    @Transactional(readOnly = true)
    public Money monthlyCommitments(UUID userId) {
        return Money.ofCents(goals.findByUserIdOrderByPriorityAscCreatedAtAsc(userId).stream()
                .filter(Goal::isActive)
                .mapToLong(Goal::getMonthlyContributionCents)
                .sum());
    }

    /**
     * Money already set aside for active goals that sits in everyday or savings accounts, so a
     * purchase check doesn't count it as spare.
     */
    @Transactional(readOnly = true)
    public Money reservedInLiquidAccounts(UUID userId) {
        Map<UUID, Account> accountById = accounts.byId(userId);
        return Money.sum(goals.findByUserIdOrderByPriorityAscCreatedAtAsc(userId).stream()
                .filter(Goal::isActive)
                .filter(goal -> goal.getLinkedAccountId() == null
                        || Optional.ofNullable(accountById.get(goal.getLinkedAccountId()))
                                .map(Account::isLiquid)
                                .orElse(false))
                .map(goal -> Money.max(Money.ZERO, saved(goal, accountById)))
                .toList());
    }

    @Transactional(readOnly = true)
    public GoalStats stats(UUID userId) {
        List<GoalResponses.View> views = list(userId);
        int active = (int)
                views.stream().filter(v -> v.status() == GoalStatus.ACTIVE).count();
        int onTrack = (int) views.stream()
                .filter(v ->
                        v.status() == GoalStatus.ACTIVE && v.projection().status() == GoalProjection.Status.ON_TRACK)
                .count();
        int achieved = (int) goals.findByUserIdOrderByPriorityAscCreatedAtAsc(userId).stream()
                .filter(g -> g.getStatus() == GoalStatus.ACHIEVED)
                .count();
        int created = goals.findByUserIdOrderByPriorityAscCreatedAtAsc(userId).size();
        return new GoalStats(created, active, onTrack, achieved);
    }

    public record GoalStats(int created, int active, int onTrack, int achieved) {}

    private GoalResponses.View view(Goal goal, Map<UUID, Account> accountById, BigDecimal rate) {
        LocalDate today = NzTime.today(clock);
        Money target = Money.ofCents(goal.getTargetCents());
        Money saved = saved(goal, accountById);
        Money monthly = goal.getMonthlyContributionCents() > 0
                ? Money.ofCents(goal.getMonthlyContributionCents())
                : recentMonthlyAverage(goal, today);
        GoalProjection projection = projector.project(target, saved, monthly, goal.getTargetDate(), today, rate);
        Account linked = goal.getLinkedAccountId() == null ? null : accountById.get(goal.getLinkedAccountId());
        return new GoalResponses.View(
                goal.getId(),
                goal.getName(),
                goal.getType(),
                MoneyResponse.of(target),
                goal.getTargetDate(),
                goal.getTargetDate() == null ? null : Math.max(0, ChronoUnit.DAYS.between(today, goal.getTargetDate())),
                goal.getPriority(),
                goal.getStatus(),
                MoneyResponse.of(goal.getMonthlyContributionCents()),
                goal.getLinkedAccountId(),
                linked == null ? null : linked.getName(),
                MoneyResponse.of(saved),
                MoneyResponse.of(projection.remaining()),
                projection.progress(),
                new GoalResponses.Projection(
                        projection.status(),
                        projection.monthsToGoal(),
                        projection.projectedDate(),
                        MoneyResponse.of(projection.requiredMonthly()),
                        MoneyResponse.of(monthly),
                        projection.milestones().stream()
                                .map(m -> new GoalResponses.Milestone(
                                        m.percent(), MoneyResponse.of(m.amount()), m.reached()))
                                .toList(),
                        projection.explanation()),
                goal.getAchievedAt(),
                goal.getCreatedAt());
    }

    private Money saved(Goal goal, Map<UUID, Account> accountById) {
        if (goal.getLinkedAccountId() != null) {
            Account account = accountById.get(goal.getLinkedAccountId());
            return account == null ? Money.ZERO : Money.ofCents(account.getCurrentBalanceCents());
        }
        return Money.ofCents(contributions.totalFor(goal.getId()));
    }

    private Money recentMonthlyAverage(Goal goal, LocalDate today) {
        if (goal.getLinkedAccountId() != null) {
            return Money.ZERO;
        }
        LocalDate since = today.minusMonths(RECENT_MONTHS);
        long recent = contributions.findByGoalIdOrderByContributedOnDescCreatedAtDesc(goal.getId()).stream()
                .filter(c -> c.getContributedOn().isAfter(since))
                .filter(c -> !STARTING_AMOUNT.equals(c.getNote()))
                .mapToLong(GoalContribution::getAmountCents)
                .sum();
        return Money.max(Money.ZERO, Money.ofCents(recent).dividedBy(RECENT_MONTHS));
    }

    private Goal find(UUID userId, UUID goalId) {
        return goals.findByIdAndUserId(goalId, userId).orElseThrow(() -> ApiException.notFound("Goal"));
    }

    private void validateLink(UUID userId, UUID accountId) {
        if (accountId != null) {
            accounts.get(userId, accountId);
        }
    }
}
