"use client";

import { ChevronLeft, ChevronRight, Pencil, Trash2 } from "lucide-react";
import { useState } from "react";
import { PageHeader } from "@/components/app/page-header";
import { Button } from "@/components/ui/button";
import { Dialog, DialogClose, DialogContent } from "@/components/ui/dialog";
import { ErrorState } from "@/components/ui/empty-state";
import { CategoryIcon } from "@/components/ui/icon";
import { ProgressBar } from "@/components/ui/progress";
import { PageSkeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api/client";
import { useApiMutation, useCurrentBudget } from "@/lib/api/queries";
import type { Budget, BudgetLine } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { formatMoney, formatMonthYear, formatPercent } from "@/lib/format";
import { BudgetEditor } from "./budget-editor";

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function shiftMonth(month: string, by: number): string {
  const [year = 0, value = 1] = month.split("-").map(Number);
  const date = new Date(year, value - 1 + by, 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
}

export function BudgetPage() {
  const thisMonth = currentMonth();
  const [month, setMonth] = useState(thisMonth);
  const budget = useCurrentBudget(month === thisMonth ? undefined : month);

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Budget"
        title="Your monthly budget"
        description="Limits built from what you actually spend, trimmed where it helps you save."
        action={
          <div className="flex items-center gap-1 rounded-xl border border-line bg-surface p-1">
            <Button
              variant="ghost"
              size="icon"
              aria-label="Previous month"
              onClick={() => setMonth(shiftMonth(month, -1))}
              disabled={month <= shiftMonth(thisMonth, -6)}
            >
              <ChevronLeft className="h-5 w-5" />
            </Button>
            <span className="min-w-32 text-center font-semibold">{formatMonthYear(month)}</span>
            <Button
              variant="ghost"
              size="icon"
              aria-label="Next month"
              onClick={() => setMonth(shiftMonth(month, 1))}
              disabled={month >= thisMonth}
            >
              <ChevronRight className="h-5 w-5" />
            </Button>
          </div>
        }
      />
      {budget.isLoading ? (
        <PageSkeleton />
      ) : budget.error ? (
        <ErrorState message="We couldn't load your budget." onRetry={() => budget.refetch()} />
      ) : budget.data ? (
        <BudgetProgressView budget={budget.data} current={month === thisMonth} />
      ) : month === thisMonth ? (
        <BudgetEditor />
      ) : (
        <p className="text-ink-2">There was no budget in {formatMonthYear(month)}.</p>
      )}
    </div>
  );
}

const groups: { status: BudgetLine["status"]; title: string; tone: string }[] = [
  { status: "OVER", title: "Over budget", tone: "text-danger" },
  { status: "AT_RISK", title: "Keep an eye on", tone: "text-warm" },
  { status: "ON_TRACK", title: "On track", tone: "text-good" },
];

function BudgetProgressView({ budget, current }: { budget: Budget; current: boolean }) {
  const toast = useToast();
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const progress = budget.progress;
  const used = progress.totalLimit.cents > 0 ? progress.totalSpent.cents / progress.totalLimit.cents : 0;
  const pace = current ? progress.dayOfMonth / progress.daysInMonth : 1;
  const daysLeft = progress.daysInMonth - progress.dayOfMonth + 1;
  const perDay = current && daysLeft > 0 ? Math.max(0, progress.totalRemaining.cents) / daysLeft : 0;
  const onTrack = progress.lines.length - progress.linesOver - progress.linesAtRisk;
  const remove = useApiMutation(() => api.delete(`/budgets/${budget.id}`));

  if (editing) return <BudgetEditor budget={budget} onDone={() => setEditing(false)} />;

  return (
    <>
      <section aria-label="This month" className="rounded-2xl border border-line bg-surface p-5 sm:p-6">
        <div className="flex flex-wrap items-end justify-between gap-x-8 gap-y-4">
          <div>
            <p className="text-sm text-muted">
              {current ? "Left to spend this month" : `Left over in ${formatMonthYear(progress.month)}`}
            </p>
            <p
              className={cn(
                "text-3xl font-semibold tabular",
                progress.totalRemaining.cents < 0 && "text-danger",
              )}
            >
              {formatMoney(progress.totalRemaining, { whole: true })}
            </p>
            <p className="text-sm text-ink-2 tabular">
              {formatMoney(progress.totalSpent, { whole: true })} spent of{" "}
              {formatMoney(progress.totalLimit, { whole: true })}
            </p>
          </div>
          <dl className="flex flex-wrap gap-x-8 gap-y-3">
            {current ? (
              <Stat label="Safe to spend a day" value={formatMoney(perDay, { whole: true })} />
            ) : null}
            <Stat label="Used" value={formatPercent(used)} />
            {current ? <Stat label="Days left" value={String(daysLeft)} /> : null}
          </dl>
        </div>
        <div className="relative mt-5">
          <ProgressBar
            value={used}
            tone={used > 1 ? "danger" : used > pace + 0.1 ? "warm" : "brand"}
            label="Budget used"
            className="bg-surface-3"
          />
          {current ? (
            <span
              className="absolute -top-1 h-4 w-0.5 rounded-full bg-ink"
              style={{ left: `${Math.min(100, pace * 100)}%` }}
              aria-hidden
            />
          ) : null}
        </div>
        <p className="mt-2 text-xs text-muted">
          {current
            ? `Day ${progress.dayOfMonth} of ${progress.daysInMonth}. The line marks an even pace through the month.`
            : "Final figures for the month."}
        </p>
        <div className="mt-4 flex flex-wrap gap-2 border-t border-line pt-4 text-sm">
          {progress.linesOver > 0 ? (
            <span className="rounded-full bg-danger-soft px-2.5 py-1 font-semibold text-danger">
              {progress.linesOver} over
            </span>
          ) : null}
          {progress.linesAtRisk > 0 ? (
            <span className="rounded-full bg-warm-soft px-2.5 py-1 font-semibold text-warm">
              {progress.linesAtRisk} to watch
            </span>
          ) : null}
          <span className="rounded-full bg-good-soft px-2.5 py-1 font-semibold text-good">
            {onTrack} on track
          </span>
        </div>
      </section>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold">{budget.name}</h2>
          <p className="text-sm text-muted">{progress.lines.length} categories</p>
        </div>
        {current ? (
          <div className="flex gap-1">
            <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
              <Pencil className="h-4 w-4" aria-hidden /> Edit budget
            </Button>
            <Button variant="ghost" size="icon" aria-label="Delete budget" onClick={() => setDeleting(true)}>
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        ) : null}
      </div>

      {groups.map((group) => {
        const lines = progress.lines
          .filter((line) => line.status === group.status)
          .sort((a, b) => b.usedFraction - a.usedFraction);
        if (lines.length === 0) return null;
        return (
          <section key={group.status} aria-labelledby={`budget-${group.status}`}>
            <h3 id={`budget-${group.status}`} className={cn("mb-2 text-sm font-semibold", group.tone)}>
              {group.title} <span className="font-normal text-muted">({lines.length})</span>
            </h3>
            <ul className="divide-y divide-line overflow-hidden rounded-2xl border border-line bg-surface">
              {lines.map((line) => (
                <BudgetLineRow key={line.category?.id ?? "uncategorised"} line={line} />
              ))}
            </ul>
          </section>
        );
      })}

      <Dialog open={deleting} onOpenChange={setDeleting}>
        <DialogContent
          title="Delete this budget?"
          description="You can build a new one from your spending at any time."
        >
          <div className="flex justify-end gap-2">
            <DialogClose asChild>
              <Button variant="secondary">Keep it</Button>
            </DialogClose>
            <Button
              variant="danger"
              onClick={() =>
                remove.mutate(undefined, {
                  onSuccess: () => {
                    setDeleting(false);
                    toast("Budget deleted");
                  },
                  onError: (error) => toast(error.message, "error"),
                })
              }
            >
              Delete
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-sm text-muted">{label}</dt>
      <dd className="font-semibold tabular">{value}</dd>
    </div>
  );
}

function BudgetLineRow({ line }: { line: BudgetLine }) {
  const over = line.status === "OVER";
  const name = line.category?.name ?? "Everything else";
  return (
    <li className="flex items-center gap-3 px-4 py-3">
      <CategoryIcon icon={line.category?.icon} colour={line.category?.colour} />
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline justify-between gap-3">
          <p className="truncate font-semibold">{name}</p>
          <p className={cn("shrink-0 text-sm font-semibold tabular", over && "text-danger")}>
            {over
              ? `${formatMoney(-line.remaining.cents, { whole: true })} over`
              : `${formatMoney(line.remaining, { whole: true })} left`}
          </p>
        </div>
        <ProgressBar
          value={line.usedFraction}
          tone={over ? "danger" : line.status === "AT_RISK" ? "warm" : "brand"}
          label={`${name} budget used`}
          size="sm"
          className="mt-1.5"
        />
        <p className="mt-1 text-xs text-muted tabular">
          {formatMoney(line.spent, { whole: true })} of {formatMoney(line.limit, { whole: true })}
          {line.rationale ? ` · ${line.rationale}` : null}
        </p>
      </div>
    </li>
  );
}
