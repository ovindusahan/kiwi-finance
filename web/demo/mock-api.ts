import type {
  Account,
  CashWithdrawal,
  Category,
  Dashboard,
  EmergencyFund,
  Goal,
  GoalPlan,
  Preferences,
  Transaction,
} from "@/lib/api/types";
import { planGoals } from "./goal-plan";

type Example = { request: Record<string, unknown>; response: unknown };
type Fixtures = {
  responses: Record<string, unknown>;
  transactions: Transaction[];
  examples: {
    affordability: Example[];
    payCalculator: Example[];
    loan: Example[];
    purchaseImpact: Example[];
  };
};

declare global {
  interface Window {
    KIWI_DEMO_DATA: Fixtures;
  }
}

const data = window.KIWI_DEMO_DATA;
const transactions = data.transactions.map((transaction) => ({ ...transaction }));
const categories = data.responses["GET /categories"] as Category[];
const accounts = data.responses["GET /accounts"] as Account[];
const allAccounts = data.responses["GET /accounts?includeArchived=true"] as Account[];
let preferences = (data.responses["GET /preferences"] as Preferences | undefined) ?? {
  homeLayout: null,
  emergencyFundReminders: true,
  emergencyFundReminderSnoozedUntil: null,
};
const fund = data.responses["GET /emergency-fund"] as EmergencyFund;
const goals = data.responses["GET /goals"] as Goal[];
const dashboard = data.responses["GET /dashboard"] as Dashboard;
const cashWithdrawals = (data.responses["GET /cash-withdrawals"] as CashWithdrawal[] | undefined) ?? [];
let cashWallet = 0;

function money(cents: number) {
  return { cents, currency: "NZD" };
}

/** Recomputes a goal's figures after its target or saved amount changes in the preview. */
function refresh(goal: Goal) {
  goal.remaining = money(Math.max(0, goal.target.cents - goal.saved.cents));
  goal.progress = goal.target.cents > 0 ? Math.min(1, goal.saved.cents / goal.target.cents) : 0;
  if (goal.status === "ACTIVE" && goal.target.cents > 0 && goal.saved.cents >= goal.target.cents) {
    goal.status = "ACHIEVED";
    goal.achievedAt = new Date().toISOString();
  }
}

/** Recomputes the home screen's figures from the accounts, the way the API does. */
function refreshDashboard(netChange: number) {
  const liquid = accounts.filter((account) => account.liquid).reduce((sum, a) => sum + a.balance.cents, 0);
  dashboard.netWorth = money(dashboard.netWorth.cents + netChange);
  dashboard.availableToSpend = money(Math.max(0, liquid - fund.current.cents));
  const ef = dashboard.emergencyFund;
  if (ef) {
    ef.current = money(fund.current.cents);
    ef.progress = fund.progress;
    ef.monthsCovered =
      ef.target.cents > 0
        ? Math.round((fund.current.cents / (ef.target.cents / ef.targetMonths)) * 10) / 10
        : 0;
  }
}

/** Counts a withdrawal as spending, or a deposit with a source as income, in this month's figures. */
function countThisMonth(type: unknown, cents: number, categorised: boolean) {
  const month = dashboard.thisMonth;
  if (type === "WITHDRAWAL") {
    month.spending = money(month.spending.cents + cents);
    month.uncategorisedSpending = money(month.uncategorisedSpending.cents + (categorised ? 0 : cents));
    month.net = money(month.net.cents - cents);
  } else if (type === "DEPOSIT" && categorised) {
    month.income = money(month.income.cents + cents);
    month.net = money(month.net.cents + cents);
  }
}

/** Keeps the account lists, emergency fund and transactions in step after money moves in the preview. */
function adjust(accountId: string | undefined, cents: number, description: string, today: string) {
  if (!accountId) return;
  for (const list of [accounts, allAccounts]) {
    const account = list.find((item) => item.id === accountId);
    if (account) account.balance = { ...account.balance, cents: account.balance.cents + cents };
  }
  const account = accounts.find((item) => item.id === accountId);
  if (fund.account?.id === accountId) {
    fund.account.balance = { ...fund.account.balance, cents: fund.account.balance.cents + cents };
    fund.current = { ...fund.current, cents: fund.current.cents + cents };
    fund.progress = fund.target.cents > 0 ? Math.min(1, fund.current.cents / fund.target.cents) : 0;
    fund.shortfall = { ...fund.shortfall, cents: Math.max(0, fund.target.cents - fund.current.cents) };
  }
  transactions.unshift({
    id: `preview-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    accountId,
    accountName: account?.name ?? null,
    postedOn: today,
    amount: { cents, currency: "NZD" },
    description,
    merchant: null,
    category: null,
    categorySource: null as unknown as Transaction["categorySource"],
    source: "MANUAL",
    transfer: true,
    notes: null,
    createdAt: new Date().toISOString(),
  } as Transaction);
}

const READ_ONLY = {
  status: 403,
  code: "preview_read_only",
  detail: "This preview runs on sample data, so changes aren't saved.",
};

function json(body: unknown, status = 200): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

function nearest(examples: Example[], score: (request: Record<string, unknown>) => number): unknown {
  return [...examples].sort((a, b) => score(a.request) - score(b.request))[0]?.response;
}

const annual: Record<string, number> = {
  WEEKLY: 52,
  FORTNIGHTLY: 26,
  FOUR_WEEKLY: 13,
  MONTHLY: 12,
  ANNUALLY: 1,
};

function listTransactions(params: URLSearchParams) {
  const search = params.get("search")?.toLowerCase();
  const from = params.get("from");
  const to = params.get("to");
  const filtered = transactions.filter(
    (t) =>
      (!search || `${t.description} ${t.merchant ?? ""}`.toLowerCase().includes(search)) &&
      (!params.get("accountId") || t.accountId === params.get("accountId")) &&
      (!params.get("categoryId") || t.category?.id === params.get("categoryId")) &&
      (params.get("uncategorised") !== "true" || t.category == null) &&
      (params.get("direction") !== "IN" || t.amount.cents > 0) &&
      (params.get("direction") !== "OUT" || t.amount.cents < 0) &&
      (!from || t.postedOn >= from) &&
      (!to || t.postedOn <= to),
  );
  const offset = Number(params.get("cursor") ?? 0);
  const limit = Number(params.get("limit") ?? 50);
  const items = filtered.slice(offset, offset + limit);
  return { items, nextCursor: offset + limit < filtered.length ? String(offset + limit) : null };
}

function setCategory(id: string, categoryId: string | null | undefined) {
  const transaction = transactions.find((t) => t.id === id);
  if (!transaction) return undefined;
  transaction.category = categories.find((c) => c.id === categoryId) ?? null;
  transaction.categorySource = transaction.category
    ? "USER"
    : (null as unknown as Transaction["categorySource"]);
  return transaction;
}

function handle(
  method: string,
  path: string,
  params: URLSearchParams,
  body: Record<string, unknown>,
): Response {
  if (method === "GET") {
    if (path === "/preferences") return json(preferences);
    if (path === "/goals") return json(goals);
    if (path === "/cash-withdrawals") return json(cashWithdrawals);
    if (path === "/goals/plan") return json(planGoals(goals, data.responses["GET /goals/plan"] as GoalPlan));
    if (path === "/accounts") return json(params.get("includeArchived") === "true" ? allAccounts : accounts);
    if (path === "/emergency-fund") return json(fund);
    if (path === "/transactions") return json(listTransactions(params));
    const transaction = path.match(/^\/transactions\/([\w-]+)$/);
    if (transaction) return json(transactions.find((t) => t.id === transaction[1]));
    const query = params.toString();
    const key = `GET ${path}${query ? `?${query}` : ""}`;
    if (key in data.responses) return json(data.responses[key]);
    if (!query && `GET ${path}` in data.responses) return json(data.responses[`GET ${path}`]);
    return json({ status: 404, code: "not_found", detail: "Not available in this preview." }, 404);
  }

  if (method === "PATCH" && path === "/auth/me") {
    const me = data.responses["GET /auth/me"] as Record<string, unknown>;
    if (typeof body.displayName === "string" && body.displayName.trim())
      me.displayName = body.displayName.trim();
    if (typeof body.email === "string" && body.email.trim()) me.email = body.email.trim().toLowerCase();
    return json(me);
  }
  if (method === "POST" && path === "/planning/affordability") {
    const price = Number(body.priceCents ?? 0);
    return json(
      nearest(
        data.examples.affordability,
        (r) => Math.abs(Number(r.priceCents) - price) + (r.funding === body.funding ? 0 : 1e9),
      ),
    );
  }
  if (method === "POST" && path === "/income/pay-calculator") {
    const gross = Number(body.amountCents ?? 0) * (annual[String(body.frequency)] ?? 1);
    return json(
      nearest(
        data.examples.payCalculator,
        (r) =>
          Math.abs(Number(r.amountCents) * (annual[String(r.frequency)] ?? 1) - gross) +
          (r.frequency === body.frequency ? 0 : 1e9) +
          (Number(r.kiwiSaverRate ?? 0) === Number(body.kiwiSaverRate ?? 0) ? 0 : 1e11) +
          (Boolean(r.studentLoan) === Boolean(body.studentLoan) ? 0 : 1e11),
      ),
    );
  }
  if (method === "POST" && path === "/planning/purchase-impact") {
    const price = Number(body.priceCents ?? 0);
    const term = body.termMonths == null ? (body.kind === "HOUSE" ? 360 : 60) : Number(body.termMonths);
    return json(
      nearest(
        data.examples.purchaseImpact,
        (r) =>
          (r.kind === body.kind ? 0 : 1e12) +
          (Number(r.termMonths) === term ? 0 : 1e10) +
          (Boolean(r.firstHome) === Boolean(body.firstHome) ? 0 : 1e9) +
          Math.abs(Number(r.priceCents) - price),
      ),
    );
  }
  if (method === "POST" && path === "/planning/loan") {
    return json(
      nearest(data.examples.loan, (r) => Math.abs(Number(r.amountCents) - Number(body.amountCents ?? 0))),
    );
  }
  const category = path.match(/^\/transactions\/([\w-]+)\/category$/);
  if (method === "PUT" && category)
    return json(setCategory(category[1]!, body.categoryId as string | undefined));
  if (method === "POST" && path === "/transactions/categorise") {
    const ids = (body.transactionIds as string[]) ?? [];
    ids.forEach((id) => setCategory(id, body.categoryId as string | undefined));
    return json({ updated: ids.length });
  }
  if (method === "POST" && path === "/goals" && goals.length > 0) {
    const template = goals[0]!;
    const goal: Goal = {
      ...template,
      id: `preview-${Date.now()}`,
      name: String(body.name ?? "New goal"),
      type: body.type as Goal["type"],
      target: money(Number(body.targetCents ?? 0)),
      saved: money(Number(body.startingAmountCents ?? 0)),
      monthlyContribution: money(Number(body.monthlyContributionCents ?? 0)),
      targetDate: (body.targetDate as string | undefined) ?? null,
      daysLeft: body.targetDate
        ? Math.round((new Date(String(body.targetDate)).getTime() - Date.now()) / 86_400_000)
        : null,
      status: "ACTIVE",
      achievedAt: null,
      projection: { ...template.projection, status: "NOT_STARTED", milestones: [] },
    };
    refresh(goal);
    goals.unshift(goal);
    return json(goal, 201);
  }
  const cash = path.match(/^\/cash-withdrawals\/([\w-]+)$/);
  if (cash && method === "POST") {
    const index = cashWithdrawals.findIndex((item) => item.transactionId === cash[1]);
    if (index < 0) return json({ status: 404, detail: "Cash withdrawal not found" }, 404);
    const [withdrawal] = cashWithdrawals.splice(index, 1);
    const lines = (body.lines as { amountCents: number }[] | undefined) ?? [];
    cashWallet += withdrawal!.amount.cents - lines.reduce((sum, line) => sum + Number(line.amountCents), 0);
    return json({
      walletAccountId: "preview-cash",
      walletBalance: money(cashWallet),
      linesRecorded: lines.length,
    });
  }
  const goalPath = path.match(/^\/goals\/([\w-]+)$/);
  if (goalPath && (method === "PUT" || method === "DELETE")) {
    const index = goals.findIndex((item) => item.id === goalPath[1]);
    if (index < 0) return json({ status: 404, detail: "Goal not found" }, 404);
    if (method === "DELETE") {
      goals.splice(index, 1);
      return json(undefined, 204);
    }
    const goal = goals[index]!;
    goal.name = String(body.name ?? goal.name);
    goal.target = money(Number(body.targetCents ?? goal.target.cents));
    goal.monthlyContribution = money(Number(body.monthlyContributionCents ?? goal.monthlyContribution.cents));
    goal.targetDate = (body.targetDate as string | undefined) ?? null;
    refresh(goal);
    return json(goal);
  }
  const goalStatus = path.match(/^\/goals\/([\w-]+)\/status$/);
  if (goalStatus && method === "PUT") {
    const goal = goals.find((item) => item.id === goalStatus[1]);
    if (!goal) return json({ status: 404, detail: "Goal not found" }, 404);
    goal.status = body.status as Goal["status"];
    if (goal.status === "ACHIEVED") goal.achievedAt = new Date().toISOString();
    return json(goal);
  }
  const contribution = path.match(/^\/goals\/([\w-]+)\/contributions$/);
  if (method === "POST" && contribution) {
    const goal = goals.find((item) => item.id === contribution[1]);
    if (goal) {
      goal.saved = money(goal.saved.cents + Number(body.amountCents ?? 0));
      refresh(goal);
    }
    return json(
      {
        id: `preview-${Date.now()}`,
        amount: money(Number(body.amountCents ?? 0)),
        contributedOn: new Date().toISOString().slice(0, 10),
        note: null,
      },
      201,
    );
  }
  if (method === "PUT" && path === "/preferences/home-layout") {
    preferences = { ...preferences, homeLayout: (body.widgets as Preferences["homeLayout"]) ?? null };
    return json(preferences);
  }
  if (method === "PATCH" && path === "/preferences") {
    preferences = {
      ...preferences,
      emergencyFundReminders: Boolean(body.emergencyFundReminders),
      emergencyFundReminderSnoozedUntil: null,
    };
    return json(preferences);
  }
  if (method === "POST" && path === "/preferences/emergency-fund-reminder/snooze") return json(preferences);
  if (method === "PUT" && path === "/emergency-fund/account") {
    const chosen = accounts.find((account) => account.id === body.accountId);
    for (const list of [accounts, allAccounts]) {
      list.forEach((account) => (account.includeInEmergencyFund = account.id === chosen?.id));
    }
    fund.account = chosen
      ? {
          id: chosen.id,
          name: chosen.name,
          institution: chosen.institution ?? null,
          balance: chosen.balance,
          fromBank: chosen.managedBy === "BANK_FEED",
        }
      : null;
    if (chosen) {
      fund.current = chosen.balance;
      fund.progress = fund.target.cents > 0 ? Math.min(1, chosen.balance.cents / fund.target.cents) : 0;
      fund.shortfall = { ...fund.shortfall, cents: Math.max(0, fund.target.cents - chosen.balance.cents) };
    }
    return json(fund);
  }
  if (method === "POST" && path === "/money-movements") {
    const cents = Number(body.amountCents ?? 0);
    const today = String(body.movedOn ?? new Date().toISOString().slice(0, 10));
    const from = accounts.find((account) => account.id === body.fromAccountId);
    const to = accounts.find((account) => account.id === body.toAccountId);
    if (!Number.isFinite(cents) || cents <= 0) {
      return json({ status: 400, code: "validation_failed", detail: "Enter an amount more than $0." }, 400);
    }
    const available = from ? Math.max(0, from.balance.cents + 10_000) : Infinity;
    if (from && from.type !== "CREDIT_CARD" && from.type !== "LOAN" && cents > available) {
      const limit = (available / 100).toLocaleString("en-NZ", { style: "currency", currency: "NZD" });
      return json(
        {
          status: 422,
          code: "insufficient_funds",
          detail: `You can take up to ${limit} from ${from.name}, which allows a $100 overdraft. Enter that amount or less.`,
        },
        422,
      );
    }
    adjust(from?.id, -cents, to ? `Transfer to ${to.name}` : "Withdrawal", today);
    adjust(to?.id, cents, from ? `Transfer from ${from.name}` : "Deposit", today);
    refreshDashboard((to ? cents : 0) - (from ? cents : 0));
    countThisMonth(body.type, cents, Boolean(body.categoryId));
    return json(
      {
        id: `preview-${Date.now()}`,
        type: body.type,
        fromAccountId: from?.id ?? null,
        fromAccountName: from?.name ?? null,
        toAccountId: to?.id ?? null,
        toAccountName: to?.name ?? null,
        amount: { cents, currency: "NZD" },
        movedOn: today,
        note: body.note ?? null,
      },
      201,
    );
  }
  return json(READ_ONLY, 403);
}

/** Answers the web app's API calls from recorded sandbox data, so the real UI runs without a server. */
export function installMockApi() {
  const realFetch = window.fetch.bind(window);
  window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(
      typeof input === "string" ? input : input instanceof URL ? input.href : input.url,
      "https://preview.local",
    );
    if (url.pathname.startsWith("/api/auth/")) return json({ ok: true });
    if (!url.pathname.startsWith("/api/proxy/")) return realFetch(input, init);
    await new Promise((resolve) => setTimeout(resolve, 120));
    let body: Record<string, unknown> = {};
    if (typeof init?.body === "string") {
      try {
        body = JSON.parse(init.body) as Record<string, unknown>;
      } catch {
        body = {};
      }
    }
    return handle(
      (init?.method ?? "GET").toUpperCase(),
      url.pathname.slice("/api/proxy".length),
      url.searchParams,
      body,
    );
  };
}
