"use client";

import { PageHeader } from "@/components/app/page-header";
import { PageSkeleton } from "@/components/ui/skeleton";
import { useIncome, useProfile, useRecurring } from "@/lib/api/queries";
import { centsToDollarsInput } from "@/lib/format";
import { type LoanRepayment, PayCalculator, type PayCalculatorInitial } from "./pay-calculator";

const intervalFrequency: Record<string, LoanRepayment["frequency"]> = {
  WEEKLY: "WEEKLY",
  FORTNIGHTLY: "FORTNIGHTLY",
  MONTHLY: "MONTHLY",
  ANNUALLY: "ANNUALLY",
};

/** The pay calculator inside the app, filled in from the person's profile, income and repayments. */
export function PayPage() {
  const profile = useProfile();
  const income = useIncome();
  const recurring = useRecurring();

  if (profile.isLoading || income.isLoading || recurring.isLoading) return <PageSkeleton />;

  const main = income.data?.sources.find((source) => source.current && source.basis === "GROSS");
  const loans: LoanRepayment[] = (recurring.data?.payments ?? [])
    .filter(
      (payment) =>
        !payment.incoming && payment.category?.group === "DEBT" && intervalFrequency[payment.interval],
    )
    .map((payment, index) => ({
      id: `detected-${index}`,
      name: payment.name,
      amount: centsToDollarsInput(payment.typicalAmount.cents),
      frequency: intervalFrequency[payment.interval]!,
    }));
  const p = profile.data;
  const initial: PayCalculatorInitial = {
    ...(main ? { amount: centsToDollarsInput(main.amount.cents), frequency: main.frequency } : {}),
    ...(p
      ? {
          taxCode: main?.taxCode ?? p.taxCode,
          kiwiSaver:
            p.kiwiSaverMember && p.kiwiSaverRate != null
              ? String(p.kiwiSaverRate)
              : p.kiwiSaverMember
                ? "0.035"
                : "none",
          studentLoan: p.hasStudentLoan,
        }
      : {}),
    loans,
  };
  const filled = [
    main ? `your ${main.name.toLowerCase()} pay` : null,
    p ? "tax code, KiwiSaver and student loan from your profile" : null,
    loans.length
      ? `${loans.length} loan repayment${loans.length === 1 ? "" : "s"} we found in your transactions`
      : null,
  ].filter(Boolean);

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Pay calculator"
        title="What you actually take home"
        description="PAYE, ACC, KiwiSaver, student loan and your other repayments, with this tax year's IRD rates."
      />
      <PayCalculator
        initial={initial}
        source={
          filled.length
            ? `We've filled in ${filled.join(", ")}. Change anything to try a different scenario.`
            : undefined
        }
      />
    </div>
  );
}
