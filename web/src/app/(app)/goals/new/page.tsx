import type { Metadata } from "next";
import { Suspense } from "react";
import { NewGoalPage } from "@/features/goals/new-goal-page";

export const metadata: Metadata = { title: "New goal" };

export default function Page() {
  return (
    <Suspense>
      <NewGoalPage />
    </Suspense>
  );
}
