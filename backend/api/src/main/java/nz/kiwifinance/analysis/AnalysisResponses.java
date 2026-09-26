package nz.kiwifinance.analysis;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import nz.kiwifinance.category.CategoryResponse;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.engine.analysis.MonthSummary;
import nz.kiwifinance.engine.analysis.RecurrenceInterval;
import org.jspecify.annotations.Nullable;

public final class AnalysisResponses {

    private AnalysisResponses() {}

    @Schema(name = "MonthSummary")
    public record Month(
            YearMonth month,
            boolean complete,
            MoneyResponse income,
            MoneyResponse spending,
            MoneyResponse essentialSpending,
            MoneyResponse lifestyleSpending,
            MoneyResponse uncategorisedSpending,
            MoneyResponse saved,
            MoneyResponse net,
            BigDecimal savingsRate) {

        public static Month from(MonthSummary summary, boolean complete) {
            return new Month(
                    summary.month(),
                    complete,
                    MoneyResponse.of(summary.income()),
                    MoneyResponse.of(summary.spending()),
                    MoneyResponse.of(summary.essentialSpending()),
                    MoneyResponse.of(summary.lifestyleSpending()),
                    MoneyResponse.of(summary.uncategorisedSpending()),
                    MoneyResponse.of(summary.saved()),
                    MoneyResponse.of(summary.net()),
                    summary.savingsRate());
        }
    }

    @Schema(name = "TypicalMonth")
    public record Typical(
            MoneyResponse income,
            IncomeBasis incomeBasis,
            MoneyResponse spending,
            MoneyResponse essentialSpending,
            MoneyResponse lifestyleSpending,
            MoneyResponse surplus,
            BigDecimal savingsRate,
            boolean variableIncome,
            int monthsOfData) {}

    @Schema(name = "Cashflow")
    public record Cashflow(List<Month> months, Typical typical) {}

    @Schema(name = "MonthAmount")
    public record MonthAmount(YearMonth month, MoneyResponse amount) {}

    /**
     * @param change the change from the previous period as a fraction, or {@code null} when there
     *     was no spending in the previous period
     */
    @Schema(name = "CategorySpend")
    public record CategorySpend(
            @Nullable CategoryResponse category,
            MoneyResponse total,
            BigDecimal share,
            MoneyResponse monthlyTypical,
            MoneyResponse previousTotal,
            @Nullable BigDecimal change,
            List<MonthAmount> monthly) {}

    @Schema(name = "SpendingBreakdown")
    public record Spending(
            LocalDate from,
            LocalDate to,
            MoneyResponse total,
            MoneyResponse previousTotal,
            List<CategorySpend> categories) {}

    @Schema(name = "RecurringPayment")
    public record Recurring(
            String name,
            @Nullable CategoryResponse category,
            RecurrenceInterval interval,
            MoneyResponse typicalAmount,
            boolean incoming,
            int occurrences,
            LocalDate lastDate,
            LocalDate nextExpectedDate,
            MoneyResponse monthlyAmount,
            MoneyResponse annualAmount,
            boolean subscription) {}

    @Schema(name = "RecurringSummary")
    public record RecurringSummary(
            List<Recurring> payments,
            List<Recurring> upcoming,
            MoneyResponse monthlyBills,
            MoneyResponse monthlySubscriptions) {}
}
