import type { Metadata } from "next";
import { Suspense } from "react";
import { SecurityPage } from "@/features/settings/security-page";

export const metadata: Metadata = { title: "Your account" };

export default function Page() {
  return (
    <Suspense>
      <SecurityPage />
    </Suspense>
  );
}
