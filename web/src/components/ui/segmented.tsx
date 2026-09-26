import { cn } from "@/lib/cn";

/** A row of mutually exclusive options, for switching a view rather than navigating between panels. */
export function Segmented<T extends string>({
  label,
  value,
  options,
  onChange,
  className,
}: {
  label: string;
  value: T;
  options: { value: T; label: string }[];
  onChange: (value: T) => void;
  className?: string;
}) {
  return (
    <div
      role="group"
      aria-label={label}
      className={cn("inline-flex overflow-hidden rounded-lg border border-line bg-surface", className)}
    >
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          aria-pressed={option.value === value}
          onClick={() => onChange(option.value)}
          className={cn(
            "border-l border-line px-3 py-2 text-sm font-semibold transition-colors first:border-l-0",
            option.value === value ? "bg-brand text-on-brand" : "text-ink-2 hover:bg-surface-2",
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
