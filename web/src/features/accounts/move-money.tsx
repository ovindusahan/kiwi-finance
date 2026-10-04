"use client";

import { Info } from "lucide-react";
import { createContext, useCallback, useContext, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { Field, Input, MoneyInput, Select } from "@/components/ui/field";
import { Segmented } from "@/components/ui/segmented";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api/client";
import { useAccounts, useApiMutation, useCategories } from "@/lib/api/queries";
import type { Account, Category, MoneyMovement, MoneyMovementRequest } from "@/lib/api/types";
import { centsToDollarsInput, formatMoney, parseDollars } from "@/lib/format";

type MovementType = MoneyMovementRequest["type"];

/** How far below zero a recorded move can take an everyday, savings or cash account. */
const OVERDRAFT_CENTS = 10_000;

/** What the dialog starts with, so a screen can open it already pointed at the right accounts. */
export type MoveMoneyDefaults = {
  type?: MovementType;
  fromAccountId?: string;
  toAccountId?: string;
  amountCents?: number;
};

const MoveMoneyContext = createContext<(defaults?: MoveMoneyDefaults) => void>(() => {});

/** Opens the move money dialog from anywhere in the app. */
export function useMoveMoney() {
  return useContext(MoveMoneyContext);
}

export function MoveMoneyProvider({ children }: { children: React.ReactNode }) {
  const [defaults, setDefaults] = useState<MoveMoneyDefaults | null>(null);
  const open = useCallback((next?: MoveMoneyDefaults) => setDefaults(next ?? {}), []);
  return (
    <MoveMoneyContext.Provider value={open}>
      {children}
      {defaults ? <MoveMoneyDialog defaults={defaults} onClose={() => setDefaults(null)} /> : null}
    </MoveMoneyContext.Provider>
  );
}

const typeOptions: { value: MovementType; label: string }[] = [
  { value: "TRANSFER", label: "Transfer" },
  { value: "WITHDRAWAL", label: "Withdraw" },
  { value: "DEPOSIT", label: "Add money" },
];

function today(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}

function MoveMoneyDialog({ defaults, onClose }: { defaults: MoveMoneyDefaults; onClose: () => void }) {
  const toast = useToast();
  const { data: accounts } = useAccounts();
  const { data: categories } = useCategories();
  const open = useMemo(() => (accounts ?? []).filter((account) => !account.archived), [accounts]);

  const [type, setType] = useState<MovementType>(defaults.type ?? "TRANSFER");
  const [from, setFrom] = useState(defaults.fromAccountId ?? "");
  const [to, setTo] = useState(defaults.toAccountId ?? "");
  const [amount, setAmount] = useState(centsToDollarsInput(defaults.amountCents));
  const [date, setDate] = useState(today());
  const [categoryId, setCategoryId] = useState("");
  const [note, setNote] = useState("");

  const record = useApiMutation((request: MoneyMovementRequest) =>
    api.post<MoneyMovement>("/money-movements", request),
  );

  const needsFrom = type !== "DEPOSIT";
  const needsTo = type !== "WITHDRAWAL";
  const fromAccount = open.find((account) => account.id === from);
  const toAccount = open.find((account) => account.id === to);
  const fromBank = [needsFrom ? fromAccount : null, needsTo ? toAccount : null].some(
    (account) => account?.managedBy === "BANK_FEED",
  );
  // Money can only leave an account that holds it, with a small overdraft; spending on a card or
  // loan adds to the debt.
  const available =
    needsFrom && fromAccount && fromAccount.type !== "CREDIT_CARD" && fromAccount.type !== "LOAN"
      ? Math.max(0, fromAccount.balance.cents + OVERDRAFT_CENTS)
      : null;
  const typedCents = parseDollars(amount);
  const tooMuch = available != null && typedCents != null && typedCents > available;
  const categoryChoices = (categories ?? []).filter((category: Category) =>
    type === "WITHDRAWAL"
      ? ["ESSENTIALS", "LIFESTYLE", "DEBT"].includes(category.group)
      : category.group === "INCOME",
  );

  function submit(event: React.FormEvent) {
    event.preventDefault();
    const cents = parseDollars(amount);
    if (cents == null || cents <= 0) {
      toast("Enter an amount more than $0.", "error");
      return;
    }
    if ((needsFrom && !from) || (needsTo && !to)) {
      toast("Choose the accounts the money moved between.", "error");
      return;
    }
    if (type === "TRANSFER" && from === to) {
      toast("Choose two different accounts.", "error");
      return;
    }
    if (tooMuch) {
      toast(
        `You can take up to ${formatMoney(available ?? 0)} from ${fromAccount?.name}, which allows a $100 overdraft.`,
        "error",
      );
      return;
    }
    record.mutate(
      {
        type,
        ...(needsFrom ? { fromAccountId: from } : {}),
        ...(needsTo ? { toAccountId: to } : {}),
        amountCents: cents,
        movedOn: date,
        ...(type !== "TRANSFER" && categoryId ? { categoryId } : {}),
        ...(note.trim() ? { note: note.trim() } : {}),
      },
      {
        onSuccess: () => {
          toast(
            type === "TRANSFER"
              ? `Moved ${formatMoney(cents)} to ${toAccount?.name}. Your figures are up to date.`
              : type === "WITHDRAWAL"
                ? `Recorded ${formatMoney(cents)} out of ${fromAccount?.name}.`
                : `Recorded ${formatMoney(cents)} into ${toAccount?.name}.`,
          );
          onClose();
        },
        onError: (error) => toast(error.message, "error"),
      },
    );
  }

  return (
    <Dialog open onOpenChange={(next) => (next ? null : onClose())}>
      <DialogContent
        title="Move money"
        description="Record money moving between your accounts, out of them or into them."
      >
        <form onSubmit={submit} className="space-y-4">
          <Segmented label="Kind of move" value={type} options={typeOptions} onChange={setType} />
          <div className="grid gap-3 sm:grid-cols-2">
            {needsFrom ? (
              <AccountField label="From" value={from} onChange={setFrom} accounts={open} exclude={to} />
            ) : null}
            {needsTo ? (
              <AccountField label="To" value={to} onChange={setTo} accounts={open} exclude={from} />
            ) : null}
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field
              label="Amount"
              hint={
                available != null
                  ? `Up to ${formatMoney(available)} from ${fromAccount?.name}, including a $100 overdraft`
                  : undefined
              }
              error={
                tooMuch
                  ? `That would take ${fromAccount?.name} more than $100 overdrawn. Enter ${formatMoney(available ?? 0)} or less.`
                  : undefined
              }
            >
              {(props) => (
                <MoneyInput {...props} value={amount} onChange={(event) => setAmount(event.target.value)} />
              )}
            </Field>
            <Field label="Date">
              {(props) => (
                <Input
                  {...props}
                  type="date"
                  value={date}
                  max={today()}
                  onChange={(event) => setDate(event.target.value)}
                />
              )}
            </Field>
          </div>
          {type !== "TRANSFER" ? (
            <Field
              label={type === "WITHDRAWAL" ? "What was it for?" : "Where did it come from?"}
              hint="Optional. It helps your spending and income figures stay accurate."
            >
              {(props) => (
                <Select {...props} value={categoryId} onChange={(event) => setCategoryId(event.target.value)}>
                  <option value="">{type === "WITHDRAWAL" ? "Not sure yet" : "Money I already had"}</option>
                  {categoryChoices.map((category) => (
                    <option key={category.id} value={category.id}>
                      {category.name}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
          ) : null}
          <Field label="Note" hint="Optional, for example “Car repair”.">
            {(props) => (
              <Input
                {...props}
                value={note}
                maxLength={200}
                onChange={(event) => setNote(event.target.value)}
              />
            )}
          </Field>
          {fromBank ? (
            <p className="flex gap-2 rounded-xl border border-brand/20 bg-brand-soft p-3 text-sm text-ink-2">
              <Info className="mt-0.5 h-4 w-4 shrink-0 text-brand" aria-hidden />
              Kiwi Finance updates your figures straight away. Make the payment in your banking app too: when
              your bank sends it through, it replaces this record so nothing is counted twice.
            </p>
          ) : null}
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={record.isPending}>
              {type === "TRANSFER"
                ? "Move money"
                : type === "WITHDRAWAL"
                  ? "Record withdrawal"
                  : "Record deposit"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function AccountField({
  label,
  value,
  onChange,
  accounts,
  exclude,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  accounts: Account[];
  exclude: string;
}) {
  return (
    <Field label={label}>
      {(props) => (
        <Select {...props} value={value} onChange={(event) => onChange(event.target.value)}>
          <option value="">Choose an account</option>
          {accounts
            .filter((account) => account.id !== exclude)
            .map((account) => (
              <option key={account.id} value={account.id}>
                {account.name} ({formatMoney(account.balance)})
                {account.includeInEmergencyFund ? " · Emergency fund" : ""}
              </option>
            ))}
        </Select>
      )}
    </Field>
  );
}
