"use client";

import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";

/** Pages that sit inside another page, and the page to go back to. */
const parents: { prefix: string; href: string; label: string }[] = [
  { prefix: "/goals/", href: "/goals", label: "Goals" },
  { prefix: "/learn/", href: "/learn", label: "Learn" },
  { prefix: "/settings/", href: "/settings", label: "Settings" },
  { prefix: "/connect/", href: "/connect", label: "Connect bank" },
];

export function parentOf(pathname: string | null) {
  return parents.find((parent) => pathname?.startsWith(parent.prefix)) ?? null;
}

/** A link back to the page this one sits inside. */
export function BackLink({ href, label }: { href: string; label: string }) {
  return (
    <Link
      href={href}
      className="mb-3 inline-flex items-center gap-1.5 text-sm font-semibold text-brand hover:underline"
    >
      <ArrowLeft className="h-4 w-4" aria-hidden /> Back to {label}
    </Link>
  );
}

export function PageHeader({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow?: string;
  title: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
}) {
  const parent = parentOf(usePathname());
  return (
    <div className="mb-6">
      {parent ? <BackLink href={parent.href} label={parent.label} /> : null}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          {eyebrow ? <p className="mb-1 text-sm font-medium text-muted">{eyebrow}</p> : null}
          <h1 className="text-2xl font-semibold text-ink sm:text-[28px]">{title}</h1>
          {description ? <p className="mt-1.5 max-w-2xl text-ink-2">{description}</p> : null}
        </div>
        {action ? <div className="flex shrink-0 flex-wrap gap-2">{action}</div> : null}
      </div>
    </div>
  );
}
