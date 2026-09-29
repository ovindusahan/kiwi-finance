import type { Metadata } from "next";
import { Suspense } from "react";
import { TransactionsPage } from "@/features/transactions/transactions-page";

export const metadata: Metadata = { title: "Transactions" };

export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ search?: string; accountId?: string }>;
}) {
  const { search = "", accountId } = await searchParams;
  return (
    <Suspense>
      <TransactionsPage
        key={`${search}|${accountId ?? ""}`}
        initialSearch={search}
        initialAccountId={accountId}
      />
    </Suspense>
  );
}
