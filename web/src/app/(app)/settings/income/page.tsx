import type { Metadata } from "next";
import { Suspense } from "react";
import { IncomePage } from "@/features/settings/income-page";

export const metadata: Metadata = { title: "Income" };

export default function Page() {
  return (
    <Suspense>
      <IncomePage />
    </Suspense>
  );
}
