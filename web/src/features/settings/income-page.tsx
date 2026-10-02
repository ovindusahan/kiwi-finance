"use client";

import { Calculator, Pencil, Plus, Trash2 } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { EmptyState, ErrorState } from "@/components/ui/empty-state";
import { Field, Input, MoneyInput, Select } from "@/components/ui/field";
import { Icon } from "@/components/ui/icon";
import { PageSkeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { api, ApiError } from "@/lib/api/client";
import { useApiMutation, useIncome } from "@/lib/api/queries";
import type { IncomeSource, IncomeSourceRequest } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { centsToDollarsInput, formatMoney, parseDollars } from "@/lib/format";
import { taxCodeLabel, taxCodes } from "./options";
import { SettingsFrame } from "./settings-nav";

const incomeTypes: { value: IncomeSource["type"]; label: string; icon: string }[] = [
  { value: "SALARY", label: "Salary", icon: "briefcase" },
  { value: "WAGES", label: "Wages", icon: "coins" },
  { value: "BENEFIT", label: "Benefit or pension", icon: "landmark" },
  { value: "SELF_EMPLOYED", label: "Self-employed", icon: "rocket" },
  { value: "OTHER", label: "Other", icon: "sparkles" },
];

const frequencies: { value: IncomeSource["frequency"]; label: string; per: string }[] = [
  { value: "WEEKLY", label: "Weekly", per: "a week" },
  { value: "FORTNIGHTLY", label: "Fortnightly", per: "a fortnight" },
  { value: "FOUR_WEEKLY", label: "Every four weeks", per: "every four weeks" },
  { value: "MONTHLY", label: "Monthly", per: "a month" },
  { value: "ANNUALLY", label: "Yearly", per: "a year" },
];

export function IncomePage() {
  const { data, isLoading, error, refetch } = useIncome();
  const [editing, setEditing] = useState<IncomeSource | "new" | null>(null);
  const toast = useToast();
  const remove = useApiMutation((id: string) => api.delete(`/income-sources/${id}`));

  if (isLoading) return <PageSkeleton />;
  if (error || !data) return <ErrorState message="We couldn't load your income." onRetry={() => refetch()} />;

  return (
    <SettingsFrame
      title="Your income"
      description="We work out your take-home pay after PAYE, ACC, KiwiSaver and student loan, using this year's IRD rates."
      action={
        <Button onClick={() => setEditing("new")}>
          <Plus className="h-4 w-4" aria-hidden /> Add income
        </Button>
      }
    >
      <div className="mb-4 grid gap-3 sm:grid-cols-3">
        <div className="rounded-xl bg-brand-soft p-4">
          <p className="text-sm font-semibold text-brand">Take-home a month</p>
          <p className="font-display text-3xl font-semibold tabular">
            {formatMoney(data.expectedMonthlyTakeHome, { whole: true })}
          </p>
        </div>
        <div className="rounded-xl bg-surface-2 p-4">
          <p className="text-sm font-semibold text-muted">Before tax a year</p>
          <p className="font-display text-3xl font-semibold tabular">
            {formatMoney(data.expectedAnnualGross, { whole: true })}
          </p>
        </div>
        <Link
          href="/pay-calculator"
          className="flex items-center gap-3 rounded-xl border-2 border-dashed border-line p-4 font-semibold hover:border-sky hover:text-sky"
        >
          <Calculator className="h-6 w-6" aria-hidden /> See the full pay breakdown
        </Link>
      </div>

      {data.sources.length === 0 ? (
        <EmptyState
          title="No income added"
          message="Add your pay so we can plan with your real take-home income."
          action={<Button onClick={() => setEditing("new")}>Add income</Button>}
        />
      ) : (
        <div className="space-y-3">
          {data.sources.map((source) => {
            const type = incomeTypes.find((entry) => entry.value === source.type);
            const frequency = frequencies.find((entry) => entry.value === source.frequency);
            return (
              <Card
                key={source.id}
                className={cn("flex flex-wrap items-center gap-4", !source.current && "opacity-60")}
              >
                <span className="grid h-12 w-12 place-items-center rounded-2xl bg-brand-soft text-brand">
                  <Icon name={type?.icon ?? "briefcase"} className="h-6 w-6" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="flex flex-wrap items-center gap-2 text-lg font-semibold">
                    {source.name}
                    {!source.current ? <Badge>Not current</Badge> : null}
                  </p>
                  <p className="text-sm text-muted">
                    {formatMoney(source.amount)} {source.basis === "GROSS" ? "before tax" : "after tax"}{" "}
                    {frequency?.per}
                    {source.taxCode ? ` · tax code ${taxCodeLabel(source.taxCode)}` : ""}
                  </p>
                </div>
                <div className="text-right">
                  <p className="font-display text-2xl font-semibold tabular">
                    {formatMoney(source.takeHomePerPeriod)}
                  </p>
                  <p className="text-xs text-muted">take-home {frequency?.per}</p>
                </div>
                <div className="flex">
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={`Edit ${source.name}`}
                    onClick={() => setEditing(source)}
                  >
                    <Pencil className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={`Delete ${source.name}`}
                    onClick={() =>
                      remove.mutate(source.id, {
                        onSuccess: () => toast("Income removed"),
                        onError: (e) => toast(e.message, "error"),
                      })
                    }
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </Card>
            );
          })}
        </div>
      )}

      <IncomeDialog source={editing} onOpenChange={(open) => !open && setEditing(null)} />
    </SettingsFrame>
  );
}

function IncomeDialog({
  source,
  onOpenChange,
}: {
  source: IncomeSource | "new" | null;
  onOpenChange: (open: boolean) => void;
}) {
  const existing = source && source !== "new" ? source : undefined;
  return (
    <Dialog open={source != null} onOpenChange={onOpenChange}>
      <DialogContent
        title={existing ? "Edit income" : "Add income"}
        description="Enter it the way it appears on your payslip or contract."
      >
        {source ? (
          <IncomeForm key={existing?.id ?? "new"} source={existing} onDone={() => onOpenChange(false)} />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function IncomeForm({ source, onDone }: { source?: IncomeSource; onDone: () => void }) {
  const toast = useToast();
  const [form, setForm] = useState({
    name: source?.name ?? "",
    type: source?.type ?? ("SALARY" as IncomeSource["type"]),
    amount: centsToDollarsInput(source?.amount.cents),
    basis: source?.basis ?? ("GROSS" as IncomeSource["basis"]),
    frequency: source?.frequency ?? ("ANNUALLY" as IncomeSource["frequency"]),
    taxCode: source?.taxCode ?? ("M" as NonNullable<IncomeSource["taxCode"]>),
    startsOn: source?.startsOn ?? "",
    endsOn: source?.endsOn ?? "",
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const save = useApiMutation((request: IncomeSourceRequest) =>
    source
      ? api.put<IncomeSource>(`/income-sources/${source.id}`, request)
      : api.post<IncomeSource>("/income-sources", request),
  );

  function submit() {
    const cents = parseDollars(form.amount);
    const found: Record<string, string> = {};
    if (!form.name.trim()) found.name = "Give this income a name.";
    if (cents == null || cents <= 0) found.amountCents = "Enter an amount.";
    setErrors(found);
    if (Object.keys(found).length) return;
    save.mutate(
      {
        name: form.name.trim(),
        type: form.type,
        amountCents: cents!,
        basis: form.basis,
        frequency: form.frequency,
        taxCode: form.taxCode,
        ...(form.startsOn ? { startsOn: form.startsOn } : {}),
        ...(form.endsOn ? { endsOn: form.endsOn } : {}),
      },
      {
        onSuccess: () => {
          toast(source ? "Income updated" : "Income added");
          onDone();
        },
        onError: (error) => {
          if (error instanceof ApiError && error.errors.length) {
            setErrors(
              Object.fromEntries(
                error.errors.map((e) => [e.field === "endsOnValid" ? "endsOn" : e.field, e.message]),
              ),
            );
          } else toast(error.message, "error");
        },
      },
    );
  }

  return (
    <form
      noValidate
      className="space-y-4"
      onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}
    >
      <div className="grid grid-cols-2 gap-3">
        <Field label="Name" error={errors.name} className="col-span-2">
          {(props) => (
            <Input
              {...props}
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              placeholder="Salary from Acme Ltd"
            />
          )}
        </Field>
        <Field label="Type">
          {(props) => (
            <Select
              {...props}
              value={form.type}
              onChange={(e) => setForm({ ...form, type: e.target.value as IncomeSource["type"] })}
            >
              {incomeTypes.map((type) => (
                <option key={type.value} value={type.value}>
                  {type.label}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="Tax code">
          {(props) => (
            <Select
              {...props}
              value={form.taxCode}
              onChange={(e) =>
                setForm({ ...form, taxCode: e.target.value as NonNullable<IncomeSource["taxCode"]> })
              }
            >
              {taxCodes.map((code) => (
                <option key={code.value} value={code.value}>
                  {code.label}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="Amount" error={errors.amountCents}>
          {(props) => (
            <MoneyInput
              {...props}
              value={form.amount}
              onChange={(e) => setForm({ ...form, amount: e.target.value })}
            />
          )}
        </Field>
        <Field label="Paid">
          {(props) => (
            <Select
              {...props}
              value={form.frequency}
              onChange={(e) => setForm({ ...form, frequency: e.target.value as IncomeSource["frequency"] })}
            >
              {frequencies.map((frequency) => (
                <option key={frequency.value} value={frequency.value}>
                  {frequency.label}
                </option>
              ))}
            </Select>
          )}
        </Field>
      </div>
      <fieldset>
        <legend className="mb-1.5 text-sm font-semibold text-ink-2">That amount is</legend>
        <div className="grid grid-cols-2 gap-2">
          {(["GROSS", "NET"] as const).map((basis) => (
            <button
              key={basis}
              type="button"
              aria-pressed={form.basis === basis}
              onClick={() => setForm({ ...form, basis })}
              className={cn(
                "rounded-2xl border-2 p-3 font-semibold",
                form.basis === basis ? "border-sky bg-sky-soft text-sky" : "border-line text-muted",
              )}
            >
              {basis === "GROSS" ? "Before tax" : "After tax"}
            </button>
          ))}
        </div>
      </fieldset>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Started" hint="Optional">
          {(props) => (
            <Input
              {...props}
              type="date"
              value={form.startsOn}
              onChange={(e) => setForm({ ...form, startsOn: e.target.value })}
            />
          )}
        </Field>
        <Field label="Ends" hint="Optional" error={errors.endsOn}>
          {(props) => (
            <Input
              {...props}
              type="date"
              value={form.endsOn}
              onChange={(e) => setForm({ ...form, endsOn: e.target.value })}
            />
          )}
        </Field>
      </div>
      <Button type="submit" size="lg" block disabled={save.isPending}>
        {source ? "Save changes" : "Add income"}
      </Button>
      <Link href="/pay-calculator" className={cn(buttonVariants({ variant: "ghost", size: "sm" }), "w-full")}>
        Not sure? Try the pay calculator
      </Link>
    </form>
  );
}
