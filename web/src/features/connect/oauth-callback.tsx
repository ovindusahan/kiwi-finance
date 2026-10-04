"use client";

import { CircleAlert, Plug } from "lucide-react";
import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef } from "react";
import { buttonVariants } from "@/components/ui/button";
import { api } from "@/lib/api/client";
import type { BankConnection } from "@/lib/api/types";

/** Finishes connecting after Akahu sends the person back with an authorisation code. */
export function OAuthCallback() {
  const params = useSearchParams();
  const router = useRouter();
  const started = useRef(false);
  const code = params.get("code");
  const state = params.get("state");
  const denied = params.get("error");
  const exchange = useMutation({
    mutationFn: () => api.post<BankConnection>("/bank-feeds/akahu/oauth/callback", { code, state }),
    onSuccess: () => router.replace("/connect?connected=1"),
  });

  useEffect(() => {
    if (started.current || !code || !state || denied) return;
    started.current = true;
    exchange.mutate();
  }, [code, state, denied, exchange]);

  const failed = denied || !code || !state || exchange.isError;
  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-4 py-16 text-center">
      <span
        className={`grid h-14 w-14 place-items-center rounded-full ${failed ? "bg-danger-soft text-danger" : "bg-brand-soft text-brand"}`}
      >
        {failed ? <CircleAlert className="h-7 w-7" aria-hidden /> : <Plug className="h-7 w-7" aria-hidden />}
      </span>
      <h1 className="text-2xl font-semibold">
        {failed ? "We couldn't finish connecting" : "Connecting your bank"}
      </h1>
      <p className="text-ink-2">
        {denied
          ? "Akahu didn't share access. You can try again whenever you're ready."
          : exchange.isError
            ? exchange.error.message
            : !code || !state
              ? "This link is missing details from Akahu. Start again from the connect page."
              : "Hang tight while we confirm everything with Akahu."}
      </p>
      {failed ? (
        <Link href="/connect" className={buttonVariants()}>
          Back to connect
        </Link>
      ) : null}
    </div>
  );
}
