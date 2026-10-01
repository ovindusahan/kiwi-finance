"use client";

import { ArrowDownLeft, ArrowUpRight, BookOpen, Check, Flag, Landmark, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { ExplanationPanel } from "@/components/app/explanation-panel";
import { PageHeader } from "@/components/app/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { ErrorState } from "@/components/ui/empty-state";
import { ProgressBar } from "@/components/ui/progress";
import { PageSkeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/components/ui/toast";
import { useMoveMoney } from "@/features/accounts/move-money";
import { api } from "@/lib/api/client";
import {
  useAccounts,
  useApiMutation,
  useEmergencyFund,
  usePreferences,
  useTransactions,
} from "@/lib/api/queries";
import type { Account, EmergencyFund, Preferences } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { formatDayMonth, formatMoney, formatMonthYear } from "@/lib/format";

const statusLabels: Record<EmergencyFund["status"], string> = {
  NOT_STARTED: "Not started",
  BUILDING: "Building",
  FUNDED: "Fully funded",
};

export function EmergencyFundPage() {
  const { data: fund, isLoading, error, refetch } = useEmergencyFund();
  const [choosing, setChoosing] = useState(false);

  if (isLoading) return <PageSkeleton />;
  if (error || !fund)
    return <ErrorState message="We couldn't load your emergency fund." onRetry={() => refetch()} />;

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Emergency fund"
        title="Your safety net"
        description="Money set aside for the unexpected: a lost job, a broken-down car, a trip home for whānau. Sized from your own essential costs."
      />

      {!fund.account || choosing ? (
        <ChooseAccount fund={fund} onDone={() => setChoosing(false)} canCancel={Boolean(fund.account)} />
      ) : (
        <>
          <FundOverview fund={fund} onChange={() => setChoosing(true)} />
          <div className="grid gap-x-10 gap-y-8 lg:grid-cols-2">
            <Plan fund={fund} />
            <RecentActivity fund={fund} />
            <Milestones fund={fund} />
            <WhyThisTarget fund={fund} />
          </div>
          <ExplanationPanel explanation={fund.explanation} />
        </>
      )}
    </div>
  );
}

function FundOverview({ fund, onChange }: { fund: EmergencyFund; onChange: () => void }) {
  const moveMoney = useMoveMoney();
  const { data: accounts } = useAccounts();
  const account = fund.account!;
  const everyday = bestSpendingAccount(accounts, account.id);

  return (
    <section className="hero-card p-6 sm:p-8" aria-label="Emergency fund account">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="flex items-center gap-2 text-sm text-ink-2">
            <Landmark className="h-4 w-4" aria-hidden />
            {[account.name, account.institution].filter(Boolean).join(" · ")}
            <span className="text-muted">· {account.fromBank ? "from your bank" : "updated by you"}</span>
          </p>
          <p className="mt-2 text-4xl font-semibold tabular">{formatMoney(account.balance)}</p>
          <p className="mt-1 text-ink-2">
            of a <span className="font-semibold text-ink">{formatMoney(fund.target, { whole: true })}</span>{" "}
            target · covers <span className="font-semibold text-ink">{fund.monthsCovered} months</span> of
            essentials
          </p>
        </div>
        <Badge tone={fund.status === "FUNDED" ? "good" : "sky"}>{statusLabels[fund.status]}</Badge>
      </div>
      <ProgressBar
        value={fund.progress}
        tone={fund.status === "FUNDED" ? "good" : "brand"}
        label="Emergency fund progress"
        size="lg"
        className="mt-5"
      />
      <div className="mt-5 flex flex-wrap items-center gap-3">
        <Button
          onClick={() =>
            moveMoney({
              type: "TRANSFER",
              toAccountId: account.id,
              fromAccountId: everyday?.id,
              amountCents:
                fund.status === "FUNDED"
                  ? undefined
                  : Math.min(fund.shortfall.cents, fund.suggestedMonthlyContribution.cents),
            })
          }
        >
          <ArrowDownLeft className="h-4 w-4" aria-hidden /> Add money
        </Button>
        <Button
          variant="outline"
          onClick={() =>
            moveMoney({ type: "TRANSFER", fromAccountId: account.id, toAccountId: everyday?.id })
          }
        >
          <ArrowUpRight className="h-4 w-4" aria-hidden /> Use money
        </Button>
        <button type="button" onClick={onChange} className="text-sm text-brand hover:underline">
          Change account
        </button>
      </div>
    </section>
  );
}

function Plan({ fund }: { fund: EmergencyFund }) {
  const finish = fund.monthsToTarget != null ? addMonths(fund.monthsToTarget) : null;
  return (
    <Card>
      <CardHeader
        title="Your plan"
        description="A steady amount each pay gets you there without feeling it."
      />
      {fund.status === "FUNDED" ? (
        <p className="text-ink-2">
          You&apos;re fully funded. If you use some of it, we&apos;ll show you how to top it back up.
        </p>
      ) : (
        <dl className="grid grid-cols-3 gap-4">
          <Figure
            label={`Each ${fund.payPeriod}`}
            value={formatMoney(fund.suggestedPerPayPeriod, { whole: true })}
          />
          <Figure label="Still to go" value={formatMoney(fund.shortfall, { whole: true })} />
          <Figure label="Fully funded by" value={finish ? formatMonthYear(finish) : "Not yet"} />
        </dl>
      )}
    </Card>
  );
}

function Figure({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-sm text-muted">{label}</dt>
      <dd className="text-xl font-semibold tabular">{value}</dd>
    </div>
  );
}

function RecentActivity({ fund }: { fund: EmergencyFund }) {
  const { data } = useTransactions({ accountId: fund.account!.id });
  const items = data?.items.slice(0, 6) ?? [];
  return (
    <Card>
      <CardHeader
        title="Recent activity"
        action={
          <Link
            href={`/transactions?accountId=${fund.account!.id}`}
            className="text-sm text-brand hover:underline"
          >
            All transactions
          </Link>
        }
      />
      {!data ? (
        <p className="text-sm text-muted">Loading</p>
      ) : items.length === 0 ? (
        <p className="text-ink-2">No money has moved in or out yet.</p>
      ) : (
        <ul className="divide-y divide-line">
          {items.map((item) => (
            <li key={item.id} className="flex items-center justify-between gap-3 py-2.5">
              <div className="min-w-0">
                <p className="truncate font-medium">{item.description}</p>
                <p className="text-sm text-muted">{formatDayMonth(item.postedOn)}</p>
              </div>
              <p className={cn("font-semibold tabular", item.amount.cents > 0 ? "text-good" : "text-ink")}>
                {formatMoney(item.amount, { signed: true })}
              </p>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function Milestones({ fund }: { fund: EmergencyFund }) {
  const nextIndex = fund.milestones.findIndex((milestone) => !milestone.reached);
  return (
    <Card>
      <CardHeader title="Milestones" description="Each one is a real step up in how protected you are." />
      <ol className="divide-y divide-line">
        {fund.milestones.map((milestone, index) => {
          const next = index === nextIndex;
          return (
            <li key={milestone.label} className="flex items-center gap-3 py-2.5">
              <span
                className={cn(
                  "grid h-7 w-7 shrink-0 place-items-center rounded-full",
                  milestone.reached
                    ? "bg-good text-on-brand"
                    : next
                      ? "border-2 border-brand text-brand"
                      : "bg-surface-3 text-muted",
                )}
              >
                {milestone.reached ? (
                  <Check className="h-4 w-4" strokeWidth={3} aria-label="Reached" />
                ) : (
                  <ShieldCheck className="h-4 w-4" aria-hidden />
                )}
              </span>
              <p className={cn("flex-1 font-medium", !milestone.reached && !next && "text-muted")}>
                {milestone.label}
              </p>
              <p className="text-sm text-muted tabular">
                {formatMoney(milestone.amount, { whole: true })}
                {next
                  ? ` · ${formatMoney(milestone.amount.cents - fund.current.cents, { whole: true })} to go`
                  : ""}
              </p>
            </li>
          );
        })}
      </ol>
    </Card>
  );
}

function WhyThisTarget({ fund }: { fund: EmergencyFund }) {
  return (
    <Card>
      <CardHeader title="Why this target?" />
      <p className="text-ink-2">
        Your essential costs, like rent, groceries, power, insurance and transport, come to about{" "}
        <span className="font-semibold text-ink">
          {formatMoney(fund.monthlyEssentials, { whole: true })} a month
        </span>
        . We aim for {fund.targetMonths} months of those.
      </p>
      {fund.reasons.length > 0 ? (
        <ul className="mt-3 space-y-2">
          {fund.reasons.map((reason) => (
            <li key={reason} className="flex gap-2 text-sm text-ink-2">
              <Flag className="mt-0.5 h-4 w-4 shrink-0 text-warm" aria-hidden />
              {reason}
            </li>
          ))}
        </ul>
      ) : null}
      <Link
        href="/learn/emergency-fund"
        className="mt-4 inline-flex items-center gap-2 text-brand hover:underline"
      >
        <BookOpen className="h-4 w-4" aria-hidden /> Emergency funds in New Zealand
      </Link>
    </Card>
  );
}

function ChooseAccount({
  fund,
  onDone,
  canCancel,
}: {
  fund: EmergencyFund;
  onDone: () => void;
  canCancel: boolean;
}) {
  const toast = useToast();
  const { data: accounts } = useAccounts();
  const options = (accounts ?? []).filter(
    (account) => !account.archived && account.liquid && account.type !== "CREDIT_CARD",
  );
  const [selected, setSelected] = useState(fund.account?.id ?? fund.suggestedAccount?.id ?? "");
  const choose = useApiMutation((accountId: string) =>
    api.put<EmergencyFund>("/emergency-fund/account", { accountId }),
  );

  return (
    <section aria-labelledby="choose-heading" className="space-y-5">
      <div className="rounded-xl border border-brand/20 bg-brand-soft p-5">
        <h2 id="choose-heading" className="text-lg font-semibold">
          Choose your emergency fund account
        </h2>
        <p className="mt-1 text-ink-2">
          Pick the one account that holds your safety net. We&apos;ll track its balance, size your target from
          your essential costs and show you how to build it up. A separate savings account works best.
        </p>
      </div>
      {accounts && options.length === 0 ? (
        <p className="text-ink-2">
          You don&apos;t have a savings or everyday account yet.{" "}
          <Link href="/connect" className="text-brand hover:underline">
            Connect your bank
          </Link>{" "}
          or{" "}
          <Link href="/settings/accounts" className="text-brand hover:underline">
            add an account
          </Link>{" "}
          first.
        </p>
      ) : (
        <fieldset>
          <legend className="sr-only">Accounts</legend>
          <ul className="divide-y divide-line border-y border-line">
            {options.map((account) => (
              <li key={account.id}>
                <label className="flex cursor-pointer items-center gap-4 py-3.5">
                  <input
                    type="radio"
                    name="fund-account"
                    value={account.id}
                    checked={selected === account.id}
                    onChange={() => setSelected(account.id)}
                    className="h-5 w-5 accent-[var(--brand)]"
                  />
                  <span className="min-w-0 flex-1">
                    <span className="flex items-center gap-2 font-semibold">
                      {account.name}
                      {fund.suggestedAccount?.id === account.id ? <Badge tone="good">Suggested</Badge> : null}
                    </span>
                    <span className="block text-sm text-muted">
                      {[account.type === "SAVINGS" ? "Savings" : "Everyday", account.institution]
                        .filter(Boolean)
                        .join(" · ")}
                    </span>
                  </span>
                  <span className="font-semibold tabular">{formatMoney(account.balance)}</span>
                </label>
              </li>
            ))}
          </ul>
        </fieldset>
      )}
      <div className="flex flex-wrap items-center gap-3">
        <Button
          disabled={!selected || choose.isPending}
          onClick={() =>
            choose.mutate(selected, {
              onSuccess: (result) => {
                toast(`${result.account?.name ?? "That account"} is now your emergency fund`);
                onDone();
              },
              onError: (failure) => toast(failure.message, "error"),
            })
          }
        >
          Use this account
        </Button>
        {canCancel ? (
          <Button variant="secondary" onClick={onDone}>
            Cancel
          </Button>
        ) : null}
      </div>
      {!canCancel ? <ReminderSetting /> : null}
    </section>
  );
}

/** Lets people turn the "choose an emergency fund account" reminder off and on again. */
export function ReminderSetting() {
  const toast = useToast();
  const { data: preferences } = usePreferences();
  const update = useApiMutation((emergencyFundReminders: boolean) =>
    api.patch<Preferences>("/preferences", { emergencyFundReminders }),
  );
  if (!preferences) return null;
  return (
    <div className="flex items-center justify-between gap-4 border-t border-line pt-4">
      <label htmlFor="fund-reminders" className="min-w-0">
        <span className="block font-medium">Remind me to choose an emergency fund account</span>
        <span className="block text-sm text-muted">
          We show a reminder on your home screen until you choose one.
        </span>
      </label>
      <Switch
        id="fund-reminders"
        checked={preferences.emergencyFundReminders}
        onCheckedChange={(on) =>
          update.mutate(on, {
            onSuccess: () => toast(on ? "Reminders turned on" : "Reminders turned off"),
            onError: (failure) => toast(failure.message, "error"),
          })
        }
      />
    </div>
  );
}

/** The account people usually spend from, for moving money in and out of the fund. */
export function bestSpendingAccount(
  accounts: Account[] | undefined,
  excludeId?: string,
): Account | undefined {
  const open = (accounts ?? []).filter((account) => !account.archived && account.id !== excludeId);
  return (
    open.find((account) => account.type === "EVERYDAY") ??
    open.find((account) => account.liquid && account.type !== "CREDIT_CARD")
  );
}

function addMonths(months: number): string {
  const date = new Date();
  date.setDate(1);
  date.setMonth(date.getMonth() + months);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-01`;
}
