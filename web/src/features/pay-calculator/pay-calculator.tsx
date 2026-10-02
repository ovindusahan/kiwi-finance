"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Calculator, Plus, X } from "lucide-react";
import { useEffect, useState } from "react";
import { ExplanationPanel } from "@/components/app/explanation-panel";
import { Field, Input, MoneyInput, Select } from "@/components/ui/field";
import { Segmented } from "@/components/ui/segmented";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { taxCodes } from "@/features/settings/options";
import { api } from "@/lib/api/client";
import type { PayBreakdown, PayCalculationRequest } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { formatMoney, parseDollars } from "@/lib/format";

type Frequency = PayCalculationRequest["frequency"];

const frequencies: { value: Frequency; label: string; per: string }[] = [
  { value: "ANNUALLY", label: "a year", per: "Year" },
  { value: "MONTHLY", label: "a month", per: "Month" },
  { value: "FORTNIGHTLY", label: "a fortnight", per: "Fortnight" },
  { value: "WEEKLY", label: "a week", per: "Week" },
];

const perYear: Record<Frequency, number> = {
  ANNUALLY: 1,
  MONTHLY: 12,
  FORTNIGHTLY: 26,
  FOUR_WEEKLY: 13,
  WEEKLY: 52,
};

const kiwiSaverOptions = ["none", "0.03", "0.035", "0.04", "0.06", "0.08", "0.1"];

/** Colours checked for colour-blind separation; loan repayments reuse take-home blue with a hatch. */
const segments = [
  { key: "takeHome", label: "Yours to spend", colour: "#2a78d6" },
  { key: "loans", label: "Loan repayments", colour: "#2a78d6", hatched: true },
  { key: "incomeTax", label: "Income tax (PAYE)", colour: "#d1452e" },
  { key: "accLevy", label: "ACC earners' levy", colour: "#d6a100" },
  { key: "kiwiSaver", label: "KiwiSaver (you)", colour: "#16915f" },
  { key: "studentLoan", label: "Student loan", colour: "#8a5cd6" },
] as const;

export type LoanRepayment = { id: string; name: string; amount: string; frequency: Frequency };

export type PayCalculatorInitial = {
  amount?: string;
  frequency?: Frequency;
  taxCode?: PayCalculationRequest["taxCode"];
  kiwiSaver?: string;
  studentLoan?: boolean;
  loans?: LoanRepayment[];
};

let nextLoan = 0;
const newLoan = (): LoanRepayment => ({
  id: `loan-${nextLoan++}`,
  name: "",
  amount: "",
  frequency: "MONTHLY",
});

/** Works out take-home pay with this tax year's IRD rates, then what's left after loan repayments. */
export function PayCalculator({ initial, source }: { initial?: PayCalculatorInitial; source?: string }) {
  const [amount, setAmount] = useState(initial?.amount ?? "65000");
  const [frequency, setFrequency] = useState<Frequency>(initial?.frequency ?? "ANNUALLY");
  const [taxCode, setTaxCode] = useState<PayCalculationRequest["taxCode"]>(initial?.taxCode ?? "M");
  const [kiwiSaver, setKiwiSaver] = useState(initial?.kiwiSaver ?? "0.035");
  const [studentLoan, setStudentLoan] = useState(initial?.studentLoan ?? false);
  const [loans, setLoans] = useState<LoanRepayment[]>(initial?.loans ?? []);
  const [view, setView] = useState<Frequency>("FORTNIGHTLY");
  const [request, setRequest] = useState<PayCalculationRequest | null>(null);

  useEffect(() => {
    const cents = parseDollars(amount);
    const timer = setTimeout(
      () =>
        setRequest(
          cents && cents > 0
            ? {
                amountCents: cents,
                frequency,
                basis: "GROSS",
                taxCode,
                studentLoan,
                ...(kiwiSaver === "none" ? {} : { kiwiSaverRate: Number(kiwiSaver) }),
              }
            : null,
        ),
      250,
    );
    return () => clearTimeout(timer);
  }, [amount, frequency, taxCode, kiwiSaver, studentLoan]);

  const result = useQuery({
    queryKey: ["pay-calculator", request],
    queryFn: () => api.post<PayBreakdown>("/income/pay-calculator", request),
    enabled: request != null,
    placeholderData: keepPreviousData,
  });

  const loansAnnual = loans.reduce(
    (sum, loan) => sum + (parseDollars(loan.amount) ?? 0) * perYear[loan.frequency],
    0,
  );

  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(0,380px)_minmax(0,1fr)]">
      <form
        className="h-fit space-y-5 rounded-2xl border border-line bg-surface p-5 sm:p-6"
        onSubmit={(event) => event.preventDefault()}
      >
        {source ? (
          <p className="rounded-xl border border-brand/20 bg-brand-soft p-3 text-sm text-ink-2">{source}</p>
        ) : null}
        <div className="grid grid-cols-[minmax(0,1fr)_auto] gap-3">
          <Field label="Your pay before tax">
            {(props) => (
              <MoneyInput
                {...props}
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                className="text-lg font-semibold"
              />
            )}
          </Field>
          <Field label="Per">
            {(props) => (
              <Select
                {...props}
                value={frequency}
                onChange={(event) => setFrequency(event.target.value as Frequency)}
              >
                {frequencies.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.per.toLowerCase()}
                  </option>
                ))}
              </Select>
            )}
          </Field>
        </div>
        <Field label="Tax code">
          {(props) => (
            <Select
              {...props}
              value={taxCode}
              onChange={(event) => setTaxCode(event.target.value as PayCalculationRequest["taxCode"])}
            >
              {taxCodes.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          )}
        </Field>

        <fieldset className="space-y-3 border-t border-line pt-4">
          <legend className="sr-only">Deductions</legend>
          <Field label="KiwiSaver contribution" hint="Your employer adds at least 3% on top.">
            {(props) => (
              <Select {...props} value={kiwiSaver} onChange={(event) => setKiwiSaver(event.target.value)}>
                {kiwiSaverOptions.map((option) => (
                  <option key={option} value={option}>
                    {option === "none" ? "Not a member" : `${Math.round(Number(option) * 1000) / 10}% of pay`}
                  </option>
                ))}
              </Select>
            )}
          </Field>
          <label className="flex items-center justify-between gap-3 rounded-xl border border-line p-3">
            <span>
              <span className="block font-medium">I&apos;m repaying a student loan</span>
              <span className="block text-sm text-muted">12% of pay over the repayment threshold.</span>
            </span>
            <Switch
              checked={studentLoan}
              onCheckedChange={setStudentLoan}
              aria-label="I'm repaying a student loan"
            />
          </label>
        </fieldset>

        <fieldset className="space-y-3 border-t border-line pt-4">
          <legend className="text-sm font-semibold">Other loan repayments</legend>
          <p className="text-sm text-muted">
            Car loans, personal loans, buy now pay later and anything else you repay.
          </p>
          {loans.map((loan, index) => (
            <div key={loan.id} className="grid grid-cols-[minmax(0,1fr)_7rem_auto] items-end gap-2">
              <Field label={`Loan ${index + 1} name`}>
                {(props) => (
                  <Input
                    {...props}
                    value={loan.name}
                    placeholder="Car loan"
                    onChange={(event) =>
                      setLoans((current) =>
                        current.map((item) =>
                          item.id === loan.id ? { ...item, name: event.target.value } : item,
                        ),
                      )
                    }
                  />
                )}
              </Field>
              <Field label="Amount">
                {(props) => (
                  <MoneyInput
                    {...props}
                    value={loan.amount}
                    onChange={(event) =>
                      setLoans((current) =>
                        current.map((item) =>
                          item.id === loan.id ? { ...item, amount: event.target.value } : item,
                        ),
                      )
                    }
                  />
                )}
              </Field>
              <button
                type="button"
                onClick={() => setLoans((current) => current.filter((item) => item.id !== loan.id))}
                className="mb-1 grid h-10 w-10 place-items-center text-muted hover:bg-surface-2 hover:text-ink"
                aria-label={`Remove ${loan.name || `loan ${index + 1}`}`}
              >
                <X className="h-4 w-4" aria-hidden />
              </button>
              <div className="col-span-3 -mt-1">
                <label className="sr-only" htmlFor={`${loan.id}-frequency`}>
                  How often for loan {index + 1}
                </label>
                <Select
                  id={`${loan.id}-frequency`}
                  value={loan.frequency}
                  onChange={(event) =>
                    setLoans((current) =>
                      current.map((item) =>
                        item.id === loan.id ? { ...item, frequency: event.target.value as Frequency } : item,
                      ),
                    )
                  }
                  className="py-1.5 text-sm"
                >
                  {frequencies.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </Select>
              </div>
            </div>
          ))}
          <button
            type="button"
            onClick={() => setLoans((current) => [...current, newLoan()])}
            className="inline-flex items-center gap-1 text-sm font-medium text-brand hover:underline"
          >
            <Plus className="h-4 w-4" aria-hidden /> Add a loan repayment
          </button>
        </fieldset>
        {result.data ? (
          <p className="text-xs text-muted">Using IRD rates for the {result.data.taxYear} tax year.</p>
        ) : null}
      </form>

      <div className="min-w-0 space-y-6">
        {!request ? (
          <div className="flex flex-col items-center gap-3 border border-dashed border-line p-10 text-center">
            <Calculator className="h-8 w-8 text-brand" aria-hidden />
            <p className="font-semibold">Enter your pay to see your take-home.</p>
          </div>
        ) : !result.data ? (
          <Skeleton className="h-96" />
        ) : (
          <Breakdown
            breakdown={result.data}
            loansAnnual={loansAnnual}
            view={view}
            onView={setView}
            stale={result.isFetching}
          />
        )}
      </div>
    </div>
  );
}

function Breakdown({
  breakdown,
  loansAnnual,
  view,
  onView,
  stale,
}: {
  breakdown: PayBreakdown;
  loansAnnual: number;
  view: Frequency;
  onView: (view: Frequency) => void;
  stale: boolean;
}) {
  const divisor = perYear[view];
  const annual = breakdown.annual;
  const per = (cents: number) => Math.round(cents / divisor);
  const loans = Math.min(loansAnnual, annual.takeHome.cents);
  const values: Record<(typeof segments)[number]["key"], number> = {
    takeHome: annual.takeHome.cents - loans,
    loans,
    incomeTax: annual.incomeTax.cents,
    accLevy: annual.accLevy.cents,
    kiwiSaver: annual.kiwiSaver.cents,
    studentLoan: annual.studentLoan.cents,
  };
  const shown = segments.filter((segment) => values[segment.key] > 0);
  const gross = annual.gross.cents;
  const label = frequencies.find((option) => option.value === view)?.label ?? "";

  return (
    <div className={cn("space-y-6 transition-opacity", stale && "opacity-70")}>
      <section
        aria-label="Your take-home pay"
        className="rounded-2xl border border-line bg-surface p-5 sm:p-6"
      >
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm font-semibold text-ink-2">You take home</p>
          <Segmented
            label="Show amounts per"
            value={view}
            onChange={onView}
            options={frequencies.map((option) => ({ value: option.value, label: option.per }))}
          />
        </div>
        <p className="mt-2 text-4xl font-semibold tabular">{formatMoney(per(annual.takeHome.cents))}</p>
        <p className="text-ink-2">
          {label}, from {formatMoney(per(gross))} before tax
        </p>
        {loans > 0 ? (
          <p className="mt-2 text-ink-2">
            <span className="font-semibold text-ink tabular">{formatMoney(per(values.takeHome))}</span> left
            to spend after {formatMoney(per(loans))} of loan repayments
          </p>
        ) : null}
        <div
          className="mt-5 flex h-6 w-full gap-0.5"
          role="img"
          aria-label="Where your pay goes; the amounts are listed below"
        >
          {shown.map((segment) => (
            <span
              key={segment.key}
              className="h-full"
              title={`${segment.label}: ${formatMoney(per(values[segment.key]))}`}
              style={{
                width: `${(values[segment.key] / gross) * 100}%`,
                ...swatch(segment),
              }}
            />
          ))}
        </div>
      </section>

      <section
        aria-labelledby="where-heading"
        className="rounded-2xl border border-line bg-surface p-5 sm:p-6"
      >
        <h2 id="where-heading" className="border-b border-line pb-2 text-lg font-semibold">
          Where it goes, {label}
        </h2>
        <table className="w-full text-sm">
          <tbody className="divide-y divide-line">
            {shown.map((segment) => (
              <tr key={segment.key}>
                <td className="py-2.5">
                  <span className="flex items-center gap-2 font-medium">
                    <span className="h-3 w-3" style={swatch(segment)} aria-hidden />
                    {segment.label}
                  </span>
                </td>
                <td className="py-2.5 text-right text-muted tabular">
                  {Math.round((values[segment.key] / gross) * 1000) / 10}%
                </td>
                <td className="py-2.5 text-right font-semibold tabular">
                  {formatMoney(per(values[segment.key]))}
                </td>
              </tr>
            ))}
            {annual.independentEarnerTaxCredit.cents > 0 ? (
              <tr>
                <td className="py-2.5 pl-5 text-muted" colSpan={2}>
                  Income tax includes the independent earner tax credit
                </td>
                <td className="py-2.5 text-right font-semibold text-good tabular">
                  -{formatMoney(per(annual.independentEarnerTaxCredit.cents))}
                </td>
              </tr>
            ) : null}
            <tr className="font-semibold">
              <td className="py-2.5" colSpan={2}>
                Pay before tax
              </td>
              <td className="py-2.5 text-right tabular">{formatMoney(per(gross))}</td>
            </tr>
          </tbody>
        </table>
      </section>

      {breakdown.employerKiwiSaver.gross.cents > 0 ? (
        <section
          aria-labelledby="kiwisaver-heading"
          className="rounded-2xl border border-line bg-surface p-5 sm:p-6"
        >
          <h2 id="kiwisaver-heading" className="font-semibold">
            Your KiwiSaver, {label}
          </h2>
          <dl className="mt-2 grid grid-cols-3 gap-3 text-sm">
            <div>
              <dt className="text-muted">You put in</dt>
              <dd className="text-lg font-semibold tabular">{formatMoney(per(annual.kiwiSaver.cents))}</dd>
            </div>
            <div>
              <dt className="text-muted">Your employer adds</dt>
              <dd className="text-lg font-semibold tabular">
                {formatMoney(per(breakdown.employerKiwiSaver.net.cents))}
              </dd>
              <dd className="text-xs text-muted">
                {formatMoney(per(breakdown.employerKiwiSaver.gross.cents))} less ESCT
              </dd>
            </div>
            <div>
              <dt className="text-muted">Total into KiwiSaver</dt>
              <dd className="text-lg font-semibold tabular">
                {formatMoney(per(annual.kiwiSaver.cents + breakdown.employerKiwiSaver.net.cents))}
              </dd>
            </div>
          </dl>
        </section>
      ) : null}

      <ExplanationPanel explanation={breakdown.explanation} />
    </div>
  );
}

function swatch(segment: (typeof segments)[number]): React.CSSProperties {
  return "hatched" in segment && segment.hatched
    ? {
        backgroundColor: "#ffffff",
        border: `1px solid ${segment.colour}`,
        backgroundImage: `repeating-linear-gradient(45deg, ${segment.colour} 0 2px, transparent 2px 5px)`,
      }
    : { backgroundColor: segment.colour };
}
