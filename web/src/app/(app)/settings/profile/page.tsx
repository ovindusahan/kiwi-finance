import type { Metadata } from "next";
import { Suspense } from "react";
import { ProfilePage } from "@/features/settings/profile-page";

export const metadata: Metadata = { title: "Pay and tax" };

export default function Page() {
  return (
    <Suspense>
      <ProfilePage />
    </Suspense>
  );
}
