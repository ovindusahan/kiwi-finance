"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import { usePreferences } from "@/lib/api/queries";
import type { HomeWidget, Preferences } from "@/lib/api/types";
import { defaultLayout, type WidgetId, widgetIds, type WidgetSize } from "./widgets";

export type LayoutItem = { id: WidgetId; size: WidgetSize };

/** The person's saved home screen, or the default one, saved to their account so it follows them. */
export function useHomeLayout() {
  const client = useQueryClient();
  const { data: preferences, isLoading } = usePreferences();
  const saved = preferences?.homeLayout
    ?.filter((widget): widget is HomeWidget & { id: WidgetId } => widgetIds.includes(widget.id as WidgetId))
    .map((widget) => ({ id: widget.id, size: widget.size }));
  const layout: LayoutItem[] = saved ?? defaultLayout;

  const save = useMutation({
    mutationFn: (widgets: LayoutItem[] | null) =>
      api.put<Preferences>("/preferences/home-layout", { widgets }),
    onSuccess: (result) => client.setQueryData(["preferences"], result),
  });

  return { layout, isLoading, isDefault: !saved, save };
}
