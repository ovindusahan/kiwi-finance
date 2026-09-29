import type { Metadata } from "next";
import { Suspense } from "react";
import { ConnectPage } from "@/features/connect/connect-page";

export const metadata: Metadata = { title: "Connect your bank" };

export default function Page() {
  return (
    <Suspense>
      <ConnectPage />
    </Suspense>
  );
}
