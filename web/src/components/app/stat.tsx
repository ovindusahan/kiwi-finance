import { cn } from "@/lib/cn";

export function Stat({
  label,
  value,
  hint,
  tone,
  className,
}: {
  label: string;
  value: React.ReactNode;
  hint?: React.ReactNode;
  tone?: "brand" | "warm" | "sky" | "danger";
  className?: string;
}) {
  const colour =
    tone === "brand"
      ? "text-good"
      : tone === "warm"
        ? "text-warm"
        : tone === "sky"
          ? "text-brand"
          : tone === "danger"
            ? "text-danger"
            : "";
  return (
    <div className={cn("rounded-2xl border border-line bg-surface p-4 shadow-card", className)}>
      <p className="text-sm text-muted">{label}</p>
      <p className={cn("mt-1 text-2xl font-semibold tabular", colour)}>{value}</p>
      {hint ? <p className="mt-0.5 text-sm text-muted">{hint}</p> : null}
    </div>
  );
}
