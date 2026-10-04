"use client";

import * as Accordion from "@radix-ui/react-accordion";
import {
  Check,
  ChevronDown,
  ExternalLink,
  Eye,
  EyeOff,
  Lightbulb,
  Lock,
  RefreshCw,
  ShieldCheck,
  TriangleAlert,
  Unplug,
} from "lucide-react";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { PageHeader } from "@/components/app/page-header";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { Dialog, DialogClose, DialogContent } from "@/components/ui/dialog";
import { ErrorState } from "@/components/ui/empty-state";
import { Field, Input } from "@/components/ui/field";
import { PageSkeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/components/ui/toast";
import { api, ApiError } from "@/lib/api/client";
import { useApiMutation, useConnections, useSetupGuide } from "@/lib/api/queries";
import type {
  BankConnection,
  BankFeedAccount,
  BankSyncRun,
  OAuthStart,
  SetupGuide,
  SetupGuideMethod,
  SetupGuideStep,
} from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { formatMoney, formatRelative } from "@/lib/format";

type Method = SetupGuideMethod["method"];

const stateBadges: Record<
  SetupGuide["state"],
  { label: string; tone: "good" | "sky" | "warm" | "gold" | "neutral" }
> = {
  NOT_CONNECTED: { label: "Not connected", tone: "neutral" },
  CHOOSE_ACCOUNTS: { label: "Choose accounts", tone: "sky" },
  SYNCING: { label: "Syncing", tone: "sky" },
  CONNECTED: { label: "Connected", tone: "good" },
  NEEDS_ATTENTION: { label: "Needs attention", tone: "warm" },
};

export function ConnectPage() {
  const params = useSearchParams();
  const { data: connections } = useConnections();
  const guide = useSetupGuide();
  const [method, setMethod] = useState<Method | null>(null);

  if (guide.isLoading) return <PageSkeleton />;
  if (guide.error || !guide.data)
    return <ErrorState message="We couldn't load the setup guide." onRetry={() => guide.refetch()} />;

  const data = guide.data;
  const chosen = method ?? data.connection?.method ?? data.recommendedMethod;
  const active = data.methods.find((entry) => entry.method === chosen) ?? data.methods[0];
  const connection = connections?.find((entry) => entry.status !== "DISCONNECTED");
  const badge = stateBadges[data.state];

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Connect your bank"
        title={data.headline}
        description="Kiwi Finance uses Akahu, New Zealand's open finance platform, to read your transactions. It can never move money."
        action={
          <Badge tone={badge.tone} className="px-3 py-1.5 text-sm">
            {badge.label}
          </Badge>
        }
      />

      {params.get("connected") === "1" ? (
        <div className="flex items-center gap-4 rounded-2xl bg-brand-soft p-5 animate-pop">
          <span className="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-good-soft text-good">
            <Check className="h-6 w-6" aria-hidden />
          </span>
          <div>
            <p className="font-display text-xl font-semibold">Kia pai! Your bank is connected.</p>
            <p className="text-ink-2">
              Choose the accounts to follow below. Your transactions will start arriving in a moment.
            </p>
          </div>
        </div>
      ) : null}

      {connection ? <ConnectionCard connection={connection} /> : null}

      <section>
        <h2 className="mb-3 text-xl font-semibold">
          {connection ? "How your connection works" : "Choose how to connect"}
        </h2>
        <div className="grid gap-3 md:grid-cols-2">
          {data.methods.map((entry) => (
            <button
              key={entry.method}
              type="button"
              onClick={() => setMethod(entry.method)}
              aria-pressed={entry.method === active?.method}
              disabled={!entry.available}
              className={cn(
                "rounded-2xl border-2 bg-surface p-5 text-left transition-colors disabled:opacity-60",
                entry.method === active?.method
                  ? "border-brand shadow-card"
                  : "border-line hover:border-ink-2/30",
              )}
            >
              <div className="flex items-center justify-between gap-2">
                <p className="text-lg font-semibold">{entry.title}</p>
                {entry.method === data.recommendedMethod ? <Badge tone="brand">Recommended</Badge> : null}
              </div>
              <p className="mt-1 text-sm text-ink-2">{entry.summary}</p>
              <p className="mt-3 text-xs font-semibold text-muted">
                About {entry.estimatedMinutes} minutes · {entry.steps.length} steps
              </p>
              {!entry.available && entry.unavailableReason ? (
                <p className="mt-2 text-xs font-semibold text-warm">{entry.unavailableReason}</p>
              ) : null}
            </button>
          ))}
        </div>
      </section>

      {active ? <MethodSteps method={active} /> : null}

      <section className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader title="Your data stays yours" />
          <ul className="grid gap-3 sm:grid-cols-2">
            {data.security.map((note) => (
              <li key={note.title} className="rounded-xl bg-surface-2 p-4">
                <p className="flex items-center gap-2 font-semibold">
                  <ShieldCheck className="h-4 w-4 text-brand" aria-hidden /> {note.title}
                </p>
                <p className="mt-1 text-sm text-ink-2">{note.body}</p>
              </li>
            ))}
          </ul>
          <div className="mt-4">
            <p className="text-sm font-semibold text-muted">Works with</p>
            <div className="mt-2 flex flex-wrap gap-2">
              {data.supportedBanks.map((bank) => (
                <Badge key={bank} className="px-3 py-1.5">
                  {bank}
                </Badge>
              ))}
            </div>
            <p className="mt-2 text-xs text-muted">{data.supportedBanksNote}</p>
          </div>
        </Card>
        <Card>
          <CardHeader title="Troubleshooting" />
          <Accordion.Root type="single" collapsible className="space-y-2">
            {data.troubleshooting.map((faq) => (
              <Accordion.Item key={faq.question} value={faq.question} className="rounded-2xl bg-surface-2">
                <Accordion.Header>
                  <Accordion.Trigger className="group flex w-full items-center justify-between gap-3 p-4 text-left font-semibold">
                    {faq.question}
                    <ChevronDown
                      className="h-5 w-5 shrink-0 text-muted transition-transform group-data-[state=open]:rotate-180"
                      aria-hidden
                    />
                  </Accordion.Trigger>
                </Accordion.Header>
                <Accordion.Content className="px-4 pb-4 text-sm text-ink-2">{faq.answer}</Accordion.Content>
              </Accordion.Item>
            ))}
          </Accordion.Root>
        </Card>
      </section>
    </div>
  );
}

function MethodSteps({ method }: { method: SetupGuideMethod }) {
  return (
    <Card>
      <CardHeader
        title={method.title}
        description={
          method.requirements.length ? `You'll need: ${method.requirements.join("; ")}.` : undefined
        }
      />
      <ol className="relative">
        {method.steps.map((step, index) => (
          <li key={step.id} className="relative flex gap-4 pb-6 last:pb-0">
            {index < method.steps.length - 1 ? (
              <span
                className={cn(
                  "absolute left-[23px] top-12 h-[calc(100%-3rem)] w-1 rounded-full",
                  step.status === "DONE" ? "bg-brand" : "bg-surface-3",
                )}
                aria-hidden
              />
            ) : null}
            <StepMarker step={step} index={index} />
            <div className={cn("min-w-0 flex-1 pt-1.5", step.status === "TODO" && "opacity-70")}>
              <p className="text-lg font-semibold">{step.title}</p>
              <p className="mt-1 text-ink-2">{step.body}</p>
              {step.tip ? (
                <p className="mt-2 flex gap-2 rounded-2xl bg-gold-soft p-3 text-sm text-ink-2">
                  <Lightbulb className="mt-0.5 h-4 w-4 shrink-0 text-gold" aria-hidden />
                  {step.tip}
                </p>
              ) : null}
              {step.links.length > 0 ? (
                <div className="mt-3 flex flex-wrap gap-2">
                  {step.links.map((link) => (
                    <a
                      key={link.url}
                      href={link.url}
                      target="_blank"
                      rel="noreferrer"
                      className={cn(buttonVariants({ variant: "secondary", size: "sm" }))}
                    >
                      {link.label} <ExternalLink className="h-3.5 w-3.5" aria-hidden />
                    </a>
                  ))}
                </div>
              ) : null}
              {step.action && step.status !== "DONE" ? <StepAction step={step} /> : null}
            </div>
          </li>
        ))}
      </ol>
    </Card>
  );
}

function StepMarker({ step, index }: { step: SetupGuideStep; index: number }) {
  return (
    <span
      className={cn(
        "relative z-10 grid h-12 w-12 shrink-0 place-items-center rounded-full font-display text-lg font-semibold",
        step.status === "DONE" && "bg-brand text-on-brand",
        step.status === "CURRENT" && "bg-warm text-on-warm",
        step.status === "ATTENTION" && "bg-danger text-on-danger",
        step.status === "TODO" && "bg-surface-3 text-muted",
      )}
    >
      {step.status === "DONE" ? (
        <Check className="h-6 w-6" strokeWidth={3} aria-label="Done" />
      ) : step.status === "ATTENTION" ? (
        <TriangleAlert className="h-5 w-5" aria-label="Needs attention" />
      ) : (
        index + 1
      )}
    </span>
  );
}

function StepAction({ step }: { step: SetupGuideStep }) {
  const action = step.action!;
  switch (action.type) {
    case "OPEN_LINK":
      return action.url ? (
        <a
          href={action.url}
          target="_blank"
          rel="noreferrer"
          className={cn(buttonVariants({ size: "sm" }), "mt-3")}
        >
          {action.label} <ExternalLink className="h-4 w-4" aria-hidden />
        </a>
      ) : null;
    case "ENTER_TOKENS":
    case "UPDATE_TOKENS":
      return (
        <TokenForm
          connectionId={action.type === "UPDATE_TOKENS" ? action.connectionId : null}
          label={action.label}
        />
      );
    case "START_OAUTH":
      return <OAuthButton label={action.label} />;
    case "SYNC_NOW":
      return action.connectionId ? (
        <SyncButton connectionId={action.connectionId} label={action.label} />
      ) : null;
    case "CHOOSE_ACCOUNTS":
      return (
        <a href="#accounts" className={cn(buttonVariants({ size: "sm" }), "mt-3")}>
          {action.label}
        </a>
      );
    default:
      return null;
  }
}

function TokenForm({ connectionId, label }: { connectionId: string | null; label: string }) {
  const toast = useToast();
  const [appToken, setAppToken] = useState("");
  const [userToken, setUserToken] = useState("");
  const [visible, setVisible] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const submit = useApiMutation((body: { appToken: string; userToken: string }) =>
    connectionId
      ? api.put<BankConnection>(`/bank-feeds/akahu/connections/${connectionId}/credentials`, body)
      : api.post<BankConnection>("/bank-feeds/akahu/personal-connections", body),
  );

  return (
    <form
      noValidate
      className="mt-4 space-y-3 rounded-xl bg-surface-2 p-4"
      onSubmit={(event) => {
        event.preventDefault();
        const found: Record<string, string> = {};
        if (!appToken.trim().startsWith("app_token_")) found.appToken = "App tokens start with app_token_";
        if (!userToken.trim().startsWith("user_token_"))
          found.userToken = "User tokens start with user_token_";
        setErrors(found);
        if (Object.keys(found).length) return;
        submit.mutate(
          { appToken: appToken.trim(), userToken: userToken.trim() },
          {
            onSuccess: () => {
              toast(connectionId ? "Tokens updated" : "Connected to Akahu");
              setAppToken("");
              setUserToken("");
            },
            onError: (error) => {
              if (error instanceof ApiError && error.errors.length)
                setErrors(Object.fromEntries(error.errors.map((e) => [e.field, e.message])));
              else toast(error.message, "error");
            },
          },
        );
      }}
    >
      <Field label="App token" error={errors.appToken}>
        {(props) => (
          <Input
            {...props}
            type={visible ? "text" : "password"}
            value={appToken}
            onChange={(event) => setAppToken(event.target.value)}
            placeholder="app_token_..."
            autoComplete="off"
            spellCheck={false}
          />
        )}
      </Field>
      <Field label="User token" error={errors.userToken}>
        {(props) => (
          <Input
            {...props}
            type={visible ? "text" : "password"}
            value={userToken}
            onChange={(event) => setUserToken(event.target.value)}
            placeholder="user_token_..."
            autoComplete="off"
            spellCheck={false}
          />
        )}
      </Field>
      <div className="flex flex-wrap items-center gap-2">
        <Button type="submit" disabled={submit.isPending}>
          <Lock className="h-4 w-4" aria-hidden /> {submit.isPending ? "Checking with Akahu" : label}
        </Button>
        <Button type="button" variant="ghost" size="sm" onClick={() => setVisible(!visible)}>
          {visible ? <EyeOff className="h-4 w-4" aria-hidden /> : <Eye className="h-4 w-4" aria-hidden />}
          {visible ? "Hide" : "Show"} tokens
        </Button>
      </div>
      <p className="text-xs text-muted">
        Tokens are encrypted before they&apos;re stored and are never shown again.
      </p>
    </form>
  );
}

function OAuthButton({ label }: { label: string }) {
  const toast = useToast();
  const [pending, setPending] = useState(false);
  return (
    <Button
      className="mt-3"
      disabled={pending}
      onClick={async () => {
        setPending(true);
        try {
          const start = await api.post<OAuthStart>("/bank-feeds/akahu/oauth/authorisations");
          window.location.assign(start.authorisationUrl);
        } catch (error) {
          toast(error instanceof Error ? error.message : "We couldn't reach Akahu.", "error");
          setPending(false);
        }
      }}
    >
      {pending ? "Opening Akahu" : label} <ExternalLink className="h-4 w-4" aria-hidden />
    </Button>
  );
}

function SyncButton({
  connectionId,
  label,
  size = "sm",
}: {
  connectionId: string;
  label: string;
  size?: "sm" | "md";
}) {
  const toast = useToast();
  const sync = useApiMutation(() => api.post<BankSyncRun>(`/bank-feeds/connections/${connectionId}/syncs`));
  return (
    <Button
      size={size}
      className={size === "sm" ? "mt-3" : undefined}
      disabled={sync.isPending}
      onClick={() =>
        sync.mutate(undefined, {
          onSuccess: () => toast("Sync started. New transactions will appear shortly."),
          onError: (error) => toast(error.message, "error"),
        })
      }
    >
      <RefreshCw className={cn("h-4 w-4", sync.isPending && "animate-spin")} aria-hidden /> {label}
    </Button>
  );
}

function ConnectionCard({ connection }: { connection: BankConnection }) {
  const toast = useToast();
  const [confirming, setConfirming] = useState(false);
  const disconnect = useApiMutation(() => api.delete(`/bank-feeds/connections/${connection.id}`));
  const latest = connection.latestSync;
  const running = latest?.status === "RUNNING";

  return (
    <Card id="accounts">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex items-center gap-3">
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-sky-soft text-sky">
            <ShieldCheck className="h-6 w-6" aria-hidden />
          </span>
          <div>
            <p className="text-lg font-semibold">
              Akahu {connection.method === "PERSONAL_APP" ? "personal app" : "connection"}
            </p>
            <p className="text-sm text-muted">
              {connection.status === "REAUTH_REQUIRED"
                ? "Akahu needs you to reconnect"
                : running
                  ? "Syncing now"
                  : `Last synced ${formatRelative(connection.lastSyncedAt)}`}
            </p>
          </div>
        </div>
        <div className="flex gap-2">
          <SyncButton connectionId={connection.id} label="Sync now" size="md" />
          <Button variant="ghost" onClick={() => setConfirming(true)} aria-label="Disconnect">
            <Unplug className="h-4 w-4" aria-hidden />
          </Button>
        </div>
      </div>

      {latest ? <SyncSummary run={latest} /> : null}

      <h3 className="mb-2 mt-5 text-sm font-semibold uppercase tracking-wider text-muted">Accounts</h3>
      <ul className="space-y-2">
        {connection.accounts.map((account) => (
          <FeedAccountRow key={account.id} connectionId={connection.id} account={account} />
        ))}
      </ul>

      <Dialog open={confirming} onOpenChange={setConfirming}>
        <DialogContent
          title="Disconnect your bank?"
          description="We'll stop syncing and ask Akahu to revoke access. Transactions already brought in stay."
        >
          <div className="flex justify-end gap-2">
            <DialogClose asChild>
              <Button variant="secondary">Stay connected</Button>
            </DialogClose>
            <Button
              variant="danger"
              onClick={() =>
                disconnect.mutate(undefined, {
                  onSuccess: () => {
                    setConfirming(false);
                    toast("Disconnected");
                  },
                  onError: (error) => toast(error.message, "error"),
                })
              }
            >
              Disconnect
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </Card>
  );
}

function SyncSummary({ run }: { run: BankSyncRun }) {
  if (run.status === "FAILED") {
    return (
      <p className="mt-4 flex gap-2 rounded-2xl bg-danger-soft p-3 text-sm font-semibold text-danger">
        <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
        The last sync didn&apos;t finish. {run.errorMessage}
      </p>
    );
  }
  if (run.status === "RUNNING") {
    return (
      <p className="mt-4 flex items-center gap-2 rounded-2xl bg-sky-soft p-3 text-sm font-semibold text-sky">
        <RefreshCw className="h-4 w-4 animate-spin" aria-hidden /> Fetching your transactions from Akahu
      </p>
    );
  }
  return (
    <p className="mt-4 rounded-2xl bg-surface-2 p-3 text-sm text-ink-2">
      Last sync checked {run.accountsSynced} account{run.accountsSynced === 1 ? "" : "s"}:{" "}
      {run.transactionsCreated} new and {run.transactionsUpdated} updated transaction
      {run.transactionsUpdated === 1 ? "" : "s"}.
    </p>
  );
}

function FeedAccountRow({ connectionId, account }: { connectionId: string; account: BankFeedAccount }) {
  const toast = useToast();
  const update = useApiMutation((syncEnabled: boolean) =>
    api.patch<BankFeedAccount>(`/bank-feeds/connections/${connectionId}/accounts/${account.id}`, {
      syncEnabled,
    }),
  );
  return (
    <li className="flex items-center justify-between gap-3 rounded-2xl bg-surface-2 p-3">
      <div className="min-w-0">
        <p className="truncate font-semibold">{account.name}</p>
        <p className="truncate text-xs text-muted">
          {[account.institution, account.maskedNumber].filter(Boolean).join(" · ")}
          {account.balance ? ` · ${formatMoney(account.balance)}` : ""}
          {!account.supportsTransactions ? " · Balance only" : ""}
        </p>
      </div>
      <Switch
        checked={account.syncEnabled}
        aria-label={`Sync ${account.name}`}
        onCheckedChange={(checked) =>
          update.mutate(checked, {
            onSuccess: () => toast(checked ? `Syncing ${account.name}` : `Stopped syncing ${account.name}`),
            onError: (error) => toast(error.message, "error"),
          })
        }
      />
    </li>
  );
}
