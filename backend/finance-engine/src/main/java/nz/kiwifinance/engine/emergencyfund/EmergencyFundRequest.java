package nz.kiwifinance.engine.emergencyfund;

import nz.kiwifinance.engine.money.Money;

/**
 * @param currentBalance money in accounts set aside for emergencies
 * @param monthlySurplus typical income minus typical spending, before any emergency fund saving
 */
public record EmergencyFundRequest(
        Money monthlyEssentialSpending,
        Money currentBalance,
        Money monthlySurplus,
        boolean variableIncome,
        boolean selfEmployed,
        boolean hasDependants,
        boolean singleIncomeHousehold,
        int monthsOfData) {}
