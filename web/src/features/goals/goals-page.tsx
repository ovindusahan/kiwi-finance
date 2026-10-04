"use client";

import { Check, Lightbulb, Plus, Trophy } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { ExplanationPanel } from "@/components/app/explanation-panel";
import { PageHeader } from "@/components/app/page-header";
import { softTone, textTone } from "@/components/app/tones";
import { Button } from "@/components/ui/button";
import { Dialog, DialogClose, DialogContent } from "@/components/ui/dialog";
import { EmptyState, ErrorState } from "@/components/ui/empty-state";
import { Field, MoneyInput } from "@/components/ui/field";
import { Icon } from "@/components/ui/icon";
import { ProgressBar, ProgressRing } from "@/components/ui/progress";
import { PageSkeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api/client";
import { useApiMutation, useGoalContributions, useGoalPlan, useGoals } from "@/lib/api/queries";
import type { Goal, GoalAdvice, GoalContribution, GoalRequest } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { formatDate, formatMoney, formatMonthYear, formatPercent, parseDollars } from "@/lib/format";
import { countdownFor } from "./goal-countdown-card";
import { GoalCelebration } from "./goal-celebration";
import { GoalFormDialog } from "./goal-dialogs";
import { goalIcon, goalTone, goalTypeLabel, projectionLabels } from "./goal-meta";

/** The changes a suggestion makes to a goal, ready to save. */
function applied(goal: Goal, advice: GoalAdvice): GoalRequest {
  const targetDate = advice.suggestedDate ?? goal.targetDate;
  return {
    name: goal.name,
    type: goal.type,
    priority: goal.priority,
    targetCents: advice.suggestedTarget?.cents ?? goal.target.cents,
    monthlyContributionCents: advice.suggestedMonthly?.cents ?? goal.monthlyContribution.cents,
    ...(targetDate ? { targetDate } : {}),
    ...(goal.linkedAccountId ? { linkedAccountId: goal.linkedAccountId } : {}),
  };
}

const changes = (advice: GoalAdvice | undefined) =>
  Boolean(advice && (advice.suggestedMonthly || advice.suggestedDate || advice.suggestedTarget));

/** Goals that need a decision come first, then the ones due soonest. */
function byUrgency(a: Goal, b: Goal) {
  const rank = (goal: Goal) => (goal.projection.status === "BEHIND" ? 0 : 1);
  if (rank(a) !== rank(b)) return rank(a) - rank(b);
  if (!a.targetDate !== !b.targetDate) return a.targetDate ? -1 : 1;
  return (a.targetDate ?? "").localeCompare(b.targetDate ?? "") || a.priority - b.priority;
}

export function GoalsPage() {
  const router = useRouter();
  const params = useSearchParams();
  useEffect(() => {
    if (params.get("new") === "1") router.replace("/goals/new");
  }, [params, router]);
  const toast = useToast();
  const { data: goals, isLoading, error, refetch } = useGoals();
  const { data: plan } = useGoalPlan();
  const [openId, setOpenId] = useState<string | null>(null);
  const [editing, setEditing] = useState<Goal | null>(null);
  const [deleting, setDeleting] = useState<Goal | null>(null);
  const [celebrating, setCelebrating] = useState<Goal | null>(null);
  const applyAll = useApiMutation(async (items: { goal: Goal; advice: GoalAdvice }[]) => {
    for (const { goal, advice } of items) await api.put<Goal>(`/goals/${goal.id}`, applied(goal, advice));
  });

  if (isLoading) return <PageSkeleton />;
  if (error || !goals) return <ErrorState message="We couldn't load your goals." onRetry={() => refetch()} />;

  const running = goals.filter((goal) => goal.status === "ACTIVE").sort(byUrgency);
  const paused = goals.filter((goal) => goal.status === "PAUSED");
  const achieved = goals
    .filter((goal) => goal.status === "ACHIEVED")
    .sort((a, b) => (b.achievedAt ?? "").localeCompare(a.achievedAt ?? ""));
  const open = [...running, ...paused];
  const adviceFor = (goal: Goal) => plan?.advice.find((item) => item.goalId === goal.id);
  const tips = running
    .map((goal) => ({ goal, advice: adviceFor(goal) }))
    .filter((item): item is { goal: Goal; advice: GoalAdvice } =>
      Boolean(item.advice && item.advice.kind !== "ON_TRACK"),
    );
  const pending = tips.filter((item) => changes(item.advice));
  const saved = open.reduce((sum, goal) => sum + goal.saved.cents, 0);
  const target = open.reduce((sum, goal) => sum + goal.target.cents, 0);
  const monthly = running.reduce((sum, goal) => sum + goal.monthlyContribution.cents, 0);
  const share = target > 0 ? saved / target : 0;
  const selected = goals.find((goal) => goal.id === openId) ?? null;
  const newGoal = () => router.push("/goals/new");

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Goals"
        title="Your goals"
        description="Everything you're saving for, how each one is tracking, and what to do next. Tap a goal for its details."
      />

      {goals.length === 0 ? (
        <EmptyState
          title="No goals yet"
          message="A trip, a car, a house deposit. Give it a target and we'll show you exactly how to get there."
          action={<Button onClick={newGoal}>Set a goal</Button>}
        />
      ) : (
        <div className="grid items-start gap-6 lg:grid-cols-[380px_minmax(0,1fr)]">
          <aside
            aria-label="Overview"
            className="space-y-5 rounded-2xl border border-line bg-surface p-5 sm:p-6 lg:sticky lg:top-32"
          >
            <div>
              <h2 className="text-lg font-semibold">Overview</h2>
              <p className="text-sm text-muted">
                {open.length} goal{open.length === 1 ? "" : "s"} on the go
                {achieved.length > 0 ? `, ${achieved.length} reached` : ""}
              </p>
            </div>
            <div className="flex items-center gap-4">
              <ProgressRing
                value={share}
                size={88}
                stroke={8}
                label={`${formatPercent(share)} of all your goals saved`}
              >
                <span className="text-base font-semibold tabular">{Math.round(share * 100)}%</span>
              </ProgressRing>
              <div className="min-w-0">
                <p className="text-sm text-muted">Saved so far</p>
                <p className="text-2xl font-semibold tabular">{formatMoney(saved, { whole: true })}</p>
                <p className="text-sm text-muted tabular">of {formatMoney(target, { whole: true })}</p>
              </div>
            </div>
            <dl className="grid grid-cols-3 divide-x divide-line rounded-xl border border-line text-center">
              <div className="p-2.5">
                <dt className="text-xs text-muted">Each month</dt>
                <dd className="font-semibold tabular">{formatMoney(monthly, { whole: true })}</dd>
              </div>
              <div className="p-2.5">
                <dt className="text-xs text-muted">Spare</dt>
                <dd className="font-semibold tabular">
                  {plan ? formatMoney(plan.forGoals, { whole: true }) : "-"}
                </dd>
              </div>
              <div className="p-2.5">
                <dt className="text-xs text-muted">Safety net</dt>
                <dd className="font-semibold tabular">
                  {plan ? formatMoney(plan.emergencyFund, { whole: true }) : "-"}
                </dd>
              </div>
            </dl>

            {plan ? (
              tips.length > 0 ? (
                <section aria-labelledby="suggestions-heading">
                  <h3 id="suggestions-heading" className="flex items-center gap-2 font-semibold">
                    <Lightbulb className="h-4 w-4 text-brand" aria-hidden /> Suggestions
                  </h3>
                  <ul className="mt-2 space-y-2">
                    {tips.map(({ goal, advice }) => (
                      <li key={goal.id} className="rounded-xl bg-brand-soft p-3 text-sm">
                        <p className="text-xs font-semibold text-brand">{goal.name}</p>
                        <p className="mt-0.5 font-semibold text-ink">{advice.title}</p>
                        <p className="mt-0.5 text-ink-2">{advice.detail}</p>
                        {changes(advice) ? (
                          <Button
                            size="sm"
                            variant="secondary"
                            className="mt-2"
                            disabled={applyAll.isPending}
                            aria-label={`Apply suggestion for ${goal.name}`}
                            onClick={() =>
                              applyAll.mutate([{ goal, advice }], {
                                onSuccess: () => toast(`${goal.name} updated`),
                                onError: (failure) => toast(failure.message, "error"),
                              })
                            }
                          >
                            Apply
                          </Button>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                  {pending.length > 1 ? (
                    <Button
                      size="sm"
                      className="mt-3 w-full"
                      disabled={applyAll.isPending}
                      onClick={() =>
                        applyAll.mutate(pending, {
                          onSuccess: () => toast(`Updated ${pending.length} goals`),
                          onError: (failure) => toast(failure.message, "error"),
                        })
                      }
                    >
                      Apply all {pending.length}
                    </Button>
                  ) : null}
                </section>
              ) : open.length > 0 ? (
                <p className="flex items-center gap-2 rounded-xl bg-good-soft p-3 text-sm font-semibold text-good">
                  <Check className="h-4 w-4" strokeWidth={3} aria-hidden /> Your goals fit what you have spare
                </p>
              ) : null
            ) : null}

            <Button block onClick={newGoal}>
              <Plus className="h-5 w-5" aria-hidden /> New goal
            </Button>
          </aside>

          <div className="min-w-0 space-y-6">
            <GoalGrid title="Saving for" goals={running} onOpen={setOpenId} adviceFor={adviceFor}>
              <li>
                <button
                  type="button"
                  onClick={newGoal}
                  className="flex h-full min-h-40 w-full flex-col items-center justify-center gap-2 rounded-2xl border border-dashed border-line bg-surface text-brand transition-colors hover:border-brand"
                >
                  <span className="grid h-10 w-10 place-items-center rounded-full bg-brand-soft">
                    <Plus className="h-5 w-5" aria-hidden />
                  </span>
                  <span className="font-semibold">Add a goal</span>
                </button>
              </li>
            </GoalGrid>
            {paused.length > 0 ? (
              <GoalGrid title="Paused" goals={paused} onOpen={setOpenId} adviceFor={adviceFor} />
            ) : null}
            {achieved.length > 0 ? (
              <section aria-labelledby="reached-heading">
                <h2 id="reached-heading" className="mb-3 flex items-center gap-2 text-lg font-semibold">
                  <Trophy className="h-5 w-5 text-gold" aria-hidden /> Goals you&apos;ve reached{" "}
                  <span className="text-base font-normal text-muted">({achieved.length})</span>
                </h2>
                <ul className="grid gap-3 sm:grid-cols-2">
                  {achieved.map((goal) => (
                    <li key={goal.id}>
                      <button
                        type="button"
                        onClick={() => setOpenId(goal.id)}
                        className="flex w-full items-center gap-3 rounded-2xl border border-line bg-surface p-4 text-left transition-colors hover:border-gold"
                      >
                        <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-gold-soft text-gold">
                          <Trophy className="h-5 w-5" aria-hidden />
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="block truncate font-semibold">{goal.name}</span>
                          <span className="block text-sm text-muted">
                            {formatMoney(goal.target, { whole: true })}
                            {goal.achievedAt ? ` · reached ${formatDate(goal.achievedAt)}` : ""}
                          </span>
                        </span>
                        <Check className="h-5 w-5 shrink-0 text-good" strokeWidth={3} aria-hidden />
                      </button>
                    </li>
                  ))}
                </ul>
              </section>
            ) : null}
          </div>
        </div>
      )}

      <GoalDetailsDialog
        goal={selected}
        advice={selected ? adviceFor(selected) : undefined}
        onOpenChange={(isOpen) => !isOpen && setOpenId(null)}
        onEdit={(goal) => {
          setOpenId(null);
          setEditing(goal);
        }}
        onDelete={(goal) => {
          setOpenId(null);
          setDeleting(goal);
        }}
        onReached={(goal) => {
          setOpenId(null);
          setCelebrating(goal);
        }}
      />
      <GoalFormDialog
        open={editing != null}
        onOpenChange={(isOpen) => !isOpen && setEditing(null)}
        goal={editing ?? undefined}
      />
      <DeleteGoalDialog goal={deleting} onOpenChange={(isOpen) => !isOpen && setDeleting(null)} />
      <GoalCelebration
        goal={celebrating}
        onOpenChange={(isOpen) => !isOpen && setCelebrating(null)}
        onNewGoal={() => {
          setCelebrating(null);
          newGoal();
        }}
      />
    </div>
  );
}

function statusStyle(goal: Goal) {
  if (goal.status === "PAUSED") return { label: "Paused", className: "bg-surface-2 text-muted" };
  const label = projectionLabels[goal.projection.status];
  if (goal.projection.status === "BEHIND") return { label, className: "bg-warm-soft text-warm" };
  if (goal.projection.status === "ON_TRACK") return { label, className: "bg-good-soft text-good" };
  return { label, className: "bg-surface-2 text-ink-2" };
}

function GoalGrid({
  title,
  goals,
  onOpen,
  adviceFor,
  children,
}: {
  title: string;
  goals: Goal[];
  onOpen: (id: string) => void;
  adviceFor: (goal: Goal) => GoalAdvice | undefined;
  children?: React.ReactNode;
}) {
  const id = `goals-${title.toLowerCase().replace(/\s+/g, "-")}`;
  return (
    <section aria-labelledby={id}>
      <h2 id={id} className="mb-3 text-lg font-semibold">
        {title} <span className="text-base font-normal text-muted">({goals.length})</span>
      </h2>
      <ul className="grid gap-3 sm:grid-cols-2">
        {goals.map((goal) => (
          <GoalCard key={goal.id} goal={goal} advice={adviceFor(goal)} onOpen={() => onOpen(goal.id)} />
        ))}
        {children}
      </ul>
    </section>
  );
}

/** A small goal card. The whole card opens the goal's details. */
function GoalCard({ goal, advice, onOpen }: { goal: Goal; advice?: GoalAdvice; onOpen: () => void }) {
  const tone = goalTone(goal);
  const status = statusStyle(goal);
  const countdown = countdownFor(goal);
  const hasTip = Boolean(advice && goal.status === "ACTIVE" && advice.kind !== "ON_TRACK");
  return (
    <li
      onClick={onOpen}
      className={cn(
        "flex cursor-pointer flex-col rounded-2xl border border-line bg-surface p-4 transition-all hover:-translate-y-0.5 hover:border-brand hover:shadow-pop",
        goal.status === "PAUSED" && "opacity-75",
      )}
    >
      <div className="flex items-center gap-3">
        <span className={cn("grid h-10 w-10 shrink-0 place-items-center rounded-xl", softTone[tone])}>
          <Icon name={goalIcon(goal.type)} className="h-5 w-5" />
        </span>
        <div className="min-w-0 flex-1">
          <h3>
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                onOpen();
              }}
              className="block max-w-full truncate text-left font-semibold hover:text-brand focus-visible:underline"
            >
              {goal.name}
            </button>
          </h3>
          <p className="truncate text-xs text-muted">
            {goalTypeLabel(goal.type)}
            {goal.targetDate ? ` · by ${formatMonthYear(goal.targetDate)}` : ""}
          </p>
        </div>
        <span className={cn("shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold", status.className)}>
          {status.label}
        </span>
      </div>
      <p className="mt-4">
        <span className="text-xl font-semibold tabular">{formatMoney(goal.saved, { whole: true })}</span>
        <span className="text-sm text-muted tabular"> of {formatMoney(goal.target, { whole: true })}</span>
      </p>
      <ProgressBar
        value={goal.progress}
        tone={tone}
        label={`${goal.name} progress`}
        size="sm"
        className="mt-2"
      />
      <div className="mt-2 flex items-center justify-between gap-2 text-xs text-muted">
        <span className="truncate">
          <span className="font-semibold text-ink tabular">{countdown.value}</span> {countdown.label}
        </span>
        <span className={cn("font-semibold tabular", textTone[tone])}>
          {Math.round(goal.progress * 100)}%
        </span>
      </div>
      {hasTip ? (
        <p className="mt-3 flex items-center gap-1.5 text-xs font-semibold text-brand">
          <Lightbulb className="h-3.5 w-3.5" aria-hidden /> Suggestion inside
        </p>
      ) : null}
    </li>
  );
}

function paceText(goal: Goal): string {
  const projection = goal.projection;
  const monthly = formatMoney(goal.monthlyContribution, { whole: true });
  if (goal.status === "PAUSED")
    return "Paused. Resume it whenever you're ready and the countdown picks up again.";
  if (projection.status === "ACHIEVED") return "Fully funded. Time to enjoy it.";
  if (goal.monthlyContribution.cents === 0) {
    return projection.requiredMonthly
      ? `Put aside ${formatMoney(projection.requiredMonthly, { whole: true })} a month to reach it by ${formatMonthYear(goal.targetDate)}.`
      : "Set a monthly amount to see when you'll get there.";
  }
  const when = projection.projectedDate ? formatMonthYear(projection.projectedDate) : null;
  if (projection.status === "BEHIND" && projection.requiredMonthly) {
    return `At ${monthly} a month you'll get there by ${when}. To make ${formatMonthYear(goal.targetDate)}, put aside ${formatMoney(projection.requiredMonthly, { whole: true })} a month.`;
  }
  return when ? `At ${monthly} a month you'll get there by ${when}.` : `Putting aside ${monthly} a month.`;
}

/** Everything about one goal: figures, suggestion, adding money, history and actions. */
function GoalDetailsDialog({
  goal,
  advice,
  onOpenChange,
  onEdit,
  onDelete,
  onReached,
}: {
  goal: Goal | null;
  advice?: GoalAdvice;
  onOpenChange: (open: boolean) => void;
  onEdit: (goal: Goal) => void;
  onDelete: (goal: Goal) => void;
  onReached: (goal: Goal) => void;
}) {
  const toast = useToast();
  const [amount, setAmount] = useState("");
  const { data: contributions } = useGoalContributions(goal?.id ?? "", goal != null);
  const setStatus = useApiMutation(({ id, status }: { id: string; status: Goal["status"] }) =>
    api.put<Goal>(`/goals/${id}/status`, { status }),
  );
  const apply = useApiMutation(({ id, request }: { id: string; request: GoalRequest }) =>
    api.put<Goal>(`/goals/${id}`, request),
  );
  const contribute = useApiMutation(({ id, cents }: { id: string; cents: number }) =>
    api.post<GoalContribution>(`/goals/${id}/contributions`, { amountCents: cents }),
  );

  if (!goal) return <Dialog open={false} onOpenChange={onOpenChange} />;

  const tone = goalTone(goal);
  const status = statusStyle(goal);
  const countdown = countdownFor(goal);
  const active = goal.status === "ACTIVE";
  const paused = goal.status === "PAUSED";
  const reached = goal.status === "ACHIEVED";
  const suggestion = advice && active && advice.kind !== "ON_TRACK" ? advice : null;
  const quickAmounts = [
    ...new Set(
      [goal.monthlyContribution.cents, 5000, 10000, goal.remaining.cents].filter(
        (cents) => cents > 0 && cents <= goal.remaining.cents,
      ),
    ),
  ].slice(0, 4);

  function add(event: React.FormEvent) {
    event.preventDefault();
    if (!goal) return;
    const cents = parseDollars(amount);
    if (cents == null || cents === 0) {
      toast("Enter an amount to add.", "error");
      return;
    }
    const current = goal;
    contribute.mutate(
      { id: current.id, cents },
      {
        onSuccess: () => {
          setAmount("");
          const total = current.saved.cents + cents;
          if (cents > 0 && current.target.cents > 0 && total >= current.target.cents) {
            onReached({ ...current, saved: { ...current.saved, cents: total } });
          } else {
            toast(`Added ${formatMoney(cents)} to ${current.name}`);
          }
        },
        onError: (failure) => toast(failure.message, "error"),
      },
    );
  }

  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent
        title={goal.name}
        description={`${goalTypeLabel(goal.type)}${goal.targetDate ? ` · by ${formatMonthYear(goal.targetDate)}` : ""}`}
        className="sm:w-[min(640px,92vw)]"
      >
        <div className="space-y-5">
          <div>
            <div className="flex items-end justify-between gap-3">
              <p>
                <span className="text-3xl font-semibold tabular">
                  {formatMoney(goal.saved, { whole: true })}
                </span>
                <span className="text-muted tabular"> of {formatMoney(goal.target, { whole: true })}</span>
              </p>
              <span className={cn("rounded-full px-2.5 py-0.5 text-sm font-semibold", status.className)}>
                {reached ? "Reached" : status.label}
              </span>
            </div>
            <ProgressBar value={goal.progress} tone={tone} label={`${goal.name} progress`} className="mt-3" />
            <p className="mt-2 flex justify-between text-sm text-ink-2">
              <span>
                <span className="font-semibold text-ink tabular">{countdown.value}</span> {countdown.label}
              </span>
              <span className={cn("font-semibold tabular", textTone[tone])}>
                {formatPercent(goal.progress)}
              </span>
            </p>
          </div>

          <dl className="grid grid-cols-3 divide-x divide-line rounded-xl border border-line">
            <div className="p-3">
              <dt className="text-xs text-muted">Still to save</dt>
              <dd className="font-semibold tabular">{formatMoney(goal.remaining, { whole: true })}</dd>
            </div>
            <div className="p-3">
              <dt className="text-xs text-muted">Each month</dt>
              <dd className="font-semibold tabular">
                {formatMoney(goal.monthlyContribution, { whole: true })}
              </dd>
            </div>
            <div className="p-3">
              <dt className="text-xs text-muted">Get there by</dt>
              <dd className="font-semibold">
                {goal.projection.projectedDate ? formatMonthYear(goal.projection.projectedDate) : "-"}
              </dd>
            </div>
          </dl>

          {suggestion ? (
            <div className="rounded-xl bg-brand-soft p-4 text-sm">
              <p className="flex items-center gap-2 font-semibold text-ink">
                <Lightbulb className="h-4 w-4 text-brand" aria-hidden /> {suggestion.title}
              </p>
              <p className="mt-1 text-ink-2">{suggestion.detail}</p>
              {changes(suggestion) ? (
                <Button
                  size="sm"
                  className="mt-3"
                  disabled={apply.isPending}
                  onClick={() =>
                    apply.mutate(
                      { id: goal.id, request: applied(goal, suggestion) },
                      {
                        onSuccess: () => toast(`${goal.name} updated`),
                        onError: (failure) => toast(failure.message, "error"),
                      },
                    )
                  }
                >
                  Apply suggestion
                </Button>
              ) : null}
            </div>
          ) : (
            <p className="text-sm text-ink-2">{paceText(goal)}</p>
          )}

          {active ? (
            <form onSubmit={add} className="rounded-xl border border-line p-4">
              <p className="font-semibold">Add money</p>
              <div className="mt-2 flex flex-wrap gap-2">
                {quickAmounts.map((cents) => (
                  <button
                    key={cents}
                    type="button"
                    onClick={() => setAmount(String(cents / 100))}
                    className="rounded-full border border-line px-3 py-1 text-sm font-semibold hover:border-brand hover:text-brand"
                  >
                    {formatMoney(cents, { whole: true })}
                  </button>
                ))}
              </div>
              <div className="mt-3 flex gap-2">
                <div className="flex-1">
                  <Field label="Amount to add">
                    {(props) => (
                      <MoneyInput
                        {...props}
                        value={amount}
                        onChange={(event) => setAmount(event.target.value)}
                      />
                    )}
                  </Field>
                </div>
                <Button type="submit" className="self-end" disabled={contribute.isPending}>
                  Add money
                </Button>
              </div>
            </form>
          ) : null}

          <div>
            <h3 className="mb-2 font-semibold">Money added</h3>
            {!contributions ? (
              <p className="text-sm text-muted">Loading</p>
            ) : contributions.length === 0 ? (
              <p className="text-sm text-muted">Nothing added yet.</p>
            ) : (
              <ul className="max-h-48 divide-y divide-line overflow-y-auto rounded-xl border border-line">
                {contributions.map((contribution) => (
                  <li
                    key={contribution.id}
                    className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
                  >
                    <span>
                      <span className="block font-semibold">{formatDate(contribution.contributedOn)}</span>
                      {contribution.note ? (
                        <span className="block text-muted">{contribution.note}</span>
                      ) : null}
                    </span>
                    <span
                      className={cn("font-semibold tabular", contribution.amount.cents > 0 && "text-good")}
                    >
                      {formatMoney(contribution.amount, { signed: true })}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <ExplanationPanel explanation={goal.projection.explanation} />

          <div className="flex flex-wrap gap-2 border-t border-line pt-4">
            <Button variant="secondary" size="sm" onClick={() => onEdit(goal)}>
              Edit
            </Button>
            {!reached ? (
              <Button
                variant="secondary"
                size="sm"
                onClick={() =>
                  setStatus.mutate(
                    { id: goal.id, status: paused ? "ACTIVE" : "PAUSED" },
                    {
                      onSuccess: () => toast(paused ? "Goal resumed" : "Goal paused"),
                      onError: (failure) => toast(failure.message, "error"),
                    },
                  )
                }
              >
                {paused ? "Resume" : "Pause"}
              </Button>
            ) : null}
            {!reached ? (
              <Button
                variant="secondary"
                size="sm"
                onClick={() =>
                  setStatus.mutate(
                    { id: goal.id, status: "ACHIEVED" },
                    {
                      onSuccess: () => onReached(goal),
                      onError: (failure) => toast(failure.message, "error"),
                    },
                  )
                }
              >
                Mark as reached
              </Button>
            ) : null}
            <Button variant="ghost" size="sm" className="ml-auto text-danger" onClick={() => onDelete(goal)}>
              Delete
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function DeleteGoalDialog({
  goal,
  onOpenChange,
}: {
  goal: Goal | null;
  onOpenChange: (open: boolean) => void;
}) {
  const toast = useToast();
  const remove = useApiMutation((id: string) => api.delete(`/goals/${id}`));
  return (
    <Dialog open={goal != null} onOpenChange={onOpenChange}>
      <DialogContent
        title={`Delete ${goal?.name ?? "goal"}?`}
        description="This removes the goal and its history. Your bank balances don't change."
      >
        <div className="flex justify-end gap-2">
          <DialogClose asChild>
            <Button variant="secondary">Keep it</Button>
          </DialogClose>
          <Button
            variant="danger"
            disabled={remove.isPending}
            onClick={() =>
              goal &&
              remove.mutate(goal.id, {
                onSuccess: () => {
                  toast("Goal deleted");
                  onOpenChange(false);
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
  );
}
