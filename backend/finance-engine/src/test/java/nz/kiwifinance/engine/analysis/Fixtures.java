package nz.kiwifinance.engine.analysis;

import java.time.LocalDate;
import nz.kiwifinance.engine.money.Money;

public final class Fixtures {

    public static final CategoryRef SALARY = new CategoryRef("salary", "Salary & wages", CategoryGroup.INCOME);
    public static final CategoryRef RENT = new CategoryRef("rent", "Rent", CategoryGroup.ESSENTIALS);
    public static final CategoryRef GROCERIES = new CategoryRef("groceries", "Groceries", CategoryGroup.ESSENTIALS);
    public static final CategoryRef EATING_OUT = new CategoryRef("eating-out", "Eating out", CategoryGroup.LIFESTYLE);
    public static final CategoryRef SUBSCRIPTIONS =
            new CategoryRef("subscriptions", "Subscriptions", CategoryGroup.LIFESTYLE);
    public static final CategoryRef SAVINGS = new CategoryRef("savings", "Savings", CategoryGroup.SAVINGS);

    private Fixtures() {}

    public static TransactionRecord spend(LocalDate date, String dollars, CategoryRef category, String merchant) {
        return new TransactionRecord(date, Money.ofDollars(dollars).negate(), category, merchant, merchant, false);
    }

    public static TransactionRecord earn(LocalDate date, String dollars) {
        return new TransactionRecord(date, Money.ofDollars(dollars), SALARY, "Employer Ltd", "SALARY", false);
    }

    public static TransactionRecord transfer(LocalDate date, String dollars) {
        return new TransactionRecord(date, Money.ofDollars(dollars).negate(), null, null, "TRANSFER TO SAVINGS", true);
    }
}
