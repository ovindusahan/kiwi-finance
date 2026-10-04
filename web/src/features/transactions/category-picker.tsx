"use client";

import { Search, X } from "lucide-react";
import { useMemo, useState } from "react";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { Input } from "@/components/ui/field";
import { CategoryIcon } from "@/components/ui/icon";
import { useCategories } from "@/lib/api/queries";
import type { Category, CategoryGroup } from "@/lib/api/types";
import { cn } from "@/lib/cn";

export const groupLabels: Record<CategoryGroup, string> = {
  INCOME: "Income",
  ESSENTIALS: "Essentials",
  LIFESTYLE: "Lifestyle",
  SAVINGS: "Savings",
  DEBT: "Debt",
  TRANSFERS: "Transfers",
};

const groupOrder: CategoryGroup[] = ["ESSENTIALS", "LIFESTYLE", "INCOME", "SAVINGS", "DEBT", "TRANSFERS"];

/** Lets a person pick a category, grouped and searchable. */
export function CategoryPicker({
  open,
  onOpenChange,
  title = "Choose a category",
  selectedId,
  onSelect,
  allowClear = false,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title?: string;
  selectedId?: string | null;
  onSelect: (category: Category | null) => void;
  allowClear?: boolean;
}) {
  const { data: categories } = useCategories();
  const [search, setSearch] = useState("");

  const grouped = useMemo(() => {
    const term = search.trim().toLowerCase();
    const matches = (categories ?? []).filter(
      (category) => !term || category.name.toLowerCase().includes(term),
    );
    return groupOrder
      .map((group) => ({ group, items: matches.filter((category) => category.group === group) }))
      .filter((entry) => entry.items.length > 0);
  }, [categories, search]);

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) setSearch("");
        onOpenChange(next);
      }}
    >
      <DialogContent title={title}>
        <div className="relative mb-4">
          <Search
            className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted"
            aria-hidden
          />
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search categories"
            aria-label="Search categories"
            className="pl-10"
          />
        </div>
        {allowClear ? (
          <button
            type="button"
            onClick={() => onSelect(null)}
            className="mb-3 flex w-full items-center gap-3 rounded-2xl border-2 border-dashed border-line p-2.5 text-left font-semibold text-muted hover:bg-surface-2"
          >
            <span className="grid h-8 w-8 place-items-center rounded-xl bg-surface-2">
              <X className="h-4 w-4" aria-hidden />
            </span>
            No category
          </button>
        ) : null}
        <div className="space-y-4">
          {grouped.map(({ group, items }) => (
            <section key={group}>
              <h3 className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted">
                {groupLabels[group]}
              </h3>
              <div className="grid grid-cols-2 gap-2">
                {items.map((category) => (
                  <button
                    key={category.id}
                    type="button"
                    onClick={() => onSelect(category)}
                    aria-pressed={category.id === selectedId}
                    className={cn(
                      "flex items-center gap-2.5 rounded-2xl border-2 p-2 text-left text-sm font-semibold transition-colors",
                      category.id === selectedId
                        ? "border-brand bg-brand-soft"
                        : "border-transparent bg-surface-2 hover:border-line",
                    )}
                  >
                    <CategoryIcon icon={category.icon} colour={category.colour} size="sm" />
                    <span className="truncate">{category.name}</span>
                  </button>
                ))}
              </div>
            </section>
          ))}
          {grouped.length === 0 ? (
            <p className="text-center text-sm text-muted">No categories match.</p>
          ) : null}
        </div>
      </DialogContent>
    </Dialog>
  );
}
