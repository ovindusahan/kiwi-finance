import {
  ArrowLeftRight,
  Baby,
  Briefcase,
  Bus,
  Calendar,
  CalendarCheck,
  Car,
  CircleHelp,
  ClipboardCheck,
  Coins,
  CreditCard,
  Flame,
  Fuel,
  Gift,
  GraduationCap,
  HeartPulse,
  Home,
  House,
  Landmark,
  PartyPopper,
  Percent,
  PiggyBank,
  Pizza,
  Plane,
  Plug,
  Receipt,
  Repeat,
  Rocket,
  Shield,
  ShieldCheck,
  Shirt,
  ShoppingBag,
  ShoppingCart,
  Sparkles,
  Sprout,
  Target,
  Ticket,
  Trophy,
  Utensils,
  Vault,
  Wifi,
  Zap,
  type LucideIcon,
} from "lucide-react";

const icons: Record<string, LucideIcon> = {
  "arrow-left-right": ArrowLeftRight,
  baby: Baby,
  briefcase: Briefcase,
  bus: Bus,
  calendar: Calendar,
  "calendar-check": CalendarCheck,
  car: Car,
  "clipboard-check": ClipboardCheck,
  coins: Coins,
  "credit-card": CreditCard,
  flame: Flame,
  fuel: Fuel,
  gift: Gift,
  "graduation-cap": GraduationCap,
  "heart-pulse": HeartPulse,
  home: Home,
  house: House,
  landmark: Landmark,
  "party-popper": PartyPopper,
  percent: Percent,
  "piggy-bank": PiggyBank,
  pizza: Pizza,
  plane: Plane,
  plug: Plug,
  receipt: Receipt,
  repeat: Repeat,
  rocket: Rocket,
  shield: Shield,
  "shield-check": ShieldCheck,
  shirt: Shirt,
  "shopping-bag": ShoppingBag,
  "shopping-cart": ShoppingCart,
  sparkles: Sparkles,
  sprout: Sprout,
  target: Target,
  ticket: Ticket,
  trophy: Trophy,
  utensils: Utensils,
  vault: Vault,
  wifi: Wifi,
  zap: Zap,
};

export function Icon({ name, className }: { name: string; className?: string }) {
  const Component = icons[name] ?? CircleHelp;
  return <Component className={className} aria-hidden />;
}

/** A category's icon in a rounded tile tinted with the category colour. */
export function CategoryIcon({
  icon,
  colour,
  size = "md",
}: {
  icon: string | null | undefined;
  colour: string | null | undefined;
  size?: "sm" | "md" | "lg";
}) {
  const box =
    size === "sm" ? "h-8 w-8 rounded-xl" : size === "lg" ? "h-14 w-14 rounded-2xl" : "h-11 w-11 rounded-2xl";
  const glyph = size === "sm" ? "h-4 w-4" : size === "lg" ? "h-7 w-7" : "h-5 w-5";
  const tint = colour ?? "#94A3B8";
  return (
    <span
      className={`grid shrink-0 place-items-center ${box}`}
      style={{ backgroundColor: `color-mix(in oklab, ${tint} 18%, transparent)`, color: tint }}
    >
      <Icon name={icon ?? "receipt"} className={glyph} />
    </span>
  );
}
