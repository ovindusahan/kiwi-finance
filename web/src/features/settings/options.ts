import type { Profile } from "@/lib/api/types";

type Option<T extends string> = { value: T; label: string };

export const payFrequencies: Option<Profile["payFrequency"]>[] = [
  { value: "WEEKLY", label: "Weekly" },
  { value: "FORTNIGHTLY", label: "Fortnightly" },
  { value: "FOUR_WEEKLY", label: "Every four weeks" },
  { value: "MONTHLY", label: "Monthly" },
];

export const taxCodes: Option<Profile["taxCode"]>[] = [
  { value: "M", label: "M: main or only job" },
  { value: "ME", label: "ME: main job, earning $24,000 to $70,000 (gets the independent earner tax credit)" },
  { value: "SB", label: "SB: second job, all your income adds up to $15,600 or less" },
  { value: "S", label: "S: second job, all your income adds up to $15,601 to $53,500" },
  { value: "SH", label: "SH: second job, all your income adds up to $53,501 to $78,100" },
  { value: "ST", label: "ST: second job, all your income adds up to $78,101 to $180,000" },
  { value: "SA", label: "SA: second job, all your income adds up to more than $180,000" },
];

/** The plain-English meaning of a tax code, for showing next to the code itself. */
export function taxCodeLabel(code: Profile["taxCode"] | null | undefined): string {
  return taxCodes.find((option) => option.value === code)?.label ?? "";
}

export const employmentTypes: Option<Profile["employmentType"]>[] = [
  { value: "EMPLOYEE", label: "Employee" },
  { value: "SELF_EMPLOYED", label: "Self-employed" },
  { value: "CONTRACTOR", label: "Contractor" },
  { value: "STUDENT", label: "Student" },
  { value: "NOT_WORKING", label: "Not working" },
  { value: "RETIRED", label: "Retired" },
];

export const housingTypes: Option<NonNullable<Profile["housingType"]>>[] = [
  { value: "RENTING", label: "Renting" },
  { value: "MORTGAGE", label: "Paying a mortgage" },
  { value: "OWN_OUTRIGHT", label: "Own my home outright" },
  { value: "BOARDING", label: "Boarding" },
  { value: "WITH_FAMILY", label: "Living with whānau" },
];

export const regions: Option<NonNullable<Profile["region"]>>[] = [
  { value: "NORTHLAND", label: "Northland" },
  { value: "AUCKLAND", label: "Auckland" },
  { value: "WAIKATO", label: "Waikato" },
  { value: "BAY_OF_PLENTY", label: "Bay of Plenty" },
  { value: "GISBORNE", label: "Gisborne" },
  { value: "HAWKES_BAY", label: "Hawke's Bay" },
  { value: "TARANAKI", label: "Taranaki" },
  { value: "MANAWATU_WHANGANUI", label: "Manawatū-Whanganui" },
  { value: "WELLINGTON", label: "Wellington" },
  { value: "TASMAN", label: "Tasman" },
  { value: "NELSON", label: "Nelson" },
  { value: "MARLBOROUGH", label: "Marlborough" },
  { value: "WEST_COAST", label: "West Coast" },
  { value: "CANTERBURY", label: "Canterbury" },
  { value: "OTAGO", label: "Otago" },
  { value: "SOUTHLAND", label: "Southland" },
];

export const kiwiSaverRates = [0.03, 0.035, 0.04, 0.06, 0.08, 0.1];
