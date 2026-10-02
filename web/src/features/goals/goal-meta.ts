import type { Goal, GoalType } from "@/lib/api/types";

export const goalTypes: { value: GoalType; label: string; icon: string }[] = [
  { value: "TRAVEL", label: "Travel", icon: "plane" },
  { value: "CAR", label: "Car", icon: "car" },
  { value: "HOUSE_DEPOSIT", label: "House deposit", icon: "house" },
  { value: "EDUCATION", label: "Education", icon: "graduation-cap" },
  { value: "PURCHASE", label: "Big purchase", icon: "shopping-bag" },
  { value: "WEDDING", label: "Wedding", icon: "party-popper" },
  { value: "CUSTOM", label: "Something else", icon: "target" },
];

export function goalIcon(type: GoalType): string {
  return goalTypes.find((entry) => entry.value === type)?.icon ?? "target";
}

export function goalTypeLabel(type: GoalType): string {
  return goalTypes.find((entry) => entry.value === type)?.label ?? "Goal";
}

const tones = ["sky", "good", "warm", "lilac", "gold"] as const;
export type GoalTone = (typeof tones)[number];

/** A stable accent per goal so a goal keeps its colour across screens. */
export function goalTone(goal: Pick<Goal, "id">): GoalTone {
  let hash = 0;
  for (const char of goal.id) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  return tones[hash % tones.length]!;
}

export const projectionLabels: Record<Goal["projection"]["status"], string> = {
  ACHIEVED: "Achieved",
  ON_TRACK: "On track",
  BEHIND: "Behind",
  NOT_STARTED: "Not started",
};
