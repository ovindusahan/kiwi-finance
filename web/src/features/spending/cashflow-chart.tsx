"use client";

import { useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  LabelList,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipContentProps,
} from "recharts";
import type { NameType, ValueType } from "recharts/types/component/DefaultTooltipContent";
import { Segmented } from "@/components/ui/segmented";
import type { MonthSummary } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import {
  formatMoney,
  formatMoneyCompact,
  formatMonthShort,
  formatMonthYear,
  formatPercent,
} from "@/lib/format";

/** Surplus and shortfall: a blue and red pair checked for colour-blind separation. */
const KEPT = "#2a78d6";
const SHORT = "#c8442c";

type Datum = {
  month: string;
  label: string;
  income: number;
  spending: number;
  essentials: number;
  lifestyle: number;
  net: number;
  rate: number;
  complete: boolean;
  callout?: string;
};

const average = (values: number[]) =>
  values.length === 0 ? 0 : values.reduce((sum, value) => sum + value, 0) / values.length;

/** What a person kept or fell short by each month, with the takeaways spelled out above it. */
export function CashflowChart({ months }: { months: MonthSummary[] }) {
  const [view, setView] = useState<"chart" | "table">("chart");
  const data: Datum[] = months.map((month) => ({
    month: month.month,
    label: formatMonthShort(month.month),
    income: month.income.cents,
    spending: month.spending.cents,
    essentials: month.essentialSpending.cents,
    lifestyle: month.lifestyleSpending.cents,
    net: month.net.cents,
    rate: month.savingsRate,
    complete: month.complete,
  }));
  const complete = data.filter((datum) => datum.complete && (datum.income > 0 || datum.spending > 0));
  const takeaways = summarise(complete);
  if (takeaways.best) takeaways.best.callout = "Best";
  if (takeaways.hardest && takeaways.hardest.net < 0) takeaways.hardest.callout = "Hardest";

  return (
    <div>
      <dl className="grid gap-4 border-b border-line pb-4 sm:grid-cols-3">
        <Takeaway
          label="Months you kept money"
          value={`${takeaways.surplusMonths} of ${complete.length}`}
          detail={
            takeaways.surplusMonths === complete.length
              ? "Every month came out ahead."
              : `${complete.length - takeaways.surplusMonths} month${complete.length - takeaways.surplusMonths === 1 ? "" : "s"} cost more than came in.`
          }
        />
        <Takeaway
          label="Average left over"
          value={formatMoney(takeaways.average, { whole: true, signed: true })}
          detail="a month, across the months shown"
          tone={takeaways.average >= 0 ? undefined : "text-danger"}
        />
        <Takeaway
          label="Lately"
          value={
            takeaways.trend == null
              ? "Not enough data"
              : `${takeaways.trend >= 0 ? "Up" : "Down"} ${formatMoney(Math.abs(takeaways.trend), { whole: true })}`
          }
          detail={
            takeaways.trend == null
              ? "Six complete months shows a trend."
              : "a month: your last 3 months against the 3 before"
          }
          tone={takeaways.trend != null && takeaways.trend < 0 ? "text-danger" : undefined}
        />
      </dl>

      {takeaways.hardest && takeaways.hardest.net < 0 ? (
        <p className="mt-3 text-sm text-ink-2">
          <span className="font-semibold text-ink">{formatMonthYear(takeaways.hardest.month)}</span> was your
          hardest month, {formatMoney(-takeaways.hardest.net, { whole: true })} short. {takeaways.reason}
        </p>
      ) : takeaways.best ? (
        <p className="mt-3 text-sm text-ink-2">
          Your best month was{" "}
          <span className="font-semibold text-ink">{formatMonthYear(takeaways.best.month)}</span>, with{" "}
          {formatMoney(takeaways.best.net, { whole: true })} left over.
        </p>
      ) : null}

      <div className="mt-4 mb-3 flex flex-wrap items-center justify-between gap-3">
        <ul className="flex flex-wrap gap-x-5 gap-y-1 text-sm text-ink-2" aria-label="Legend">
          <li className="flex items-center gap-2">
            <span className="h-3 w-3" style={{ backgroundColor: KEPT }} aria-hidden /> Kept
          </li>
          <li className="flex items-center gap-2">
            <span className="h-3 w-3" style={{ backgroundColor: SHORT }} aria-hidden /> Short
          </li>
          <li className="flex items-center gap-2">
            <span className="w-4 border-t-2 border-dashed border-ink-2" aria-hidden /> Your average
          </li>
        </ul>
        <Segmented
          label="Show as"
          value={view}
          onChange={setView}
          options={[
            { value: "chart", label: "Chart" },
            { value: "table", label: "Table" },
          ]}
        />
      </div>

      {view === "chart" ? (
        <div
          className="h-72 w-full"
          role="img"
          aria-label={`Money left over each month. You kept money in ${takeaways.surplusMonths} of ${complete.length} months, ${formatMoney(takeaways.average, { whole: true })} a month on average.`}
        >
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} barCategoryGap="28%" margin={{ top: 22, right: 8, bottom: 0, left: 0 }}>
              <CartesianGrid vertical={false} stroke="var(--chart-grid)" />
              <XAxis
                dataKey="label"
                tickLine={false}
                axisLine={false}
                tick={{ fill: "var(--muted)", fontSize: 12 }}
                interval="preserveStartEnd"
              />
              <YAxis
                tickLine={false}
                axisLine={false}
                width={56}
                tick={{ fill: "var(--muted)", fontSize: 12 }}
                tickFormatter={(value: number) => formatMoneyCompact(value)}
              />
              <Tooltip
                cursor={{ fill: "var(--surface-2)" }}
                content={(props) => <MonthTooltip {...props} />}
              />
              <ReferenceLine y={0} stroke="var(--muted)" />
              {complete.length > 0 ? (
                <ReferenceLine
                  y={takeaways.average}
                  stroke="var(--text-2)"
                  strokeDasharray="5 4"
                  strokeWidth={1.5}
                />
              ) : null}
              <Bar dataKey="net" name="Left over" radius={[4, 4, 4, 4]} maxBarSize={28}>
                {data.map((datum) => (
                  <Cell
                    key={datum.month}
                    fill={datum.net >= 0 ? KEPT : SHORT}
                    fillOpacity={datum.complete ? 1 : 0.4}
                  />
                ))}
                <LabelList
                  dataKey="callout"
                  position="top"
                  style={{ fill: "var(--text-2)", fontSize: 11, fontWeight: 600 }}
                />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <div className="max-h-72 overflow-auto border border-line">
          <table className="w-full text-sm">
            <thead className="sticky top-0 bg-surface-2 text-left text-xs uppercase tracking-wider text-muted">
              <tr>
                <th className="px-3 py-2 font-semibold">Month</th>
                <th className="px-3 py-2 text-right font-semibold">Money in</th>
                <th className="px-3 py-2 text-right font-semibold">Money out</th>
                <th className="px-3 py-2 text-right font-semibold">Left over</th>
                <th className="px-3 py-2 text-right font-semibold">Kept</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {[...data].reverse().map((datum) => (
                <tr key={datum.month}>
                  <td className="px-3 py-2 font-semibold">
                    {formatMonthYear(datum.month)}
                    {datum.complete ? "" : " (so far)"}
                  </td>
                  <td className="px-3 py-2 text-right tabular">
                    {formatMoney(datum.income, { whole: true })}
                  </td>
                  <td className="px-3 py-2 text-right tabular">
                    {formatMoney(datum.spending, { whole: true })}
                  </td>
                  <td
                    className={cn(
                      "px-3 py-2 text-right font-semibold tabular",
                      datum.net < 0 && "text-danger",
                    )}
                  >
                    {formatMoney(datum.net, { whole: true, signed: true })}
                  </td>
                  <td className="px-3 py-2 text-right tabular">
                    {datum.income > 0 ? formatPercent(datum.rate) : ""}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="mt-2 text-xs text-muted">
        Each bar is money in minus money out. The faded bar is this month, which isn&apos;t finished yet.
      </p>
    </div>
  );
}

function summarise(complete: Datum[]) {
  const nets = complete.map((datum) => datum.net);
  const best = complete.length ? complete.reduce((a, b) => (b.net > a.net ? b : a)) : undefined;
  const hardest = complete.length ? complete.reduce((a, b) => (b.net < a.net ? b : a)) : undefined;
  const recent = complete.slice(-3);
  const before = complete.slice(-6, -3);
  const trend =
    before.length === 3 ? average(recent.map((d) => d.net)) - average(before.map((d) => d.net)) : null;

  let reason = "";
  if (hardest) {
    const others = complete.filter((datum) => datum !== hardest);
    const extraSpending = hardest.spending - average(others.map((d) => d.spending));
    const lessIncome = average(others.map((d) => d.income)) - hardest.income;
    if (extraSpending >= lessIncome && extraSpending > 0) {
      const extraLifestyle = hardest.lifestyle - average(others.map((d) => d.lifestyle));
      reason = `You spent ${formatMoney(extraSpending, { whole: true })} more than usual${
        extraLifestyle > extraSpending / 2 ? ", mostly on lifestyle spending" : ""
      }.`;
    } else if (lessIncome > 0) {
      reason = `Your income was ${formatMoney(lessIncome, { whole: true })} lower than usual.`;
    }
  }

  return {
    surplusMonths: nets.filter((net) => net >= 0).length,
    average: Math.round(average(nets)),
    trend: trend == null ? null : Math.round(trend),
    best,
    hardest,
    reason,
  };
}

function Takeaway({
  label,
  value,
  detail,
  tone,
}: {
  label: string;
  value: string;
  detail: string;
  tone?: string;
}) {
  return (
    <div>
      <dt className="text-sm text-muted">{label}</dt>
      <dd className={cn("text-2xl font-semibold tabular", tone)}>{value}</dd>
      <dd className="text-sm text-ink-2">{detail}</dd>
    </div>
  );
}

function MonthTooltip({ active, payload }: TooltipContentProps<ValueType, NameType>) {
  const datum = payload?.[0]?.payload as Datum | undefined;
  if (!active || !datum) return null;
  const rows = [
    ["Money in", datum.income],
    ["Essentials", datum.essentials],
    ["Lifestyle", datum.lifestyle],
    ["Money out", datum.spending],
  ] as const;
  return (
    <div className="min-w-52 border border-line bg-surface p-3 text-sm shadow-pop">
      <p className="mb-2 font-semibold">
        {formatMonthYear(datum.month)}
        {datum.complete ? "" : " so far"}
      </p>
      {rows.map(([label, value]) => (
        <p key={label} className="flex justify-between gap-4">
          <span className="text-ink-2">{label}</span>
          <span className="tabular">{formatMoney(value, { whole: true })}</span>
        </p>
      ))}
      <p className="mt-2 flex justify-between gap-4 border-t border-line pt-2">
        <span className="text-ink-2">Left over</span>
        <span className={cn("font-semibold tabular", datum.net < 0 && "text-danger")}>
          {formatMoney(datum.net, { whole: true, signed: true })}
          {datum.income > 0 ? ` (${formatPercent(datum.rate)})` : ""}
        </span>
      </p>
    </div>
  );
}
