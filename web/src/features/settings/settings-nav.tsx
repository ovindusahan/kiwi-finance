"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { PageHeader } from "@/components/app/page-header";
import { cn } from "@/lib/cn";

export const settingsSections = [
  { href: "/settings/account", label: "Your account", description: "Name, email, password and your data" },
  { href: "/settings/profile", label: "Pay and tax", description: "Tax code, KiwiSaver and household" },
  { href: "/settings/income", label: "Income", description: "Salary, wages, benefits and other income" },
  { href: "/settings/accounts", label: "Accounts", description: "Bank accounts, cards, KiwiSaver and cash" },
  { href: "/settings/rules", label: "Categories", description: "Your categories and automatic rules" },
];

/** The settings page header with links between sections. */
export function SettingsFrame({
  title,
  description,
  action,
  children,
}: {
  title: string;
  description?: string;
  action?: React.ReactNode;
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  return (
    <div>
      <PageHeader eyebrow="Settings" title={title} description={description} action={action} />
      <nav aria-label="Settings" className="-mx-4 mb-6 overflow-x-auto border-b border-line px-4">
        <ul className="flex gap-1">
          {settingsSections.map((section) => (
            <li key={section.href}>
              <Link
                href={section.href}
                aria-current={pathname === section.href ? "page" : undefined}
                className={cn(
                  "-mb-px block whitespace-nowrap border-b-2 px-3 py-2.5 text-sm transition-colors",
                  pathname === section.href
                    ? "border-brand font-semibold text-ink"
                    : "border-transparent text-brand hover:underline",
                )}
              >
                {section.label}
              </Link>
            </li>
          ))}
        </ul>
      </nav>
      {children}
    </div>
  );
}
