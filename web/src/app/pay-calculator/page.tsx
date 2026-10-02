import type { Metadata } from "next";
import Link from "next/link";
import { PageHeader } from "@/components/app/page-header";
import { PayCalculator } from "@/features/pay-calculator/pay-calculator";

export const metadata: Metadata = {
  title: "NZ pay calculator",
  description:
    "Work out your take-home pay after PAYE, ACC, KiwiSaver, student loan and other repayments, with this year's IRD rates.",
};

export default function PayCalculatorPage() {
  return (
    <div className="min-h-dvh">
      <header className="bg-navy text-on-navy">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-4 px-4 sm:px-6">
          <Link href="/" className="text-xl font-bold tracking-tight text-white">
            Kiwi Finance
          </Link>
          <Link
            href="/sign-in"
            className="ml-auto inline-flex h-8 items-center rounded-lg border border-white px-3 text-sm font-semibold uppercase tracking-wide text-white hover:bg-white hover:text-navy"
          >
            Log on
          </Link>
        </div>
        <div className="h-3 bg-navy-2" aria-hidden />
      </header>
      <main className="mx-auto w-full max-w-6xl space-y-6 px-4 pb-16 pt-6 sm:px-6 lg:pt-8">
        <PageHeader
          eyebrow="Pay calculator"
          title="What will I actually take home?"
          description="PAYE, ACC, KiwiSaver, student loan and your other repayments, worked out with this tax year's rates. Free, and no sign-up needed."
        />
        <PayCalculator />
      </main>
    </div>
  );
}
