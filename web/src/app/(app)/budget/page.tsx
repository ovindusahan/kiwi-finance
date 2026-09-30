import type { Metadata } from "next";
import { Suspense } from "react";
import { BudgetPage } from "@/features/budget/budget-page";

export const metadata: Metadata = { title: "Budget" };

export default function Page() {
  return (
    <Suspense>
      <BudgetPage />
    </Suspense>
  );
}
