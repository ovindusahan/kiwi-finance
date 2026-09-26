import { cn } from "@/lib/cn";

type Tone = "neutral" | "brand" | "good" | "warm" | "sky" | "lilac" | "gold" | "danger";

const tones: Record<Tone, string> = {
  neutral: "bg-surface-2 text-ink-2",
  brand: "bg-brand-soft text-brand",
  good: "bg-good-soft text-good",
  warm: "bg-warm-soft text-warm",
  sky: "bg-sky-soft text-sky",
  lilac: "bg-lilac-soft text-lilac",
  gold: "bg-gold-soft text-gold",
  danger: "bg-danger-soft text-danger",
};

export function Badge({
  tone = "neutral",
  className,
  ...props
}: React.HTMLAttributes<HTMLSpanElement> & { tone?: Tone }) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium leading-none",
        tones[tone],
        className,
      )}
      {...props}
    />
  );
}
