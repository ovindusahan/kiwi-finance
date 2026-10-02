// Records the sandbox demo account's API responses for the static demo build.
// Run the API with the "local,sandbox" profiles first.
import { writeFile } from "node:fs/promises";
import path from "node:path";

const api = process.env.KIWI_API_URL ?? "http://localhost:8080";
const out = process.argv[2] ?? path.join(import.meta.dirname, "fixtures.json");

async function call(method, route, token, body) {
  const response = await fetch(`${api}/api/v1${route}`, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) throw new Error(`${method} ${route} returned ${response.status}`);
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

const { accessToken: token } = await call("POST", "/auth/login", null, {
  email: "demo@kiwifinance.nz",
  password: "kiwi-demo-2026",
});
const responses = {};
const get = async (route) => (responses[`GET ${route}`] = await call("GET", route, token));

for (const route of [
  "/auth/me",
  "/profile",
  "/dashboard",
  "/accounts",
  "/accounts?includeArchived=true",
  "/categories",
  "/categorisation-rules",
  "/income-sources",
  "/imports",
  "/analysis/cashflow?months=12",
  "/analysis/recurring",
  "/insights",
  "/budgets/recommendation",
  "/budgets/current",
  "/emergency-fund",
  "/preferences",
  "/cash-withdrawals",
  "/goals/plan",
  "/money-movements?limit=10",
  "/planning/purchase-plans",
  "/progress",
  "/bank-feeds/akahu/setup-guide",
  "/education/glossary",
]) {
  await get(route);
}
const [year, month] = responses["GET /dashboard"].today.split("-").map(Number);
for (let back = 1; back <= 6; back++) {
  const shifted = year * 12 + month - 1 - back;
  await get(
    `/budgets/current?month=${Math.floor(shifted / 12)}-${String((shifted % 12) + 1).padStart(2, "0")}`,
  );
}
for (const months of [1, 3, 6, 12]) await get(`/analysis/spending?months=${months}`);
for (const goal of await get("/goals")) await get(`/goals/${goal.id}/contributions`);
for (const connection of await get("/bank-feeds/connections"))
  await get(`/bank-feeds/connections/${connection.id}/syncs`);
for (const guide of await get("/education/guides")) await get(`/education/guides/${guide.slug}`);

const transactions = [];
let cursor = null;
do {
  const page = await call("GET", `/transactions?limit=200${cursor ? `&cursor=${cursor}` : ""}`, token);
  transactions.push(...page.items);
  cursor = page.nextCursor;
} while (cursor);

const nextJuly = `${month >= 6 ? year + 1 : year}-07-01`;
const inThreeYears = `${year + 3}-${String(month).padStart(2, "0")}-01`;
const examples = { affordability: [], payCalculator: [], loan: [] };
for (const request of [
  { itemName: "a weekend in Queenstown", priceCents: 120_000, funding: "CASH" },
  { itemName: "noise-cancelling headphones", priceCents: 50_000, funding: "CASH" },
  { itemName: "a new laptop", priceCents: 250_000, funding: "CASH" },
  { itemName: "an e-bike", priceCents: 400_000, funding: "CASH" },
  { itemName: "a trip to Japan", priceCents: 600_000, funding: "CASH", desiredDate: nextJuly },
  { itemName: "a used car", priceCents: 1_200_000, funding: "CASH" },
  { itemName: "a wedding", priceCents: 3_500_000, funding: "CASH" },
  {
    itemName: "a house deposit",
    priceCents: 8_000_000,
    funding: "CASH",
    firstHome: true,
    desiredDate: inThreeYears,
  },
  {
    itemName: "a Toyota Aqua",
    priceCents: 1_800_000,
    funding: "FINANCE",
    depositCents: 400_000,
    loanRate: 0.1195,
    loanTermMonths: 48,
    loanFeesCents: 35_000,
  },
]) {
  examples.affordability.push({
    request,
    response: await call("POST", "/planning/affordability", token, request),
  });
}
const periods = { ANNUALLY: 1, MONTHLY: 12, FORTNIGHTLY: 26, WEEKLY: 52 };
for (const annual of [4_500_000, 6_500_000, 8_500_000, 12_000_000, 18_000_000]) {
  for (const [frequency, divisor] of Object.entries(periods)) {
    for (const kiwiSaverRate of [null, 0.03, 0.035, 0.04, 0.06, 0.08, 0.1]) {
      for (const studentLoan of [false, true]) {
        const request = {
          amountCents: Math.round(annual / divisor),
          frequency,
          basis: "GROSS",
          taxCode: "M",
          studentLoan,
          ...(kiwiSaverRate == null ? {} : { kiwiSaverRate }),
        };
        examples.payCalculator.push({
          request,
          response: await call("POST", "/income/pay-calculator", token, request),
        });
      }
    }
  }
}
for (const request of [
  { amountCents: 1_400_000, annualRate: 0.1195, termMonths: 48, feesCents: 35_000 },
  { amountCents: 2_500_000, annualRate: 0.0995, termMonths: 60, feesCents: 0 },
]) {
  examples.loan.push({ request, response: await call("POST", "/planning/loan", token, request) });
}

examples.purchaseImpact = [];
for (const request of [
  ...[1_000_000, 1_800_000, 2_500_000, 4_000_000, 6_000_000].flatMap((priceCents) =>
    [36, 48, 60, 84, 0].map((termMonths) => ({ kind: "CAR", priceCents, termMonths, firstHome: false })),
  ),
  ...[45_000_000, 65_000_000, 90_000_000].flatMap((priceCents) =>
    [240, 300, 360].flatMap((termMonths) =>
      [true, false].map((firstHome) => ({ kind: "HOUSE", priceCents, termMonths, firstHome })),
    ),
  ),
]) {
  examples.purchaseImpact.push({
    request,
    response: await call("POST", "/planning/purchase-impact", token, request),
  });
}

await writeFile(out, JSON.stringify({ responses, transactions, examples }));
console.log(
  `${Object.keys(responses).length} responses and ${transactions.length} transactions written to ${out}`,
);
