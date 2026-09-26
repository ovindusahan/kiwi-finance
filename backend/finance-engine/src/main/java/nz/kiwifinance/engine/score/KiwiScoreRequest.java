package nz.kiwifinance.engine.score;

import java.math.BigDecimal;
import nz.kiwifinance.engine.money.Money;

/**
 * The facts the Kiwi Score is built from.
 *
 * @param savingsRate typical share of income left after spending; negative when overspending
 * @param emergencyFundProgress current emergency fund as a fraction of its target
 * @param monthsWithSurplus recent complete months where income exceeded spending
 * @param monthsAssessed how many recent complete months were checked
 * @param budgetLinesOnTrack lines within their limit last month, or {@code null} without a budget
 * @param budgetLines number of lines in the budget, or {@code null} without a budget
 * @param consumerDebt money owed on credit cards and personal loans, as a positive amount
 * @param activeGoals goals currently being saved for
 * @param goalsOnTrack active goals projected to finish on time
 */
public record KiwiScoreRequest(
        BigDecimal savingsRate,
        BigDecimal emergencyFundProgress,
        int monthsWithSurplus,
        int monthsAssessed,
        Integer budgetLinesOnTrack,
        Integer budgetLines,
        Money consumerDebt,
        Money monthlyIncome,
        int activeGoals,
        int goalsOnTrack) {}
