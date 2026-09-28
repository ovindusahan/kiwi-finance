"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Field, Input } from "@/components/ui/field";

type Mode = "sign-in" | "sign-up";
type Problem = { detail?: string; errors?: { field: string; message: string }[] };

const showDemo = process.env.NEXT_PUBLIC_SHOW_DEMO_ACCOUNT === "true";

export function AuthForm({ mode }: { mode: Mode }) {
  const router = useRouter();
  const params = useSearchParams();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  async function submit(form: FormData) {
    setPending(true);
    setError(null);
    setFieldErrors({});
    const body =
      mode === "sign-up"
        ? { displayName: form.get("displayName"), email: form.get("email"), password: form.get("password") }
        : { email: form.get("email"), password: form.get("password") };
    const response = await fetch(`/api/auth/${mode === "sign-up" ? "register" : "login"}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (response.ok) {
      const next = params.get("next");
      router.replace(mode === "sign-up" ? "/welcome" : next && next.startsWith("/") ? next : "/home");
      router.refresh();
      return;
    }
    const problem = (await response.json().catch(() => ({}))) as Problem;
    setFieldErrors(Object.fromEntries((problem.errors ?? []).map((e) => [e.field, e.message])));
    setError(problem.errors?.length ? null : (problem.detail ?? "Something went wrong. Please try again."));
    setPending(false);
  }

  return (
    <Card className="p-6 sm:p-8">
      <h1 className="text-3xl font-semibold">
        {mode === "sign-up" ? "Create your account" : "Welcome back"}
      </h1>
      <p className="mt-2 text-ink-2">
        {mode === "sign-up"
          ? "Takes a minute. Your data stays yours, always."
          : "Sign in to see where your money is going."}
      </p>
      <form action={submit} className="mt-6 space-y-4" noValidate>
        {mode === "sign-up" ? (
          <Field label="What should we call you?" error={fieldErrors.displayName}>
            {(props) => (
              <Input {...props} name="displayName" autoComplete="given-name" required maxLength={100} />
            )}
          </Field>
        ) : null}
        <Field label="Email" error={fieldErrors.email}>
          {(props) => <Input {...props} name="email" type="email" autoComplete="email" required />}
        </Field>
        <Field
          label="Password"
          error={fieldErrors.password}
          hint={mode === "sign-up" ? "At least 12 characters. A short sentence works well." : undefined}
        >
          {(props) => (
            <Input
              {...props}
              name="password"
              type="password"
              autoComplete={mode === "sign-up" ? "new-password" : "current-password"}
              required
              minLength={mode === "sign-up" ? 12 : undefined}
            />
          )}
        </Field>
        {error ? (
          <p role="alert" className="rounded-2xl bg-danger-soft px-4 py-3 text-sm font-semibold text-danger">
            {error}
          </p>
        ) : null}
        <Button type="submit" size="lg" block disabled={pending}>
          {pending ? "One moment…" : mode === "sign-up" ? "Create account" : "Sign in"}
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-ink-2">
        {mode === "sign-up" ? (
          <>
            Already have an account?{" "}
            <Link href="/sign-in" className="font-semibold text-sky hover:underline">
              Sign in
            </Link>
          </>
        ) : (
          <>
            New here?{" "}
            <Link href="/sign-up" className="font-semibold text-sky hover:underline">
              Create an account
            </Link>
          </>
        )}
      </p>
      {showDemo && mode === "sign-in" ? (
        <p className="mt-4 rounded-2xl bg-surface-2 p-3 text-center text-sm text-ink-2">
          Exploring? Sign in as <span className="font-semibold text-ink">demo@kiwifinance.nz</span> with
          password <span className="font-semibold text-ink">kiwi-demo-2026</span>.
        </p>
      ) : null}
    </Card>
  );
}
