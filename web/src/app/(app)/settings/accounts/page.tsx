import type { Metadata } from "next";
import { Suspense } from "react";
import { AccountsPage } from "@/features/settings/accounts-page";

export const metadata: Metadata = { title: "Accounts" };

export default function Page() {
  return (
    <Suspense>
      <AccountsPage />
    </Suspense>
  );
}
