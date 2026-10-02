"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { Field, Input, MoneyInput } from "@/components/ui/field";
import { Icon } from "@/components/ui/icon";
import { useToast } from "@/components/ui/toast";
import { api, ApiError } from "@/lib/api/client";
import { useApiMutation } from "@/lib/api/queries";
import type { Goal, GoalContribution, GoalRequest, GoalType } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { centsToDollarsInput, formatMoney, parseDollars } from "@/lib/format";
import { goalTypes } from "./goal-meta";

type GoalForm = {
  name: string;
  type: GoalType;
  target: string;
  targetDate: string;
  monthly: string;
  starting: string;
};

function initial(goal?: Goal): GoalForm {
  return {
    name: goal?.name ?? "",
    type: goal?.type ?? "TRAVEL",
    target: centsToDollarsInput(goal?.target.cents),
    targetDate: goal?.targetDate ?? "",
    monthly: centsToDollarsInput(goal?.monthlyContribution.cents),
    starting: "",
  };
}

/** Creates a goal, or edits one when {@code goal} is given. */
export function GoalFormDialog({
  open,
  onOpenChange,
  goal,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  goal?: Goal;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        title={goal ? "Edit goal" : "New goal"}
        description={
          goal ? undefined : "Give it a name, a target and, if you like, a date. We'll work out the rest."
        }
      >
        {open ? <GoalFormBody goal={goal} onDone={() => onOpenChange(false)} /> : null}
      </DialogContent>
    </Dialog>
  );
}

function GoalFormBody({ goal, onDone }: { goal?: Goal; onDone: () => void }) {
  const toast = useToast();
  const [form, setForm] = useState<GoalForm>(() => initial(goal));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const save = useApiMutation((request: GoalRequest) =>
    goal ? api.put<Goal>(`/goals/${goal.id}`, request) : api.post<Goal>("/goals", request),
  );

  function submit() {
    const found: Record<string, string> = {};
    const target = parseDollars(form.target);
    const monthly = form.monthly ? parseDollars(form.monthly) : 0;
    const starting = form.starting ? parseDollars(form.starting) : 0;
    if (!form.name.trim()) found.name = "Give your goal a name.";
    if (target == null || target <= 0) found.targetCents = "Enter a target amount.";
    if (monthly == null || monthly < 0)
      found.monthlyContributionCents = "Enter an amount, or leave it empty.";
    if (starting == null || starting < 0) found.startingAmountCents = "Enter an amount, or leave it empty.";
    setErrors(found);
    if (Object.keys(found).length) return;
    const request: GoalRequest = {
      name: form.name.trim(),
      type: form.type,
      targetCents: target!,
      monthlyContributionCents: monthly!,
      priority: goal?.priority,
      ...(form.targetDate ? { targetDate: form.targetDate } : {}),
      ...(goal ? {} : { startingAmountCents: starting! }),
    };
    save.mutate(request, {
      onSuccess: () => {
        toast(goal ? "Goal updated" : "Goal created. Let's go!");
        onDone();
      },
      onError: (error) => {
        if (error instanceof ApiError && error.errors.length)
          setErrors(Object.fromEntries(error.errors.map((e) => [e.field, e.message])));
        else toast(error.message, "error");
      },
    });
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
      <fieldset>
        <legend className="mb-1.5 text-sm font-semibold text-ink-2">What kind of goal?</legend>
        <div className="grid grid-cols-4 gap-2 sm:grid-cols-7">
          {goalTypes.map((type) => (
            <button
              key={type.value}
              type="button"
              onClick={() => setForm((current) => ({ ...current, type: type.value }))}
              aria-pressed={form.type === type.value}
              title={type.label}
              className={cn(
                "flex flex-col items-center gap-1 rounded-2xl border-2 p-2 text-[11px] font-semibold transition-colors",
                form.type === type.value
                  ? "border-brand bg-brand-soft text-brand"
                  : "border-line text-muted hover:bg-surface-2",
              )}
            >
              <Icon name={type.icon} className="h-5 w-5" />
              <span className="truncate">{type.label.split(" ")[0]}</span>
            </button>
          ))}
        </div>
      </fieldset>
      <Field label="Name" error={errors.name}>
        {(props) => (
          <Input
            {...props}
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            placeholder="Trip to Japan"
          />
        )}
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Target" error={errors.targetCents}>
          {(props) => (
            <MoneyInput
              {...props}
              value={form.target}
              onChange={(e) => setForm({ ...form, target: e.target.value })}
            />
          )}
        </Field>
        <Field label="Target date" hint="Optional" error={errors.targetDate}>
          {(props) => (
            <Input
              {...props}
              type="date"
              value={form.targetDate}
              onChange={(e) => setForm({ ...form, targetDate: e.target.value })}
            />
          )}
        </Field>
        <Field label="Put aside each month" error={errors.monthlyContributionCents}>
          {(props) => (
            <MoneyInput
              {...props}
              value={form.monthly}
              onChange={(e) => setForm({ ...form, monthly: e.target.value })}
              placeholder="0"
            />
          )}
        </Field>
        {goal ? null : (
          <Field label="Already saved" error={errors.startingAmountCents}>
            {(props) => (
              <MoneyInput
                {...props}
                value={form.starting}
                onChange={(e) => setForm({ ...form, starting: e.target.value })}
                placeholder="0"
              />
            )}
          </Field>
        )}
      </div>
      <Button type="submit" size="lg" block disabled={save.isPending}>
        {goal ? "Save changes" : "Create goal"}
      </Button>
    </form>
  );
}

/** Records money put towards a goal. */
export function ContributeDialog({
  goal,
  onOpenChange,
}: {
  goal: Goal | null;
  onOpenChange: (open: boolean) => void;
}) {
  const toast = useToast();
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState<string>();
  const contribute = useApiMutation((input: { id: string; amountCents: number; note?: string }) =>
    api.post<GoalContribution>(`/goals/${input.id}/contributions`, {
      amountCents: input.amountCents,
      note: input.note,
    }),
  );

  function submit() {
    const cents = parseDollars(amount);
    if (!goal || cents == null || cents === 0) {
      setError("Enter an amount. Use a minus sign to take money out.");
      return;
    }
    contribute.mutate(
      { id: goal.id, amountCents: cents, note: note.trim() || undefined },
      {
        onSuccess: () => {
          const reached = cents > 0 && goal.saved.cents + cents >= goal.target.cents;
          toast(
            reached
              ? `You did it! ${goal.name} is fully funded.`
              : `${formatMoney(cents)} added to ${goal.name}`,
          );
          setAmount("");
          setNote("");
          onOpenChange(false);
        },
        onError: (failure) => toast(failure.message, "error"),
      },
    );
  }

  return (
    <Dialog open={goal != null} onOpenChange={onOpenChange}>
      <DialogContent
        title={goal ? `Add to ${goal.name}` : "Add money"}
        description={goal ? `${formatMoney(goal.remaining)} to go.` : undefined}
      >
        <form
          noValidate
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            submit();
          }}
        >
          <div className="flex flex-wrap gap-2">
            {[20, 50, 100, 250].map((preset) => (
              <button
                key={preset}
                type="button"
                onClick={() => setAmount(String(preset))}
                className="rounded-lg border border-line px-4 py-1.5 font-semibold hover:border-brand hover:text-brand"
              >
                ${preset}
              </button>
            ))}
          </div>
          <Field label="Amount" error={error}>
            {(props) => (
              <MoneyInput {...props} value={amount} onChange={(e) => setAmount(e.target.value)} autoFocus />
            )}
          </Field>
          <Field label="Note" hint="Optional">
            {(props) => (
              <Input
                {...props}
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="Birthday money"
              />
            )}
          </Field>
          <Button type="submit" size="lg" block disabled={contribute.isPending}>
            Add money
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
