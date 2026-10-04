import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { signIn } from "./support";

async function audit(page: Page, path: string) {
  await page.goto(path);
  await page.waitForLoadState("networkidle");
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
    .analyze();
  const summary = results.violations.map(
    (violation) =>
      `${path} ${violation.id}: ${violation.help}\n${violation.nodes
        .slice(0, 3)
        .map((node) => `  ${node.target.join(" ")}`)
        .join("\n")}`,
  );
  expect(summary, summary.join("\n")).toEqual([]);
}

test("public pages meet WCAG 2.2 AA", async ({ page }) => {
  for (const path of ["/", "/sign-in", "/sign-up", "/pay-calculator"]) await audit(page, path);
});

test("signed-in screens meet WCAG 2.2 AA", async ({ page }) => {
  test.setTimeout(180_000);
  await signIn(page);
  for (const path of [
    "/home",
    "/spending",
    "/transactions",
    "/budget",
    "/goals",
    "/goals/new",
    "/pay",
    "/afford",
    "/emergency-fund",
    "/progress",
    "/learn",
    "/learn/emergency-fund",
    "/connect",
    "/settings",
    "/settings/profile",
    "/settings/income",
    "/settings/accounts",
    "/settings/rules",
    "/settings/account",
  ]) {
    await audit(page, path);
  }
});
