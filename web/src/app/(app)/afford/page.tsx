import type { Metadata } from "next";
import { Suspense } from "react";
import { AffordPage } from "@/features/afford/afford-page";

export const metadata: Metadata = { title: "Can I afford it?" };

export default function Page() {
  return (
    <Suspense>
      <AffordPage />
    </Suspense>
  );
}
