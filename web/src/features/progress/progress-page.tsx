"use client";

import { Award, Flame, Lock, PiggyBank, ShieldCheck, Target } from "lucide-react";
import Link from "next/link";
import { PageHeader } from "@/components/app/page-header";
import { ErrorState } from "@/components/ui/empty-state";
import { Icon } from "@/components/ui/icon";
import { ProgressBar, ProgressRing } from "@/components/ui/progress";
import { PageSkeleton } from "@/components/ui/skeleton";
import { useCashflow, useEmergencyFund, useGoals, useProgress } from "@/lib/api/queries";
import type { MonthSummary } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { formatMoney, formatPercent } from "@/lib/format";

const card = "rounded-2xl border border-line bg-surface p-5 sm:p-6";

export function ProgressPage() {
  const { data, isLoading, error, refetch } = useProgress();
  const { data: cashflow } = useCashflow(12);
  const { data: fund } = useEmergencyFund();
  const { data: goals } = useGoals();
  if (isLoading) return <PageSkeleton />;
  if (error || !data)
    return <ErrorState message="We couldn't load your progress." onRetry={() => refetch()} />;

  const { score, streaks } = data;
  const months = (cashflow?.months ?? []).filter((month) => month.complete);
  const kept = months.filter((month) => month.net.cents > 0).length;
  const averageRate = months.length
    ? months.reduce((sum, month) => sum + month.savingsRate, 0) / months.length
    : 0;
  const totalKept = months.reduce((sum, month) => sum + month.net.cents, 0);
  const active = (goals ?? []).filter((goal) => goal.status === "ACTIVE");
  const onTrack = active.filter((goal) => goal.projection.status === "ON_TRACK").length;
  const sortedBadges = [...data.achievements].sort(
    (a, b) => Number(b.unlocked) - Number(a.unlocked) || b.progress - a.progress,
  );

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Progress"
        title="How you're tracking"
        description="Your Kiwi Score sums up your financial health. Below it are the habits and numbers behind it."
      />

      <div className="grid gap-6 lg:grid-cols-[380px_minmax(0,1fr)]">
        <section aria-label="Kiwi Score" className={cn(card, "flex flex-col items-center text-center")}>
          <ProgressRing
            value={score.score / 100}
            size={150}
            stroke={14}
            label={`Kiwi Score ${score.score} out of 100`}
          >
            <div>
              <p className="text-4xl font-semibold tabular">{score.score}</p>
              <p className="text-xs text-muted">Kiwi Score</p>
            </div>
          </ProgressRing>
          <p className="mt-4 text-xl font-semibold text-brand">{score.bandLabel}</p>
          <p className="mt-1 text-ink-2">{score.summary}</p>
          <div className="mt-4 w-full rounded-xl bg-brand-soft p-3 text-left text-sm">
            <p className="font-semibold text-ink">Your next step</p>
            <p className="text-ink-2">{score.nextStep}</p>
          </div>
        </section>

        <div className="grid gap-6">
          <section aria-labelledby="numbers-heading" className={card}>
            <h2 id="numbers-heading" className="text-lg font-semibold">
              The last 12 months
            </h2>
            <ul className="mt-4 grid grid-cols-2 gap-3 xl:grid-cols-4">
              <Figure
                icon={PiggyBank}
                label="Average saved"
                value={formatPercent(averageRate)}
                detail="of your income"
              />
              <Figure
                icon={Flame}
                label="Months you kept money"
                value={`${kept} of ${months.length}`}
                detail={`${formatMoney(totalKept, { whole: true, signed: true })} in total`}
              />
              <Figure
                icon={ShieldCheck}
                label="Emergency fund"
                value={fund ? formatPercent(fund.progress) : "-"}
                detail={fund ? `${fund.monthsCovered.toFixed(1)} months covered` : ""}
              />
              <Figure
                icon={Target}
                label="Goals on track"
                value={`${onTrack} of ${active.length}`}
                detail={active.length ? "active goals" : "no active goals"}
              />
            </ul>
            {months.length > 1 ? <SavingsRateBars months={months} /> : null}
          </section>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <section aria-labelledby="parts-heading" className={card}>
          <h2 id="parts-heading" className="text-lg font-semibold">
            What makes up your score
          </h2>
          <p className="text-sm text-muted">
            Each part is scored out of 100 and weighted by how much it matters.
          </p>
          <ul className="mt-3 divide-y divide-line">
            {score.components.map((component) => (
              <li key={component.key} className="py-3">
                <div className="flex items-baseline justify-between gap-3">
                  <p className="font-semibold">{component.title}</p>
                  <p className="text-sm text-muted">
                    <span className="font-semibold text-ink tabular">{component.score}</span> ·{" "}
                    {component.weight}% of score
                  </p>
                </div>
                <ProgressBar
                  value={component.score / 100}
                  tone={component.score >= 75 ? "good" : component.score >= 50 ? "brand" : "warm"}
                  label={`${component.title} score`}
                  size="sm"
                  className="mt-1.5"
                />
                <p className="mt-1.5 text-sm text-ink-2">{component.detail}</p>
                <p className="text-sm text-muted">{component.tip}</p>
              </li>
            ))}
          </ul>
        </section>

        <div className="grid content-start gap-6">
          <section aria-labelledby="streaks-heading" className={card}>
            <h2 id="streaks-heading" className="text-lg font-semibold">
              Streaks
            </h2>
            <ul className="mt-3 grid grid-cols-2 gap-3">
              {[
                { value: streaks.surplusMonths, label: "months spending less than you earn" },
                { value: streaks.noSpendDays, label: "no-spend days in a row" },
                { value: streaks.noSpendDaysThisMonth, label: "no-spend days this month" },
                ...(streaks.underBudgetWeeks != null
                  ? [{ value: streaks.underBudgetWeeks, label: "weeks under budget" }]
                  : []),
              ].map((item) => (
                <li key={item.label} className="rounded-xl border border-line p-3">
                  <p className="text-2xl font-semibold tabular">{item.value}</p>
                  <p className="text-sm text-ink-2">{item.label}</p>
                </li>
              ))}
            </ul>
          </section>

          <section aria-labelledby="badges-heading" className={card}>
            <div className="flex items-baseline justify-between gap-3">
              <h2 id="badges-heading" className="text-lg font-semibold">
                Badges
              </h2>
              <p className="text-sm text-muted">
                {data.unlocked} of {data.total} earned
              </p>
            </div>
            <ul className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3">
              {sortedBadges.map((achievement) => (
                <li
                  key={achievement.key}
                  title={achievement.description}
                  className={cn(
                    "flex flex-col items-center rounded-xl border border-line p-3 text-center",
                    !achievement.unlocked && "bg-surface-2",
                  )}
                >
                  <span
                    className={cn(
                      "relative grid h-11 w-11 place-items-center rounded-full",
                      achievement.unlocked ? "bg-gold-soft text-gold" : "bg-surface text-muted",
                    )}
                  >
                    <Icon name={achievement.icon} className="h-5 w-5" />
                    {!achievement.unlocked ? (
                      <Lock className="absolute -bottom-0.5 -right-0.5 h-3.5 w-3.5" aria-label="Locked" />
                    ) : null}
                  </span>
                  <p className={cn("mt-2 text-sm font-semibold", !achievement.unlocked && "text-ink-2")}>
                    {achievement.title}
                  </p>
                  {!achievement.unlocked ? (
                    <ProgressBar
                      value={achievement.progress}
                      size="sm"
                      label={`${achievement.title} progress`}
                      className="mt-2"
                    />
                  ) : (
                    <p className="mt-1 inline-flex items-center gap-1 text-xs font-semibold text-gold">
                      <Award className="h-3 w-3" aria-hidden /> Earned
                    </p>
                  )}
                </li>
              ))}
            </ul>
            <p className="mt-3 text-sm text-muted">
              See where your money goes on{" "}
              <Link href="/spending" className="text-brand underline">
                Spending
              </Link>
              .
            </p>
          </section>
        </div>
      </div>
    </div>
  );
}

function Figure({
  icon: Glyph,
  label,
  value,
  detail,
}: {
  icon: typeof PiggyBank;
  label: string;
  value: string;
  detail: string;
}) {
  return (
    <li className="rounded-xl border border-line p-3">
      <p className="flex items-center gap-1.5 text-sm text-muted">
        <Glyph className="h-4 w-4 text-brand" aria-hidden /> {label}
      </p>
      <p className="mt-1 text-xl font-semibold tabular">{value}</p>
      <p className="text-xs text-muted">{detail}</p>
    </li>
  );
}

/** Savings rate for each finished month, as bars above or below zero. */
function SavingsRateBars({ months }: { months: MonthSummary[] }) {
  const peak = Math.max(0.05, ...months.map((month) => Math.abs(month.savingsRate)));
  const label = (month: MonthSummary) =>
    new Date(`${month.month}-01T00:00:00`).toLocaleDateString("en-NZ", { month: "short" });
  return (
    <figure className="mt-5">
      <figcaption className="mb-2 text-sm font-semibold">Share of income saved each month</figcaption>
      <div className="flex h-32 items-center gap-1.5" role="list" aria-label="Savings rate by month">
        {months.map((month) => {
          const height = (Math.abs(month.savingsRate) / peak) * 50;
          const positive = month.savingsRate >= 0;
          return (
            <div
              key={month.month}
              role="listitem"
              aria-label={`${label(month)}: ${formatPercent(month.savingsRate)}`}
              title={`${label(month)}: ${formatPercent(month.savingsRate)} saved`}
              className="flex h-full flex-1 flex-col"
            >
              <div className="flex flex-1 items-end">
                {positive ? (
                  <div
                    className="w-full rounded-t"
                    style={{ height: `${height * 2}%`, background: "var(--chart-in)" }}
                  />
                ) : null}
              </div>
              <div className="h-px bg-line" />
              <div className="flex flex-1 items-start">
                {!positive ? (
                  <div
                    className="w-full rounded-b"
                    style={{ height: `${height * 2}%`, background: "var(--chart-out)" }}
                  />
                ) : null}
              </div>
            </div>
          );
        })}
      </div>
      <div className="mt-1 flex gap-1.5 text-[11px] text-muted">
        {months.map((month) => (
          <span key={month.month} className="flex-1 text-center">
            {label(month)}
          </span>
        ))}
      </div>
    </figure>
  );
}
