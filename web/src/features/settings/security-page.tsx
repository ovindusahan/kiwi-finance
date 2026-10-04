"use client";

import { Download, LogOut, TriangleAlert } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { Dialog, DialogClose, DialogContent } from "@/components/ui/dialog";
import { Field, Input } from "@/components/ui/field";
import { useToast } from "@/components/ui/toast";
import { api, ApiError } from "@/lib/api/client";
import { useApiMutation, useMe } from "@/lib/api/queries";
import type { AuthenticatedUser, DataExport, UpdateAccountRequest } from "@/lib/api/types";
import { formatDate } from "@/lib/format";
import { SettingsFrame } from "./settings-nav";

export function SecurityPage() {
  const router = useRouter();
  const { data: me } = useMe();
  const [deleting, setDeleting] = useState(false);
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string>();
  const [pending, setPending] = useState(false);

  async function signOut() {
    await fetch("/api/auth/logout", { method: "POST" });
    router.replace("/sign-in");
    router.refresh();
  }

  async function deleteAccount() {
    setPending(true);
    setError(undefined);
    try {
      await api.delete("/auth/me", { password });
      await fetch("/api/auth/logout", { method: "POST" });
      router.replace("/");
      router.refresh();
    } catch (failure) {
      setError(
        failure instanceof ApiError && failure.status === 401
          ? "That password isn't right."
          : failure instanceof Error
            ? failure.message
            : "Something went wrong.",
      );
      setPending(false);
    }
  }

  return (
    <SettingsFrame title="Your account" description="Your name, sign-in details and your data.">
      <div className="space-y-4">
        {me ? (
          <section className="flex flex-wrap items-center gap-4 rounded-2xl border border-line bg-surface p-5">
            <span
              aria-hidden
              className="grid h-14 w-14 shrink-0 place-items-center rounded-full bg-brand text-xl font-semibold text-on-brand"
            >
              {me.displayName.trim().charAt(0).toUpperCase()}
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-lg font-semibold">{me.displayName}</p>
              <p className="truncate text-ink-2">{me.email}</p>
              <p className="text-sm text-muted">Member since {formatDate(me.createdAt)}</p>
            </div>
            <Button variant="secondary" onClick={signOut}>
              <LogOut className="h-4 w-4" aria-hidden /> Sign out
            </Button>
          </section>
        ) : null}
        {me ? <DetailsForm key={`${me.displayName}-${me.email}`} me={me} /> : null}
        <PasswordForm />
        <DownloadData />
        <Card className="border-danger/40">
          <CardHeader
            title="Delete your account"
            description="Removes your profile, transactions, goals and plans, and revokes Kiwi Finance's access in Akahu."
          />
          <Button variant="danger" onClick={() => setDeleting(true)}>
            Delete my account
          </Button>
        </Card>
      </div>

      <Dialog open={deleting} onOpenChange={setDeleting}>
        <DialogContent title="Delete your account?" description="This can't be undone.">
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault();
              void deleteAccount();
            }}
          >
            <p className="flex gap-2 rounded-2xl bg-danger-soft p-3 text-sm font-semibold text-danger">
              <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              Everything in Kiwi Finance is deleted straight away. Your bank accounts aren&apos;t affected.
            </p>
            <Field label="Enter your password to confirm" error={error}>
              {(props) => (
                <Input
                  {...props}
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  autoComplete="current-password"
                />
              )}
            </Field>
            <div className="flex justify-end gap-2">
              <DialogClose asChild>
                <Button variant="secondary">Cancel</Button>
              </DialogClose>
              <Button type="submit" variant="danger" disabled={!password || pending}>
                Delete everything
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </SettingsFrame>
  );
}

const fieldError = (failure: unknown, field: string) =>
  failure instanceof ApiError ? failure.fieldError(field) : undefined;

function DetailsForm({ me }: { me: AuthenticatedUser }) {
  const toast = useToast();
  const client = useQueryClient();
  const [name, setName] = useState(me.displayName);
  const [email, setEmail] = useState(me.email);
  const [password, setPassword] = useState("");
  const emailChanged = email.trim().toLowerCase() !== me.email;
  const save = useApiMutation((request: UpdateAccountRequest) =>
    api.patch<AuthenticatedUser>("/auth/me", request),
  );
  const failure = save.error;
  const wrongPassword = failure instanceof ApiError && failure.code === "wrong_password";
  const taken = failure instanceof ApiError && failure.code === "email_already_registered";

  return (
    <Card>
      <CardHeader title="Your details" description="How we greet you, and the email you sign in with." />
      <form
        className="grid gap-4 sm:grid-cols-2"
        onSubmit={(event) => {
          event.preventDefault();
          save.mutate(
            {
              displayName: name.trim(),
              ...(emailChanged ? { email: email.trim(), currentPassword: password } : {}),
            },
            {
              onSuccess: (updated) => {
                setPassword("");
                client.setQueryData(["me"], updated);
                toast("Your details are saved");
              },
            },
          );
        }}
      >
        <Field label="Your name" error={fieldError(failure, "displayName")}>
          {(props) => (
            <Input
              {...props}
              value={name}
              onChange={(event) => setName(event.target.value)}
              autoComplete="name"
              required
              maxLength={100}
            />
          )}
        </Field>
        <Field
          label="Email"
          error={taken ? "Another account already uses this email." : fieldError(failure, "email")}
        >
          {(props) => (
            <Input
              {...props}
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
              required
            />
          )}
        </Field>
        {emailChanged ? (
          <Field
            label="Your password, to confirm the new email"
            error={wrongPassword ? "That password isn't right." : undefined}
            className="sm:col-span-2"
          >
            {(props) => (
              <Input
                {...props}
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="current-password"
                required
              />
            )}
          </Field>
        ) : null}
        <div className="sm:col-span-2">
          <Button
            type="submit"
            disabled={save.isPending || !name.trim() || (name.trim() === me.displayName && !emailChanged)}
          >
            Save details
          </Button>
        </div>
      </form>
    </Card>
  );
}

function PasswordForm() {
  const toast = useToast();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<{ field: "current" | "next" | "confirm"; message: string }>();
  const [pending, setPending] = useState(false);

  async function change(event: React.FormEvent) {
    event.preventDefault();
    setError(undefined);
    if (next.length < 12) {
      setError({ field: "next", message: "Use at least 12 characters." });
      return;
    }
    if (next !== confirm) {
      setError({ field: "confirm", message: "The passwords don't match." });
      return;
    }
    setPending(true);
    try {
      const response = await fetch("/api/auth/password", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ currentPassword: current, newPassword: next }),
      });
      if (!response.ok) {
        const problem = (await response.json().catch(() => ({}))) as { code?: string; detail?: string };
        setError(
          problem.code === "wrong_password"
            ? { field: "current", message: "That password isn't right." }
            : { field: "next", message: problem.detail ?? "We couldn't change your password." },
        );
        return;
      }
      setCurrent("");
      setNext("");
      setConfirm("");
      toast("Password changed. You've been signed out everywhere else.");
    } finally {
      setPending(false);
    }
  }

  return (
    <Card>
      <CardHeader title="Password" description="Changing it signs you out on every other device." />
      <form className="grid gap-4 sm:grid-cols-3" onSubmit={change}>
        <Field label="Current password" error={error?.field === "current" ? error.message : undefined}>
          {(props) => (
            <Input
              {...props}
              type="password"
              value={current}
              onChange={(event) => setCurrent(event.target.value)}
              autoComplete="current-password"
              required
            />
          )}
        </Field>
        <Field
          label="New password"
          hint="At least 12 characters"
          error={error?.field === "next" ? error.message : undefined}
        >
          {(props) => (
            <Input
              {...props}
              type="password"
              value={next}
              onChange={(event) => setNext(event.target.value)}
              autoComplete="new-password"
              required
            />
          )}
        </Field>
        <Field label="New password again" error={error?.field === "confirm" ? error.message : undefined}>
          {(props) => (
            <Input
              {...props}
              type="password"
              value={confirm}
              onChange={(event) => setConfirm(event.target.value)}
              autoComplete="new-password"
              required
            />
          )}
        </Field>
        <div className="sm:col-span-3">
          <Button type="submit" variant="secondary" disabled={pending || !current || !next || !confirm}>
            Change password
          </Button>
        </div>
      </form>
    </Card>
  );
}

function DownloadData() {
  const toast = useToast();
  const [pending, setPending] = useState(false);

  async function download() {
    setPending(true);
    try {
      const data = await api.get<DataExport>("/auth/me/export");
      const url = URL.createObjectURL(
        new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }),
      );
      const link = document.createElement("a");
      link.href = url;
      link.download = `kiwi-finance-data-${data.exportedAt.slice(0, 10)}.json`;
      link.click();
      URL.revokeObjectURL(url);
      toast("Your data is downloading");
    } catch (error) {
      toast(error instanceof Error ? error.message : "We couldn't prepare your data.", "error");
    } finally {
      setPending(false);
    }
  }

  return (
    <Card>
      <CardHeader
        title="Download your data"
        description="Everything Kiwi Finance stores about you, as a JSON file. Passwords and bank tokens are never included."
      />
      <Button variant="secondary" onClick={download} disabled={pending}>
        <Download className="h-4 w-4" aria-hidden /> {pending ? "Preparing" : "Download my data"}
      </Button>
    </Card>
  );
}
