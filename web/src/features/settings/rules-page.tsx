"use client";

import { Plus, Trash2, Wand2 } from "lucide-react";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { ErrorState } from "@/components/ui/empty-state";
import { Field, Input, Select } from "@/components/ui/field";
import { CategoryIcon } from "@/components/ui/icon";
import { PageSkeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { CategoryPicker, groupLabels } from "@/features/transactions/category-picker";
import { api } from "@/lib/api/client";
import { useApiMutation, useCategories, useRules } from "@/lib/api/queries";
import type { CategorisationRule, Category, CategoryGroup } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { SettingsFrame } from "./settings-nav";

const matchTypes: { value: CategorisationRule["matchType"]; label: string }[] = [
  { value: "CONTAINS", label: "contains" },
  { value: "STARTS_WITH", label: "starts with" },
  { value: "EQUALS", label: "is exactly" },
];

const icons = [
  "shopping-bag",
  "gift",
  "baby",
  "heart-pulse",
  "shirt",
  "ticket",
  "plane",
  "car",
  "house",
  "graduation-cap",
  "pizza",
  "sparkles",
];
const colours = ["#2A78D6", "#1FAA59", "#FF7A3D", "#6B5CE7", "#E8A300", "#D03B3B", "#0EA5A4", "#DB2777"];

export function RulesPage() {
  const toast = useToast();
  const { data: rules, isLoading, error, refetch } = useRules();
  const { data: categories } = useCategories();
  const [adding, setAdding] = useState(false);
  const [creatingCategory, setCreatingCategory] = useState(false);
  const remove = useApiMutation((id: string) => api.delete(`/categorisation-rules/${id}`));
  const removeCategory = useApiMutation((id: string) => api.delete(`/categories/${id}`));

  if (isLoading) return <PageSkeleton />;
  if (error || !rules) return <ErrorState message="We couldn't load your rules." onRetry={() => refetch()} />;

  const byId = new Map((categories ?? []).map((category) => [category.id, category]));
  const custom = (categories ?? []).filter((category) => category.custom);

  return (
    <SettingsFrame
      title="Categories and rules"
      description="Rules sort new transactions automatically. The first matching rule wins, in priority order."
    >
      <div className="space-y-4">
        <Card>
          <CardHeader
            title="Rules"
            description={`${rules.length} rule${rules.length === 1 ? "" : "s"}`}
            action={
              <Button size="sm" onClick={() => setAdding(true)}>
                <Plus className="h-4 w-4" aria-hidden /> Add rule
              </Button>
            }
          />
          {rules.length === 0 ? (
            <p className="text-ink-2">
              No rules yet. Add one, like &ldquo;contains countdown&rdquo; goes to Groceries.
            </p>
          ) : (
            <ul className="divide-y divide-line">
              {[...rules]
                .sort((a, b) => a.priority - b.priority)
                .map((rule) => {
                  const category = byId.get(rule.categoryId);
                  return (
                    <li key={rule.id} className="flex items-center gap-3 py-3">
                      <span className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-lilac-soft text-lilac">
                        <Wand2 className="h-5 w-5" aria-hidden />
                      </span>
                      <p className="min-w-0 flex-1 text-sm">
                        Description {matchTypes.find((type) => type.value === rule.matchType)?.label}{" "}
                        <span className="rounded-lg bg-surface-2 px-1.5 py-0.5 font-mono font-semibold">
                          {rule.pattern}
                        </span>{" "}
                        goes to{" "}
                        <span className="font-semibold">{category?.name ?? "a deleted category"}</span>
                      </p>
                      <Badge>#{rule.priority}</Badge>
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`Delete rule for ${rule.pattern}`}
                        onClick={() =>
                          remove.mutate(rule.id, {
                            onSuccess: () => toast("Rule deleted"),
                            onError: (e) => toast(e.message, "error"),
                          })
                        }
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </li>
                  );
                })}
            </ul>
          )}
        </Card>

        <Card>
          <CardHeader
            title="Your categories"
            description="Add your own alongside the built-in New Zealand categories."
            action={
              <Button size="sm" variant="secondary" onClick={() => setCreatingCategory(true)}>
                <Plus className="h-4 w-4" aria-hidden /> New category
              </Button>
            }
          />
          {custom.length === 0 ? (
            <p className="text-ink-2">You&apos;re using the built-in categories.</p>
          ) : (
            <ul className="grid gap-2 sm:grid-cols-2">
              {custom.map((category) => (
                <li key={category.id} className="flex items-center gap-3 rounded-2xl bg-surface-2 p-2">
                  <CategoryIcon icon={category.icon} colour={category.colour} size="sm" />
                  <span className="flex-1 font-semibold">{category.name}</span>
                  <Badge>{groupLabels[category.group]}</Badge>
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={`Delete ${category.name}`}
                    onClick={() =>
                      removeCategory.mutate(category.id, {
                        onSuccess: () => toast("Category deleted"),
                        onError: (e) => toast(e.message, "error"),
                      })
                    }
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <RuleDialog
        open={adding}
        onOpenChange={setAdding}
        nextPriority={Math.max(0, ...rules.map((rule) => rule.priority)) + 10}
      />
      <CategoryDialog open={creatingCategory} onOpenChange={setCreatingCategory} />
    </SettingsFrame>
  );
}

function RuleDialog({
  open,
  onOpenChange,
  nextPriority,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  nextPriority: number;
}) {
  const toast = useToast();
  const [pattern, setPattern] = useState("");
  const [matchType, setMatchType] = useState<CategorisationRule["matchType"]>("CONTAINS");
  const [category, setCategory] = useState<Category | null>(null);
  const [picking, setPicking] = useState(false);
  const [error, setError] = useState<string>();
  const create = useApiMutation((body: Record<string, unknown>) =>
    api.post<CategorisationRule>("/categorisation-rules", body),
  );
  const reapply = useApiMutation(() => api.post<{ updated: number }>("/transactions/recategorise"));

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title="Add a rule" description="Matching ignores capital letters.">
        <form
          noValidate
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            if (!pattern.trim() || !category) {
              setError(!pattern.trim() ? "Enter the text to match." : "Choose a category.");
              return;
            }
            create.mutate(
              { pattern: pattern.trim(), matchType, categoryId: category.id, priority: nextPriority },
              {
                onSuccess: () => {
                  reapply.mutate(undefined, {
                    onSuccess: (result) =>
                      toast(
                        `Rule added. ${result.updated} transaction${result.updated === 1 ? "" : "s"} recategorised.`,
                      ),
                  });
                  setPattern("");
                  setCategory(null);
                  onOpenChange(false);
                },
                onError: (failure) => toast(failure.message, "error"),
              },
            );
          }}
        >
          <div className="grid grid-cols-[auto_1fr] gap-3">
            <Field label="When the description">
              {(props) => (
                <Select
                  {...props}
                  value={matchType}
                  onChange={(e) => setMatchType(e.target.value as CategorisationRule["matchType"])}
                >
                  {matchTypes.map((type) => (
                    <option key={type.value} value={type.value}>
                      {type.label}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
            <Field label="This text" error={error}>
              {(props) => (
                <Input
                  {...props}
                  value={pattern}
                  onChange={(e) => setPattern(e.target.value)}
                  placeholder="countdown"
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
            <span className="flex-1 font-semibold">
              {category ? `Goes to ${category.name}` : "Choose a category"}
            </span>
          </button>
          <Button type="submit" size="lg" block disabled={create.isPending}>
            Add rule
          </Button>
        </form>
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

function CategoryDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const toast = useToast();
  const [name, setName] = useState("");
  const [group, setGroup] = useState<CategoryGroup>("LIFESTYLE");
  const [icon, setIcon] = useState(icons[0]!);
  const [colour, setColour] = useState(colours[0]!);
  const create = useApiMutation((body: Record<string, unknown>) => api.post<Category>("/categories", body));

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title="New category">
        <form
          noValidate
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            if (!name.trim()) return;
            create.mutate(
              { name: name.trim(), group, icon, colour },
              {
                onSuccess: () => {
                  toast(`${name.trim()} added`);
                  setName("");
                  onOpenChange(false);
                },
                onError: (failure) => toast(failure.message, "error"),
              },
            );
          }}
        >
          <div className="grid grid-cols-2 gap-3">
            <Field label="Name">
              {(props) => (
                <Input {...props} value={name} onChange={(e) => setName(e.target.value)} placeholder="Pets" />
              )}
            </Field>
            <Field label="Group">
              {(props) => (
                <Select {...props} value={group} onChange={(e) => setGroup(e.target.value as CategoryGroup)}>
                  {(["ESSENTIALS", "LIFESTYLE", "INCOME", "SAVINGS", "DEBT", "TRANSFERS"] as const).map(
                    (value) => (
                      <option key={value} value={value}>
                        {groupLabels[value]}
                      </option>
                    ),
                  )}
                </Select>
              )}
            </Field>
          </div>
          <fieldset>
            <legend className="mb-1.5 text-sm font-semibold text-ink-2">Icon</legend>
            <div className="flex flex-wrap gap-2">
              {icons.map((value) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={icon === value}
                  aria-label={value}
                  onClick={() => setIcon(value)}
                  className={cn(
                    "rounded-2xl border-2 p-0.5",
                    icon === value ? "border-ink" : "border-transparent",
                  )}
                >
                  <CategoryIcon icon={value} colour={colour} />
                </button>
              ))}
            </div>
          </fieldset>
          <fieldset>
            <legend className="mb-1.5 text-sm font-semibold text-ink-2">Colour</legend>
            <div className="flex flex-wrap gap-2">
              {colours.map((value) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={colour === value}
                  aria-label={value}
                  onClick={() => setColour(value)}
                  className={cn(
                    "h-9 w-9 rounded-full border-4",
                    colour === value ? "border-ink" : "border-transparent",
                  )}
                  style={{ backgroundColor: value }}
                />
              ))}
            </div>
          </fieldset>
          <Button type="submit" size="lg" block disabled={!name.trim() || create.isPending}>
            Add category
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
