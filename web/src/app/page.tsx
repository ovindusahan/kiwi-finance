import { ArrowRight, Calculator, ChartPie, Flame, Plug, ShieldCheck, Sparkles, Target } from "lucide-react";
import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/cn";

const features = [
  {
    icon: Plug,
    title: "Connects to your bank",
    body: "Bring in transactions from ANZ, ASB, BNZ, Kiwibank, Westpac and more through Akahu. Read-only, always.",
    tone: "bg-sky-soft text-sky",
  },
  {
    icon: ChartPie,
    title: "See where it all goes",
    body: "Spending by category, the bills that come round every month, and the habits that quietly add up.",
    tone: "bg-lilac-soft text-lilac",
  },
  {
    icon: Sparkles,
    title: "Can I afford it?",
    body: "Ask about a car, a trip or a laptop. Get a straight answer, a realistic date and what would get you there sooner.",
    tone: "bg-warm-soft text-warm",
  },
  {
    icon: ShieldCheck,
    title: "A real safety net",
    body: "An emergency fund sized from your own essential costs, with milestones that make it feel achievable.",
    tone: "bg-brand-soft text-brand",
  },
  {
    icon: Target,
    title: "Goals with a countdown",
    body: "Track every goal with the monthly amount it needs and the date you'll actually get there.",
    tone: "bg-gold-soft text-gold",
  },
  {
    icon: Flame,
    title: "Habits that stick",
    body: "Streaks, achievements and your Kiwi Score turn good money habits into something you want to keep up.",
    tone: "bg-warm-soft text-warm",
  },
];

const nzRules = [
  "PAYE and tax codes",
  "ACC earners' levy",
  "KiwiSaver and ESCT",
  "Student loan repayments",
  "First-home withdrawals",
  "NZD everywhere",
];

export default function LandingPage() {
  return (
    <div className="mx-auto max-w-6xl overflow-x-clip px-4 sm:px-6">
      <header className="flex items-center justify-between py-6">
        <Link href="/">
          <span className="text-xl font-bold text-navy">Kiwi Finance</span>
        </Link>
        <nav className="flex items-center gap-2">
          <Link href="/sign-in" className={cn(buttonVariants({ variant: "ghost", size: "sm" }))}>
            Sign in
          </Link>
          <Link href="/sign-up" className={cn(buttonVariants({ size: "sm" }))}>
            Get started
          </Link>
        </nav>
      </header>

      <section className="grid items-center gap-10 py-10 md:grid-cols-[1.2fr_1fr] md:py-20">
        <div>
          <p className="mb-4 inline-flex rounded-sm bg-brand-soft px-3 py-1 text-sm font-semibold text-brand">
            Made for Aotearoa New Zealand
          </p>
          <h1 className="text-5xl font-semibold leading-[1.02] sm:text-6xl lg:text-7xl">
            Money, sorted.
            <br />
            <span className="text-warm">The Kiwi way.</span>
          </h1>
          <p className="mt-6 max-w-xl text-lg text-ink-2">
            Kiwi Finance learns from your real spending, builds a budget you can actually keep, and gives you
            honest answers to the question everyone asks: can I afford this?
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link href="/sign-up" className={cn(buttonVariants({ size: "lg" }))}>
              Start for free <ArrowRight className="h-5 w-5" aria-hidden />
            </Link>
            <Link href="/pay-calculator" className={cn(buttonVariants({ variant: "secondary", size: "lg" }))}>
              <Calculator className="h-5 w-5" aria-hidden /> Pay calculator
            </Link>
          </div>
        </div>
        <div className="relative">
          <div className="absolute -inset-6 rounded-2xl bg-[radial-gradient(circle_at_30%_20%,var(--brand-soft),transparent_60%),radial-gradient(circle_at_80%_80%,var(--warm-soft),transparent_55%)]" />
          <div className="relative rounded-2xl border border-line bg-surface p-6 shadow-card">
            <div className="flex items-center justify-between">
              <p className="font-semibold text-muted">Can I afford a trip to Japan?</p>
              <span className="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-brand-soft text-brand">
                <Sparkles className="h-6 w-6" aria-hidden />
              </span>
            </div>
            <p className="mt-3 font-display text-3xl font-semibold leading-tight">
              Yes, by <span className="text-brand">July 2027</span> if you put aside{" "}
              <span className="text-warm">$185</span> a fortnight.
            </p>
            <div className="mt-5 grid grid-cols-3 gap-2 text-center">
              {[
                ["Saved", "$1,200"],
                ["To go", "$4,800"],
                ["Days left", "273"],
              ].map(([label, value]) => (
                <div key={label} className="rounded-2xl bg-surface-2 p-3">
                  <p className="text-xs font-semibold text-muted">{label}</p>
                  <p className="font-display text-xl font-semibold tabular">{value}</p>
                </div>
              ))}
            </div>
            <div className="mt-4 h-3.5 overflow-hidden rounded-full bg-surface-3">
              <div className="h-full w-1/5 rounded-full bg-brand" />
            </div>
          </div>
        </div>
      </section>

      <section className="py-10">
        <h2 className="text-3xl font-semibold sm:text-4xl">
          Everything your money needs, nothing it doesn&apos;t
        </h2>
        <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {features.map(({ icon: Icon, title, body, tone }) => (
            <div key={title} className="rounded-2xl border border-line bg-surface p-6 shadow-card">
              <span className={cn("grid h-12 w-12 place-items-center rounded-2xl", tone)}>
                <Icon className="h-6 w-6" aria-hidden />
              </span>
              <h3 className="mt-4 text-xl font-semibold">{title}</h3>
              <p className="mt-2 text-ink-2">{body}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="my-10 rounded-2xl bg-[linear-gradient(135deg,var(--brand),color-mix(in_oklab,var(--brand),var(--sky)_45%))] p-8 text-on-brand sm:p-12">
        <h2 className="text-3xl font-semibold sm:text-4xl">Built on New Zealand rules</h2>
        <p className="mt-3 max-w-2xl text-lg opacity-90">
          Every number uses the tax year it belongs to and tells you exactly how it was worked out.
        </p>
        <ul className="mt-6 flex flex-wrap gap-2">
          {nzRules.map((rule) => (
            <li key={rule} className="rounded-sm bg-black/15 px-4 py-2 font-semibold">
              {rule}
            </li>
          ))}
        </ul>
      </section>

      <footer className="flex flex-col gap-2 border-t border-line py-8 text-sm text-muted sm:flex-row sm:justify-between">
        <p>Kiwi Finance gives general information, not financial advice.</p>
        <p>Made in Aotearoa.</p>
      </footer>
    </div>
  );
}
