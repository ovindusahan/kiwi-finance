"use client";

import { ChevronRight, KeyRound, Landmark, Tags, User, Wallet } from "lucide-react";
import Link from "next/link";
import { useMe } from "@/lib/api/queries";
import { SettingsFrame, settingsSections } from "./settings-nav";

const icons = [KeyRound, User, Wallet, Landmark, Tags];

export function SettingsIndex() {
  const { data: me } = useMe();
  return (
    <SettingsFrame
      title="Settings"
      description="Keep these up to date and every number in Kiwi Finance stays accurate."
    >
      {me ? (
        <Link
          href="/settings/account"
          className="mb-6 flex items-center gap-4 rounded-2xl border border-line bg-surface p-5 transition-colors hover:border-brand"
        >
          <span
            aria-hidden
            className="grid h-14 w-14 shrink-0 place-items-center rounded-full bg-brand text-xl font-semibold text-on-brand"
          >
            {me.displayName.trim().charAt(0).toUpperCase()}
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-lg font-semibold">{me.displayName}</span>
            <span className="block truncate text-ink-2">{me.email}</span>
          </span>
          <span className="hidden text-sm font-semibold text-brand sm:inline">Edit details</span>
          <ChevronRight className="h-5 w-5 text-muted" aria-hidden />
        </Link>
      ) : null}
      <ul className="divide-y divide-line overflow-hidden rounded-2xl border border-line bg-surface">
        {settingsSections.map((section, index) => {
          const Glyph = icons[index] ?? User;
          return (
            <li key={section.href}>
              <Link
                href={section.href}
                className="flex items-center gap-4 px-5 py-4 transition-colors hover:bg-panel"
              >
                <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-brand-soft text-brand">
                  <Glyph className="h-5 w-5" aria-hidden />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block font-semibold">{section.label}</span>
                  <span className="block text-sm text-muted">{section.description}</span>
                </span>
                <ChevronRight className="h-5 w-5 text-muted" aria-hidden />
              </Link>
            </li>
          );
        })}
      </ul>
    </SettingsFrame>
  );
}
