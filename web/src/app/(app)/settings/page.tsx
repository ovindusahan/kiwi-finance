import type { Metadata } from "next";
import { Suspense } from "react";
import { SettingsIndex } from "@/features/settings/settings-index";

export const metadata: Metadata = { title: "Settings" };

export default function Page() {
  return (
    <Suspense>
      <SettingsIndex />
    </Suspense>
  );
}
