"use client";

import { useMutation } from "@tanstack/react-query";
import {
  CircleCheck,
  CircleX,
  Clock,
  CalendarClock,
  CircleAlert,
  Landmark,
  Pause,
  PiggyBank,
  Scissors,
  Target,
  Mountain,
  Laptop,
  Plane,
  Car,
  House,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { ExplanationPanel } from "@/components/app/explanation-panel";
import { PageHeader } from "@/components/app/page-header";
import { softTone, textTone, type Tone } from "@/components/app/tones";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Field, Input, MoneyInput } from "@/components/ui/field";
import { ProgressBar } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/components/ui/toast";
import { api, ApiError } from "@/lib/api/client";
import { useApiMutation } from "@/lib/api/queries";
import type { Affordability, AffordabilityLever, Goal, GoalRequest, PurchaseRequest } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { formatMoney, formatMonthYear, parseDate, parseDollars } from "@/lib/format";

type Verdict = Affordability["verdict"];

export const verdicts: Record<Verdict, { label: string; tone: Tone; icon: typeof PiggyBank }> = {
  AFFORDABLE_NOW: { label: "Yes, you can afford it", tone: "good", icon: CircleCheck },
  ON_TRACK: { label: "Yes, you're on track", tone: "good", icon: CircleCheck },
  SAVE_UP: { label: "Yes, with a little saving", tone: "sky", icon: Clock },
  NEEDS_CHANGES: { label: "Possible, with a few changes", tone: "gold", icon: CircleAlert },
  OUT_OF_REACH: { label: "Not yet", tone: "warm", icon: CircleX },
};

function VerdictIcon({ verdict }: { verdict: Verdict }) {
  const Glyph = verdicts[verdict].icon;
  return (
    <span
      className={cn(
        "grid h-12 w-12 shrink-0 place-items-center rounded-full bg-surface",
        textTone[verdicts[verdict].tone],
      )}
    >
      <Glyph className="h-6 w-6" aria-hidden />
    </span>
  );
}

const leverIcons: Record<AffordabilityLever["kind"], typeof PiggyBank> = {
  SAVE_MORE: PiggyBank,
  REDUCE_SPENDING: Scissors,
  PAUSE_GOALS: Pause,
  USE_KIWISAVER: Landmark,
  MOVE_DATE: CalendarClock,
};

type Form = {
  itemName: string;
  price: string;
  desiredDate: string;
  funding: "CASH" | "FINANCE";
  deposit: string;
  rate: string;
  term: string;
  fees: string;
  firstHome: boolean;
};

const blank: Form = {
  itemName: "",
  price: "",
  desiredDate: "",
  funding: "CASH",
  deposit: "",
  rate: "",
  term: "48",
  fees: "",
  firstHome: false,
};

const examples: { label: string; detail: string; icon: typeof PiggyBank; tone: Tone; form: Partial<Form> }[] =
  [
    {
      label: "Weekend in Queenstown",
      detail: "$1,200",
      icon: Mountain,
      tone: "sky",
      form: { itemName: "a weekend in Queenstown", price: "1200" },
    },
    {
      label: "New laptop",
      detail: "$2,500",
      icon: Laptop,
      tone: "lilac",
      form: { itemName: "a new laptop", price: "2500" },
    },
    {
      label: "Trip to Japan by July",
      detail: "$6,000 by a date",
      icon: Plane,
      tone: "warm",
      form: { itemName: "a trip to Japan", price: "6000", desiredDate: nextJuly() },
    },
    {
      label: "Car on finance",
      detail: "$18,000 with a $4,000 deposit",
      icon: Car,
      tone: "good",
      form: {
        itemName: "a Toyota Aqua",
        price: "18000",
        funding: "FINANCE",
        deposit: "4000",
        rate: "11.95",
        term: "48",
        fees: "350",
      },
    },
    {
      label: "First home deposit",
      detail: "$80,000 in 3 years",
      icon: House,
      tone: "gold",
      form: { itemName: "a house deposit", price: "80000", firstHome: true, desiredDate: inYears(3) },
    },
  ];

function nextJuly(): string {
  const now = new Date();
  const year = now.getMonth() >= 5 ? now.getFullYear() + 1 : now.getFullYear();
  return `${year}-07-01`;
}

function inYears(years: number): string {
  const now = new Date();
  return `${now.getFullYear() + years}-${String(now.getMonth() + 1).padStart(2, "0")}-01`;
}

function toRequest(form: Form): { request?: PurchaseRequest; errors: Record<string, string> } {
  const errors: Record<string, string> = {};
  const price = parseDollars(form.price);
  if (!form.itemName.trim()) errors.itemName = "Tell us what you'd like to buy.";
  if (price == null || price <= 0) errors.priceCents = "Enter a price in dollars.";
  const request: PurchaseRequest = {
    itemName: form.itemName.trim(),
    priceCents: price ?? 0,
    funding: form.funding,
    firstHome: form.firstHome,
    ...(form.desiredDate ? { desiredDate: form.desiredDate } : {}),
  };
  if (form.funding === "FINANCE") {
    const deposit = parseDollars(form.deposit || "0");
    const rate = Number(form.rate);
    const term = Number(form.term);
    const fees = parseDollars(form.fees || "0");
    if (deposit == null || deposit < 0) errors.depositCents = "Enter a deposit, or 0.";
    else if (price != null && deposit >= price)
      errors.depositCents = "The deposit must be less than the price.";
    if (!form.rate || Number.isNaN(rate) || rate < 0 || rate > 40)
      errors.loanRate = "Enter the interest rate, for example 11.95.";
    if (!Number.isInteger(term) || term < 1 || term > 360) errors.loanTermMonths = "Enter a term in months.";
    Object.assign(request, {
      depositCents: deposit ?? 0,
      loanRate: Math.round(rate * 100) / 10_000,
      loanTermMonths: term,
      loanFeesCents: fees ?? 0,
    });
  }
  return Object.keys(errors).length ? { errors } : { request, errors };
}

function goalFor(request: PurchaseRequest, answer: Affordability): GoalRequest {
  const name = request.itemName.replace(/^(a|an|the)\s+/i, "");
  const targetDate = answer.realisticDate ?? answer.desiredDate;
  return {
    name: name.charAt(0).toUpperCase() + name.slice(1),
    type: request.firstHome ? "HOUSE_DEPOSIT" : "PURCHASE",
    targetCents: answer.target.cents,
    monthlyContributionCents: answer.requiredMonthlySaving?.cents ?? 0,
    ...(targetDate ? { targetDate } : {}),
  };
}

export function AffordPage() {
  const toast = useToast();
  const router = useRouter();
  const [form, setForm] = useState<Form>(blank);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [result, setResult] = useState<{ request: PurchaseRequest; answer: Affordability } | null>(null);
  const [savedGoal, setSavedGoal] = useState(false);
  const resultRef = useRef<HTMLDivElement>(null);

  const assess = useMutation({
    mutationFn: (request: PurchaseRequest) => api.post<Affordability>("/planning/affordability", request),
    onSuccess: (answer, request) => {
      setResult({ request, answer });
      setSavedGoal(false);
      if (window.matchMedia("(max-width: 1023px)").matches) {
        requestAnimationFrame(() =>
          resultRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }),
        );
      }
    },
    onError: (error) => {
      if (error instanceof ApiError && error.errors.length) {
        setErrors(Object.fromEntries(error.errors.map((e) => [e.field, e.message])));
      } else {
        toast(error instanceof Error ? error.message : "We couldn't work that out.", "error");
      }
    },
  });
  const createGoal = useApiMutation((request: GoalRequest) => api.post<Goal>("/goals", request));

  function update<K extends keyof Form>(key: K, value: Form[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function ask(next: Form) {
    const { request, errors: found } = toRequest(next);
    setErrors(found);
    if (request) assess.mutate(request);
  }

  function saveAsGoal() {
    if (!result) return;
    createGoal.mutate(goalFor(result.request, result.answer), {
      onSuccess: () => {
        setSavedGoal(true);
        toast("Added to your goals");
      },
      onError: (error) => toast(error.message, "error"),
    });
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Tools"
        title="Can I afford it?"
        description="Check anything against your real income, spending, goals and safety net. You'll get a straight answer and a realistic date."
      />

      <div className="grid items-start gap-6 lg:grid-cols-[380px_minmax(0,1fr)]">
        <section
          aria-labelledby="purchase-heading"
          className="rounded-2xl border border-line bg-surface p-5 sm:p-6 lg:sticky lg:top-32"
        >
          <h2 id="purchase-heading" className="text-lg font-semibold">
            Your purchase
          </h2>
          <p className="mb-5 text-sm text-muted">Tell us what it is and how you&apos;d pay.</p>
          <form
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              ask(form);
            }}
            className="space-y-4"
          >
            <Field label="What would you like to buy?" error={errors.itemName}>
              {(props) => (
                <Input
                  {...props}
                  value={form.itemName}
                  onChange={(event) => update("itemName", event.target.value)}
                  placeholder="A trip to Rarotonga"
                  autoComplete="off"
                />
              )}
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Price" error={errors.priceCents}>
                {(props) => (
                  <MoneyInput
                    {...props}
                    value={form.price}
                    onChange={(event) => update("price", event.target.value)}
                    placeholder="3,000"
                  />
                )}
              </Field>
              <Field label="By when?" hint="Optional" error={errors.desiredDate}>
                {(props) => (
                  <Input
                    {...props}
                    type="date"
                    value={form.desiredDate}
                    onChange={(event) => update("desiredDate", event.target.value)}
                  />
                )}
              </Field>
            </div>

            <fieldset>
              <legend className="mb-1.5 text-sm font-medium text-ink-2">How will you pay?</legend>
              <div className="grid grid-cols-2 gap-1 rounded-xl bg-surface-2 p-1">
                {(["CASH", "FINANCE"] as const).map((option) => (
                  <button
                    key={option}
                    type="button"
                    onClick={() => update("funding", option)}
                    aria-pressed={form.funding === option}
                    className={cn(
                      "rounded-lg px-3 py-2 text-sm font-semibold transition-colors",
                      form.funding === option
                        ? "bg-surface text-brand shadow-sm"
                        : "text-ink-2 hover:text-ink",
                    )}
                  >
                    {option === "CASH" ? "Save up" : "Finance"}
                  </button>
                ))}
              </div>
            </fieldset>

            {form.funding === "FINANCE" ? (
              <div className="grid grid-cols-2 gap-3 rounded-xl bg-panel p-3">
                <Field label="Deposit" error={errors.depositCents}>
                  {(props) => (
                    <MoneyInput
                      {...props}
                      value={form.deposit}
                      onChange={(event) => update("deposit", event.target.value)}
                    />
                  )}
                </Field>
                <Field label="Interest rate (%)" error={errors.loanRate}>
                  {(props) => (
                    <Input
                      {...props}
                      inputMode="decimal"
                      value={form.rate}
                      onChange={(event) => update("rate", event.target.value)}
                      placeholder="11.95"
                    />
                  )}
                </Field>
                <Field label="Term (months)" error={errors.loanTermMonths}>
                  {(props) => (
                    <Input
                      {...props}
                      inputMode="numeric"
                      value={form.term}
                      onChange={(event) => update("term", event.target.value)}
                    />
                  )}
                </Field>
                <Field label="Loan fees">
                  {(props) => (
                    <MoneyInput
                      {...props}
                      value={form.fees}
                      onChange={(event) => update("fees", event.target.value)}
                      placeholder="0"
                    />
                  )}
                </Field>
              </div>
            ) : null}

            <label className="flex items-center justify-between gap-3 rounded-xl border border-line px-3 py-2.5">
              <span>
                <span className="block text-sm font-semibold">It&apos;s my first home</span>
                <span className="block text-xs text-muted">Counts a KiwiSaver first-home withdrawal.</span>
              </span>
              <Switch
                checked={form.firstHome}
                onCheckedChange={(checked) => update("firstHome", checked)}
                aria-label="First home"
              />
            </label>

            <Button type="submit" block disabled={assess.isPending}>
              {assess.isPending ? "Working it out" : "Check it"}
            </Button>
          </form>
        </section>

        <div ref={resultRef} className="min-w-0 scroll-mt-32">
          {assess.isPending ? (
            <Skeleton className="h-[480px] rounded-2xl" />
          ) : result ? (
            <AffordabilityResult
              answer={result.answer}
              itemName={result.request.itemName}
              action={
                result.answer.shortfall.cents > 0 ? (
                  savedGoal ? (
                    <Button variant="secondary" onClick={() => router.push("/goals")}>
                      <Target className="h-4 w-4" aria-hidden /> View your goals
                    </Button>
                  ) : (
                    <Button onClick={saveAsGoal} disabled={createGoal.isPending}>
                      <Target className="h-4 w-4" aria-hidden /> Save as a goal
                    </Button>
                  )
                ) : null
              }
            />
          ) : (
            <section aria-labelledby="examples-heading" className="space-y-4">
              <div>
                <h2 id="examples-heading" className="text-lg font-semibold">
                  Try an example
                </h2>
                <p className="text-sm text-muted">Tap one to see how the answer works with your money.</p>
              </div>
              <ul className="grid gap-3 sm:grid-cols-2">
                {examples.map((example) => (
                  <li key={example.label}>
                    <button
                      type="button"
                      onClick={() => {
                        const next = { ...blank, ...example.form };
                        setForm(next);
                        ask(next);
                      }}
                      className="flex w-full items-center gap-4 rounded-2xl border border-line bg-surface p-4 text-left transition-all hover:-translate-y-0.5 hover:border-brand hover:shadow-pop"
                    >
                      <span
                        className={cn(
                          "grid h-12 w-12 shrink-0 place-items-center rounded-2xl",
                          softTone[example.tone],
                        )}
                      >
                        <example.icon className="h-6 w-6" aria-hidden />
                      </span>
                      <span className="min-w-0">
                        <span className="block font-semibold">{example.label}</span>
                        <span className="block text-sm text-muted">{example.detail}</span>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
              <div className="grid gap-3 rounded-2xl border border-line bg-surface p-5 sm:grid-cols-3">
                {[
                  {
                    icon: CircleCheck,
                    title: "A straight answer",
                    text: "Yes, not yet, or what it would take.",
                  },
                  {
                    icon: CalendarClock,
                    title: "A realistic date",
                    text: "From what you really have spare.",
                  },
                  { icon: Scissors, title: "Ways to get there sooner", text: "The changes that help most." },
                ].map((item) => (
                  <div key={item.title} className="flex gap-3">
                    <item.icon className="mt-0.5 h-5 w-5 shrink-0 text-brand" aria-hidden />
                    <div>
                      <p className="text-sm font-semibold">{item.title}</p>
                      <p className="text-sm text-ink-2">{item.text}</p>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          )}
        </div>
      </div>
    </div>
  );
}

export function AffordabilityResult({
  answer,
  itemName,
  action,
}: {
  answer: Affordability;
  itemName: string;
  action?: React.ReactNode;
}) {
  const verdict = verdicts[answer.verdict];
  const months = answer.realisticDate ? monthsUntil(answer.realisticDate) : null;
  const covered = answer.target.cents > 0 ? Math.min(1, answer.availableNow.cents / answer.target.cents) : 1;

  return (
    <div className="animate-rise space-y-4">
      <section aria-label="The answer" className="overflow-hidden rounded-2xl border border-line bg-surface">
        <div className={cn("flex items-start gap-4 p-5 sm:p-6", softTone[verdict.tone])}>
          <VerdictIcon verdict={answer.verdict} />
          <div className="min-w-0">
            <p className="text-sm font-semibold">{verdict.label}</p>
            <h2 className="mt-0.5 text-xl font-semibold leading-snug text-balance text-ink">
              {answer.headline}
            </h2>
          </div>
        </div>

        <div className="space-y-5 p-5 sm:p-6">
          <div>
            <div className="flex items-baseline justify-between gap-3 text-sm">
              <span className="text-muted">
                {answer.loan ? "Deposit" : "Price"}{" "}
                <span className="font-semibold text-ink tabular">
                  {formatMoney(answer.target, { whole: true })}
                </span>
              </span>
              <span className="text-muted">
                You can use now{" "}
                <span className="font-semibold text-ink tabular">
                  {formatMoney(answer.availableNow, { whole: true })}
                </span>
              </span>
            </div>
            <ProgressBar
              value={covered}
              tone={verdict.tone === "warm" ? "warm" : verdict.tone === "good" ? "good" : "brand"}
              label="How much of it you could cover now"
              size="lg"
              className="mt-2"
            />
          </div>

          <dl className="grid grid-cols-3 divide-x divide-line rounded-xl border border-line">
            <div className="p-3 sm:p-4">
              <dt className="text-xs text-muted sm:text-sm">Still to save</dt>
              <dd className="text-lg font-semibold tabular">
                {formatMoney(answer.shortfall, { whole: true })}
              </dd>
            </div>
            <div className="p-3 sm:p-4">
              <dt className="text-xs text-muted sm:text-sm">Save each {answer.payPeriod}</dt>
              <dd className="text-lg font-semibold tabular">
                {answer.shortfall.cents > 0
                  ? formatMoney(answer.requiredPerPayPeriod, { whole: true })
                  : "Nothing"}
              </dd>
            </div>
            <div className="p-3 sm:p-4">
              <dt className="text-xs text-muted sm:text-sm">Realistic date</dt>
              <dd className="text-lg font-semibold">
                {answer.realisticDate ? formatMonthYear(answer.realisticDate) : "Now"}
              </dd>
            </div>
          </dl>

          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-ink-2">
              {months != null && months > 0
                ? `${months} month${months === 1 ? "" : "s"} to go`
                : "You could buy it now"}
              {answer.desiredDate ? ` · you asked for ${formatMonthYear(answer.desiredDate)}` : ""}
            </p>
            {action}
          </div>
        </div>
      </section>

      {answer.warnings.map((warning) => (
        <p
          key={warning}
          className="flex items-start gap-2 rounded-xl border border-warm/25 bg-warm-soft p-3 text-sm font-semibold text-warm"
        >
          <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          {warning}
        </p>
      ))}

      {answer.levers.length > 0 ? (
        <section
          aria-labelledby="levers-heading"
          className="rounded-2xl border border-line bg-surface p-5 sm:p-6"
        >
          <h2 id="levers-heading" className="text-lg font-semibold">
            Ways to get there sooner
          </h2>
          <p className="text-sm text-muted">What would make {itemName} happen, or happen sooner.</p>
          <ul className="mt-3 divide-y divide-line">
            {answer.levers.map((lever) => (
              <LeverRow key={`${lever.kind}-${lever.title}`} lever={lever} />
            ))}
          </ul>
        </section>
      ) : null}

      {answer.loan ? (
        <section
          aria-labelledby="loan-heading"
          className="rounded-2xl border border-line bg-surface p-5 sm:p-6"
        >
          <h2 id="loan-heading" className="text-lg font-semibold">
            The loan
          </h2>
          <p className="text-sm text-muted">{answer.loan.explanation.summary}</p>
          <dl className="mt-4 grid grid-cols-3 divide-x divide-line rounded-xl border border-line text-center">
            {(
              [
                ["Weekly", answer.loan.weeklyRepayment],
                ["Fortnightly", answer.loan.fortnightlyRepayment],
                ["Monthly", answer.loan.monthlyRepayment],
              ] as const
            ).map(([label, value]) => (
              <div key={label} className="p-3">
                <dt className="text-xs text-muted">{label}</dt>
                <dd className="font-semibold tabular">{formatMoney(value)}</dd>
              </div>
            ))}
          </dl>
          <dl className="mt-4 grid grid-cols-2 gap-x-6 gap-y-1.5 text-sm">
            <dt className="text-muted">Borrowed</dt>
            <dd className="text-right font-semibold tabular">{formatMoney(answer.loan.principal)}</dd>
            <dt className="text-muted">Interest over the loan</dt>
            <dd className="text-right font-semibold tabular text-warm">
              {formatMoney(answer.loan.totalInterest)}
            </dd>
            <dt className="text-muted">Fees</dt>
            <dd className="text-right font-semibold tabular">{formatMoney(answer.loan.fees)}</dd>
            <dt className="text-muted">Total repaid</dt>
            <dd className="text-right font-semibold tabular">{formatMoney(answer.loan.totalRepaid)}</dd>
          </dl>
        </section>
      ) : null}

      <ExplanationPanel explanation={answer.explanation} />
    </div>
  );
}

function leverTone(lever: AffordabilityLever): Tone {
  if (lever.kind === "REDUCE_SPENDING") return "lilac";
  if (lever.kind === "USE_KIWISAVER") return "gold";
  if (lever.kind === "PAUSE_GOALS") return "warm";
  return "sky";
}

function LeverRow({ lever }: { lever: AffordabilityLever }) {
  const Glyph = leverIcons[lever.kind];
  return (
    <li className="flex gap-3 py-3">
      <span
        className={cn("grid h-10 w-10 shrink-0 place-items-center rounded-full", softTone[leverTone(lever)])}
      >
        <Glyph className="h-5 w-5" aria-hidden />
      </span>
      <div className="min-w-0 flex-1">
        <p className="font-semibold">{lever.title}</p>
        <p className="text-sm text-ink-2">{lever.description}</p>
        <div className="mt-1.5 flex flex-wrap gap-1.5">
          {lever.resultingDate ? <Badge tone="good">By {formatMonthYear(lever.resultingDate)}</Badge> : null}
          {lever.monthsSooner ? (
            <Badge tone="sky">
              {lever.monthsSooner} month{lever.monthsSooner === 1 ? "" : "s"} sooner
            </Badge>
          ) : null}
        </div>
      </div>
    </li>
  );
}

function monthsUntil(date: string): number {
  const target = parseDate(date);
  const now = new Date();
  return Math.max(0, (target.getFullYear() - now.getFullYear()) * 12 + target.getMonth() - now.getMonth());
}
