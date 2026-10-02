"use client";

import { Archive, ArchiveRestore, ArrowLeftRight, Landmark, Plug, Plus } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { ErrorState } from "@/components/ui/empty-state";
import { Field, Input, MoneyInput, Select } from "@/components/ui/field";
import { PageSkeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/components/ui/toast";
import { useMoveMoney } from "@/features/accounts/move-money";
import { ReminderSetting } from "@/features/emergency-fund/emergency-fund-page";
import { api } from "@/lib/api/client";
import { useAccounts, useApiMutation } from "@/lib/api/queries";
import type { Account, AccountType } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { formatMoney, parseDollars } from "@/lib/format";
import { SettingsFrame } from "./settings-nav";

export const accountTypes: { value: AccountType; label: string }[] = [
  { value: "EVERYDAY", label: "Everyday" },
  { value: "SAVINGS", label: "Savings" },
  { value: "CREDIT_CARD", label: "Credit card" },
  { value: "LOAN", label: "Loan" },
  { value: "KIWISAVER", label: "KiwiSaver" },
  { value: "INVESTMENT", label: "Investment" },
  { value: "CASH", label: "Cash" },
  { value: "OTHER", label: "Other" },
];

export function AccountsPage() {
  const { data: accounts, isLoading, error, refetch } = useAccounts(true);
  const [adding, setAdding] = useState(false);
  const moveMoney = useMoveMoney();

  if (isLoading) return <PageSkeleton />;
  if (error || !accounts)
    return <ErrorState message="We couldn't load your accounts." onRetry={() => refetch()} />;

  const fundAccount = accounts.find((account) => account.includeInEmergencyFund && !account.archived);
  const total = accounts
    .filter((account) => !account.archived)
    .reduce((sum, account) => sum + account.balance.cents, 0);

  return (
    <SettingsFrame
      title="Your accounts"
      description="Accounts from your bank feed update on their own. Add cash or anything else you'd like to track by hand."
      action={
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => moveMoney()}>
            <ArrowLeftRight className="h-4 w-4" aria-hidden /> Move money
          </Button>
          <Button onClick={() => setAdding(true)}>
            <Plus className="h-4 w-4" aria-hidden /> Add account
          </Button>
        </div>
      }
    >
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4 border-b border-line pb-4">
        <div>
          <p className="text-sm text-muted">Net worth across these accounts</p>
          <p className="text-3xl font-semibold tabular">{formatMoney(total, { whole: true })}</p>
        </div>
        <p className="text-sm text-ink-2">
          {fundAccount ? (
            <>
              Emergency fund: <span className="font-semibold text-ink">{fundAccount.name}</span>.{" "}
            </>
          ) : (
            "No emergency fund account chosen yet. "
          )}
          <Link href="/emergency-fund" className="text-brand underline">
            {fundAccount ? "Change it" : "Choose one"}
          </Link>
        </p>
      </div>
      <ul className="divide-y divide-line border-b border-line">
        {accounts.map((account) => (
          <AccountRow key={account.id} account={account} />
        ))}
      </ul>
      {!fundAccount ? (
        <div className="mt-6">
          <ReminderSetting />
        </div>
      ) : null}
      <AddAccountDialog open={adding} onOpenChange={setAdding} />
    </SettingsFrame>
  );
}

function AccountRow({ account }: { account: Account }) {
  const toast = useToast();
  const moveMoney = useMoveMoney();
  const update = useApiMutation((body: Partial<Pick<Account, "liquid" | "archived">>) =>
    api.patch<Account>(`/accounts/${account.id}`, body),
  );
  const label = accountTypes.find((type) => type.value === account.type)?.label ?? account.type;

  function change(body: Partial<Pick<Account, "liquid" | "archived">>, message: string) {
    update.mutate(body, {
      onSuccess: () => toast(message),
      onError: (error) => toast(error.message, "error"),
    });
  }

  return (
    <li className={cn("py-4", account.archived && "opacity-60")}>
      <div className="flex flex-wrap items-center gap-4">
        <span className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-surface-2 text-muted">
          {account.managedBy === "BANK_FEED" ? (
            <Plug className="h-5 w-5" aria-hidden />
          ) : (
            <Landmark className="h-5 w-5" aria-hidden />
          )}
        </span>
        <div className="min-w-0 flex-1">
          <p className="flex flex-wrap items-center gap-2 font-semibold">
            {account.name}
            {account.includeInEmergencyFund ? <Badge tone="good">Emergency fund</Badge> : null}
            {account.archived ? <Badge tone="warm">Archived</Badge> : null}
          </p>
          <p className="text-sm text-muted">
            {[
              label,
              account.institution,
              account.managedBy === "BANK_FEED" ? "From your bank" : "Updated by you",
            ]
              .filter(Boolean)
              .join(" · ")}
          </p>
        </div>
        <p className={cn("text-lg font-semibold tabular", account.balance.cents < 0 && "text-danger")}>
          {formatMoney(account.balance)}
        </p>
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-x-6 gap-y-2 pl-15 text-sm">
        <label className="flex items-center gap-2">
          <Switch
            checked={account.liquid}
            onCheckedChange={(liquid) =>
              change({ liquid }, liquid ? "Counted as spendable money" : "No longer counted as spendable")
            }
            aria-label={`${account.name} is spendable money`}
          />
          Spendable money
        </label>
        {!account.archived ? (
          <button
            type="button"
            onClick={() => moveMoney({ type: "TRANSFER", fromAccountId: account.id })}
            className="text-brand hover:underline"
          >
            Move money
          </button>
        ) : null}
        <button
          type="button"
          className="text-brand hover:underline"
          onClick={() =>
            change(
              { archived: !account.archived },
              account.archived ? "Account restored" : "Account archived",
            )
          }
        >
          {account.archived ? (
            <ArchiveRestore className="mr-1 inline h-4 w-4" aria-hidden />
          ) : (
            <Archive className="mr-1 inline h-4 w-4" aria-hidden />
          )}
          {account.archived ? "Restore" : "Archive"}
        </button>
      </div>
    </li>
  );
}

function AddAccountDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const toast = useToast();
  const [form, setForm] = useState({
    name: "",
    type: "CASH" as AccountType,
    institution: "",
    balance: "",
    liquid: true,
  });
  const [error, setError] = useState<string>();
  const create = useApiMutation((body: Record<string, unknown>) => api.post<Account>("/accounts", body));

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        title="Add an account"
        description="Track cash, a term deposit or any account that isn't connected."
      >
        <form
          noValidate
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            if (!form.name.trim()) {
              setError("Give the account a name.");
              return;
            }
            create.mutate(
              {
                name: form.name.trim(),
                type: form.type,
                ...(form.institution.trim() ? { institution: form.institution.trim() } : {}),
                currentBalanceCents: parseDollars(form.balance || "0") ?? 0,
                liquid: form.liquid,
              },
              {
                onSuccess: () => {
                  toast("Account added");
                  setForm({ ...form, name: "", institution: "", balance: "" });
                  onOpenChange(false);
                },
                onError: (failure) => toast(failure.message, "error"),
              },
            );
          }}
        >
          <Field label="Name" error={error}>
            {(props) => (
              <Input
                {...props}
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="Wallet"
              />
            )}
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Type">
              {(props) => (
                <Select
                  {...props}
                  value={form.type}
                  onChange={(e) => setForm({ ...form, type: e.target.value as AccountType })}
                >
                  {accountTypes.map((type) => (
                    <option key={type.value} value={type.value}>
                      {type.label}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
            <Field label="Current balance">
              {(props) => (
                <MoneyInput
                  {...props}
                  value={form.balance}
                  onChange={(e) => setForm({ ...form, balance: e.target.value })}
                  placeholder="0"
                />
              )}
            </Field>
          </div>
          <Field label="Bank or provider" hint="Optional">
            {(props) => (
              <Input
                {...props}
                value={form.institution}
                onChange={(e) => setForm({ ...form, institution: e.target.value })}
              />
            )}
          </Field>
          <label className="flex items-center justify-between rounded-2xl border border-line p-3 font-semibold">
            Spendable money
            <Switch
              checked={form.liquid}
              onCheckedChange={(liquid) => setForm({ ...form, liquid })}
              aria-label="Spendable money"
            />
          </label>

          <Button type="submit" size="lg" block disabled={create.isPending}>
            Add account
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
