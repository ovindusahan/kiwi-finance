import { StrictMode, Suspense } from "react";
import { createRoot } from "react-dom/client";
import LandingPage from "@/app/page";
import PayCalculatorPage from "@/app/pay-calculator/page";
import { Providers } from "@/app/providers";
import { AppShell } from "@/components/app/app-shell";
import { EmptyState } from "@/components/ui/empty-state";
import { AuthForm } from "@/features/auth/auth-form";
import AuthLayout from "@/app/(auth)/layout";
import { installMockApi } from "./mock-api";
import { match } from "./routes";
import { useLocation } from "./shims/router";

installMockApi();

function DemoBar() {
  return (
    <div className="border-b border-line bg-surface-2 px-4 py-2 text-center text-xs font-medium text-ink-2">
      You&apos;re exploring Kiwi Finance with sample data for Aroha, a demo customer. Changes aren&apos;t
      saved.
    </div>
  );
}

function NotFound() {
  return <EmptyState title="This page isn't in the demo" message="Pick another screen from the menu." />;
}

function App() {
  const { pathname, search } = useLocation();
  if (pathname === "/") return <LandingPage />;
  if (pathname === "/pay-calculator") return <PayCalculatorPage />;
  if (pathname === "/sign-in" || pathname === "/sign-up") {
    return (
      <AuthLayout>
        <AuthForm mode={pathname === "/sign-in" ? "sign-in" : "sign-up"} />
      </AuthLayout>
    );
  }
  const route = match(pathname);
  return (
    <>
      <DemoBar />
      <AppShell>
        <Suspense>
          {route ? (
            route({ slug: pathname.split("/")[2] ?? "", params: new URLSearchParams(search) })
          ) : (
            <NotFound />
          )}
        </Suspense>
      </AppShell>
    </>
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <Providers>
      <App />
    </Providers>
  </StrictMode>,
);
