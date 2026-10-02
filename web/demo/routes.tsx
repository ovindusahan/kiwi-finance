import { AffordPage } from "@/features/afford/afford-page";
import { BudgetPage } from "@/features/budget/budget-page";
import { ConnectPage } from "@/features/connect/connect-page";
import { EmergencyFundPage } from "@/features/emergency-fund/emergency-fund-page";
import { GoalsPage } from "@/features/goals/goals-page";
import { NewGoalPage } from "@/features/goals/new-goal-page";
import { PayPage } from "@/features/pay-calculator/pay-page";
import { HomePage } from "@/features/home/home-page";
import { GuidePage, LearnPage } from "@/features/learn/learn-pages";
import { ProgressPage } from "@/features/progress/progress-page";
import { AccountsPage } from "@/features/settings/accounts-page";
import { IncomePage } from "@/features/settings/income-page";
import { ProfilePage } from "@/features/settings/profile-page";
import { RulesPage } from "@/features/settings/rules-page";
import { SecurityPage } from "@/features/settings/security-page";
import { SettingsIndex } from "@/features/settings/settings-index";
import { SpendingPage } from "@/features/spending/spending-page";
import { TransactionsPage } from "@/features/transactions/transactions-page";

type Render = (context: { slug: string; params: URLSearchParams }) => React.ReactNode;

/** The signed-in screens, keyed by path. A dynamic segment is written as [slug]. */
const routes: Record<string, Render> = {
  "/home": () => <HomePage />,
  "/spending": () => <SpendingPage />,
  "/transactions": ({ params }) => (
    <TransactionsPage
      key={params.toString()}
      initialSearch={params.get("search") ?? ""}
      initialAccountId={params.get("accountId") ?? undefined}
    />
  ),
  "/budget": () => <BudgetPage />,
  "/goals": () => <GoalsPage />,
  "/goals/new": () => <NewGoalPage />,
  "/pay": () => <PayPage />,
  "/afford": () => <AffordPage />,
  "/emergency-fund": () => <EmergencyFundPage />,
  "/progress": () => <ProgressPage />,
  "/learn": () => <LearnPage />,
  "/learn/[slug]": ({ slug }) => <GuidePage slug={slug} />,
  "/connect": () => <ConnectPage />,
  "/settings": () => <SettingsIndex />,
  "/settings/profile": () => <ProfilePage />,
  "/settings/income": () => <IncomePage />,
  "/settings/accounts": () => <AccountsPage />,
  "/settings/rules": () => <RulesPage />,
  "/settings/account": () => <SecurityPage />,
};

export function match(pathname: string): Render | undefined {
  return routes[pathname] ?? routes[pathname.replace(/\/[^/]+$/, "/[slug]")];
}
