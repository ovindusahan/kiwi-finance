import Link from "next/link";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center px-4 py-10">
      <Link href="/" className="mb-8">
        <span className="text-2xl font-bold text-navy">Kiwi Finance</span>
      </Link>
      <div className="w-full max-w-md">{children}</div>
    </div>
  );
}
