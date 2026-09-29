"use client";

import { Banknote, Plus, X } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { Input, MoneyInput, Select } from "@/components/ui/field";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api/client";
import { useApiMutation, useCashWithdrawals, useCategories } from "@/lib/api/queries";
import type { CashSpendingRequest, CashSpendingResult, CashWithdrawal } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { formatDayMonth, formatMoney, parseDollars } from "@/lib/format";

/**
 * Asks about cash withdrawals the bank feed brought in, so cash spending lands in the right
 * categories instead of disappearing from the picture.
 */
export function CashWithdrawalsPrompt() {
  const { data } = useCashWithdrawals();
  const [open, setOpen] = useState(false);
  if (!data || data.length === 0) return null;
  const total = data.reduce((sum, item) => sum + item.amount.cents, 0);
  return (
    <>
      <div className="flex flex-wrap items-start gap-3 rounded-xl border border-warm/25 bg-warm-soft p-4">
        <Banknote className="mt-0.5 h-5 w-5 shrink-0 text-warm" aria-hidden />
        <div className="min-w-0 flex-1">
          <p className="font-semibold">
            You took out {formatMoney(total, { whole: true })} in cash ({data.length} withdrawal
            {data.length === 1 ? "" : "s"})
          </p>
          <p className="text-sm text-ink-2">
            Tell us what it went on so your spending and budget stay accurate. Anything you haven&apos;t spent
            stays in your cash wallet.
          </p>
        </div>
        <Button variant="outline" onClick={() => setOpen(true)}>
          Sort out cash
        </Button>
      </div>
      {open ? <CashDialog withdrawals={data} onClose={() => setOpen(false)} /> : null}
    </>
  );
}

type Line = { id: number; categoryId: string; amount: string; note: string };
let nextLine = 0;
const emptyLine = (): Line => ({ id: nextLine++, categoryId: "", amount: "", note: "" });

function CashDialog({ withdrawals, onClose }: { withdrawals: CashWithdrawal[]; onClose: () => void }) {
  const toast = useToast();
  const { data: categories } = useCategories();
  const [index, setIndex] = useState(0);
  const [lines, setLines] = useState<Line[]>([emptyLine()]);
  const current = withdrawals[Math.min(index, withdrawals.length - 1)];
  const save = useApiMutation((input: { id: string; request: CashSpendingRequest }) =>
    api.post<CashSpendingResult>(`/cash-withdrawals/${input.id}`, input.request),
  );
  if (!current) return null;

  const spending = (categories ?? []).filter((category) =>
    ["ESSENTIALS", "LIFESTYLE", "DEBT"].includes(category.group),
  );
  const spent = lines.reduce((sum, line) => sum + (parseDollars(line.amount) ?? 0), 0);
  const left = current.amount.cents - spent;

  function submit(event: React.FormEvent) {
    event.preventDefault();
    const filled = lines.filter((line) => line.categoryId && (parseDollars(line.amount) ?? 0) > 0);
    if (left < 0) {
      toast("That adds up to more than the cash you took out.", "error");
      return;
    }
    save.mutate(
      {
        id: current!.transactionId,
        request: {
          lines: filled.map((line) => ({
            categoryId: line.categoryId,
            amountCents: parseDollars(line.amount)!,
            ...(line.note.trim() ? { note: line.note.trim() } : {}),
          })),
        },
      },
      {
        onSuccess: (result) => {
          toast(`Saved. Your cash wallet now has ${formatMoney(result.walletBalance)}.`);
          setLines([emptyLine()]);
          if (withdrawals.length <= 1) onClose();
          else setIndex((value) => Math.min(value, withdrawals.length - 2));
        },
        onError: (failure) => toast(failure.message, "error"),
      },
    );
  }

  return (
    <Dialog open onOpenChange={(next) => (next ? null : onClose())}>
      <DialogContent
        title="What did you spend the cash on?"
        description={`${withdrawals.length} withdrawal${withdrawals.length === 1 ? "" : "s"} to sort. Split each one across as many categories as you need.`}
        className="sm:w-[min(640px,92vw)]"
      >
        <form onSubmit={submit} className="space-y-4">
          <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-line pb-3">
            <div>
              <p className="font-semibold">{formatMoney(current.amount)} cash</p>
              <p className="text-sm text-muted">
                {formatDayMonth(current.postedOn)} · {current.description}
                {current.accountName ? ` · ${current.accountName}` : ""}
              </p>
            </div>
            {withdrawals.length > 1 ? (
              <p className="text-sm text-muted">
                {index + 1} of {withdrawals.length}
              </p>
            ) : null}
          </div>

          <ul className="space-y-3">
            {lines.map((line, position) => (
              <li key={line.id} className="grid grid-cols-[minmax(0,1fr)_7rem_auto] items-center gap-2">
                <label className="sr-only" htmlFor={`cash-category-${line.id}`}>
                  Category {position + 1}
                </label>
                <Select
                  id={`cash-category-${line.id}`}
                  value={line.categoryId}
                  onChange={(event) =>
                    setLines((all) =>
                      all.map((item) =>
                        item.id === line.id ? { ...item, categoryId: event.target.value } : item,
                      ),
                    )
                  }
                >
                  <option value="">Choose a category</option>
                  {spending.map((category) => (
                    <option key={category.id} value={category.id}>
                      {category.name}
                    </option>
                  ))}
                </Select>
                <label className="sr-only" htmlFor={`cash-amount-${line.id}`}>
                  Amount {position + 1}
                </label>
                <MoneyInput
                  id={`cash-amount-${line.id}`}
                  value={line.amount}
                  placeholder={position === 0 ? String(current.amount.cents / 100) : "0"}
                  onChange={(event) =>
                    setLines((all) =>
                      all.map((item) =>
                        item.id === line.id ? { ...item, amount: event.target.value } : item,
                      ),
                    )
                  }
                />
                <button
                  type="button"
                  onClick={() =>
                    setLines((all) =>
                      all.length > 1 ? all.filter((item) => item.id !== line.id) : [emptyLine()],
                    )
                  }
                  className="grid h-10 w-10 place-items-center text-muted hover:bg-surface-2 hover:text-ink"
                  aria-label={`Remove line ${position + 1}`}
                >
                  <X className="h-4 w-4" aria-hidden />
                </button>
                <label className="sr-only" htmlFor={`cash-note-${line.id}`}>
                  Note {position + 1}
                </label>
                <Input
                  id={`cash-note-${line.id}`}
                  value={line.note}
                  placeholder="Note, for example “Saturday market”"
                  onChange={(event) =>
                    setLines((all) =>
                      all.map((item) => (item.id === line.id ? { ...item, note: event.target.value } : item)),
                    )
                  }
                  className="col-span-2 py-1.5 text-sm"
                />
              </li>
            ))}
          </ul>
          <button
            type="button"
            onClick={() => setLines((all) => [...all, emptyLine()])}
            className="inline-flex items-center gap-1 text-sm font-medium text-brand hover:underline"
          >
            <Plus className="h-4 w-4" aria-hidden /> Split across another category
          </button>

          <p className={cn("text-sm", left < 0 ? "font-semibold text-danger" : "text-ink-2")}>
            {left < 0
              ? `That's ${formatMoney(-left)} more than you took out.`
              : left === 0
                ? "All of it is accounted for."
                : `${formatMoney(left)} stays in your cash wallet.`}
          </p>

          <div className="flex flex-wrap justify-end gap-2">
            {withdrawals.length > 1 ? (
              <Button
                variant="ghost"
                onClick={() => {
                  setLines([emptyLine()]);
                  setIndex((value) => (value + 1) % withdrawals.length);
                }}
              >
                Skip for now
              </Button>
            ) : null}
            <Button variant="secondary" onClick={onClose}>
              Later
            </Button>
            <Button type="submit" disabled={save.isPending}>
              Save
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
