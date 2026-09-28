"use client";

import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import { ChevronDown, LogOut, Menu } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { MoveMoneyProvider } from "@/features/accounts/move-money";
import { useMe } from "@/lib/api/queries";
import { cn } from "@/lib/cn";
import { mobileTabs, moreNav, primaryNav, secondaryNav } from "./nav";

function isActive(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { data: me } = useMe();
  const [moreOpen, setMoreOpen] = useState(false);

  async function signOut() {
    await fetch("/api/auth/logout", { method: "POST" });
    router.replace("/sign-in");
    router.refresh();
  }

  const moreActive = secondaryNav.some((item) => isActive(pathname, item.href));

  return (
    <MoveMoneyProvider>
      <div className="min-h-dvh">
        <header className="sticky top-0 z-30 bg-navy text-on-navy">
          <div className="mx-auto flex h-14 max-w-6xl items-center gap-4 px-4 sm:px-6">
            <Link href="/home" className="text-xl font-bold tracking-tight text-white">
              Kiwi Finance
            </Link>
            <div className="ml-auto flex items-center gap-3">
              {me?.displayName ? (
                <span className="hidden text-sm text-white sm:inline">{me.displayName}</span>
              ) : null}
              <button
                type="button"
                onClick={signOut}
                className="h-8 rounded-lg border border-white px-3 text-sm font-semibold uppercase tracking-wide text-white hover:bg-white hover:text-navy"
              >
                Log out
              </button>
            </div>
          </div>
          <nav aria-label="Main" className="hidden bg-navy-2 lg:block">
            <ul className="mx-auto flex max-w-6xl items-center gap-1 px-4 sm:px-6">
              {primaryNav.map((item) => (
                <li key={item.href}>
                  <NavLink href={item.href} label={item.label} active={isActive(pathname, item.href)} />
                </li>
              ))}
              <li>
                <DropdownMenu.Root>
                  <DropdownMenu.Trigger
                    className={cn(
                      "flex items-center gap-1 border-b-2 px-3 py-3 text-[15px] text-white hover:border-white/50",
                      moreActive ? "border-white font-semibold text-white" : "border-transparent",
                    )}
                  >
                    More <ChevronDown className="h-4 w-4" aria-hidden />
                  </DropdownMenu.Trigger>
                  <DropdownMenu.Portal>
                    <DropdownMenu.Content
                      align="start"
                      sideOffset={4}
                      className="z-50 w-56 rounded-lg border border-line bg-surface py-1 text-ink shadow-pop"
                    >
                      {secondaryNav.map((item) => (
                        <DropdownMenu.Item key={item.href} asChild>
                          <Link
                            href={item.href}
                            className="block px-4 py-2 text-sm text-brand outline-none data-[highlighted]:bg-surface-2 data-[highlighted]:underline"
                          >
                            {item.label}
                          </Link>
                        </DropdownMenu.Item>
                      ))}
                    </DropdownMenu.Content>
                  </DropdownMenu.Portal>
                </DropdownMenu.Root>
              </li>
            </ul>
          </nav>
        </header>

        <main className="mx-auto w-full max-w-6xl px-4 pb-32 pt-6 sm:px-6 lg:pb-16 lg:pt-8">{children}</main>

        <nav
          aria-label="Main"
          className="fixed inset-x-0 bottom-0 z-40 border-t border-line bg-surface px-2 pb-[max(env(safe-area-inset-bottom),8px)] pt-1.5 lg:hidden"
        >
          <ul className="grid grid-cols-5">
            {mobileTabs.map((item) => {
              const active = isActive(pathname, item.href);
              const Glyph = item.icon;
              return (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    aria-current={active ? "page" : undefined}
                    className={cn(
                      "flex flex-col items-center gap-0.5 py-1.5 text-[11px] font-medium",
                      active ? "text-brand" : "text-muted",
                    )}
                  >
                    <Glyph className="h-5 w-5" aria-hidden strokeWidth={active ? 2.4 : 2} />
                    {item.label}
                  </Link>
                </li>
              );
            })}
            <li>
              <button
                type="button"
                onClick={() => setMoreOpen(true)}
                className="flex w-full flex-col items-center gap-0.5 py-1.5 text-[11px] font-medium text-muted"
              >
                <Menu className="h-5 w-5" aria-hidden />
                More
              </button>
            </li>
          </ul>
        </nav>

        <Dialog open={moreOpen} onOpenChange={setMoreOpen}>
          <DialogContent title="More">
            <ul className="grid grid-cols-2 gap-x-4">
              {moreNav.map((item) => {
                const Glyph = item.icon;
                return (
                  <li key={item.href}>
                    <Link
                      href={item.href}
                      onClick={() => setMoreOpen(false)}
                      className="flex items-center gap-3 border-b border-line p-3 text-sm font-medium text-brand hover:underline"
                    >
                      <Glyph className="h-5 w-5 text-brand" aria-hidden />
                      {item.label}
                    </Link>
                  </li>
                );
              })}
            </ul>
            <button
              type="button"
              onClick={signOut}
              className="mt-4 flex w-full items-center justify-center gap-2 rounded-lg border border-line p-3 text-sm font-semibold hover:bg-surface-2"
            >
              <LogOut className="h-4 w-4" aria-hidden />
              Sign out
            </button>
          </DialogContent>
        </Dialog>
      </div>
    </MoveMoneyProvider>
  );
}

function NavLink({ href, label, active }: { href: string; label: string; active: boolean }) {
  return (
    <Link
      href={href}
      aria-current={active ? "page" : undefined}
      className={cn(
        "block whitespace-nowrap border-b-2 px-3 py-3 text-[15px] transition-colors",
        active
          ? "border-white font-semibold text-white"
          : "border-transparent text-white hover:border-white/50",
      )}
    >
      {label}
    </Link>
  );
}
