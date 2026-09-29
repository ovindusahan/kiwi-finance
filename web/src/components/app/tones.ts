export type Tone = "brand" | "good" | "warm" | "sky" | "lilac" | "gold" | "danger";

export const softTone: Record<Tone, string> = {
  brand: "bg-brand-soft text-brand",
  good: "bg-good-soft text-good",
  warm: "bg-warm-soft text-warm",
  sky: "bg-sky-soft text-sky",
  lilac: "bg-lilac-soft text-lilac",
  gold: "bg-gold-soft text-gold",
  danger: "bg-danger-soft text-danger",
};

export const textTone: Record<Tone, string> = {
  brand: "text-brand",
  good: "text-good",
  warm: "text-warm",
  sky: "text-sky",
  lilac: "text-lilac",
  gold: "text-gold",
  danger: "text-danger",
};
