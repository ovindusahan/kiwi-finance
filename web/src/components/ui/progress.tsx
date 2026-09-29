import { cn } from "@/lib/cn";

type Tone = "brand" | "good" | "warm" | "sky" | "lilac" | "gold" | "danger";

const fills: Record<Tone, string> = {
  brand: "bg-brand",
  good: "bg-good",
  warm: "bg-warm",
  sky: "bg-sky",
  lilac: "bg-lilac",
  gold: "bg-gold",
  danger: "bg-danger",
};

/** A chunky progress bar with a highlight stripe. Value is a fraction from 0 to 1. */
export function ProgressBar({
  value,
  tone = "brand",
  label,
  className,
  size = "md",
}: {
  value: number;
  tone?: Tone;
  label: string;
  className?: string;
  size?: "sm" | "md" | "lg";
}) {
  const percent = Math.max(0, Math.min(1, value)) * 100;
  const height = size === "sm" ? "h-1.5" : size === "lg" ? "h-3" : "h-2";
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(percent)}
      className={cn("relative w-full overflow-hidden rounded-full bg-surface-3", height, className)}
    >
      <div
        className={cn("h-full rounded-full transition-[width] duration-700 ease-out", fills[tone])}
        style={{ width: `${percent}%` }}
      />
    </div>
  );
}

/** A circular progress ring with content in the middle. */
export function ProgressRing({
  value,
  size = 120,
  stroke = 10,
  tone = "var(--brand)",
  track = "var(--surface-3)",
  label,
  children,
}: {
  value: number;
  size?: number;
  stroke?: number;
  tone?: string;
  track?: string;
  label: string;
  children?: React.ReactNode;
}) {
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const fraction = Math.max(0, Math.min(1, value));
  return (
    <div
      className="relative inline-grid place-items-center"
      style={{ width: size, height: size }}
      role="img"
      aria-label={label}
    >
      <svg width={size} height={size} className="-rotate-90" aria-hidden>
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke={track} strokeWidth={stroke} />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={tone}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - fraction)}
          style={{ transition: "stroke-dashoffset 900ms ease-out" }}
        />
      </svg>
      <div className="absolute inset-0 grid place-items-center text-center">{children}</div>
    </div>
  );
}
