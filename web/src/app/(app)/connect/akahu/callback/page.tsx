import type { Metadata } from "next";
import { Suspense } from "react";
import { OAuthCallback } from "@/features/connect/oauth-callback";

export const metadata: Metadata = { title: "Connecting" };

export default function Page() {
  return (
    <Suspense>
      <OAuthCallback />
    </Suspense>
  );
}
