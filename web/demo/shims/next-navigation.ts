import { useMemo } from "react";
import { navigate, useLocation } from "./router";

export function useRouter() {
  return useMemo(
    () => ({
      push: (href: string) => navigate(href),
      replace: (href: string) => navigate(href, true),
      refresh: () => undefined,
      back: () => window.history.back(),
      forward: () => window.history.forward(),
      prefetch: () => undefined,
    }),
    [],
  );
}

export function usePathname(): string {
  return useLocation().pathname;
}

export function useSearchParams(): URLSearchParams {
  const { search } = useLocation();
  return useMemo(() => new URLSearchParams(search), [search]);
}

export function useParams<T extends Record<string, string>>(): T {
  const { pathname } = useLocation();
  const slug = pathname.split("/")[2] ?? "";
  return { slug } as unknown as T;
}

export function notFound(): never {
  throw new Error("Not found");
}

export function redirect(href: string): never {
  navigate(href, true);
  throw new Error("Redirected");
}
