"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { CircleAlert, CircleCheck, Info, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { PageHeader } from "@/components/app/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Field, Input, MoneyInput, Select } from "@/components/ui/field";
import { Icon } from "@/components/ui/icon";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api/client";
import { useApiMutation, useCurrentBudget, useDashboard, useGoalPlan } from "@/lib/api/queries";
import type {
  Budget,
  Goal,
  GoalRequest,
  GoalType,
  PurchaseImpact,
  PurchaseImpactRequest,
} from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { centsToDollarsInput, formatMoney, formatMonthYear, formatPercent, parseDollars } from "@/lib/format";

type Choice = {
  key: string;
  label: string;
  description: string;
  icon: string;
  type?: GoalType;
  planner?: "CAR" | "HOUSE";
  href?: string;
};

const choices: Choice[] = [
  {
    key: "emergency",
    label: "Emergency fund",
    description: "A safety net for the unexpected.",
    icon: "shield",
    href: "/emergency-fund",
  },
  {
    key: "car",
    label: "A car",
    description: "Deposit, finance, running costs and what you can afford.",
    icon: "car",
    type: "CAR",
    planner: "CAR",
  },
  {
    key: "house",
    label: "A home",
    description: "Deposit, mortgage, KiwiSaver and the true monthly cost.",
    icon: "house",
    type: "HOUSE_DEPOSIT",
    planner: "HOUSE",
  },
  { key: "travel", label: "Travel", description: "A trip or holiday.", icon: "plane", type: "TRAVEL" },
  {
    key: "purchase",
    label: "Something to buy",
    description: "A laptop, furniture or anything else.",
    icon: "shopping-bag",
    type: "PURCHASE",
  },
  {
    key: "education",
    label: "Study",
    description: "Course fees, books or a qualification.",
    icon: "graduation-cap",
    type: "EDUCATION",
  },
  {
    key: "wedding",
    label: "A wedding",
    description: "Or another big occasion.",
    icon: "heart",
    type: "WEDDING",
  },
  {
    key: "custom",
    label: "Short-term savings",
    description: "Anything else you want to put money aside for.",
    icon: "piggy-bank",
    type: "CUSTOM",
  },
];

function addMonths(months: number): string {
  const date = new Date();
  date.setDate(1);
  date.setMonth(date.getMonth() + months);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-01`;
}

function monthsUntil(date: string): number {
  const [year = 0, month = 1] = date.split("-").map(Number);
  const now = new Date();
  return Math.max(1, (year - now.getFullYear()) * 12 + (month - 1 - now.getMonth()));
}

/** Starts a new goal: pick what it's for, and the planning adapts to it. */
export function NewGoalPage() {
  const params = useSearchParams();
  const [choice, setChoice] = useState<Choice | null>(
    () => choices.find((item) => item.key === params.get("type") && !item.href) ?? null,
  );
  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="New goal"
        title={
          choice
            ? choice.planner
              ? `Plan for ${choice.label.toLowerCase()}`
              : choice.label
            : "What are you saving for?"
        }
        description={
          choice
            ? "We've filled in what we can from your accounts and spending. Change anything that doesn't fit."
            : "Choose a type and we'll tailor the numbers, from deposits and loans to what you can put aside each month."
        }
      />
      {!choice ? (
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {choices.map((item) => (
            <li key={item.key}>
              {item.href ? (
                <Link
                  href={item.href}
                  className="flex h-full flex-col gap-2 rounded-2xl border border-line bg-surface p-4 hover:border-brand"
                >
                  <ChoiceBody item={item} />
                </Link>
              ) : (
                <button
                  type="button"
                  onClick={() => setChoice(item)}
                  className="flex h-full w-full flex-col gap-2 rounded-2xl border border-line bg-surface p-4 text-left hover:border-brand"
                >
                  <ChoiceBody item={item} />
                </button>
              )}
            </li>
          ))}
        </ul>
      ) : choice.planner ? (
        <PurchasePlanner choice={choice} onBack={() => setChoice(null)} />
      ) : (
        <SimpleGoal choice={choice} onBack={() => setChoice(null)} />
      )}
    </div>
  );
}

function ChoiceBody({ item }: { item: Choice }) {
  return (
    <>
      <span className="grid h-10 w-10 place-items-center rounded-full bg-surface text-brand">
        {item.key === "emergency" ? (
          <ShieldCheck className="h-5 w-5" aria-hidden />
        ) : (
          <Icon name={item.icon} className="h-5 w-5" />
        )}
      </span>
      <span className="font-semibold text-brand">{item.label}</span>
      <span className="text-sm text-ink-2">{item.description}</span>
    </>
  );
}

/** A goal with no loan involved: we suggest what to save each month from what's really spare. */
function SimpleGoal({ choice, onBack }: { choice: Choice; onBack: () => void }) {
  const router = useRouter();
  const toast = useToast();
  const { data: plan } = useGoalPlan();
  const [name, setName] = useState(choice.key === "custom" ? "" : choice.label);
  const [target, setTarget] = useState("");
  const [date, setDate] = useState("");
  const [starting, setStarting] = useState("");
  const [monthly, setMonthly] = useState<string | null>(null);
  const create = useApiMutation((request: GoalRequest) => api.post<Goal>("/goals", request));

  const targetCents = parseDollars(target) ?? 0;
  const startingCents = parseDollars(starting) ?? 0;
  const remaining = Math.max(0, targetCents - startingCents);
  const spare = Math.max(0, plan?.unplanned.cents ?? 0);
  const needed = date ? Math.ceil(remaining / monthsUntil(date) / 100) * 100 : null;
  const suggested = needed ?? (spare > 0 ? Math.min(remaining, Math.ceil(spare / 1000) * 1000) : 0);
  const monthlyCents = monthly == null ? suggested : (parseDollars(monthly) ?? 0);
  const finish = monthlyCents > 0 && remaining > 0 ? addMonths(Math.ceil(remaining / monthlyCents)) : null;

  function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!name.trim() || targetCents <= 0) {
      toast("Give your goal a name and a target.", "error");
      return;
    }
    create.mutate(
      {
        name: name.trim(),
        type: choice.type!,
        targetCents,
        monthlyContributionCents: monthlyCents,
        startingAmountCents: startingCents,
        ...(date ? { targetDate: date } : {}),
      },
      {
        onSuccess: () => {
          toast("Goal created. Let's go!");
          router.push("/goals");
        },
        onError: (failure) => toast(failure.message, "error"),
      },
    );
  }

  return (
    <form onSubmit={submit} className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
      <div className="space-y-4">
        <Field label="Name">
          {(props) => <Input {...props} value={name} onChange={(e) => setName(e.target.value)} />}
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Target">
            {(props) => <MoneyInput {...props} value={target} onChange={(e) => setTarget(e.target.value)} />}
          </Field>
          <Field label="Already saved" hint="Optional">
            {(props) => (
              <MoneyInput {...props} value={starting} onChange={(e) => setStarting(e.target.value)} />
            )}
          </Field>
        </div>
        <Field label="When do you need it?" hint="Optional. Leave blank and we'll suggest a date.">
          {(props) => (
            <Input
              {...props}
              type="month"
              value={date.slice(0, 7)}
              onChange={(e) => setDate(e.target.value ? `${e.target.value}-01` : "")}
            />
          )}
        </Field>
        <Field
          label="Put aside each month"
          hint={monthly == null ? "Filled in from what you can afford" : undefined}
        >
          {(props) => (
            <MoneyInput
              {...props}
              value={monthly ?? centsToDollarsInput(suggested)}
              onChange={(e) => setMonthly(e.target.value)}
            />
          )}
        </Field>
        <div className="flex gap-3">
          <Button type="submit" disabled={create.isPending}>
            Create goal
          </Button>
          <Button variant="secondary" onClick={onBack}>
            Back
          </Button>
        </div>
      </div>

      <aside className="space-y-4 rounded-2xl border border-line bg-surface p-5" aria-label="Recommendation">
        <h2 className="text-lg font-semibold">Our suggestion</h2>
        {!plan ? (
          <Skeleton className="h-24" />
        ) : targetCents <= 0 ? (
          <p className="text-ink-2">
            Enter a target and we&apos;ll work out what to put aside. Right now you have about{" "}
            <span className="font-semibold text-ink">{formatMoney(spare, { whole: true })} a month</span> that
            isn&apos;t planned for anything else.
          </p>
        ) : (
          <>
            <dl className="grid grid-cols-2 gap-4">
              <div>
                <dt className="text-sm text-muted">Spare each month</dt>
                <dd className="text-xl font-semibold tabular">{formatMoney(spare, { whole: true })}</dd>
              </div>
              <div>
                <dt className="text-sm text-muted">{date ? "Needed each month" : "Ready by"}</dt>
                <dd className="text-xl font-semibold tabular">
                  {date
                    ? formatMoney(needed ?? 0, { whole: true })
                    : finish
                      ? formatMonthYear(finish)
                      : "Not yet"}
                </dd>
              </div>
            </dl>
            {date && needed != null && needed > spare ? (
              <Notice tone="warm">
                Reaching {formatMoney(targetCents, { whole: true })} by {formatMonthYear(date)} needs{" "}
                {formatMoney(needed, { whole: true })} a month, more than the{" "}
                {formatMoney(spare, { whole: true })} you have spare.{" "}
                {spare > 0
                  ? `At ${formatMoney(spare, { whole: true })} a month you'd get there by ${formatMonthYear(addMonths(Math.ceil(remaining / spare)))}.`
                  : "Trimming your budget would free some up."}
              </Notice>
            ) : finish ? (
              <Notice tone="good">
                At {formatMoney(monthlyCents, { whole: true })} a month you&apos;ll have it by{" "}
                {formatMonthYear(finish)}
                {monthlyCents <= spare ? ", using money you usually have spare." : "."}
              </Notice>
            ) : null}
            {plan.emergencyFund.cents > 0 ? (
              <p className="text-sm text-ink-2">
                Your emergency fund isn&apos;t full yet, so we keep{" "}
                {formatMoney(plan.emergencyFund, { whole: true })} a month for it before suggesting amounts
                for goals.
              </p>
            ) : null}
          </>
        )}
      </aside>
    </form>
  );
}

function Notice({ tone, children }: { tone: "good" | "warm" | "brand"; children: React.ReactNode }) {
  const Glyph = tone === "good" ? CircleCheck : tone === "warm" ? CircleAlert : Info;
  return (
    <p
      className={cn(
        "flex gap-2 rounded-xl border p-3 text-sm text-ink-2",
        tone === "good"
          ? "border-good/25 bg-good-soft"
          : tone === "warm"
            ? "border-warm/25 bg-warm-soft"
            : "border-brand/20 bg-brand-soft",
      )}
    >
      <Glyph
        className={cn(
          "mt-0.5 h-4 w-4 shrink-0",
          tone === "good" ? "text-good" : tone === "warm" ? "text-warm" : "text-brand",
        )}
        aria-hidden
      />
      <span>{children}</span>
    </p>
  );
}

const verdicts: Record<
  PurchaseImpact["verdict"],
  { label: string; tone: "good" | "brand" | "warm" | "danger" }
> = {
  COMFORTABLE: { label: "Comfortably affordable", tone: "good" },
  TIGHT: { label: "Affordable, but tight", tone: "brand" },
  STRETCH: { label: "A stretch", tone: "warm" },
  NOT_AFFORDABLE: { label: "Not affordable yet", tone: "danger" },
};

const termLabel = (months: number) => (months % 12 === 0 ? `${months / 12} years` : `${months} months`);

/** Works out what a car or home would really cost and what it does to the budget, as you type. */
function PurchasePlanner({ choice, onBack }: { choice: Choice; onBack: () => void }) {
  const router = useRouter();
  const toast = useToast();
  const kind = choice.planner!;
  const house = kind === "HOUSE";
  const { data: dashboard } = useDashboard();
  const budget = useCurrentBudget();
  const [name, setName] = useState(house ? "First home" : "Car");
  const [price, setPrice] = useState(house ? "650000" : "25000");
  const [deposit, setDeposit] = useState<string | null>(null);
  const [finance, setFinance] = useState(true);
  const [rate, setRate] = useState<string | null>(null);
  const [term, setTerm] = useState<number | null>(null);
  const [firstHome, setFirstHome] = useState(true);
  const [running, setRunning] = useState<string | null>(null);

  const request: PurchaseImpactRequest | null = useMemo(() => {
    const priceCents = parseDollars(price);
    if (priceCents == null || priceCents <= 0) return null;
    const depositCents = deposit == null ? undefined : (parseDollars(deposit) ?? 0);
    const rateValue = rate == null || rate === "" ? undefined : Number(rate) / 100;
    return {
      kind,
      priceCents,
      ...(depositCents != null ? { depositCents } : {}),
      ...(rateValue != null && Number.isFinite(rateValue) ? { annualRate: rateValue } : {}),
      termMonths: finance ? (term ?? undefined) : 0,
      ...(running != null && running !== "" ? { ownershipCostsCents: parseDollars(running) ?? 0 } : {}),
      firstHome: house && firstHome,
    };
  }, [kind, price, deposit, rate, term, finance, running, house, firstHome]);

  const [debounced, setDebounced] = useState(request);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(request), 350);
    return () => clearTimeout(timer);
  }, [request]);

  const impact = useQuery({
    queryKey: ["purchase-impact", debounced],
    queryFn: () => api.post<PurchaseImpact>("/planning/purchase-impact", debounced),
    enabled: debounced != null,
    placeholderData: keepPreviousData,
  });
  const result = impact.data;

  const createGoal = useApiMutation((goal: GoalRequest) => api.post<Goal>("/goals", goal));
  const addToBudget = useApiMutation((input: { budget: Budget; impact: PurchaseImpact }) =>
    api.put<Budget>(`/budgets/${input.budget.id}`, {
      name: input.budget.name,
      lines: mergeIntoBudget(input.budget, input.impact),
    }),
  );

  function saveGoal() {
    if (!result) return;
    const months = result.monthsToSave && result.monthsToSave > 0 ? result.monthsToSave : 1;
    createGoal.mutate(
      {
        name: name.trim() || choice.label,
        type: choice.type!,
        targetCents: result.cashNeeded.cents,
        monthlyContributionCents: Math.ceil(result.cashShortfall.cents / months / 100) * 100,
        startingAmountCents: 0,
        ...(result.readyBy ? { targetDate: result.readyBy } : {}),
      },
      {
        onSuccess: () => {
          toast(`${name.trim() || choice.label} added to your goals`);
          router.push("/goals");
        },
        onError: (failure) => toast(failure.message, "error"),
      },
    );
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.4fr)]">
      <form className="space-y-4" onSubmit={(event) => event.preventDefault()}>
        <Field label="Name">
          {(props) => <Input {...props} value={name} onChange={(e) => setName(e.target.value)} />}
        </Field>
        <Field label={house ? "Price of the home" : "Price of the car"}>
          {(props) => <MoneyInput {...props} value={price} onChange={(e) => setPrice(e.target.value)} />}
        </Field>
        <Field label="Deposit" hint={result?.depositGuidance}>
          {(props) => (
            <MoneyInput
              {...props}
              value={deposit ?? (result ? centsToDollarsInput(result.deposit.cents) : "")}
              onChange={(e) => setDeposit(e.target.value)}
            />
          )}
        </Field>
        {house ? (
          <label className="flex items-center justify-between gap-3 border border-line p-3">
            <span>
              <span className="block font-medium">This is my first home</span>
              <span className="block text-sm text-muted">Counts your KiwiSaver towards the deposit.</span>
            </span>
            <Switch checked={firstHome} onCheckedChange={setFirstHome} aria-label="This is my first home" />
          </label>
        ) : (
          <label className="flex items-center justify-between gap-3 border border-line p-3">
            <span>
              <span className="block font-medium">Use car finance</span>
              <span className="block text-sm text-muted">Turn off to save up and pay cash.</span>
            </span>
            <Switch checked={finance} onCheckedChange={setFinance} aria-label="Use car finance" />
          </label>
        )}
        {finance ? (
          <div className="grid grid-cols-2 gap-3">
            <Field label="Interest rate (%)">
              {(props) => (
                <Input
                  {...props}
                  inputMode="decimal"
                  value={
                    rate ?? (result?.loan ? String(Math.round(result.loan.annualRate * 10_000) / 100) : "")
                  }
                  onChange={(e) => setRate(e.target.value)}
                />
              )}
            </Field>
            <Field label={house ? "Mortgage term" : "Loan term"}>
              {(props) => (
                <Select
                  {...props}
                  value={String(term ?? result?.loan?.termMonths ?? (house ? 360 : 60))}
                  onChange={(e) => setTerm(Number(e.target.value))}
                >
                  {(house ? [240, 300, 360] : [36, 48, 60, 84]).map((months) => (
                    <option key={months} value={months}>
                      {termLabel(months)}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
          </div>
        ) : null}
        <Field label="Running costs each month" hint="Leave as is to use typical costs.">
          {(props) => (
            <MoneyInput
              {...props}
              value={
                running ??
                (result
                  ? centsToDollarsInput(
                      Math.round(
                        result.monthlyCosts
                          .filter((cost) => cost.amount.cents > 0 && !/repayment/i.test(cost.label))
                          .reduce((sum, cost) => sum + cost.amount.cents, 0) / 100,
                      ) * 100,
                    )
                  : "")
              }
              onChange={(e) => setRunning(e.target.value)}
            />
          )}
        </Field>
        <div className="flex flex-wrap gap-3 pt-2">
          <Button onClick={saveGoal} disabled={!result || createGoal.isPending}>
            Save as a goal
          </Button>
          <Button variant="secondary" onClick={onBack}>
            Back
          </Button>
        </div>
      </form>

      <section aria-label="What it means for you" className="space-y-6" aria-busy={impact.isFetching}>
        {!result ? (
          impact.error ? (
            <p className="text-danger">{impact.error.message}</p>
          ) : (
            <Skeleton className="h-96" />
          )
        ) : (
          <>
            <div className="rounded-2xl border border-line bg-surface p-5">
              <Badge tone={verdicts[result.verdict].tone}>{verdicts[result.verdict].label}</Badge>
              <p className="mt-2 text-lg font-semibold">{result.headline}</p>
              <dl className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
                <Figure label="New monthly cost" value={formatMoney(result.monthlyTotal, { whole: true })} />
                <Figure
                  label="Cash needed up front"
                  value={formatMoney(result.cashNeeded, { whole: true })}
                />
                <Figure
                  label="Left over after"
                  value={formatMoney(result.surplusAfter, { whole: true, signed: true })}
                  tone={result.surplusAfter.cents < 0 ? "text-danger" : undefined}
                />
                <Figure label="Savings rate after" value={formatPercent(result.savingsRateAfter)} />
              </dl>
            </div>

            {dashboard ? <MonthBar dashboard={dashboard} impact={result} /> : null}

            <div className="grid gap-6 md:grid-cols-2">
              <CostList title="Every month" lines={result.monthlyCosts} total={result.monthlyTotal.cents} />
              <CostList
                title="Up front"
                lines={[{ label: "Deposit", amount: result.deposit, category: null }, ...result.upfrontCosts]}
                total={result.cashNeeded.cents}
                footer={
                  result.cashShortfall.cents > 0
                    ? `You have ${formatMoney(result.cashAvailable, { whole: true })} towards it${
                        result.kiwiSaverAvailable.cents > 0 ? ", including KiwiSaver" : ""
                      }, so ${formatMoney(result.cashShortfall, { whole: true })} to save.`
                    : `You have ${formatMoney(result.cashAvailable, { whole: true })} available, which covers it.`
                }
              />
            </div>

            {result.options.length > 0 ? (
              <div>
                <h3 className="border-b border-line pb-2 text-lg font-semibold">Finance options</h3>
                <table className="w-full text-sm">
                  <thead className="text-left text-muted">
                    <tr>
                      <th className="py-2 font-medium">Term</th>
                      <th className="py-2 text-right font-medium">Each month</th>
                      <th className="py-2 text-right font-medium">Total interest</th>
                      <th className="py-2 text-right font-medium">Left over after</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {result.options.map((option) => {
                      const chosen = option.termMonths === result.loan?.termMonths;
                      return (
                        <tr key={option.termMonths} className={cn(chosen && "bg-panel font-semibold")}>
                          <td className="py-2">
                            <button
                              type="button"
                              onClick={() => setTerm(option.termMonths)}
                              className="text-brand hover:underline"
                              aria-pressed={chosen}
                            >
                              {termLabel(option.termMonths)}
                            </button>
                          </td>
                          <td className="py-2 text-right tabular">
                            {formatMoney(option.monthlyRepayment, { whole: true })}
                          </td>
                          <td className="py-2 text-right tabular">
                            {formatMoney(option.totalInterest, { whole: true })}
                          </td>
                          <td
                            className={cn(
                              "py-2 text-right tabular",
                              option.surplusAfter.cents < 0 && "text-danger",
                            )}
                          >
                            {formatMoney(option.surplusAfter, { whole: true, signed: true })}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            ) : null}

            {result.goalsAffected.length > 0 ? (
              <div className="rounded-xl border border-warm/25 bg-warm-soft p-4">
                <p className="flex items-center gap-2 font-semibold">
                  <CircleAlert className="h-4 w-4 text-warm" aria-hidden /> Your other goals would slow down
                </p>
                <ul className="mt-2 space-y-1 text-sm text-ink-2">
                  {result.goalsAffected.map((goal) => (
                    <li key={goal.goalId}>
                      <span className="font-semibold text-ink">{goal.name}:</span> {goal.change}
                    </li>
                  ))}
                </ul>
              </div>
            ) : (
              <Notice tone="good">Your other goals would stay on track.</Notice>
            )}

            {budget.data ? (
              <div className="flex flex-wrap items-center justify-between gap-3 border border-line p-4">
                <p className="text-sm text-ink-2">
                  Add these monthly costs to your budget so it reflects life after the purchase.
                </p>
                <Button
                  variant="outline"
                  disabled={addToBudget.isPending}
                  onClick={() =>
                    addToBudget.mutate(
                      { budget: budget.data!, impact: result },
                      {
                        onSuccess: () => toast("Your budget now includes these costs"),
                        onError: (failure) => toast(failure.message, "error"),
                      },
                    )
                  }
                >
                  Add to my budget
                </Button>
              </div>
            ) : null}

            {result.notes.length > 0 ? (
              <ul className="space-y-1 text-sm text-muted">
                {result.notes.map((note) => (
                  <li key={note}>{note}</li>
                ))}
              </ul>
            ) : null}
          </>
        )}
      </section>
    </div>
  );
}

function Figure({ label, value, tone }: { label: string; value: string; tone?: string }) {
  return (
    <div>
      <dt className="text-sm text-muted">{label}</dt>
      <dd className={cn("text-xl font-semibold tabular", tone)}>{value}</dd>
    </div>
  );
}

function CostList({
  title,
  lines,
  total,
  footer,
}: {
  title: string;
  lines: PurchaseImpact["monthlyCosts"];
  total: number;
  footer?: string;
}) {
  return (
    <div>
      <h3 className="border-b border-line pb-2 text-lg font-semibold">{title}</h3>
      <ul className="divide-y divide-line text-sm">
        {lines.map((line) => (
          <li key={line.label} className="flex justify-between gap-3 py-2">
            <span>{line.label}</span>
            <span className={cn("tabular", line.amount.cents < 0 && "text-good")}>
              {formatMoney(line.amount, { whole: true, signed: line.amount.cents < 0 })}
            </span>
          </li>
        ))}
        <li className="flex justify-between gap-3 py-2 font-semibold">
          <span>Total</span>
          <span className="tabular">{formatMoney(total, { whole: true })}</span>
        </li>
      </ul>
      {footer ? <p className="mt-1 text-sm text-ink-2">{footer}</p> : null}
    </div>
  );
}

/** Colours checked for colour-blind separation; every segment is also labelled with its amount. */
const segmentColours = { essentials: "#2a78d6", lifestyle: "#d6a100", purchase: "#d1452e", left: "#16915f" };

/** A typical month after the purchase, split into where the money goes. */
function MonthBar({
  dashboard,
  impact,
}: {
  dashboard: NonNullable<ReturnType<typeof useDashboard>["data"]>;
  impact: PurchaseImpact;
}) {
  const income = impact.income.cents;
  if (income <= 0) return null;
  const essentials = dashboard.typical.essentialSpending.cents;
  const lifestyle = Math.max(0, dashboard.typical.spending.cents - essentials);
  const purchase = Math.max(0, impact.monthlyTotal.cents);
  const left = income - essentials - lifestyle - purchase;
  const segments = [
    { key: "essentials", label: "Essentials", value: essentials },
    { key: "lifestyle", label: "Lifestyle", value: lifestyle },
    { key: "purchase", label: house(impact) ? "Home costs" : "Car costs", value: purchase },
    { key: "left", label: left >= 0 ? "Left over" : "Short", value: Math.abs(left) },
  ] as const;
  const scale = Math.max(income, essentials + lifestyle + purchase);
  return (
    <figure>
      <figcaption className="mb-2 text-sm font-semibold">
        A typical month after buying, from {formatMoney(income, { whole: true })} take-home pay
      </figcaption>
      <div
        className="flex h-6 w-full gap-0.5"
        role="img"
        aria-label={segments.map((s) => `${s.label} ${formatMoney(s.value, { whole: true })}`).join(", ")}
      >
        {segments.map((segment) =>
          segment.value > 0 ? (
            <div
              key={segment.key}
              title={`${segment.label}: ${formatMoney(segment.value, { whole: true })}`}
              style={{
                width: `${(segment.value / scale) * 100}%`,
                backgroundColor: segment.key === "left" && left < 0 ? "#ffffff" : segmentColours[segment.key],
                backgroundImage:
                  segment.key === "left" && left < 0
                    ? "repeating-linear-gradient(45deg, #d1452e 0 2px, transparent 2px 6px)"
                    : undefined,
                border: segment.key === "left" && left < 0 ? "1px solid #d1452e" : undefined,
              }}
            />
          ) : null,
        )}
      </div>
      <ul className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-sm">
        {segments.map((segment) => (
          <li key={segment.key} className="flex items-center gap-2">
            <span
              className="h-3 w-3"
              style={
                segment.key === "left" && left < 0
                  ? {
                      border: "1px solid #d1452e",
                      backgroundImage: "repeating-linear-gradient(45deg, #d1452e 0 2px, transparent 2px 5px)",
                    }
                  : { backgroundColor: segmentColours[segment.key] }
              }
              aria-hidden
            />
            <span className="text-ink-2">{segment.label}</span>
            <span className="font-semibold tabular">{formatMoney(segment.value, { whole: true })}</span>
          </li>
        ))}
      </ul>
    </figure>
  );
}

const house = (impact: PurchaseImpact) => impact.kind === "HOUSE";

/** The budget's lines with the purchase's monthly costs added to the matching categories. */
function mergeIntoBudget(budget: Budget, impact: PurchaseImpact) {
  const lines = new Map<string, { categoryId?: string; limitCents: number; rationale?: string }>();
  budget.progress.lines.forEach((line) =>
    lines.set(line.category?.id ?? "", {
      ...(line.category ? { categoryId: line.category.id } : {}),
      limitCents: line.limit.cents,
      rationale: line.rationale ?? undefined,
    }),
  );
  impact.monthlyCosts.forEach((cost) => {
    if (!cost.category) return;
    const existing = lines.get(cost.category.id);
    const limitCents = Math.max(0, (existing?.limitCents ?? 0) + cost.amount.cents);
    lines.set(cost.category.id, {
      categoryId: cost.category.id,
      limitCents,
      rationale: existing?.rationale ?? `Added from your ${impact.kind === "HOUSE" ? "home" : "car"} plan`,
    });
  });
  return [...lines.values()];
}
