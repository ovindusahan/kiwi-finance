import type { Money } from "@/lib/api/types";

const NZD = new Intl.NumberFormat("en-NZ", { style: "currency", currency: "NZD" });
const NZD_WHOLE = new Intl.NumberFormat("en-NZ", {
  style: "currency",
  currency: "NZD",
  maximumFractionDigits: 0,
  minimumFractionDigits: 0,
});
const NZD_COMPACT = new Intl.NumberFormat("en-NZ", {
  style: "currency",
  currency: "NZD",
  notation: "compact",
  maximumFractionDigits: 1,
});

type Amount = Money | number | null | undefined;

function cents(amount: Amount): number {
  if (amount == null) return 0;
  return typeof amount === "number" ? amount : amount.cents;
}

/** Formats an amount of cents as NZD, e.g. $1,234.50. */
export function formatMoney(amount: Amount, options: { whole?: boolean; signed?: boolean } = {}): string {
  const value = cents(amount) / 100;
  const formatter = options.whole ? NZD_WHOLE : NZD;
  const text = formatter.format(Math.abs(value));
  if (value < 0) return `-${text}`;
  return options.signed && value > 0 ? `+${text}` : text;
}

export function formatMoneyCompact(amount: Amount): string {
  return NZD_COMPACT.format(cents(amount) / 100);
}

/** Formats a fraction such as 0.153 as 15%. */
export function formatPercent(fraction: number | null | undefined, digits = 0): string {
  if (fraction == null) return "";
  return `${(fraction * 100).toFixed(digits)}%`;
}

const DATE = new Intl.DateTimeFormat("en-NZ", { day: "numeric", month: "short", year: "numeric" });
const DAY_MONTH = new Intl.DateTimeFormat("en-NZ", { day: "numeric", month: "short" });
const MONTH_YEAR = new Intl.DateTimeFormat("en-NZ", { month: "long", year: "numeric" });
const MONTH_SHORT = new Intl.DateTimeFormat("en-NZ", { month: "short" });
const WEEKDAY = new Intl.DateTimeFormat("en-NZ", { weekday: "long", day: "numeric", month: "long" });

/** Parses an ISO date (YYYY-MM-DD) as a local calendar date, avoiding time zone shifts. */
export function parseDate(value: string): Date {
  const [year, month, day] = value.slice(0, 10).split("-").map(Number);
  return new Date(year ?? 1970, (month ?? 1) - 1, day ?? 1);
}

export function formatDate(value: string | null | undefined): string {
  return value ? DATE.format(parseDate(value)) : "";
}

export function formatDayMonth(value: string | null | undefined): string {
  return value ? DAY_MONTH.format(parseDate(value)) : "";
}

export function formatMonthYear(value: string | null | undefined): string {
  return value ? MONTH_YEAR.format(parseDate(value.length === 7 ? `${value}-01` : value)) : "";
}

export function formatMonthShort(value: string): string {
  return MONTH_SHORT.format(parseDate(value.length === 7 ? `${value}-01` : value));
}

export function formatLongDay(value: string): string {
  return WEEKDAY.format(parseDate(value));
}

/** Describes how long ago a timestamp was, e.g. "5 minutes ago". */
export function formatRelative(timestamp: string | null | undefined, now: Date = new Date()): string {
  if (!timestamp) return "never";
  const seconds = Math.round((now.getTime() - new Date(timestamp).getTime()) / 1000);
  if (seconds < 60) return "just now";
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} minute${minutes === 1 ? "" : "s"} ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} hour${hours === 1 ? "" : "s"} ago`;
  const days = Math.round(hours / 24);
  return `${days} day${days === 1 ? "" : "s"} ago`;
}

/** Converts a dollars string typed by a person into cents, or null if it isn't a valid amount. */
export function parseDollars(input: string): number | null {
  const cleaned = input.replace(/[$,\s]/g, "");
  if (!/^-?\d+(\.\d{0,2})?$/.test(cleaned)) return null;
  const [whole = "0", fraction = ""] = cleaned.replace("-", "").split(".");
  const value = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  return cleaned.startsWith("-") ? -value : value;
}

export function centsToDollarsInput(cents: number | null | undefined): string {
  if (cents == null) return "";
  return (cents / 100).toFixed(2).replace(/\.00$/, "");
}

export function greeting(now: Date = new Date()): string {
  const hour = now.getHours();
  if (hour < 12) return "Mōrena";
  if (hour < 18) return "Kia ora";
  return "Pō mārie";
}
