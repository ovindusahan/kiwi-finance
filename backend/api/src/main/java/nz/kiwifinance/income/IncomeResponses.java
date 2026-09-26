package nz.kiwifinance.income;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.nzrules.TaxCode;
import nz.kiwifinance.engine.tax.PayBreakdown;
import nz.kiwifinance.engine.time.PayFrequency;
import org.jspecify.annotations.Nullable;

public final class IncomeResponses {

    private IncomeResponses() {}

    @Schema(name = "IncomeSource")
    public record Source(
            UUID id,
            String name,
            IncomeType type,
            MoneyResponse amount,
            AmountBasis basis,
            PayFrequency frequency,
            @Nullable TaxCode taxCode,
            @Nullable LocalDate startsOn,
            @Nullable LocalDate endsOn,
            boolean current,
            MoneyResponse takeHomePerPeriod,
            MoneyResponse takeHomeMonthly,
            MoneyResponse grossAnnual) {}

    @Schema(name = "IncomeSummary")
    public record Summary(
            MoneyResponse expectedMonthlyTakeHome, MoneyResponse expectedAnnualGross, List<Source> sources) {}

    @Schema(name = "PayBreakdown")
    public record Pay(
            String taxYear,
            PayFrequency frequency,
            Amounts perPeriod,
            Amounts annual,
            Employer employerKiwiSaver,
            Explanation explanation) {

        static Pay from(PayBreakdown breakdown) {
            return new Pay(
                    breakdown.taxYear().label(),
                    breakdown.frequency(),
                    Amounts.from(breakdown.perPeriod()),
                    Amounts.from(breakdown.annual()),
                    new Employer(
                            MoneyResponse.of(breakdown.employerKiwiSaver().grossPerPeriod()),
                            MoneyResponse.of(breakdown.employerKiwiSaver().esctPerPeriod()),
                            MoneyResponse.of(breakdown.employerKiwiSaver().netPerPeriod())),
                    breakdown.explanation());
        }
    }

    @Schema(name = "PayAmounts")
    public record Amounts(
            MoneyResponse gross,
            MoneyResponse incomeTax,
            MoneyResponse accLevy,
            MoneyResponse studentLoan,
            MoneyResponse kiwiSaver,
            MoneyResponse independentEarnerTaxCredit,
            MoneyResponse takeHome) {

        static Amounts from(PayBreakdown.Amounts amounts) {
            return new Amounts(
                    MoneyResponse.of(amounts.gross()),
                    MoneyResponse.of(amounts.incomeTax()),
                    MoneyResponse.of(amounts.accLevy()),
                    MoneyResponse.of(amounts.studentLoan()),
                    MoneyResponse.of(amounts.kiwiSaver()),
                    MoneyResponse.of(amounts.independentEarnerTaxCredit()),
                    MoneyResponse.of(amounts.takeHome()));
        }
    }

    @Schema(name = "EmployerKiwiSaver")
    public record Employer(MoneyResponse gross, MoneyResponse esct, MoneyResponse net) {}
}
