"use client";

import { FileUp, Landmark } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { Field, Input, MoneyInput, Select, Textarea } from "@/components/ui/field";
import { CategoryIcon } from "@/components/ui/icon";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/components/ui/toast";
import { api, ApiError } from "@/lib/api/client";
import { useAccounts, useApiMutation } from "@/lib/api/queries";
import type { Category, ImportBatch, Transaction } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { formatDate, formatMoney, parseDollars } from "@/lib/format";
import { CategoryPicker } from "./category-picker";

const sources: Record<Transaction["source"], string> = {
  MANUAL: "Added by you",
  CSV_IMPORT: "Imported from a statement",
  BANK_FEED: "From your bank",
};

/** Shows a transaction and lets a person recategorise it, add notes or mark it as a transfer. */
export function TransactionDialog({
  transaction,
  onOpenChange,
}: {
  transaction: Transaction | null;
  onOpenChange: (open: boolean) => void;
}) {
  return (
    <Dialog open={transaction != null} onOpenChange={onOpenChange}>
      <DialogContent
        title={transaction?.merchant ?? transaction?.description ?? "Transaction"}
        description={transaction ? formatDate(transaction.postedOn) : undefined}
      >
        {transaction ? (
          <TransactionBody
            key={transaction.id}
            transaction={transaction}
            onDone={() => onOpenChange(false)}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function TransactionBody({ transaction, onDone }: { transaction: Transaction; onDone: () => void }) {
  const toast = useToast();
  const [picking, setPicking] = useState(false);
  const [notes, setNotes] = useState(transaction.notes ?? "");
  const [transfer, setTransfer] = useState(transaction.transfer);
  const setCategory = useApiMutation((category: Category | null) =>
    api.put<Transaction>(`/transactions/${transaction.id}/category`, { categoryId: category?.id }),
  );
  const update = useApiMutation((body: { notes: string; transfer: boolean }) =>
    api.patch<Transaction>(`/transactions/${transaction.id}`, body),
  );
  const remove = useApiMutation(() => api.delete(`/transactions/${transaction.id}`));
  const [category, setLocalCategory] = useState(transaction.category);

  return (
    <div className="space-y-4">
      <div className="rounded-xl bg-surface-2 p-4 text-center">
        <p
          className={cn(
            "font-display text-4xl font-semibold tabular",
            transaction.amount.cents > 0 && "text-good",
          )}
        >
          {formatMoney(transaction.amount, { signed: true })}
        </p>
        <p className="mt-1 text-sm text-muted">{transaction.description}</p>
        <p className="mt-2 inline-flex items-center gap-1.5 text-xs font-semibold text-muted">
          <Landmark className="h-3.5 w-3.5" aria-hidden />
          {transaction.accountName ?? "Account"} · {sources[transaction.source]}
        </p>
      </div>

      <div>
        <p className="mb-1.5 text-sm font-semibold text-ink-2">Category</p>
        <button
          type="button"
          onClick={() => setPicking(true)}
          className="flex w-full items-center gap-3 rounded-2xl border-2 border-line p-3 text-left hover:bg-surface-2"
        >
          <CategoryIcon icon={category?.icon ?? "circle-help"} colour={category?.colour} size="sm" />
          <span className="flex-1 font-semibold">{category?.name ?? "Uncategorised"}</span>
          <span className="text-sm font-semibold text-sky">Change</span>
        </button>
      </div>

      <Field label="Notes">
        {(props) => (
          <Textarea
            {...props}
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            placeholder="Add a note"
          />
        )}
      </Field>
      <label className="flex items-center justify-between gap-3 rounded-2xl border border-line p-3">
        <span>
          <span className="block font-semibold">Transfer between my accounts</span>
          <span className="block text-xs text-muted">Transfers don&apos;t count as spending or income.</span>
        </span>
        <Switch checked={transfer} onCheckedChange={setTransfer} aria-label="Transfer between my accounts" />
      </label>

      <div className="flex gap-2">
        {transaction.source === "MANUAL" ? (
          <Button
            variant="ghost"
            onClick={() =>
              remove.mutate(undefined, {
                onSuccess: () => {
                  toast("Transaction deleted");
                  onDone();
                },
                onError: (error) => toast(error.message, "error"),
              })
            }
          >
            Delete
          </Button>
        ) : null}
        <Button
          className="ml-auto"
          disabled={
            update.isPending || (notes === (transaction.notes ?? "") && transfer === transaction.transfer)
          }
          onClick={() =>
            update.mutate(
              { notes, transfer },
              {
                onSuccess: () => {
                  toast("Saved");
                  onDone();
                },
                onError: (error) => toast(error.message, "error"),
              },
            )
          }
        >
          Save
        </Button>
      </div>

      <CategoryPicker
        open={picking}
        onOpenChange={setPicking}
        selectedId={category?.id}
        allowClear
        onSelect={(next) => {
          setPicking(false);
          setCategory.mutate(next, {
            onSuccess: () => {
              setLocalCategory(next);
              toast(next ? `Moved to ${next.name}` : "Category removed");
            },
            onError: (error) => toast(error.message, "error"),
          });
        }}
      />
    </div>
  );
}

/** Adds a transaction by hand, for cash or accounts without a bank feed. */
export function AddTransactionDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const toast = useToast();
  const { data: accounts } = useAccounts();
  const manual = accounts?.filter((account) => account.managedBy === "USER" && !account.archived) ?? [];
  const today = new Date().toISOString().slice(0, 10);
  const [form, setForm] = useState({
    accountId: "",
    postedOn: today,
    description: "",
    amount: "",
    direction: "OUT" as "IN" | "OUT",
  });
  const [category, setCategory] = useState<Category | null>(null);
  const [picking, setPicking] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const create = useApiMutation((body: Record<string, unknown>) =>
    api.post<Transaction>("/transactions", body),
  );

  // With only one account to choose from, choose it.
  const accountId = form.accountId || (manual.length === 1 ? manual[0]!.id : "");

  function submit() {
    const cents = parseDollars(form.amount);
    const found: Record<string, string> = {};
    if (!accountId) found.accountId = "Choose an account.";
    if (!form.description.trim()) found.description = "Describe the transaction.";
    if (cents == null || cents <= 0) found.amountCents = "Enter an amount.";
    setErrors(found);
    if (Object.keys(found).length) return;
    create.mutate(
      {
        accountId,
        postedOn: form.postedOn,
        description: form.description.trim(),
        amountCents: form.direction === "OUT" ? -cents! : cents!,
        ...(category ? { categoryId: category.id } : {}),
      },
      {
        onSuccess: () => {
          toast("Transaction added");
          setForm({ ...form, description: "", amount: "" });
          setCategory(null);
          onOpenChange(false);
        },
        onError: (error) => {
          if (error instanceof ApiError && error.errors.length)
            setErrors(Object.fromEntries(error.errors.map((e) => [e.field, e.message])));
          else toast(error.message, "error");
        },
      },
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        title="Add a transaction"
        description="For cash, or an account that isn't connected to a bank feed."
      >
        {manual.length === 0 ? (
          <p className="text-ink-2">
            Add an account you manage yourself in Settings first. Accounts linked to your bank update on their
            own.
          </p>
        ) : (
          <form
            noValidate
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault();
              submit();
            }}
          >
            <div className="grid grid-cols-2 gap-2">
              {(["OUT", "IN"] as const).map((direction) => (
                <button
                  key={direction}
                  type="button"
                  aria-pressed={form.direction === direction}
                  onClick={() => setForm({ ...form, direction })}
                  className={cn(
                    "rounded-2xl border-2 p-3 font-semibold",
                    form.direction === direction
                      ? "border-sky bg-sky-soft text-sky"
                      : "border-line text-muted",
                  )}
                >
                  {direction === "OUT" ? "Money out" : "Money in"}
                </button>
              ))}
            </div>
            <Field label="Account" error={errors.accountId}>
              {(props) => (
                <Select
                  {...props}
                  value={accountId}
                  onChange={(event) => setForm({ ...form, accountId: event.target.value })}
                >
                  <option value="">Choose an account</option>
                  {manual.map((account) => (
                    <option key={account.id} value={account.id}>
                      {account.name}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
            <Field label="Description" error={errors.description}>
              {(props) => (
                <Input
                  {...props}
                  value={form.description}
                  onChange={(event) => setForm({ ...form, description: event.target.value })}
                  placeholder="Farmers market"
                />
              )}
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Amount" error={errors.amountCents}>
                {(props) => (
                  <MoneyInput
                    {...props}
                    value={form.amount}
                    onChange={(event) => setForm({ ...form, amount: event.target.value })}
                  />
                )}
              </Field>
              <Field label="Date">
                {(props) => (
                  <Input
                    {...props}
                    type="date"
                    value={form.postedOn}
                    onChange={(event) => setForm({ ...form, postedOn: event.target.value })}
                  />
                )}
              </Field>
            </div>
            <button
              type="button"
              onClick={() => setPicking(true)}
              className="flex w-full items-center gap-3 rounded-2xl border-2 border-line p-3 text-left hover:bg-surface-2"
            >
              <CategoryIcon icon={category?.icon ?? "circle-help"} colour={category?.colour} size="sm" />
              <span className="flex-1 font-semibold">{category?.name ?? "Choose a category (optional)"}</span>
            </button>
            <Button type="submit" size="lg" block disabled={create.isPending}>
              Add transaction
            </Button>
          </form>
        )}
        <CategoryPicker
          open={picking}
          onOpenChange={setPicking}
          selectedId={category?.id}
          onSelect={(next) => {
            setCategory(next);
            setPicking(false);
          }}
        />
      </DialogContent>
    </Dialog>
  );
}

/** Imports a CSV statement exported from internet banking. */
export function ImportDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const toast = useToast();
  const { data: accounts } = useAccounts();
  const manual = accounts?.filter((account) => account.managedBy === "USER" && !account.archived) ?? [];
  const [accountId, setAccountId] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<ImportBatch | null>(null);
  const upload = useApiMutation((input: { accountId: string; file: File }) => {
    const body = new FormData();
    body.append("file", input.file);
    return api.post<ImportBatch>(`/imports?accountId=${input.accountId}`, body);
  });

  function close(next: boolean) {
    if (!next) {
      setResult(null);
      setFile(null);
    }
    onOpenChange(next);
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent
        title="Import a statement"
        description="Export a CSV from ANZ, ASB, BNZ, Kiwibank or Westpac internet banking and drop it here."
      >
        {result ? (
          <div className="space-y-4">
            <div className="grid grid-cols-3 gap-2 text-center">
              <ImportFigure label="Imported" value={result.importedCount} tone="text-brand" />
              <ImportFigure label="Already had" value={result.duplicateCount} />
              <ImportFigure
                label="Skipped"
                value={result.skippedRows.length}
                tone={result.skippedRows.length ? "text-warm" : undefined}
              />
            </div>
            <p className="text-sm text-muted">
              Read as a {result.format === "GENERIC" ? "standard" : result.format} statement with{" "}
              {result.rowCount} rows.
            </p>
            {result.skippedRows.length > 0 ? (
              <ul className="max-h-40 space-y-1 overflow-auto rounded-2xl bg-surface-2 p-3 text-sm">
                {result.skippedRows.map((row) => (
                  <li key={row.lineNumber}>
                    <span className="font-semibold">Line {row.lineNumber}:</span> {row.reason}
                  </li>
                ))}
              </ul>
            ) : null}
            <Button block onClick={() => close(false)}>
              Done
            </Button>
          </div>
        ) : manual.length === 0 ? (
          <p className="text-ink-2">
            Statements import into accounts you manage yourself. Add one in Settings first.
          </p>
        ) : (
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault();
              if (!accountId || !file) return;
              upload.mutate(
                { accountId, file },
                { onSuccess: (batch) => setResult(batch), onError: (error) => toast(error.message, "error") },
              );
            }}
          >
            <Field label="Into which account?">
              {(props) => (
                <Select {...props} value={accountId} onChange={(event) => setAccountId(event.target.value)}>
                  <option value="">Choose an account</option>
                  {manual.map((account) => (
                    <option key={account.id} value={account.id}>
                      {account.name}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
            <label className="flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 border-dashed border-line p-8 text-center hover:border-sky">
              <FileUp className="h-8 w-8 text-sky" aria-hidden />
              <span className="font-semibold">{file ? file.name : "Choose a CSV file"}</span>
              <span className="text-xs text-muted">
                Up to 5 MB. Transactions you already have are skipped.
              </span>
              <input
                type="file"
                accept=".csv,text/csv"
                className="sr-only"
                onChange={(event) => setFile(event.target.files?.[0] ?? null)}
              />
            </label>
            <Button type="submit" size="lg" block disabled={!accountId || !file || upload.isPending}>
              {upload.isPending ? "Importing" : "Import"}
            </Button>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}

function ImportFigure({ label, value, tone }: { label: string; value: number; tone?: string }) {
  return (
    <div className="rounded-2xl bg-surface-2 p-3">
      <p className={cn("font-display text-3xl font-semibold tabular", tone)}>{value}</p>
      <p className="text-xs font-semibold text-muted">{label}</p>
    </div>
  );
}
