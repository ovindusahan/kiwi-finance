package nz.kiwifinance.income;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.error.ApiException;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.nzrules.NzRules;
import nz.kiwifinance.engine.nzrules.TaxCode;
import nz.kiwifinance.engine.tax.PayBreakdown;
import nz.kiwifinance.engine.tax.PayCalculator;
import nz.kiwifinance.engine.tax.PayRequest;
import nz.kiwifinance.engine.time.NzTime;
import nz.kiwifinance.engine.time.PayFrequency;
import nz.kiwifinance.user.ProfileService;
import nz.kiwifinance.user.UserProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncomeService {

    private final IncomeSourceRepository sources;
    private final ProfileService profiles;
    private final PayCalculator payCalculator = new PayCalculator(NzRules.standard());
    private final Clock clock;

    @Transactional(readOnly = true)
    public IncomeResponses.Summary summary(UUID userId) {
        LocalDate today = NzTime.today(clock);
        UserProfile profile = profiles.get(userId);
        List<IncomeResponses.Source> views = sources.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(source -> view(source, profile, today))
                .toList();
        long monthly = views.stream()
                .filter(IncomeResponses.Source::current)
                .mapToLong(view -> view.takeHomeMonthly().cents())
                .sum();
        long annualGross = views.stream()
                .filter(IncomeResponses.Source::current)
                .mapToLong(view -> view.grossAnnual().cents())
                .sum();
        return new IncomeResponses.Summary(MoneyResponse.of(monthly), MoneyResponse.of(annualGross), views);
    }

    /**
     * The take-home pay a person expects each month from their current income sources.
     */
    @Transactional(readOnly = true)
    public Money expectedMonthlyTakeHome(UUID userId) {
        return Money.ofCents(summary(userId).expectedMonthlyTakeHome().cents());
    }

    @Transactional
    IncomeResponses.Source create(UUID userId, IncomeRequests.Save request) {
        IncomeSource source = new IncomeSource(userId);
        source.apply(request);
        return view(sources.save(source), profiles.get(userId), NzTime.today(clock));
    }

    @Transactional
    IncomeResponses.Source update(UUID userId, UUID id, IncomeRequests.Save request) {
        IncomeSource source = find(userId, id);
        source.apply(request);
        return view(source, profiles.get(userId), NzTime.today(clock));
    }

    @Transactional
    void delete(UUID userId, UUID id) {
        sources.delete(find(userId, id));
    }

    IncomeResponses.Pay calculate(IncomeRequests.PayCalculation request) {
        LocalDate payDate = request.payDate() == null ? NzTime.today(clock) : request.payDate();
        PayRequest template = new PayRequest(
                Money.ofCents(request.amountCents()),
                request.frequency(),
                request.taxCode(),
                request.studentLoan(),
                request.kiwiSaverRate(),
                payDate);
        PayBreakdown breakdown = request.basis() == AmountBasis.GROSS
                ? payCalculator.calculate(template)
                : payCalculator.fromTakeHome(Money.ofCents(request.amountCents()), template);
        return IncomeResponses.Pay.from(breakdown);
    }

    private IncomeSource find(UUID userId, UUID id) {
        return sources.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Income source"));
    }

    /**
     * Works out take-home pay for a source. Employment income uses the person's KiwiSaver and
     * student loan settings; other gross income is taxed on the same scale without KiwiSaver.
     */
    private IncomeResponses.Source view(IncomeSource source, UserProfile profile, LocalDate today) {
        Money amount = Money.ofCents(source.getAmountCents());
        PayFrequency frequency = source.getFrequency();
        Money takeHome;
        Money grossAnnual;
        if (source.getBasis() == AmountBasis.NET) {
            takeHome = amount;
            grossAnnual = frequency.toAnnual(amount);
        } else {
            boolean employment = source.getType().isEmployment();
            TaxCode taxCode = source.getTaxCode() != null ? source.getTaxCode() : profile.getTaxCode();
            PayBreakdown breakdown = payCalculator.calculate(new PayRequest(
                    amount,
                    frequency,
                    taxCode,
                    employment && profile.hasStudentLoan(),
                    employment && profile.isKiwiSaverMember() ? profile.getKiwiSaverRate() : null,
                    today));
            takeHome = breakdown.perPeriod().takeHome();
            grossAnnual = breakdown.annual().gross();
        }
        return new IncomeResponses.Source(
                source.getId(),
                source.getName(),
                source.getType(),
                MoneyResponse.of(amount),
                source.getBasis(),
                frequency,
                source.getTaxCode(),
                source.getStartsOn(),
                source.getEndsOn(),
                source.isCurrent(today),
                MoneyResponse.of(takeHome),
                MoneyResponse.of(frequency.convert(takeHome, PayFrequency.MONTHLY)),
                MoneyResponse.of(grossAnnual));
    }
}
