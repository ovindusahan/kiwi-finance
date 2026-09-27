"use client";

import { QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ApiError } from "@/lib/api/client";
import { ToastProvider } from "@/components/ui/toast";

export function Providers({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [client] = useState(
    () =>
      new QueryClient({
        queryCache: new QueryCache({
          onError: (error) => {
            if (error instanceof ApiError && error.status === 401) {
              const next = `${window.location.pathname}${window.location.search}`;
              router.replace(`/sign-in?next=${encodeURIComponent(next)}`);
            }
          },
        }),
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            refetchOnWindowFocus: false,
            retry: (failures, error) => !(error instanceof ApiError && error.status < 500) && failures < 2,
          },
        },
      }),
  );
  return (
    <QueryClientProvider client={client}>
      <ToastProvider>{children}</ToastProvider>
    </QueryClientProvider>
  );
}
