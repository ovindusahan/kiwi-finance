"use client";

import { ArrowLeftRight, CheckSquare, FileUp, Plus, RefreshCw, Search, Square, X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { PageHeader } from "@/components/app/page-header";
import { CashWithdrawalsPrompt } from "./cash-withdrawals";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState, ErrorState } from "@/components/ui/empty-state";
import { Input, Select } from "@/components/ui/field";
import { CategoryIcon } from "@/components/ui/icon";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api/client";
import {
  useAccounts,
  useApiMutation,
  useCategories,
  useTransactions,
  type TransactionFilters,
} from "@/lib/api/queries";
import type { Category, Transaction } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { formatLongDay, formatMoney } from "@/lib/format";
import { CategoryPicker } from "./category-picker";
import { AddTransactionDialog, ImportDialog, TransactionDialog } from "./transaction-dialogs";

export function TransactionsPage({
  initialSearch = "",
  initialAccountId,
}: {
  initialSearch?: string;
  initialAccountId?: string;
}) {
  const toast = useToast();
  const [searchInput, setSearchInput] = useState(initialSearch);
  const [filters, setFilters] = useState<TransactionFilters>({
    search: initialSearch || undefined,
    accountId: initialAccountId,
  });
  const [paging, setPaging] = useState<{ key: string; cursors: (string | undefined)[] }>({
    key: "{}",
    cursors: [undefined],
  });
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [selecting, setSelecting] = useState(false);
  const [open, setOpen] = useState<Transaction | null>(null);
  const [picking, setPicking] = useState(false);
  const [adding, setAdding] = useState(false);
  const [importing, setImporting] = useState(false);
  const recategorise = useApiMutation(() => api.post<{ updated: number }>("/transactions/recategorise"));
  const categorise = useApiMutation((input: { ids: string[]; category: Category | null }) =>
    api.post<{ updated: number }>("/transactions/categorise", {
      transactionIds: input.ids,
      categoryId: input.category?.id,
    }),
  );

  useEffect(() => {
    const timer = setTimeout(() => {
      setFilters((current) => ({ ...current, search: searchInput.trim() || undefined }));
      setSelected(new Set());
    }, 300);
    return () => clearTimeout(timer);
  }, [searchInput]);

  const filtersKey = JSON.stringify(filters);
  const cursors = paging.key === filtersKey ? paging.cursors : [undefined];

  function applyFilters(next: TransactionFilters) {
    setFilters(next);
    setSelected(new Set());
  }

  function toggle(id: string) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  const filtered = Object.values(filters).some((value) => value !== undefined && value !== false);

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Transactions"
        title="Every dollar, in one place"
        description="Search, filter and tidy up categories. Changes flow through to your budget, insights and plans."
        action={
          <>
            <Button variant="secondary" onClick={() => setImporting(true)}>
              <FileUp className="h-4 w-4" aria-hidden /> Import
            </Button>
            <Button onClick={() => setAdding(true)}>
              <Plus className="h-4 w-4" aria-hidden /> Add
            </Button>
          </>
        }
      />
      <CashWithdrawalsPrompt />

      <Card className="space-y-3 p-4 sm:p-4">
        <div className="relative">
          <Search
            className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted"
            aria-hidden
          />
          <Input
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            placeholder="Search by description or merchant"
            aria-label="Search transactions"
            className="pl-10"
          />
        </div>
        <Filters filters={filters} onChange={applyFilters} />
        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant={selecting ? "secondary" : "ghost"}
            size="sm"
            onClick={() => {
              setSelecting(!selecting);
              setSelected(new Set());
            }}
          >
            <CheckSquare className="h-4 w-4" aria-hidden /> {selecting ? "Done selecting" : "Select"}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            disabled={recategorise.isPending}
            onClick={() =>
              recategorise.mutate(undefined, {
                onSuccess: (result) =>
                  toast(`${result.updated} transaction${result.updated === 1 ? "" : "s"} recategorised`),
                onError: (error) => toast(error.message, "error"),
              })
            }
          >
            <RefreshCw className={cn("h-4 w-4", recategorise.isPending && "animate-spin")} aria-hidden />{" "}
            Re-apply rules
          </Button>
          {filtered ? (
            <Button
              variant="ghost"
              size="sm"
              className="ml-auto"
              onClick={() => {
                setSearchInput("");
                applyFilters({});
              }}
            >
              <X className="h-4 w-4" aria-hidden /> Clear filters
            </Button>
          ) : null}
        </div>
      </Card>

      <div className="space-y-6">
        {cursors.map((cursor, index) => (
          <TransactionChunk
            key={cursor ?? "first"}
            filters={filters}
            cursor={cursor}
            last={index === cursors.length - 1}
            selecting={selecting}
            selected={selected}
            onToggle={toggle}
            onOpen={setOpen}
            onMore={(next) => setPaging({ key: filtersKey, cursors: [...cursors, next] })}
            emptyHint={filtered}
          />
        ))}
      </div>

      {selecting && selected.size > 0 ? (
        <div className="fixed inset-x-4 bottom-24 z-40 mx-auto flex max-w-md items-center gap-3 rounded-xl border border-line bg-surface p-3 shadow-card animate-pop lg:bottom-6">
          <p className="flex-1 pl-2 font-semibold">{selected.size} selected</p>
          <Button size="sm" onClick={() => setPicking(true)}>
            Set category
          </Button>
        </div>
      ) : null}

      <CategoryPicker
        open={picking}
        onOpenChange={setPicking}
        title={`Categorise ${selected.size} transaction${selected.size === 1 ? "" : "s"}`}
        allowClear
        onSelect={(category) => {
          setPicking(false);
          categorise.mutate(
            { ids: [...selected], category },
            {
              onSuccess: (result) => {
                toast(`${result.updated} updated`);
                setSelected(new Set());
                setSelecting(false);
              },
              onError: (error) => toast(error.message, "error"),
            },
          );
        }}
      />
      <TransactionDialog transaction={open} onOpenChange={(next) => !next && setOpen(null)} />
      <AddTransactionDialog open={adding} onOpenChange={setAdding} />
      <ImportDialog open={importing} onOpenChange={setImporting} />
    </div>
  );
}

function Filters({
  filters,
  onChange,
}: {
  filters: TransactionFilters;
  onChange: (filters: TransactionFilters) => void;
}) {
  const { data: accounts } = useAccounts();
  const { data: categories } = useCategories();
  const categoryValue = filters.uncategorised ? "none" : (filters.categoryId ?? "");

  return (
    <div className="grid grid-cols-2 gap-2 md:grid-cols-5">
      <Select
        aria-label="Account"
        value={filters.accountId ?? ""}
        onChange={(event) => onChange({ ...filters, accountId: event.target.value || undefined })}
        className="py-2 text-sm"
      >
        <option value="">All accounts</option>
        {accounts?.map((account) => (
          <option key={account.id} value={account.id}>
            {account.name}
          </option>
        ))}
      </Select>
      <Select
        aria-label="Category"
        value={categoryValue}
        onChange={(event) => {
          const value = event.target.value;
          onChange({
            ...filters,
            categoryId: value && value !== "none" ? value : undefined,
            uncategorised: value === "none" || undefined,
          });
        }}
        className="py-2 text-sm"
      >
        <option value="">All categories</option>
        <option value="none">Uncategorised</option>
        {categories?.map((category) => (
          <option key={category.id} value={category.id}>
            {category.name}
          </option>
        ))}
      </Select>
      <Select
        aria-label="Direction"
        value={filters.direction ?? ""}
        onChange={(event) =>
          onChange({
            ...filters,
            direction: (event.target.value || undefined) as TransactionFilters["direction"],
          })
        }
        className="py-2 text-sm"
      >
        <option value="">Money in and out</option>
        <option value="OUT">Money out</option>
        <option value="IN">Money in</option>
      </Select>
      <Input
        type="date"
        aria-label="From"
        value={filters.from ?? ""}
        onChange={(event) => onChange({ ...filters, from: event.target.value || undefined })}
        className="py-2 text-sm"
      />
      <Input
        type="date"
        aria-label="To"
        value={filters.to ?? ""}
        onChange={(event) => onChange({ ...filters, to: event.target.value || undefined })}
        className="py-2 text-sm"
      />
    </div>
  );
}

function TransactionChunk({
  filters,
  cursor,
  last,
  selecting,
  selected,
  onToggle,
  onOpen,
  onMore,
  emptyHint,
}: {
  filters: TransactionFilters;
  cursor?: string;
  last: boolean;
  selecting: boolean;
  selected: Set<string>;
  onToggle: (id: string) => void;
  onOpen: (transaction: Transaction) => void;
  onMore: (cursor: string) => void;
  emptyHint: boolean;
}) {
  const { data, error, refetch, isFetching } = useTransactions(filters, cursor);
  const days = useMemo(() => {
    const groups = new Map<string, Transaction[]>();
    for (const transaction of data?.items ?? []) {
      groups.set(transaction.postedOn, [...(groups.get(transaction.postedOn) ?? []), transaction]);
    }
    return [...groups.entries()];
  }, [data]);

  if (error) return <ErrorState message="We couldn't load transactions." onRetry={() => refetch()} />;
  if (!data) {
    return (
      <div className="space-y-2">
        {Array.from({ length: 8 }, (_, index) => (
          <Skeleton key={index} className="h-16" />
        ))}
      </div>
    );
  }
  if (!cursor && data.items.length === 0) {
    return (
      <EmptyState
        title={emptyHint ? "Nothing matches" : "No transactions yet"}
        message={
          emptyHint
            ? "Try a different search or clear the filters."
            : "Connect your bank or import a statement to get started."
        }
      />
    );
  }

  return (
    <div className={cn("space-y-6 transition-opacity", isFetching && "opacity-60")}>
      {days.map(([day, items]) => {
        const net = items.filter((t) => !t.transfer).reduce((sum, t) => sum + t.amount.cents, 0);
        return (
          <section key={day}>
            <div className="mb-2 flex items-baseline justify-between px-2">
              <h2 className="text-sm font-semibold text-muted">{formatLongDay(day)}</h2>
              <span className="text-sm font-semibold text-muted tabular">
                {formatMoney(net, { signed: true })}
              </span>
            </div>
            <Card className="p-2 sm:p-2">
              <ul className="divide-y divide-line">
                {items.map((transaction) => (
                  <TransactionRow
                    key={transaction.id}
                    transaction={transaction}
                    selecting={selecting}
                    checked={selected.has(transaction.id)}
                    onClick={() => (selecting ? onToggle(transaction.id) : onOpen(transaction))}
                  />
                ))}
              </ul>
            </Card>
          </section>
        );
      })}
      {last && data.nextCursor ? (
        <div className="flex justify-center">
          <Button variant="secondary" onClick={() => onMore(data.nextCursor!)}>
            Load more
          </Button>
        </div>
      ) : null}
    </div>
  );
}

function TransactionRow({
  transaction,
  selecting,
  checked,
  onClick,
}: {
  transaction: Transaction;
  selecting: boolean;
  checked: boolean;
  onClick: () => void;
}) {
  const incoming = transaction.amount.cents > 0;
  return (
    <li>
      <button
        type="button"
        onClick={onClick}
        className="flex w-full items-center gap-3 rounded-2xl p-2 text-left hover:bg-surface-2"
      >
        {selecting ? (
          checked ? (
            <CheckSquare className="h-5 w-5 shrink-0 text-brand" aria-label="Selected" />
          ) : (
            <Square className="h-5 w-5 shrink-0 text-muted" aria-label="Not selected" />
          )
        ) : null}
        {transaction.transfer ? (
          <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-surface-2 text-muted">
            <ArrowLeftRight className="h-5 w-5" aria-hidden />
          </span>
        ) : (
          <CategoryIcon
            icon={transaction.category?.icon ?? "circle-help"}
            colour={transaction.category?.colour}
          />
        )}
        <span className="min-w-0 flex-1">
          <span className="block truncate font-semibold">
            {transaction.merchant ?? transaction.description}
          </span>
          <span className="flex items-center gap-1.5 truncate text-xs text-muted">
            {transaction.category ? transaction.category.name : <Badge tone="gold">Uncategorised</Badge>}
            <span aria-hidden>·</span>
            {transaction.accountName}
          </span>
        </span>
        <span
          className={cn(
            "shrink-0 font-semibold tabular",
            incoming && "text-good",
            transaction.transfer && "text-muted",
          )}
        >
          {formatMoney(transaction.amount, { signed: true })}
        </span>
      </button>
    </li>
  );
}
