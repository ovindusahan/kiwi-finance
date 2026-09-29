"use client";

import { ChevronRight, CircleAlert, CircleCheck, Info } from "lucide-react";
import Link from "next/link";
import { type MoveMoneyDefaults, useMoveMoney } from "@/features/accounts/move-money";
import { bestSpendingAccount } from "@/features/emergency-fund/emergency-fund-page";
import { useAccounts } from "@/lib/api/queries";
import type { Account, Insight } from "@/lib/api/types";
import { cn } from "@/lib/cn";

const styles: Record<Insight["tone"], { icon: typeof Info; tone: string }> = {
  POSITIVE: { icon: CircleCheck, tone: "border-good/25 bg-good-soft text-good" },
  NEUTRAL: { icon: Info, tone: "border-brand/20 bg-brand-soft text-brand" },
  WARNING: { icon: CircleAlert, tone: "border-warm/25 bg-warm-soft text-warm" },
};

const actions: Record<NonNullable<Insight["action"]>, { label: string; href?: string }> = {
  OPEN_BUDGET: { label: "Open your budget", href: "/budget" },
  OPEN_GOALS: { label: "Review your goals", href: "/goals" },
  MOVE_MONEY: { label: "Move money" },
  REVIEW_SPENDING: { label: "See your spending", href: "/spending" },
  OPEN_EMERGENCY_FUND: { label: "Open your emergency fund", href: "/emergency-fund" },
};

/** Points the move money dialog at the accounts an insight is about. */
function movementFor(insight: Insight, accounts: Account[] | undefined): MoveMoneyDefaults {
  const open = (accounts ?? []).filter((account) => !account.archived);
  const fund = open.find((account) => account.includeInEmergencyFund);
  const spending = bestSpendingAccount(open, fund?.id);
  const amountCents = insight.amount && insight.amount.cents > 0 ? insight.amount.cents : undefined;
  switch (insight.key) {
    case "spare_cash":
    case "emergency_fund_used":
      return { type: "TRANSFER", fromAccountId: spending?.id, toAccountId: fund?.id, amountCents };
    case "bills_exceed_balance": {
      const savings =
        open.find(
          (account) => account.type === "SAVINGS" && account.id !== fund?.id && account.balance.cents > 0,
        ) ?? fund;
      return { type: "TRANSFER", fromAccountId: savings?.id, toAccountId: spending?.id, amountCents };
    }
    case "debt_clearable": {
      const debt = [...open]
        .filter((account) => account.type === "CREDIT_CARD" || account.type === "LOAN")
        .sort((a, b) => a.balance.cents - b.balance.cents)[0];
      return { type: "TRANSFER", fromAccountId: spending?.id, toAccountId: debt?.id, amountCents };
    }
    default:
      return { type: "TRANSFER", amountCents };
  }
}

export function InsightCard({ insight, className }: { insight: Insight; className?: string }) {
  const style = styles[insight.tone];
  const Glyph = style.icon;
  const moveMoney = useMoveMoney();
  const { data: accounts } = useAccounts();
  const action = insight.action ? actions[insight.action] : null;
  const linkClass = "mt-2 inline-flex items-center text-sm font-medium text-brand hover:underline";
  return (
    <div className={cn("flex gap-3 rounded-xl border p-4", style.tone, className)}>
      <span className="shrink-0 pt-0.5">
        <Glyph className="h-5 w-5" aria-hidden />
      </span>
      <div className="min-w-0">
        <p className="font-semibold text-ink">{insight.title}</p>
        <p className="mt-0.5 text-sm text-ink-2">{insight.message}</p>
        {action ? (
          action.href ? (
            <Link href={action.href} className={linkClass}>
              {action.label} <ChevronRight className="h-4 w-4" aria-hidden />
            </Link>
          ) : (
            <button
              type="button"
              className={linkClass}
              onClick={() => moveMoney(movementFor(insight, accounts))}
            >
              {action.label} <ChevronRight className="h-4 w-4" aria-hidden />
            </button>
          )
        ) : null}
      </div>
    </div>
  );
}
