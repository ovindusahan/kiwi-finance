"use client";

import {
  closestCenter,
  DndContext,
  type DragEndEvent,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import {
  arrayMove,
  rectSortingStrategy,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { ArrowLeftRight, Check, CircleDot, GripVertical, Info, LayoutGrid, Plus, X } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { ErrorState } from "@/components/ui/empty-state";
import { ProgressBar } from "@/components/ui/progress";
import { PageSkeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { useMoveMoney } from "@/features/accounts/move-money";
import { CashWithdrawalsPrompt } from "@/features/transactions/cash-withdrawals";
import { api } from "@/lib/api/client";
import { useApiMutation, useDashboard } from "@/lib/api/queries";
import type { Dashboard, Preferences, SetupStep } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { formatLongDay, greeting } from "@/lib/format";
import { type LayoutItem, useHomeLayout } from "./home-layout";
import {
  sizeClasses,
  sizeLabels,
  type WidgetId,
  WidgetBody,
  widgetCatalog,
  widgetIds,
  type WidgetSize,
} from "./widgets";

const sizes: WidgetSize[] = ["SMALL", "MEDIUM", "LARGE", "FULL"];

export function HomePage() {
  const { data, isLoading, error, refetch } = useDashboard();
  const { layout, isLoading: layoutLoading, save } = useHomeLayout();
  const toast = useToast();
  const moveMoney = useMoveMoney();
  const [draft, setDraft] = useState<LayoutItem[] | null>(null);

  if (isLoading || layoutLoading) return <PageSkeleton />;
  if (error || !data)
    return <ErrorState message="We couldn't load your dashboard." onRetry={() => refetch()} />;

  const editing = draft != null;
  const items = draft ?? layout;
  const setupRemaining = data.setup.filter((step) => !step.done).length;

  function finish(widgets: LayoutItem[] | null, message: string) {
    save.mutate(widgets, {
      onSuccess: () => {
        setDraft(null);
        toast(message);
      },
      onError: (failure) => toast(failure.message, "error"),
    });
  }

  return (
    <div className="space-y-8">
      {data.emergencyFundReminder ? <EmergencyFundReminder /> : null}
      <CashWithdrawalsPrompt />
      <header className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm text-muted">{formatLongDay(data.today)}</p>
          <h1 className="text-2xl font-semibold sm:text-[28px]">
            {greeting()}, {data.displayName}
          </h1>
        </div>
        {!editing ? (
          <div className="flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={() => setDraft(layout)}
              className="inline-flex items-center gap-1.5 text-sm text-brand hover:underline"
            >
              <LayoutGrid className="h-4 w-4" aria-hidden />
              Customise
            </button>
            <Button variant="outline" onClick={() => moveMoney()}>
              <ArrowLeftRight className="h-4 w-4" aria-hidden /> Move money
            </Button>
            <Link href="/afford" className={buttonVariants({ variant: "outline" })}>
              Can I afford it?
            </Link>
          </div>
        ) : null}
      </header>

      {setupRemaining > 0 && !editing ? <SetupChecklist steps={data.setup} /> : null}

      {editing ? (
        <EditLayout
          items={items}
          data={data}
          onChange={setDraft}
          onCancel={() => setDraft(null)}
          onSave={() => finish(items, "Home screen saved")}
          onReset={() => finish(null, "Home screen reset")}
          saving={save.isPending}
        />
      ) : (
        <div className="grid grid-cols-1 gap-6 md:grid-cols-6 md:grid-flow-row-dense">
          {items.map((item) => (
            <section
              key={item.id}
              aria-labelledby={`widget-${item.id}`}
              className={cn(
                "@container min-w-0 rounded-2xl border border-line bg-surface p-5 sm:p-6",
                sizeClasses[item.size],
              )}
            >
              <WidgetBody id={item.id} data={data} />
            </section>
          ))}
        </div>
      )}
    </div>
  );
}

function EditLayout({
  items,
  data,
  onChange,
  onCancel,
  onSave,
  onReset,
  saving,
}: {
  items: LayoutItem[];
  data: Dashboard;
  onChange: (items: LayoutItem[]) => void;
  onCancel: () => void;
  onSave: () => void;
  onReset: () => void;
  saving: boolean;
}) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );
  const hidden = widgetIds.filter((id) => !items.some((item) => item.id === id));

  function onDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const from = items.findIndex((item) => item.id === active.id);
    const to = items.findIndex((item) => item.id === over.id);
    onChange(arrayMove(items, from, to));
  }

  return (
    <div className="space-y-6">
      <div className="sticky top-14 z-20 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-brand/20 bg-brand-soft p-4 lg:top-[106px]">
        <p className="text-sm text-ink-2">
          Drag widgets by their handle to move them, choose a size, or remove the ones you don&apos;t need.
          With a keyboard, focus a handle, press space, then use the arrow keys.
        </p>
        <div className="flex flex-wrap gap-2">
          <Button variant="ghost" onClick={onReset} disabled={saving}>
            Reset to default
          </Button>
          <Button variant="secondary" onClick={onCancel} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={onSave} disabled={saving}>
            Save layout
          </Button>
        </div>
      </div>

      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
        <SortableContext items={items.map((item) => item.id)} strategy={rectSortingStrategy}>
          <div className="grid grid-cols-1 gap-6 md:grid-cols-6 md:grid-flow-row-dense">
            {items.map((item) => (
              <SortableWidget
                key={item.id}
                item={item}
                data={data}
                onResize={(size) =>
                  onChange(items.map((current) => (current.id === item.id ? { ...current, size } : current)))
                }
                onRemove={() => onChange(items.filter((current) => current.id !== item.id))}
              />
            ))}
          </div>
        </SortableContext>
      </DndContext>

      <section aria-labelledby="add-widgets" className="border-t border-line pt-5">
        <h2 id="add-widgets" className="text-lg font-semibold">
          Add widgets
        </h2>
        {hidden.length === 0 ? (
          <p className="mt-2 text-sm text-muted">Every widget is already on your home screen.</p>
        ) : (
          <ul className="mt-2 divide-y divide-line">
            {hidden.map((id) => (
              <li key={id} className="flex items-center justify-between gap-4 py-3">
                <div>
                  <p className="font-semibold">{widgetCatalog[id].title}</p>
                  <p className="text-sm text-muted">{widgetCatalog[id].description}</p>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => onChange([...items, { id, size: "MEDIUM" }])}
                  aria-label={`Add ${widgetCatalog[id].title}`}
                >
                  <Plus className="h-4 w-4" aria-hidden /> Add
                </Button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function SortableWidget({
  item,
  data,
  onResize,
  onRemove,
}: {
  item: LayoutItem;
  data: Dashboard;
  onResize: (size: WidgetSize) => void;
  onRemove: () => void;
}) {
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } =
    useSortable({ id: item.id });
  const title = widgetCatalog[item.id as WidgetId].title;
  return (
    <section
      ref={setNodeRef}
      style={{ transform: CSS.Translate.toString(transform), transition }}
      aria-label={title}
      className={cn(
        "@container min-w-0 rounded-xl border-2 border-dashed border-line bg-surface p-3",
        sizeClasses[item.size],
        isDragging && "relative z-10 border-brand shadow-pop",
      )}
    >
      <div className="mb-3 flex flex-wrap items-center gap-2 border-b border-line pb-2">
        <button
          type="button"
          ref={setActivatorNodeRef}
          {...attributes}
          {...listeners}
          aria-label={`Move ${title}`}
          className="grid h-9 w-9 cursor-grab touch-none place-items-center text-muted hover:bg-surface-2 hover:text-ink active:cursor-grabbing"
        >
          <GripVertical className="h-5 w-5" aria-hidden />
        </button>
        <p className="min-w-0 flex-1 truncate font-semibold">{title}</p>
        <div role="group" aria-label={`Size of ${title}`} className="inline-flex border border-line">
          {sizes.map((size) => (
            <button
              key={size}
              type="button"
              aria-pressed={item.size === size}
              aria-label={sizeLabels[size]}
              title={sizeLabels[size]}
              onClick={() => onResize(size)}
              className={cn(
                "h-8 min-w-8 border-l border-line px-2 text-xs font-semibold first:border-l-0",
                item.size === size ? "bg-brand text-on-brand" : "text-ink-2 hover:bg-surface-2",
              )}
            >
              {size === "FULL" ? "Full" : size[0]}
            </button>
          ))}
        </div>
        <button
          type="button"
          onClick={onRemove}
          aria-label={`Remove ${title}`}
          className="grid h-9 w-9 place-items-center text-muted hover:bg-surface-2 hover:text-danger"
        >
          <X className="h-4 w-4" aria-hidden />
        </button>
      </div>
      <div className="pointer-events-none select-none opacity-70" aria-hidden inert>
        <WidgetBody id={item.id as WidgetId} data={data} />
      </div>
    </section>
  );
}

function EmergencyFundReminder() {
  const toast = useToast();
  const snooze = useApiMutation(() => api.post<Preferences>("/preferences/emergency-fund-reminder/snooze"));
  const turnOff = useApiMutation(() =>
    api.patch<Preferences>("/preferences", { emergencyFundReminders: false }),
  );
  return (
    <div className="flex flex-wrap items-start gap-3 rounded-xl border border-brand/20 bg-brand-soft p-4">
      <Info className="mt-0.5 h-5 w-5 shrink-0 text-brand" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="font-semibold">Choose your emergency fund account</p>
        <p className="text-sm text-ink-2">
          Tell us which account holds your safety net and we&apos;ll track it, size your target and show you
          how to build it.
        </p>
        <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-sm">
          <Link href="/emergency-fund" className="font-semibold text-brand hover:underline">
            Choose an account
          </Link>
          <button
            type="button"
            className="text-brand hover:underline"
            onClick={() =>
              snooze.mutate(undefined, {
                onSuccess: () => toast("We'll remind you again in two weeks"),
                onError: (failure) => toast(failure.message, "error"),
              })
            }
          >
            Remind me later
          </button>
          <button
            type="button"
            className="text-brand hover:underline"
            onClick={() =>
              turnOff.mutate(undefined, {
                onSuccess: () => toast("Reminders turned off. You can turn them back on in Settings."),
                onError: (failure) => toast(failure.message, "error"),
              })
            }
          >
            Don&apos;t remind me
          </button>
        </div>
      </div>
    </div>
  );
}

function SetupChecklist({ steps }: { steps: SetupStep[] }) {
  const done = steps.filter((step) => step.done).length;
  const currentIndex = steps.findIndex((step) => !step.done);
  return (
    <Card>
      <CardHeader
        title="Finish setting up"
        description={`${done} of ${steps.length} done. Each step makes your numbers more accurate.`}
        action={
          <span className="text-sm font-semibold text-muted tabular">
            {Math.round((done / steps.length) * 100)}%
          </span>
        }
      />
      <ProgressBar value={done / steps.length} label="Setup progress" className="mb-4" />
      <ol className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
        {steps.map((step, index) => (
          <li key={step.key}>
            <Link
              href={step.href}
              className={cn(
                "flex h-full items-start gap-3 rounded-xl border p-3 transition-colors hover:bg-surface-2",
                index === currentIndex ? "border-brand/30 bg-brand-soft" : "border-line",
              )}
            >
              {step.done ? (
                <span className="grid h-6 w-6 shrink-0 place-items-center rounded-full bg-good text-on-brand">
                  <Check className="h-3.5 w-3.5" strokeWidth={3} aria-label="Done" />
                </span>
              ) : (
                <CircleDot
                  className={cn("h-6 w-6 shrink-0", index === currentIndex ? "text-brand" : "text-muted")}
                  aria-label="To do"
                />
              )}
              <span>
                <span className={cn("block text-sm font-semibold", step.done && "text-muted line-through")}>
                  {step.title}
                </span>
                {!step.done ? <span className="block text-xs text-muted">{step.description}</span> : null}
              </span>
            </Link>
          </li>
        ))}
      </ol>
    </Card>
  );
}
