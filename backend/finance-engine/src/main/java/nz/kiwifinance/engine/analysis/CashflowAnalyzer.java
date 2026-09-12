package nz.kiwifinance.engine.analysis;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.MonthRange;

public final class CashflowAnalyzer {

    public CashflowAnalysis analyse(List<TransactionRecord> transactions, MonthRange range) {
        Map<YearMonth, List<TransactionRecord>> byMonth = transactions.stream()
                .filter(t -> range.contains(t.date()))
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.date())));

        List<MonthSummary> months = range.months().stream()
                .map(month -> summarise(month, byMonth.getOrDefault(month, List.of())))
                .toList();

        List<MonthSummary> withData = monthsWithData(months);
        Money income = typical(withData, MonthSummary::income);
        Money spending = typical(withData, MonthSummary::spending);
        return new CashflowAnalysis(
                range,
                months,
                withData.size(),
                income,
                spending,
                typical(withData, MonthSummary::essentialSpending),
                typical(withData, MonthSummary::lifestyleSpending),
                income.minus(spending),
                Stats.coefficientOfVariation(
                        withData.stream().map(MonthSummary::income).toList()));
    }

    static MonthSummary summarise(YearMonth month, List<TransactionRecord> transactions) {
        Money income = Money.ZERO;
        Money spending = Money.ZERO;
        Money essential = Money.ZERO;
        Money lifestyle = Money.ZERO;
        Money uncategorised = Money.ZERO;
        Money saved = Money.ZERO;
        for (TransactionRecord transaction : transactions) {
            Money amount = transaction.amount();
            switch (transaction.flow()) {
                case INCOME -> income = income.plus(amount);
                case SAVING -> saved = saved.minus(amount);
                case SPENDING -> {
                    Money spent = amount.negate();
                    spending = spending.plus(spent);
                    if (transaction.category() == null) {
                        uncategorised = uncategorised.plus(spent);
                    } else if (transaction.category().group().isEssential()) {
                        essential = essential.plus(spent);
                    } else {
                        lifestyle = lifestyle.plus(spent);
                    }
                }
                case TRANSFER -> {}
            }
        }
        return new MonthSummary(
                month, income, spending, essential, lifestyle, uncategorised, saved, transactions.size());
    }

    private static List<MonthSummary> monthsWithData(List<MonthSummary> months) {
        List<MonthSummary> result = new ArrayList<>();
        boolean started = false;
        for (MonthSummary month : months) {
            started = started || month.hasActivity();
            if (started) {
                result.add(month);
            }
        }
        return result;
    }

    private static Money typical(List<MonthSummary> months, Function<MonthSummary, Money> value) {
        return Stats.typical(months.stream().map(value).toList());
    }
}
