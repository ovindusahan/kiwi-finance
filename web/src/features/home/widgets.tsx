"use client";

import { ChevronRight, Plus } from "lucide-react";
import Link from "next/link";
import { InsightCard } from "@/components/app/insight-card";
import { CategoryIcon, Icon } from "@/components/ui/icon";
import { ProgressBar, ProgressRing } from "@/components/ui/progress";
import { useMoveMoney } from "@/features/accounts/move-money";
import { GoalCountdownCard } from "@/features/goals/goal-countdown-card";
import { useAccounts } from "@/lib/api/queries";
import type { Account, Dashboard, HomeWidget } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { formatDayMonth, formatMoney, formatMonthYear, formatPercent, parseDate } from "@/lib/format";

export type WidgetId =
  "accounts" | "spending" | "summary" | "goals" | "bills" | "insights" | "emergencyFund" | "score";
export type WidgetSize = HomeWidget["size"];

/** Every widget the home screen can show, in the order they're offered. */
export const widgetCatalog: Record<WidgetId, { title: string; description: string }> = {
  accounts: {
    title: "Your accounts",
    description: "Balances for every account, with links to transactions.",
  },
  spending: {
    title: "Spending this month",
    description: "What you've spent so far, by category and against budget.",
  },
  summary: { title: "Summary", description: "Cash on hand, net worth and how much you usually save." },
  goals: { title: "Your goals", description: "Countdowns for your goals." },
  bills: { title: "Coming up", description: "Regular payments due in the next few weeks." },
  insights: { title: "Insights", description: "Suggestions based on your accounts, budget and goals." },
  emergencyFund: { title: "Emergency fund", description: "How much of your safety net is saved." },
  score: { title: "Kiwi Score", description: "Your financial health in one number." },
};

export const widgetIds = Object.keys(widgetCatalog) as WidgetId[];

export const defaultLayout: { id: WidgetId; size: WidgetSize }[] = [
  { id: "summary", size: "FULL" },
  { id: "accounts", size: "FULL" },
  { id: "bills", size: "MEDIUM" },
  { id: "insights", size: "MEDIUM" },
];

export const sizeLabels: Record<WidgetSize, string> = {
  SMALL: "Small",
  MEDIUM: "Medium",
  LARGE: "Large",
  FULL: "Full width",
};

/** Columns on a six-column grid. Tablets get half or full width; phones always full width. */
export const sizeClasses: Record<WidgetSize, string> = {
  SMALL: "md:col-span-3 lg:col-span-2",
  MEDIUM: "md:col-span-3",
  LARGE: "md:col-span-6 lg:col-span-4",
  FULL: "md:col-span-6",
};

export function WidgetBody({ id, data }: { id: WidgetId; data: Dashboard }) {
  switch (id) {
    case "accounts":
      return <AccountsWidget />;
    case "spending":
      return <SpendingWidget data={data} />;
    case "summary":
      return <SummaryWidget data={data} />;
    case "goals":
      return <GoalsWidget data={data} />;
    case "bills":
      return <BillsWidget data={data} />;
    case "insights":
      return <InsightsWidget data={data} />;
    case "emergencyFund":
      return <EmergencyFundWidget data={data} />;
    case "score":
      return <ScoreWidget data={data} />;
  }
}

function WidgetHeader({ id, link }: { id: WidgetId; link?: { href: string; label: string } }) {
  return (
    <div className="mb-3 flex items-baseline justify-between gap-3 border-b border-line pb-2">
      <h2 id={`widget-${id}`} className="text-lg font-semibold">
        {widgetCatalog[id].title}
      </h2>
      {link ? (
        <Link
          href={link.href}
          className="inline-flex shrink-0 items-center text-sm text-brand hover:underline"
        >
          {link.label} <ChevronRight className="h-4 w-4" aria-hidden />
        </Link>
      ) : null}
    </div>
  );
}

const accountIcons: Record<Account["type"], string> = {
  EVERYDAY: "credit-card",
  SAVINGS: "piggy-bank",
  CREDIT_CARD: "credit-card",
  LOAN: "landmark",
  KIWISAVER: "sprout",
  INVESTMENT: "coins",
  CASH: "coins",
  OTHER: "landmark",
};

const accountTypeLabels: Record<Account["type"], string> = {
  EVERYDAY: "Everyday",
  SAVINGS: "Savings",
  CREDIT_CARD: "Credit card",
  LOAN: "Loan",
  KIWISAVER: "KiwiSaver",
  INVESTMENT: "Investment",
  CASH: "Cash",
  OTHER: "Other",
};

function AccountsWidget() {
  const { data: accounts } = useAccounts();
  const moveMoney = useMoveMoney();
  const groups = [
    { title: "Everyday and savings", match: (account: Account) => account.liquid },
    { title: "Cards, loans and investments", match: (account: Account) => !account.liquid },
  ];
  return (
    <>
      <WidgetHeader id="accounts" link={{ href: "/settings/accounts", label: "Manage" }} />
      {!accounts ? (
        <p className="py-4 text-sm text-muted">Loading accounts</p>
      ) : accounts.length === 0 ? (
        <div className="py-4">
          <p className="text-ink-2">No accounts yet.</p>
          <Link href="/connect" className="mt-2 inline-flex items-center gap-1 text-brand hover:underline">
            Connect your bank <ChevronRight className="h-4 w-4" aria-hidden />
          </Link>
        </div>
      ) : (
        <div className="space-y-6">
          {groups.map((group) => {
            const items = accounts.filter(group.match);
            if (items.length === 0) return null;
            return (
              <div key={group.title}>
                <h3 className="text-sm font-semibold uppercase tracking-wide text-muted">{group.title}</h3>
                <ul className="divide-y divide-line">
                  {items.map((account) => (
                    <AccountRow
                      key={account.id}
                      account={account}
                      onMove={() => moveMoney({ fromAccountId: account.id })}
                    />
                  ))}
                </ul>
              </div>
            );
          })}
        </div>
      )}
    </>
  );
}

function AccountRow({ account, onMove }: { account: Account; onMove: () => void }) {
  const href = `/transactions?accountId=${account.id}`;
  return (
    <li className="flex items-center gap-4 py-3.5">
      <span className="hidden h-11 w-11 shrink-0 place-items-center rounded-full bg-surface-2 text-muted @md:grid">
        <Icon name={accountIcons[account.type]} className="h-5 w-5" />
      </span>
      <div className="min-w-0 flex-1">
        <Link href={href} className="block truncate font-semibold text-brand hover:underline">
          {account.name}
        </Link>
        <p className="truncate text-sm text-muted">
          {[accountTypeLabels[account.type], account.institution].filter(Boolean).join(" · ")}
          {account.includeInEmergencyFund ? " · Emergency fund" : ""}
        </p>
        <p className="mt-0.5 flex flex-wrap gap-x-5 gap-y-1 text-sm">
          <Link
            href={href}
            className="inline-flex min-h-6 items-center text-brand hover:underline"
            aria-label={`View recent transactions for ${account.name}`}
          >
            View recent transactions <ChevronRight className="h-4 w-4" aria-hidden />
          </Link>
          <button
            type="button"
            onClick={onMove}
            className="inline-flex min-h-6 items-center text-brand hover:underline"
            aria-label={`Move money from ${account.name}`}
          >
            Move money
          </button>
        </p>
      </div>
      <div className="text-right">
        <p
          className={cn(
            "text-lg font-semibold tabular",
            account.balance.cents < 0 ? "text-danger" : "text-ink",
          )}
        >
          {formatMoney(account.balance)}
        </p>
        <p className="text-sm text-muted">{account.liquid ? "Available" : "Balance"}</p>
      </div>
    </li>
  );
}

function SpendingWidget({ data }: { data: Dashboard }) {
  const month = data.thisMonth;
  const budget = data.budget;
  const today = parseDate(data.today);
  const day = budget?.dayOfMonth ?? today.getDate();
  const daysInMonth = budget?.daysInMonth ?? new Date(today.getFullYear(), today.getMonth() + 1, 0).getDate();
  const usualByNow = Math.round((data.typical.spending.cents * day) / daysInMonth);
  const difference = month.spending.cents - usualByNow;
  const categories = data.thisMonthByCategory.slice(0, 5);
  const left = budget ? budget.totalLimit.cents - month.spending.cents : null;
  const perDay =
    left != null && daysInMonth - day + 1 > 0 ? Math.max(0, left) / (daysInMonth - day + 1) : null;

  return (
    <>
      <WidgetHeader id="spending" link={{ href: "/spending", label: "Details" }} />
      <dl className="grid grid-cols-2 gap-4 @xl:grid-cols-3">
        <div>
          <dt className="text-sm text-muted">Spent in {formatMonthYear(month.month)}</dt>
          <dd className="mt-0.5 text-2xl font-semibold tabular">
            {formatMoney(month.spending, { whole: true })}
          </dd>
          <dd className={cn("text-sm", difference > 0 ? "text-danger" : "text-good")}>
            {data.typical.spending.cents === 0
              ? "Day " + day + " of " + daysInMonth
              : difference > 0
                ? `${formatMoney(difference, { whole: true })} more than usual by now`
                : `${formatMoney(-difference, { whole: true })} less than usual by now`}
          </dd>
        </div>
        {left != null ? (
          <div>
            <dt className="text-sm text-muted">Left in your budget</dt>
            <dd className={cn("text-2xl font-semibold tabular", left < 0 && "text-danger")}>
              {formatMoney(left, { whole: true })}
            </dd>
            <dd className="text-sm text-ink-2">
              {perDay != null
                ? `About ${formatMoney(perDay, { whole: true })} a day to the end of the month`
                : ""}
            </dd>
          </div>
        ) : (
          <div>
            <dt className="text-sm text-muted">No budget yet</dt>
            <dd className="mt-1 text-sm">
              <Link href="/budget" className="text-brand hover:underline">
                Set one from your spending
              </Link>
            </dd>
          </div>
        )}
        <div className="col-span-2 @xl:col-span-1">
          <dt className="text-sm text-muted">Left over so far</dt>
          <dd className={cn("text-2xl font-semibold tabular", month.net.cents < 0 && "text-danger")}>
            {formatMoney(month.net, { whole: true, signed: true })}
          </dd>
          <dd className="text-sm text-ink-2">
            {formatMoney(month.income, { whole: true })} in, {formatMoney(month.spending, { whole: true })}{" "}
            out
          </dd>
        </div>
      </dl>

      <h3 className="mt-5 text-sm font-semibold uppercase tracking-wide text-muted">Where it went</h3>
      {categories.length === 0 ? (
        <p className="mt-2 text-sm text-ink-2">Nothing spent yet this month.</p>
      ) : (
        <table className="mt-1 w-full text-sm">
          <thead className="sr-only">
            <tr>
              <th>Category</th>
              <th>This month</th>
              <th>Compared with</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {categories.map((item) => {
              const reference = item.limit ?? (item.typical.cents > 0 ? item.typical : null);
              const over = reference != null && item.spent.cents > reference.cents;
              return (
                <tr key={item.category?.id ?? "uncategorised"}>
                  <td className="py-2">
                    <span className="flex items-center gap-2">
                      <CategoryIcon icon={item.category?.icon} colour={item.category?.colour} size="sm" />
                      <span className="truncate font-medium">{item.category?.name ?? "Uncategorised"}</span>
                    </span>
                  </td>
                  <td className="py-2 text-right font-semibold tabular">
                    {formatMoney(item.spent, { whole: true })}
                  </td>
                  <td
                    className={cn("w-40 py-2 pl-3 text-right tabular", over ? "text-danger" : "text-muted")}
                  >
                    {reference == null
                      ? "New this month"
                      : over
                        ? `${formatMoney(item.spent.cents - reference.cents, { whole: true })} over ${item.limit ? "budget" : "usual"}`
                        : `${formatMoney(reference.cents - item.spent.cents, { whole: true })} left of ${item.limit ? "budget" : "usual"}`}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </>
  );
}

function SummaryWidget({ data }: { data: Dashboard }) {
  const figures = [
    {
      label: "Cash on hand",
      value: formatMoney(data.availableToSpend),
      hint: "Not counting your emergency fund",
    },
    {
      label: "Net worth",
      value: formatMoney(data.netWorth, { whole: true }),
      hint: "What you own, less what you owe",
    },
    {
      label: "Left over in a typical month",
      value: formatMoney(data.typical.surplus, { whole: true, signed: true }),
      hint: `From ${formatMoney(data.typical.income, { whole: true })} coming in`,
    },
    {
      label: "Savings rate",
      value: formatPercent(data.typical.savingsRate),
      hint: `${data.streaks.surplusMonths} month${data.streaks.surplusMonths === 1 ? "" : "s"} in a row spending less than you earn`,
    },
  ];
  return (
    <>
      <WidgetHeader id="summary" />
      <dl className="grid gap-5 @md:grid-cols-2 @4xl:grid-cols-4">
        {figures.map((figure) => (
          <div key={figure.label} className="min-w-0">
            <dt className="text-sm text-ink-2">{figure.label}</dt>
            <dd className="mt-0.5 text-2xl font-semibold tabular">{figure.value}</dd>
            <dd className="text-sm text-muted">{figure.hint}</dd>
          </div>
        ))}
      </dl>
    </>
  );
}

function GoalsWidget({ data }: { data: Dashboard }) {
  return (
    <>
      <WidgetHeader id="goals" link={{ href: "/goals", label: "All goals" }} />
      <div className="grid gap-4 @lg:grid-cols-2 @4xl:grid-cols-4">
        {data.goals.slice(0, 3).map((goal) => (
          <GoalCountdownCard key={goal.id} goal={goal} />
        ))}
        <Link
          href="/goals/new"
          className="flex min-h-32 items-center justify-center gap-2 border border-dashed border-line p-5 text-center text-brand transition-colors hover:border-brand hover:underline"
        >
          <Plus className="h-4 w-4" aria-hidden />
          <span className="font-medium">
            {data.goals.length === 0 ? "Set your first goal" : "Add a goal"}
          </span>
        </Link>
      </div>
    </>
  );
}

function BillsWidget({ data }: { data: Dashboard }) {
  const start = parseDate(data.today).getTime();
  return (
    <>
      <WidgetHeader id="bills" link={{ href: "/spending#recurring", label: "All bills" }} />
      {data.upcomingBills.length === 0 ? (
        <p className="text-sm text-ink-2">
          We&apos;ll spot your regular bills once a couple of months of transactions are in.
        </p>
      ) : (
        <ul className="divide-y divide-line">
          {data.upcomingBills.map((bill) => {
            const days = Math.round((parseDate(bill.nextExpectedDate).getTime() - start) / 86_400_000);
            return (
              <li key={`${bill.name}-${bill.nextExpectedDate}`} className="flex items-center gap-3 py-2.5">
                <CategoryIcon icon={bill.category?.icon} colour={bill.category?.colour} size="sm" />
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{bill.name}</p>
                  <p className="text-xs text-muted">
                    {formatDayMonth(bill.nextExpectedDate)} ·{" "}
                    {days <= 0 ? "Due today" : days === 1 ? "Tomorrow" : `In ${days} days`}
                    {bill.subscription ? " · Subscription" : ""}
                  </p>
                </div>
                <p className="font-semibold tabular">{formatMoney(bill.typicalAmount)}</p>
              </li>
            );
          })}
        </ul>
      )}
    </>
  );
}

function InsightsWidget({ data }: { data: Dashboard }) {
  return (
    <>
      <WidgetHeader id="insights" />
      {data.insights.length === 0 ? (
        <p className="text-sm text-ink-2">Insights appear once there are a few weeks of transactions.</p>
      ) : (
        <div className="grid gap-3 @3xl:grid-cols-2">
          {data.insights.map((insight, index) => (
            <InsightCard key={`${insight.key}-${insight.category?.id ?? index}`} insight={insight} />
          ))}
        </div>
      )}
    </>
  );
}

function EmergencyFundWidget({ data }: { data: Dashboard }) {
  const fund = data.emergencyFund;
  return (
    <>
      <WidgetHeader id="emergencyFund" link={{ href: "/emergency-fund", label: "View" }} />
      <p className="text-2xl font-semibold tabular">{formatMoney(fund.current, { whole: true })}</p>
      <p className="text-sm text-muted tabular">of {formatMoney(fund.target, { whole: true })}</p>
      <ProgressBar
        value={fund.progress}
        tone={fund.status === "FUNDED" ? "good" : "brand"}
        label="Emergency fund progress"
        className="mt-3"
      />
      <p className="mt-2 text-sm text-ink-2">
        {fund.status === "FUNDED"
          ? "Fully funded. Nice work."
          : fund.status === "NOT_STARTED"
            ? "Not started yet."
            : `${fund.monthsCovered} of ${fund.targetMonths} months of essentials covered.`}
      </p>
    </>
  );
}

function ScoreWidget({ data }: { data: Dashboard }) {
  return (
    <>
      <WidgetHeader id="score" link={{ href: "/progress", label: "Details" }} />
      <div className="flex items-center gap-4">
        <ProgressRing
          value={data.score.score / 100}
          size={84}
          stroke={8}
          label={`Kiwi Score ${data.score.score} out of 100`}
        >
          <p className="text-xl font-semibold tabular">{data.score.score}</p>
        </ProgressRing>
        <div className="min-w-0">
          <p className="font-semibold text-good">{data.score.bandLabel}</p>
          <p className="mt-0.5 text-sm text-ink-2">{data.score.nextStep}</p>
        </div>
      </div>
    </>
  );
}
