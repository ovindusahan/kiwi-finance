import type { Money as MoneyValue } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { formatMoney } from "@/lib/format";

/** Shows an amount in NZD. Incoming money is green when {@code colored} is set. */
export function Money({
  value,
  whole,
  signed,
  colored,
  className,
}: {
  value: MoneyValue | number | null | undefined;
  whole?: boolean;
  signed?: boolean;
  colored?: boolean;
  className?: string;
}) {
  const cents = value == null ? 0 : typeof value === "number" ? value : value.cents;
  return (
    <span className={cn("tabular", colored && cents > 0 && "text-good", className)}>
      {formatMoney(cents, { whole, signed })}
    </span>
  );
}
