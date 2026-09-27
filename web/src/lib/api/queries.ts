"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "./client";
import type {
  Account,
  AuthenticatedUser,
  BankConnection,
  BankSyncRun,
  Budget,
  BudgetRecommendation,
  Cashflow,
  CashWithdrawal,
  CategorisationRule,
  Category,
  Dashboard,
  EmergencyFund,
  GlossaryEntry,
  Goal,
  GoalContribution,
  GoalPlan,
  Guide,
  GuideSummary,
  ImportBatch,
  IncomeSummary,
  Insight,
  MoneyMovement,
  Preferences,
  Profile,
  Progress,
  RecurringSummary,
  SetupGuide,
  SpendingBreakdown,
  TransactionPage,
} from "./types";

export type TransactionFilters = {
  search?: string;
  accountId?: string;
  categoryId?: string;
  uncategorised?: boolean;
  direction?: "IN" | "OUT";
  from?: string;
  to?: string;
};

function query(params: Record<string, string | number | boolean | undefined | null>) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "" && value !== false)
      search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

export const useMe = () =>
  useQuery({ queryKey: ["me"], queryFn: () => api.get<AuthenticatedUser>("/auth/me") });
export const useProfile = () =>
  useQuery({ queryKey: ["profile"], queryFn: () => api.get<Profile>("/profile") });
export const useDashboard = () =>
  useQuery({ queryKey: ["dashboard"], queryFn: () => api.get<Dashboard>("/dashboard") });
export const useAccounts = (includeArchived = false) =>
  useQuery({
    queryKey: ["accounts", includeArchived],
    queryFn: () => api.get<Account[]>(`/accounts${query({ includeArchived })}`),
  });
export const useCategories = () =>
  useQuery({
    queryKey: ["categories"],
    queryFn: () => api.get<Category[]>("/categories"),
    staleTime: 5 * 60_000,
  });
export const useRules = () =>
  useQuery({ queryKey: ["rules"], queryFn: () => api.get<CategorisationRule[]>("/categorisation-rules") });
export const useIncome = () =>
  useQuery({ queryKey: ["income"], queryFn: () => api.get<IncomeSummary>("/income-sources") });
export const useImports = () =>
  useQuery({ queryKey: ["imports"], queryFn: () => api.get<ImportBatch[]>("/imports") });

export const useTransactions = (filters: TransactionFilters, cursor?: string) =>
  useQuery({
    queryKey: ["transactions", filters, cursor ?? null],
    queryFn: () => api.get<TransactionPage>(`/transactions${query({ ...filters, cursor, limit: 50 })}`),
    placeholderData: keepPreviousData,
  });

export const useCashflow = (months = 12) =>
  useQuery({
    queryKey: ["cashflow", months],
    queryFn: () => api.get<Cashflow>(`/analysis/cashflow?months=${months}`),
  });
export const useSpending = (months = 3) =>
  useQuery({
    queryKey: ["spending", months],
    queryFn: () => api.get<SpendingBreakdown>(`/analysis/spending?months=${months}`),
    placeholderData: keepPreviousData,
  });
export const useRecurring = () =>
  useQuery({ queryKey: ["recurring"], queryFn: () => api.get<RecurringSummary>("/analysis/recurring") });
export const useInsights = () =>
  useQuery({ queryKey: ["insights"], queryFn: () => api.get<Insight[]>("/insights") });

export const useBudgetRecommendation = () =>
  useQuery({
    queryKey: ["budget-recommendation"],
    queryFn: () => api.get<BudgetRecommendation>("/budgets/recommendation"),
  });

/** The active budget, or null when the person hasn't created one. */
export const useCurrentBudget = (month?: string) =>
  useQuery({
    queryKey: ["budget", month ?? "current"],
    queryFn: async () => {
      try {
        return await api.get<Budget>(`/budgets/current${query({ month })}`);
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) return null;
        throw error;
      }
    },
  });

export const useGoals = () => useQuery({ queryKey: ["goals"], queryFn: () => api.get<Goal[]>("/goals") });
export const useGoalContributions = (goalId: string, enabled = true) =>
  useQuery({
    queryKey: ["goal-contributions", goalId],
    queryFn: () => api.get<GoalContribution[]>(`/goals/${goalId}/contributions`),
    enabled,
  });
export const usePreferences = () =>
  useQuery({ queryKey: ["preferences"], queryFn: () => api.get<Preferences>("/preferences") });
export const useGoalPlan = () =>
  useQuery({ queryKey: ["goal-plan"], queryFn: () => api.get<GoalPlan>("/goals/plan") });
export const useMoneyMovements = (accountId?: string) =>
  useQuery({
    queryKey: ["money-movements", accountId ?? "all"],
    queryFn: () => api.get<MoneyMovement[]>(`/money-movements${query({ accountId, limit: 10 })}`),
  });
export const useCashWithdrawals = () =>
  useQuery({ queryKey: ["cash-withdrawals"], queryFn: () => api.get<CashWithdrawal[]>("/cash-withdrawals") });
export const useEmergencyFund = () =>
  useQuery({ queryKey: ["emergency-fund"], queryFn: () => api.get<EmergencyFund>("/emergency-fund") });
export const useProgress = () =>
  useQuery({ queryKey: ["progress"], queryFn: () => api.get<Progress>("/progress") });

/** The setup guide, refreshed every few seconds while a sync is running. */
export const useSetupGuide = () =>
  useQuery({
    queryKey: ["setup-guide"],
    queryFn: () => api.get<SetupGuide>("/bank-feeds/akahu/setup-guide"),
    refetchInterval: (query) => (query.state.data?.state === "SYNCING" ? 2000 : false),
  });

/** Bank connections, refreshed every few seconds while a sync is running. */
export const useConnections = () =>
  useQuery({
    queryKey: ["connections"],
    queryFn: () => api.get<BankConnection[]>("/bank-feeds/connections"),
    refetchInterval: (query) =>
      query.state.data?.some((connection) => connection.latestSync?.status === "RUNNING") ? 2000 : false,
  });
export const useSyncRuns = (connectionId: string | undefined) =>
  useQuery({
    queryKey: ["syncs", connectionId],
    queryFn: () => api.get<BankSyncRun[]>(`/bank-feeds/connections/${connectionId}/syncs`),
    enabled: Boolean(connectionId),
  });

export const useGuides = () =>
  useQuery({
    queryKey: ["guides"],
    queryFn: () => api.get<GuideSummary[]>("/education/guides"),
    staleTime: Infinity,
  });
export const useGuide = (slug: string) =>
  useQuery({
    queryKey: ["guide", slug],
    queryFn: () => api.get<Guide>(`/education/guides/${slug}`),
    staleTime: Infinity,
  });
export const useGlossary = () =>
  useQuery({
    queryKey: ["glossary"],
    queryFn: () => api.get<GlossaryEntry[]>("/education/glossary"),
    staleTime: Infinity,
  });

/**
 * A mutation that refreshes everything derived from the person's money afterwards. Almost every
 * change affects the dashboard, insights and plans, so refreshing broadly keeps every screen honest.
 */
export function useApiMutation<TInput, TResult>(fn: (input: TInput) => Promise<TResult>) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: fn,
    // Not awaited: the caller's own callbacks run straight away, before a refreshed screen can
    // unmount the component that started the change.
    onSuccess: () => {
      void client.invalidateQueries({
        predicate: (q) =>
          !["categories", "guides", "guide", "glossary", "me"].includes(String(q.queryKey[0])),
      });
    },
  });
}
