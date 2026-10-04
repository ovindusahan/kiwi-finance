"use client";

import { Info, X } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";
import { ExplanationPanel } from "@/components/app/explanation-panel";
import { Button } from "@/components/ui/button";
import { MoneyInput, Select } from "@/components/ui/field";
import { CategoryIcon } from "@/components/ui/icon";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api/client";
import { useApiMutation, useBudgetRecommendation, useCategories } from "@/lib/api/queries";
import type {
  Budget,
  BudgetRecommendation,
  BudgetRequest,
  Category,
  RecommendedBudgetLine,
} from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { centsToDollarsInput, formatMoney, formatPercent, parseDollars } from "@/lib/format";

/** Categories a new budget starts with when there is no spending history to build from. */
const starterSlugs = [
  "rent",
  "groceries",
  "power-gas",
  "internet-phone",
  "fuel",
  "insurance",
  "eating-out",
  "subscriptions",
];
const spendingGroups: Category["group"][] = ["ESSENTIALS", "LIFESTYLE", "DEBT"];
const EVERYTHING_ELSE = "everything-else";

type Row = { key: string; category: Category | null; value: string };

const keyOf = (category: Category | null | undefined) => category?.id ?? EVERYTHING_ELSE;

function initialRows(
  budget: Budget | undefined,
  recommendation: BudgetRecommendation,
  categories: Category[],
): Row[] {
  if (budget) {
    return budget.progress.lines.map((line) => ({
      key: keyOf(line.category),
      category: line.category ?? null,
      value: centsToDollarsInput(line.limit.cents),
    }));
  }
  if (recommendation.lines.length > 0) {
    return recommendation.lines.map((line) => ({
      key: keyOf(line.category),
      category: line.category ?? null,
      value: centsToDollarsInput(line.recommended.cents),
    }));
  }
  return starterSlugs
    .map((slug) => categories.find((category) => category.slug === slug))
    .filter((category): category is Category => Boolean(category))
    .map((category) => ({ key: category.id, category, value: "" }));
}

/**
 * Creates or edits the monthly budget. Limits start from what the person actually spends, so most
 * people only need to review them and save.
 */
export function BudgetEditor({ budget, onDone }: { budget?: Budget; onDone?: () => void }) {
  const recommendation = useBudgetRecommendation();
  const categories = useCategories();

  if (recommendation.error || categories.error) {
    return (
      <p className="text-ink-2">
        We couldn&apos;t load your spending history.{" "}
        <button
          type="button"
          className="text-brand underline"
          onClick={() => {
            void recommendation.refetch();
            void categories.refetch();
          }}
        >
          Try again
        </button>
      </p>
    );
  }
  if (!recommendation.data || !categories.data) return <Skeleton className="h-96" />;
  return (
    <Editor
      budget={budget}
      recommendation={recommendation.data}
      categories={categories.data}
      onDone={onDone}
    />
  );
}

function Editor({
  budget,
  recommendation,
  categories,
  onDone,
}: {
  budget?: Budget;
  recommendation: BudgetRecommendation;
  categories: Category[];
  onDone?: () => void;
}) {
  const toast = useToast();
  const [rows, setRows] = useState<Row[]>(() => initialRows(budget, recommendation, categories));
  const save = useApiMutation((request: BudgetRequest) =>
    budget ? api.put<Budget>(`/budgets/${budget.id}`, request) : api.post<Budget>("/budgets", request),
  );

  const suggestions = useMemo(() => {
    const map = new Map<string, RecommendedBudgetLine>();
    recommendation.lines.forEach((line) => map.set(keyOf(line.category), line));
    return map;
  }, [recommendation]);

  const available = categories
    .filter((category) => spendingGroups.includes(category.group))
    .filter((category) => !rows.some((row) => row.key === category.id))
    .sort((a, b) => a.name.localeCompare(b.name));
  const hasEverythingElse = rows.some((row) => row.key === EVERYTHING_ELSE);

  const total = rows.reduce((sum, row) => sum + (parseDollars(row.value) ?? 0), 0);
  const income = recommendation.monthlyIncome.cents;
  const leftOver = income - total;
  const history = recommendation.lines.length > 0;

  function update(key: string, value: string) {
    setRows((current) => current.map((row) => (row.key === key ? { ...row, value } : row)));
  }

  function add(key: string) {
    if (!key) return;
    const category = key === EVERYTHING_ELSE ? null : (categories.find((item) => item.id === key) ?? null);
    const suggestion = suggestions.get(key);
    setRows((current) => [
      ...current,
      { key, category, value: suggestion ? centsToDollarsInput(suggestion.recommended.cents) : "" },
    ]);
  }

  function applySuggestions() {
    setRows((current) => {
      const next = current.map((row) => {
        const suggestion = suggestions.get(row.key);
        return suggestion ? { ...row, value: centsToDollarsInput(suggestion.recommended.cents) } : row;
      });
      recommendation.lines.forEach((line) => {
        const key = keyOf(line.category);
        if (!next.some((row) => row.key === key)) {
          next.push({
            key,
            category: line.category ?? null,
            value: centsToDollarsInput(line.recommended.cents),
          });
        }
      });
      return next;
    });
  }

  function submit() {
    const lines = rows
      .map((row) => ({ row, cents: parseDollars(row.value) }))
      .filter((item): item is { row: Row; cents: number } => item.cents != null)
      .map(({ row, cents }) => ({
        ...(row.category ? { categoryId: row.category.id } : {}),
        limitCents: cents,
        rationale: suggestions.get(row.key)?.rationale,
      }));
    if (lines.length === 0) {
      toast("Set a limit for at least one category.", "error");
      return;
    }
    if (lines.some((line) => line.limitCents < 0)) {
      toast("Limits can't be negative.", "error");
      return;
    }
    save.mutate(
      { name: budget?.name ?? "Everyday budget", lines },
      {
        onSuccess: () => {
          toast(budget ? "Budget saved" : "Budget created");
          onDone?.();
        },
        onError: (error) => toast(error.message, "error"),
      },
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start gap-3 rounded-xl border border-brand/20 bg-brand-soft p-4">
        <Info className="mt-0.5 h-5 w-5 shrink-0 text-brand" aria-hidden />
        <p className="text-sm text-ink-2">
          {history
            ? `We've filled in limits from your recent spending. ${recommendation.explanation.summary}`
            : "There isn't enough transaction history to suggest limits yet, so we've started you with common categories. Enter what you expect to spend, and we'll suggest limits once your transactions come in."}
        </p>
      </div>

      <section
        aria-label="Budget summary"
        className="grid grid-cols-2 gap-x-6 gap-y-4 border-y border-line py-4 sm:grid-cols-4"
      >
        <SummaryFigure
          label="Take-home a month"
          value={income > 0 ? formatMoney(income, { whole: true }) : "Not set"}
        />
        <SummaryFigure label="Planned spending" value={formatMoney(total, { whole: true })} />
        <SummaryFigure
          label="Left to save"
          value={income > 0 ? formatMoney(leftOver, { whole: true }) : "Unknown"}
          className={income > 0 ? (leftOver >= 0 ? "text-good" : "text-danger") : undefined}
        />
        <SummaryFigure
          label="Savings rate"
          value={income > 0 ? formatPercent(leftOver / income) : "Unknown"}
        />
        {income <= 0 ? (
          <p className="col-span-full text-sm text-muted">
            <Link href="/settings/income" className="text-brand hover:underline">
              Add your income
            </Link>{" "}
            to see how much this budget leaves you to save.
          </p>
        ) : null}
      </section>

      <div>
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line pb-2">
          <h2 className="text-lg font-semibold">Monthly limits</h2>
          {history ? (
            <button type="button" onClick={applySuggestions} className="text-sm text-brand hover:underline">
              Use all suggested limits
            </button>
          ) : null}
        </div>
        <ul className="divide-y divide-line">
          {rows.map((row) => {
            const name = row.category?.name ?? "Everything else";
            const suggestion = suggestions.get(row.key);
            const suggested = suggestion ? centsToDollarsInput(suggestion.recommended.cents) : null;
            const id = `limit-${row.key}`;
            return (
              <li key={row.key} className="flex flex-wrap items-center gap-3 py-3 sm:flex-nowrap">
                <CategoryIcon icon={row.category?.icon} colour={row.category?.colour} size="sm" />
                <div className="min-w-0 flex-1">
                  <label htmlFor={id} className="block font-semibold">
                    {name}
                  </label>
                  <p className="text-sm text-muted">
                    {suggestion ? (
                      <>
                        You usually spend {formatMoney(suggestion.typical, { whole: true })} a month.{" "}
                        {suggested !== row.value ? (
                          <button
                            type="button"
                            onClick={() => update(row.key, suggested ?? "")}
                            className="text-brand hover:underline"
                            aria-label={`Use suggested limit of ${formatMoney(suggestion.recommended, { whole: true })} for ${name}`}
                          >
                            Use {formatMoney(suggestion.recommended, { whole: true })}
                          </button>
                        ) : (
                          <span>Suggested limit.</span>
                        )}
                      </>
                    ) : (
                      "No spending history yet."
                    )}
                  </p>
                </div>
                <div className="w-36">
                  <MoneyInput
                    id={id}
                    value={row.value}
                    placeholder="0"
                    onChange={(event) => update(row.key, event.target.value)}
                    className="py-2"
                  />
                </div>
                <button
                  type="button"
                  onClick={() => setRows((current) => current.filter((item) => item.key !== row.key))}
                  className="grid h-9 w-9 place-items-center text-muted hover:bg-surface-2 hover:text-ink"
                  aria-label={`Remove ${name}`}
                >
                  <X className="h-4 w-4" aria-hidden />
                </button>
              </li>
            );
          })}
        </ul>
        <div className="mt-3 max-w-xs">
          <label htmlFor="add-category" className="sr-only">
            Add a category
          </label>
          <Select id="add-category" value="" onChange={(event) => add(event.target.value)}>
            <option value="">Add a category</option>
            {available.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
                {suggestions.has(category.id) ? " (has spending)" : ""}
              </option>
            ))}
            {!hasEverythingElse ? <option value={EVERYTHING_ELSE}>Everything else</option> : null}
          </Select>
        </div>
      </div>

      <div className="flex flex-wrap gap-3">
        <Button size="lg" onClick={submit} disabled={save.isPending}>
          {budget ? "Save budget" : "Create budget"}
        </Button>
        {onDone && budget ? (
          <Button size="lg" variant="secondary" onClick={onDone}>
            Cancel
          </Button>
        ) : null}
      </div>

      {history && !budget ? <ExplanationPanel explanation={recommendation.explanation} /> : null}
    </div>
  );
}

function SummaryFigure({ label, value, className }: { label: string; value: string; className?: string }) {
  return (
    <div>
      <p className="text-sm text-muted">{label}</p>
      <p className={cn("text-xl font-semibold tabular", className)}>{value}</p>
    </div>
  );
}
