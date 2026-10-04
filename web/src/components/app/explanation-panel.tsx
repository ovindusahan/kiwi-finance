"use client";

import * as Accordion from "@radix-ui/react-accordion";
import { ChevronDown, Lightbulb } from "lucide-react";
import type { Explanation } from "@/lib/api/types";

/** "How we worked this out": the steps and assumptions behind a result. */
export function ExplanationPanel({
  explanation,
  defaultOpen = false,
}: {
  explanation: Explanation;
  defaultOpen?: boolean;
}) {
  return (
    <Accordion.Root type="single" collapsible defaultValue={defaultOpen ? "how" : undefined}>
      <Accordion.Item value="how" className="rounded-xl border border-line bg-surface-2">
        <Accordion.Header>
          <Accordion.Trigger className="group flex w-full items-center justify-between gap-3 p-4 text-left font-semibold">
            <span className="flex items-center gap-2">
              <Lightbulb className="h-5 w-5 text-gold" aria-hidden />
              How we worked this out
            </span>
            <ChevronDown
              className="h-5 w-5 text-muted transition-transform group-data-[state=open]:rotate-180"
              aria-hidden
            />
          </Accordion.Trigger>
        </Accordion.Header>
        <Accordion.Content className="px-4 pb-4">
          <ol className="divide-y divide-line">
            {explanation.steps.map((step, index) => (
              <li key={`${step.label}-${index}`} className="flex items-start justify-between gap-4 py-3">
                <div>
                  <p className="font-semibold">{step.label}</p>
                  {step.detail ? <p className="mt-0.5 text-sm text-muted">{step.detail}</p> : null}
                </div>
                <p className="shrink-0 text-right font-semibold tabular">{step.value}</p>
              </li>
            ))}
          </ol>
          {explanation.assumptions.length > 0 ? (
            <div className="mt-3 rounded-2xl bg-surface p-3">
              <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted">Assumptions</p>
              <ul className="space-y-1.5 text-sm text-ink-2">
                {explanation.assumptions.map((assumption) => (
                  <li key={assumption.key}>
                    <span className="font-semibold text-ink">{assumption.label}: </span>
                    {assumption.value}
                    {assumption.source ? (
                      <a
                        href={assumption.source}
                        target="_blank"
                        rel="noreferrer"
                        className="ml-1 text-sky hover:underline"
                      >
                        Source
                      </a>
                    ) : null}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </Accordion.Content>
      </Accordion.Item>
    </Accordion.Root>
  );
}
