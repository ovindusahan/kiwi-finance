import type { Metadata } from "next";
import { PayPage } from "@/features/pay-calculator/pay-page";

export const metadata: Metadata = { title: "Pay calculator" };

export default function Page() {
  return <PayPage />;
}
