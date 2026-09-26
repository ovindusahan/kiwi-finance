package nz.kiwifinance.engine.insights;

import java.util.List;
import nz.kiwifinance.engine.analysis.CashflowAnalysis;
import nz.kiwifinance.engine.analysis.CategorySpending;
import nz.kiwifinance.engine.analysis.RecurringPayment;
import nz.kiwifinance.engine.emergencyfund.EmergencyFundPlan;

/**
 * @param spending per-category spending over the same window as the cash flow analysis
 * @param emergencyFund the person's emergency fund plan, or {@code null} if not yet available
 * @param context the person's situation today, or {@code null} to base insights on history alone
 */
public record InsightRequest(
        CashflowAnalysis cashflow,
        List<CategorySpending> spending,
        List<RecurringPayment> recurring,
        EmergencyFundPlan emergencyFund,
        InsightContext context) {

    public InsightRequest {
        spending = List.copyOf(spending);
        recurring = List.copyOf(recurring);
    }

    public InsightRequest(
            CashflowAnalysis cashflow,
            List<CategorySpending> spending,
            List<RecurringPayment> recurring,
            EmergencyFundPlan emergencyFund) {
        this(cashflow, spending, recurring, emergencyFund, null);
    }
}
