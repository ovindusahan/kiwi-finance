import type { Metadata } from "next";
import { Suspense } from "react";
import { EmergencyFundPage } from "@/features/emergency-fund/emergency-fund-page";

export const metadata: Metadata = { title: "Emergency fund" };

export default function Page() {
  return (
    <Suspense>
      <EmergencyFundPage />
    </Suspense>
  );
}
