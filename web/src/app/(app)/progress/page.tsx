import type { Metadata } from "next";
import { Suspense } from "react";
import { ProgressPage } from "@/features/progress/progress-page";

export const metadata: Metadata = { title: "Progress" };

export default function Page() {
  return (
    <Suspense>
      <ProgressPage />
    </Suspense>
  );
}
