"use client";

import { BookOpen, Check, Clock, Search } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";
import { BackLink, PageHeader } from "@/components/app/page-header";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader } from "@/components/ui/card";
import { ErrorState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/field";
import { Icon } from "@/components/ui/icon";
import { PageSkeleton } from "@/components/ui/skeleton";
import { useGlossary, useGuide, useGuides } from "@/lib/api/queries";
import { cn } from "@/lib/cn";

const topicStyles: Record<string, { icon: string; tone: string; label: string }> = {
  SAVING: { icon: "piggy-bank", tone: "bg-brand-soft text-brand", label: "Saving" },
  BUDGETING: { icon: "clipboard-check", tone: "bg-sky-soft text-sky", label: "Budgeting" },
  KIWISAVER: { icon: "sprout", tone: "bg-gold-soft text-gold", label: "KiwiSaver" },
  INCOME: { icon: "briefcase", tone: "bg-lilac-soft text-lilac", label: "Income and tax" },
  PLANNING: { icon: "target", tone: "bg-warm-soft text-warm", label: "Planning" },
};

function topic(name: string) {
  return (
    topicStyles[name] ?? {
      icon: "sparkles",
      tone: "bg-surface-2 text-ink-2",
      label: name.charAt(0) + name.slice(1).toLowerCase(),
    }
  );
}

export function LearnPage() {
  const { data: guides, isLoading, error, refetch } = useGuides();
  if (isLoading) return <PageSkeleton />;
  if (error || !guides)
    return <ErrorState message="We couldn't load the guides." onRetry={() => refetch()} />;

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Learn"
        title="Money, explained the Kiwi way"
        description="Short guides on the things that matter in New Zealand: KiwiSaver, PAYE, emergency funds and buying your first home."
      />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {guides.map((guide) => {
          const style = topic(guide.topic);
          return (
            <Link
              key={guide.slug}
              href={`/learn/${guide.slug}`}
              className="group flex flex-col rounded-2xl border border-line bg-surface p-5 shadow-card transition-transform hover:-translate-y-0.5"
            >
              <span className={cn("grid h-12 w-12 place-items-center rounded-2xl", style.tone)}>
                <Icon name={style.icon} className="h-6 w-6" />
              </span>
              <p className="mt-4 text-lg font-semibold group-hover:text-brand">{guide.title}</p>
              <p className="mt-1 flex-1 text-sm text-ink-2">{guide.summary}</p>
              <div className="mt-4 flex items-center gap-2">
                <Badge>{style.label}</Badge>
                <span className="flex items-center gap-1 text-xs font-semibold text-muted">
                  <Clock className="h-3.5 w-3.5" aria-hidden /> {guide.readingMinutes} min read
                </span>
              </div>
            </Link>
          );
        })}
      </div>
      <Glossary />
    </div>
  );
}

function Glossary() {
  const { data: entries } = useGlossary();
  const [search, setSearch] = useState("");
  const matches = useMemo(() => {
    const term = search.trim().toLowerCase();
    return (entries ?? [])
      .filter(
        (entry) =>
          !term || entry.term.toLowerCase().includes(term) || entry.definition.toLowerCase().includes(term),
      )
      .sort((a, b) => a.term.localeCompare(b.term));
  }, [entries, search]);

  return (
    <Card id="glossary">
      <CardHeader
        title="Glossary"
        description="The jargon on your payslip and bank statements, in plain English."
      />
      <div className="relative mb-4">
        <Search
          className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted"
          aria-hidden
        />
        <Input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Search terms like ESCT or IETC"
          aria-label="Search the glossary"
          className="pl-10"
        />
      </div>
      <dl className="grid gap-3 md:grid-cols-2">
        {matches.map((entry) => (
          <div key={entry.term} className="rounded-xl bg-surface-2 p-4">
            <dt className="font-semibold">{entry.term}</dt>
            <dd className="mt-1 text-sm text-ink-2">{entry.definition}</dd>
          </div>
        ))}
      </dl>
      {entries && matches.length === 0 ? (
        <p className="text-center text-sm text-muted">No terms match.</p>
      ) : null}
    </Card>
  );
}

export function GuidePage({ slug }: { slug: string }) {
  const { data: guide, isLoading, error, refetch } = useGuide(slug);
  const { data: guides } = useGuides();
  if (isLoading) return <PageSkeleton />;
  if (error || !guide) return <ErrorState message="We couldn't find that guide." onRetry={() => refetch()} />;
  const style = topic(guide.topic);
  const others = (guides ?? []).filter((entry) => entry.slug !== slug).slice(0, 3);

  return (
    <article className="space-y-6">
      <BackLink href="/learn" label="Learn" />
      <header className="hero-card rounded-2xl p-6 sm:p-8">
        <div className="flex items-center gap-3">
          <span className={cn("grid h-12 w-12 place-items-center rounded-2xl", style.tone)}>
            <Icon name={style.icon} className="h-6 w-6" />
          </span>
          <span className="text-sm font-semibold text-ink-2">
            {style.label} · {guide.readingMinutes} min read
          </span>
        </div>
        <h1 className="mt-4 text-3xl font-semibold text-balance sm:text-4xl">{guide.title}</h1>
        <p className="mt-2 text-lg text-ink-2">{guide.summary}</p>
      </header>
      <div className="space-y-4">
        {guide.sections.map((section, index) => (
          <Card key={section.heading}>
            <h2 className="flex items-center gap-3 text-xl font-semibold">
              <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-brand-soft text-sm font-semibold text-brand">
                {index + 1}
              </span>
              {section.heading}
            </h2>
            <div className="mt-3 space-y-3 leading-relaxed text-ink-2">
              {section.body.split(/\n{2,}/).map((paragraph) => (
                <p key={paragraph.slice(0, 40)}>{paragraph}</p>
              ))}
            </div>
          </Card>
        ))}
      </div>
      <div className="flex items-center gap-4 rounded-2xl bg-brand-soft p-5">
        <span className="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-good-soft text-good">
          <Check className="h-6 w-6" aria-hidden />
        </span>
        <p className="font-semibold">
          Nice one. Knowing this puts you ahead of most people. Put it into practice with your{" "}
          <Link href="/home" className="text-brand underline">
            dashboard
          </Link>
          .
        </p>
      </div>
      {others.length > 0 ? (
        <section>
          <h2 className="mb-3 flex items-center gap-2 text-lg font-semibold">
            <BookOpen className="h-5 w-5 text-brand" aria-hidden /> Keep reading
          </h2>
          <div className="grid gap-3 sm:grid-cols-3">
            {others.map((entry) => (
              <Link
                key={entry.slug}
                href={`/learn/${entry.slug}`}
                className="rounded-xl border border-line bg-surface p-4 font-semibold hover:bg-surface-2"
              >
                {entry.title}
              </Link>
            ))}
          </div>
        </section>
      ) : null}
    </article>
  );
}
