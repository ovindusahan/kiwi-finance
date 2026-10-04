import type { Metadata } from "next";
import { GuidePage } from "@/features/learn/learn-pages";

export const metadata: Metadata = { title: "Learn" };

export default async function Page({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  return <GuidePage slug={slug} />;
}
