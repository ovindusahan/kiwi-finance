import type { Metadata } from "next";
import { Suspense } from "react";
import { RulesPage } from "@/features/settings/rules-page";

export const metadata: Metadata = { title: "Categories and rules" };

export default function Page() {
  return (
    <Suspense>
      <RulesPage />
    </Suspense>
  );
}
