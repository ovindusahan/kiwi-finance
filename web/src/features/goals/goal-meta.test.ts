import type { Goal } from "@/lib/api/types";
import { countdownFor } from "./goal-countdown-card";
import { goalIcon, goalTone } from "./goal-meta";

const money = (cents: number) => ({ cents, currency: "NZD" });

function goal(overrides: Partial<Goal> = {}): Goal {
  return {
    id: "01a0f5b1-de9f-73c9-9f5e-92f9351c818c",
    name: "Trip to Japan",
    type: "TRAVEL",
    target: money(600_000),
    saved: money(120_000),
    remaining: money(480_000),
    progress: 0.2,
    monthlyContribution: money(25_000),
    targetDate: "2027-07-01",
    daysLeft: 273,
    priority: 1,
    status: "ACTIVE",
    linkedAccountId: null,
    linkedAccountName: null,
    achievedAt: null,
    createdAt: "2026-10-01T00:00:00Z",
    projection: {
      status: "BEHIND",
      monthsToGoal: 20,
      projectedDate: "2028-06-01",
      requiredMonthly: money(53_334),
      monthlyContribution: money(25_000),
      milestones: [],
      explanation: { summary: "", steps: [], assumptions: [] },
    },
    ...overrides,
  };
}

describe("goal countdowns", () => {
  it("counts days to a near target date", () => {
    expect(countdownFor(goal())).toEqual({ value: "273", label: "days to 1 Jul 2027" });
  });

  it("switches to months for distant dates", () => {
    expect(countdownFor(goal({ daysLeft: 1461, targetDate: "2030-10-01" })).value).toBe("48");
  });

  it("falls back to the projection when there is no date", () => {
    expect(countdownFor(goal({ daysLeft: null, targetDate: null }))).toEqual({
      value: "20",
      label: "months at $250 a month",
    });
  });

  it("celebrates achieved goals", () => {
    expect(countdownFor(goal({ status: "ACHIEVED" })).value).toBe("100%");
  });
});

describe("goal styling", () => {
  it("keeps a stable tone per goal and an icon per type", () => {
    expect(goalTone(goal())).toBe(goalTone(goal()));
    expect(goalIcon("HOUSE_DEPOSIT")).toBe("house");
  });
});
