import type { Metadata } from "next";
import { Suspense } from "react";
import { SpendingPage } from "@/features/spending/spending-page";

export const metadata: Metadata = { title: "Spending" };

export default function Page() {
  return (
    <Suspense>
      <SpendingPage />
    </Suspense>
  );
}
