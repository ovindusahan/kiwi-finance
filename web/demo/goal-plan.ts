import type { Goal, GoalAdvice, GoalPlan } from "@/lib/api/types";

/**
 * The preview's copy of the API's goal advisor, so suggestions update as goals are added, edited,
 * reached or deleted in the preview. It follows the same rules: the emergency fund first, then
 * goals by how soon they're due.
 */
const money = (cents: number) => ({ cents: Math.round(cents), currency: "NZD" });
const dollars = (cents: number) => `$${Math.round(cents / 100).toLocaleString("en-NZ")}`;
const monthYear = (date: Date) => date.toLocaleDateString("en-NZ", { month: "long", year: "numeric" });

function monthsLeft(targetDate: string, today: Date) {
  const target = new Date(targetDate);
  return Math.max(
    1,
    (target.getFullYear() - today.getFullYear()) * 12 + target.getMonth() - today.getMonth(),
  );
}

function plusMonths(today: Date, months: number) {
  return new Date(today.getFullYear(), today.getMonth() + months, today.getDate());
}

const roundUp = (cents: number, step: number) => Math.ceil(cents / step) * step;

export function planGoals(goals: Goal[], recorded: GoalPlan, today = new Date()): GoalPlan {
  const available = Math.max(0, recorded.available.cents);
  const reserved = Math.min(available, recorded.emergencyFund.cents);
  let left = available - reserved;
  const open = goals
    .filter((goal) => goal.status === "ACTIVE")
    .map((goal) => ({ goal, remaining: Math.max(0, goal.target.cents - goal.saved.cents) }))
    .filter((item) => item.remaining > 0)
    .sort((a, b) => {
      if (!a.goal.targetDate !== !b.goal.targetDate) return a.goal.targetDate ? -1 : 1;
      if (a.goal.targetDate && b.goal.targetDate)
        return (
          monthsLeft(a.goal.targetDate, today) - monthsLeft(b.goal.targetDate, today) ||
          a.goal.priority - b.goal.priority
        );
      return a.goal.priority - b.goal.priority;
    });

  const allocation = new Map<string, number>();
  const required = (item: { goal: Goal; remaining: number }) =>
    roundUp(item.remaining / monthsLeft(item.goal.targetDate!, today), 100);
  for (const item of open.filter((entry) => entry.goal.targetDate)) {
    const given = Math.min(required(item), left);
    allocation.set(item.goal.id, given);
    left -= given;
  }
  const flexible = open.filter((entry) => !entry.goal.targetDate);
  flexible.forEach((item, index) => {
    const share = Math.floor(left / (flexible.length - index));
    const given =
      item.goal.monthlyContribution.cents > 0 ? Math.min(item.goal.monthlyContribution.cents, left) : share;
    allocation.set(item.goal.id, given);
    left -= given;
  });

  const advice: GoalAdvice[] = open.map((item) => {
    const { goal, remaining } = item;
    const allocated = allocation.get(goal.id) ?? 0;
    const current = goal.monthlyContribution.cents;
    const base = {
      goalId: goal.id,
      allocated: money(allocated),
      suggestedMonthly: null,
      suggestedDate: null,
      suggestedTarget: null,
    };
    const finish = (monthly: number) => plusMonths(today, Math.ceil(remaining / monthly));
    if (!goal.targetDate) {
      const urgency = "FLEXIBLE" as const;
      if (current <= 0) {
        if (allocated <= 0)
          return {
            ...base,
            urgency,
            kind: "REVIEW_BUDGET",
            title: "Free up some money for this goal",
            detail: "There's nothing left over each month once your other goals are paid.",
          };
        const suggested = roundUp(allocated, 1000);
        return {
          ...base,
          urgency,
          kind: "SET_CONTRIBUTION",
          title: `Save ${dollars(suggested)} a month`,
          detail: `That would get you there by ${monthYear(finish(suggested))}.`,
          suggestedMonthly: money(suggested),
        };
      }
      if (allocated < current)
        return {
          ...base,
          urgency,
          kind: "REDUCE_CONTRIBUTION",
          title: `Ease off to ${dollars(allocated)} a month`,
          detail: "You've promised more to your goals than you usually have spare.",
          suggestedMonthly: money(allocated),
        };
      return {
        ...base,
        urgency,
        kind: "ON_TRACK",
        title: "On track",
        detail: `At ${dollars(current)} a month you'll get there by ${monthYear(finish(current))}.`,
      };
    }
    const months = monthsLeft(goal.targetDate, today);
    const urgency = months <= 3 ? ("URGENT" as const) : months <= 12 ? ("SOON" as const) : ("LATER" as const);
    const need = required(item);
    const due = monthYear(new Date(goal.targetDate));
    if (allocated >= need) {
      if (current < need)
        return {
          ...base,
          urgency,
          kind: "INCREASE_CONTRIBUTION",
          title: `Save ${dollars(need)} a month`,
          detail: `Raising it from ${dollars(current)} finishes this goal by ${due}, and you can afford it.`,
          suggestedMonthly: money(need),
        };
      if (current > need * 1.25 + 1000)
        return {
          ...base,
          urgency,
          kind: "REDUCE_CONTRIBUTION",
          title: `You could save ${dollars(need)} a month`,
          detail: `That still finishes by ${due} and frees ${dollars(current - need)} a month for your other goals.`,
          suggestedMonthly: money(need),
        };
      return {
        ...base,
        urgency,
        kind: "ON_TRACK",
        title: `On track for ${due}`,
        detail: `Keep saving ${dollars(current)} a month and you'll make it.`,
      };
    }
    if (months <= 6) {
      const target = Math.floor((goal.saved.cents + allocated * months) / 1000) * 1000;
      return {
        ...base,
        urgency,
        kind: "LOWER_TARGET",
        title: `Aim for ${dollars(target)} by ${due}`,
        detail: `${dollars(need)} a month is more than you usually have spare.`,
        suggestedMonthly: allocated > 0 ? money(allocated) : null,
        suggestedTarget: money(target),
      };
    }
    if (allocated <= 0)
      return {
        ...base,
        urgency,
        kind: "REVIEW_BUDGET",
        title: "Free up some money for this goal",
        detail: "Your more urgent goals use everything you have spare right now.",
      };
    const suggested = roundUp(allocated, 100);
    const realistic = finish(suggested);
    return {
      ...base,
      urgency,
      kind: "EXTEND_DATE",
      title: `Move the date to ${monthYear(realistic)}`,
      detail: `Finishing by ${due} needs ${dollars(need)} a month. At ${dollars(suggested)} a month you'll get there by ${monthYear(realistic)}.`,
      suggestedMonthly: money(suggested),
      suggestedDate: `${realistic.getFullYear()}-${String(realistic.getMonth() + 1).padStart(2, "0")}-01`,
    };
  });

  return {
    available: money(available),
    emergencyFund: money(reserved),
    forGoals: money(available - reserved),
    unplanned: money(Math.max(0, left)),
    summary: recorded.summary,
    advice,
  };
}
