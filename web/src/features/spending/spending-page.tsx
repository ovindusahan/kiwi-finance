"use client";

import { ArrowDownRight, ArrowUpRight, Repeat } from "lucide-react";
import { useState } from "react";
import { PageHeader } from "@/components/app/page-header";
import { Stat } from "@/components/app/stat";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader } from "@/components/ui/card";
import { EmptyState, ErrorState } from "@/components/ui/empty-state";
import { CategoryIcon } from "@/components/ui/icon";
import { Skeleton } from "@/components/ui/skeleton";
import { Segmented } from "@/components/ui/segmented";
import { useCashflow, useRecurring, useSpending } from "@/lib/api/queries";
import type { CategorySpend, RecurringPayment } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { formatDate, formatDayMonth, formatMoney, formatPercent } from "@/lib/format";
import { CashflowChart } from "./cashflow-chart";

const periods = [
  { months: 1, label: "Last month" },
  { months: 3, label: "3 months" },
  { months: 6, label: "6 months" },
  { months: 12, label: "12 months" },
];

const intervals: Record<RecurringPayment["interval"], string> = {
  WEEKLY: "Weekly",
  FORTNIGHTLY: "Fortnightly",
  MONTHLY: "Monthly",
  QUARTERLY: "Quarterly",
  ANNUALLY: "Yearly",
};

export function SpendingPage() {
  const cashflow = useCashflow(12);

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Spending"
        title="Where your money goes"
        description="Your typical month, the categories that cost the most and the bills that come round again."
      />

      {cashflow.error ? (
        <ErrorState message="We couldn't load your cash flow." onRetry={() => cashflow.refetch()} />
      ) : !cashflow.data ? (
        <Skeleton className="h-96" />
      ) : cashflow.data.typical.monthsOfData === 0 ? (
        <EmptyState
          title="No spending to show yet"
          message="Connect your bank or import a statement and your spending will appear here."
        />
      ) : (
        <>
          <section className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Stat
              label="Typical money in"
              value={formatMoney(cashflow.data.typical.income, { whole: true })}
              hint="a month"
            />
            <Stat
              label="Typical money out"
              value={formatMoney(cashflow.data.typical.spending, { whole: true })}
              hint={`${formatMoney(cashflow.data.typical.essentialSpending, { whole: true })} on essentials`}
            />
            <Stat
              label="Left over"
              value={formatMoney(cashflow.data.typical.surplus, { whole: true, signed: true })}
              tone={cashflow.data.typical.surplus.cents >= 0 ? "brand" : "danger"}
              hint="in a typical month"
            />
            <Stat
              label="Savings rate"
              value={formatPercent(cashflow.data.typical.savingsRate)}
              hint={`Based on ${cashflow.data.typical.monthsOfData} months`}
            />
          </section>
          <Card>
            <CardHeader
              title="What you kept each month"
              description="Money in minus money out over the last 12 months, and what it says about your habits."
            />
            <CashflowChart months={cashflow.data.months} />
          </Card>
        </>
      )}

      <CategoriesCard />
      <RecurringCard />
    </div>
  );
}

function CategoriesCard() {
  const [months, setMonths] = useState(3);
  const { data, error, refetch, isFetching } = useSpending(months);

  return (
    <Card>
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-lg font-semibold">By category</h2>
          <p className="mt-0.5 text-sm text-muted">
            {data
              ? `${formatDate(data.from)} to ${formatDate(data.to)}, compared with the period before.`
              : " "}
          </p>
        </div>
        <Segmented
          label="Period"
          value={String(months)}
          options={periods.map((period) => ({ value: String(period.months), label: period.label }))}
          onChange={(value) => setMonths(Number(value))}
        />
      </div>

      {error ? (
        <ErrorState message="We couldn't load your categories." onRetry={() => refetch()} />
      ) : !data ? (
        <div className="space-y-3">
          {Array.from({ length: 6 }, (_, index) => (
            <Skeleton key={index} className="h-14" />
          ))}
        </div>
      ) : data.categories.length === 0 ? (
        <p className="text-ink-2">No spending in this period.</p>
      ) : (
        <>
          <p className="mb-4 font-display text-3xl font-semibold tabular">
            {formatMoney(data.total, { whole: true })}
            <span className="ml-2 text-base font-semibold text-muted">spent in total</span>
          </p>
          <ul className={cn("space-y-1 transition-opacity", isFetching && "opacity-60")}>
            {data.categories.map((category, index) => (
              <CategoryRow
                key={category.category?.id ?? "uncategorised"}
                spend={category}
                largest={data.categories[0]?.total.cents ?? 1}
                rank={index + 1}
                months={months}
              />
            ))}
          </ul>
        </>
      )}
    </Card>
  );
}

function CategoryRow({
  spend,
  largest,
  rank,
  months,
}: {
  spend: CategorySpend;
  largest: number;
  rank: number;
  months: number;
}) {
  const name = spend.category?.name ?? "Uncategorised";
  const width = largest > 0 ? (spend.total.cents / largest) * 100 : 0;
  return (
    <li className="flex items-center gap-3 rounded-2xl p-2 hover:bg-surface-2">
      <span className="w-5 text-right text-sm font-semibold text-muted tabular">{rank}</span>
      <CategoryIcon icon={spend.category?.icon} colour={spend.category?.colour} />
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline justify-between gap-3">
          <p className="truncate font-semibold">{name}</p>
          <p className="shrink-0 font-semibold tabular">{formatMoney(spend.total, { whole: true })}</p>
        </div>
        <div className="mt-1.5 flex items-center gap-3">
          <div className="h-2 flex-1 overflow-hidden rounded-full bg-surface-3">
            <div className="h-full rounded-full bg-[var(--chart-out)]" style={{ width: `${width}%` }} />
          </div>
          <span className="w-10 shrink-0 text-right text-xs font-semibold text-muted tabular">
            {formatPercent(spend.share)}
          </span>
        </div>
        <div className="mt-1 flex flex-wrap items-center gap-x-3 text-xs text-muted">
          {months > 1 ? (
            <span>About {formatMoney(spend.monthlyTypical, { whole: true })} a month</span>
          ) : null}
          <Change change={spend.change} />
        </div>
      </div>
    </li>
  );
}

function Change({ change }: { change: number | null }) {
  if (change == null) return <span>New this period</span>;
  if (Math.abs(change) < 0.005) return <span>Same as before</span>;
  const up = change > 0;
  const Glyph = up ? ArrowUpRight : ArrowDownRight;
  return (
    <span className={cn("inline-flex items-center gap-0.5 font-semibold", up ? "text-warm" : "text-brand")}>
      <Glyph className="h-3.5 w-3.5" aria-hidden />
      {formatPercent(Math.abs(change))} {up ? "more" : "less"} than before
    </span>
  );
}

function RecurringCard() {
  const { data, error, refetch } = useRecurring();
  const outgoing = data?.payments.filter((payment) => !payment.incoming) ?? [];
  const incoming = data?.payments.filter((payment) => payment.incoming) ?? [];

  return (
    <Card id="recurring">
      <CardHeader
        title="Bills and subscriptions"
        description="Payments that repeat, found in your transactions."
      />
      {error ? (
        <ErrorState message="We couldn't load your regular payments." onRetry={() => refetch()} />
      ) : !data ? (
        <Skeleton className="h-64" />
      ) : data.payments.length === 0 ? (
        <p className="text-ink-2">We need a couple of months of transactions to spot payments that repeat.</p>
      ) : (
        <>
          <div className="mb-5 grid grid-cols-2 gap-3">
            <div className="rounded-xl bg-surface-2 p-4">
              <p className="text-sm font-semibold text-muted">Regular bills</p>
              <p className="mt-1 font-display text-2xl font-semibold tabular">
                {formatMoney(data.monthlyBills, { whole: true })}
                <span className="text-sm font-semibold text-muted"> /month</span>
              </p>
            </div>
            <div className="rounded-xl bg-surface-2 p-4">
              <p className="text-sm font-semibold text-muted">Subscriptions</p>
              <p className="mt-1 font-display text-2xl font-semibold tabular">
                {formatMoney(data.monthlySubscriptions, { whole: true })}
                <span className="text-sm font-semibold text-muted"> /month</span>
              </p>
              <p className="text-xs text-muted">
                {formatMoney(data.monthlySubscriptions.cents * 12, { whole: true })} a year
              </p>
            </div>
          </div>
          <RecurringList payments={outgoing} />
          {incoming.length > 0 ? (
            <>
              <h3 className="mb-2 mt-6 text-sm font-semibold uppercase tracking-wider text-muted">
                Regular income
              </h3>
              <RecurringList payments={incoming} />
            </>
          ) : null}
        </>
      )}
    </Card>
  );
}

function RecurringList({ payments }: { payments: RecurringPayment[] }) {
  return (
    <ul className="divide-y divide-line">
      {payments.map((payment) => (
        <li key={`${payment.name}-${payment.interval}`} className="flex items-center gap-3 py-3">
          {payment.category ? (
            <CategoryIcon icon={payment.category.icon} colour={payment.category.colour} />
          ) : (
            <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-surface-2 text-muted">
              <Repeat className="h-5 w-5" aria-hidden />
            </span>
          )}
          <div className="min-w-0 flex-1">
            <p className="flex items-center gap-2 truncate font-semibold">
              {payment.name}
              {payment.subscription ? <Badge tone="lilac">Subscription</Badge> : null}
            </p>
            <p className="text-xs text-muted">
              {intervals[payment.interval]} · next around {formatDayMonth(payment.nextExpectedDate)}
            </p>
          </div>
          <div className="text-right">
            <p className={cn("font-semibold tabular", payment.incoming && "text-good")}>
              {formatMoney(payment.typicalAmount)}
            </p>
            {payment.interval !== "MONTHLY" ? (
              <p className="text-xs text-muted tabular">
                {formatMoney(payment.monthlyAmount, { whole: true })} a month
              </p>
            ) : null}
          </div>
        </li>
      ))}
    </ul>
  );
}
