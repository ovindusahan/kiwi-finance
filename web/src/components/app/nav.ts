import {
  BookOpen,
  ChartPie,
  Goal,
  House,
  Landmark,
  ListOrdered,
  Medal,
  Plug,
  Settings,
  ShieldCheck,
  Sparkles,
  Wallet,
  type LucideIcon,
} from "lucide-react";

export type NavItem = { href: string; label: string; icon: LucideIcon };

/** Shown in the navigation bar on larger screens. */
export const primaryNav: NavItem[] = [
  { href: "/home", label: "Home", icon: House },
  { href: "/spending", label: "Spending", icon: ChartPie },
  { href: "/transactions", label: "Transactions", icon: ListOrdered },
  { href: "/budget", label: "Budget", icon: Wallet },
  { href: "/goals", label: "Goals", icon: Goal },
  { href: "/emergency-fund", label: "Emergency fund", icon: ShieldCheck },
  { href: "/afford", label: "Can I afford it?", icon: Sparkles },
];

/** Grouped under "More" on larger screens. */
export const secondaryNav: NavItem[] = [
  { href: "/progress", label: "Progress", icon: Medal },
  { href: "/learn", label: "Learn", icon: BookOpen },
  { href: "/connect", label: "Connect bank", icon: Plug },
  { href: "/pay", label: "Pay calculator", icon: Landmark },
  { href: "/settings", label: "Settings", icon: Settings },
];

export const mobileTabs: NavItem[] = [
  { href: "/home", label: "Home", icon: House },
  { href: "/spending", label: "Spending", icon: ChartPie },
  { href: "/goals", label: "Goals", icon: Goal },
  { href: "/budget", label: "Budget", icon: Wallet },
];

export const moreNav: NavItem[] = [
  { href: "/transactions", label: "Transactions", icon: ListOrdered },
  { href: "/emergency-fund", label: "Emergency fund", icon: ShieldCheck },
  { href: "/afford", label: "Can I afford it?", icon: Sparkles },
  ...secondaryNav,
];
