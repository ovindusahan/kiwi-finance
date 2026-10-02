import type { Metadata } from "next";
import { Suspense } from "react";
import { LearnPage } from "@/features/learn/learn-pages";

export const metadata: Metadata = { title: "Learn" };

export default function Page() {
  return (
    <Suspense>
      <LearnPage />
    </Suspense>
  );
}
