import type { Metadata } from "next";
import { Suspense } from "react";
import { HomePage } from "@/features/home/home-page";

export const metadata: Metadata = { title: "Home" };

export default function Page() {
  return (
    <Suspense>
      <HomePage />
    </Suspense>
  );
}
