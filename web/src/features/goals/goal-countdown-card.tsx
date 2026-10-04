import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { Icon } from "@/components/ui/icon";
import { ProgressBar } from "@/components/ui/progress";
import type { Goal } from "@/lib/api/types";
import { formatDate, formatMoney, formatMonthYear } from "@/lib/format";
import { goalIcon, goalTone, projectionLabels } from "./goal-meta";

/** A goal shown as a countdown: how long until the date, and how far along the saving is. */
export function GoalCountdownCard({ goal, href = "/goals" }: { goal: Goal; href?: string }) {
  const tone = goalTone(goal);
  const projection = goal.projection;
  const achieved = goal.status === "ACHIEVED" || projection.status === "ACHIEVED";
  const countdown = countdownFor(goal);

  return (
    <Link
      href={href}
      className="group flex h-full flex-col border border-line bg-surface transition-colors hover:border-brand"
    >
      <div className="flex items-center gap-2 border-b border-line px-4 py-3">
        <Icon name={goalIcon(goal.type)} className="h-4 w-4 shrink-0 text-muted" />
        <p className="min-w-0 flex-1 truncate font-semibold text-brand group-hover:underline">{goal.name}</p>
        <Badge tone={achieved ? "good" : projection.status === "BEHIND" ? "warm" : "neutral"}>
          {achieved ? "Achieved" : projectionLabels[projection.status]}
        </Badge>
      </div>
      <div className="flex flex-1 flex-col p-4">
        <p className="text-3xl font-semibold leading-none tabular text-ink">{countdown.value}</p>
        <p className="mt-1 text-sm text-muted">{countdown.label}</p>
        <div className="mt-auto pt-4">
          <ProgressBar value={goal.progress} tone={tone} label={`${goal.name} progress`} />
          <div className="mt-2 flex items-baseline justify-between text-sm">
            <span className="font-semibold tabular">{formatMoney(goal.saved, { whole: true })}</span>
            <span className="text-muted tabular">of {formatMoney(goal.target, { whole: true })}</span>
          </div>
        </div>
      </div>
    </Link>
  );
}

export function countdownFor(goal: Goal): { value: string; label: string } {
  if (goal.status === "ACHIEVED" || goal.projection.status === "ACHIEVED") {
    return {
      value: "100%",
      label: goal.achievedAt ? `Reached ${formatDate(goal.achievedAt)}` : "Goal reached",
    };
  }
  if (goal.daysLeft != null && goal.targetDate) {
    if (goal.daysLeft <= 0) return { value: "Today", label: `Target date ${formatDate(goal.targetDate)}` };
    if (goal.daysLeft > 99 * 7) {
      const months = Math.round(goal.daysLeft / 30.44);
      return { value: String(months), label: `months to ${formatMonthYear(goal.targetDate)}` };
    }
    return { value: String(goal.daysLeft), label: `days to ${formatDate(goal.targetDate)}` };
  }
  if (goal.projection.monthsToGoal != null) {
    return {
      value: String(goal.projection.monthsToGoal),
      label: `months at ${formatMoney(goal.monthlyContribution, { whole: true })} a month`,
    };
  }
  return { value: formatMoney(goal.remaining, { whole: true }), label: "still to save" };
}
