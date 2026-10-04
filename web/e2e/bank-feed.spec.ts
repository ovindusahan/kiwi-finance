import { expect, test } from "@playwright/test";
import { signUp, watchForErrors } from "./support";

test("connects a sandbox personal app and brings in transactions", async ({ page }) => {
  const noErrors = watchForErrors(page);
  await signUp(page, "Mere");
  await page.goto("/connect");
  await expect(page.getByRole("heading", { level: 1, name: /Connect/ })).toBeVisible();

  await page.getByRole("button", { name: /Use your own Akahu personal app/ }).click();
  await page.getByLabel("App token").fill("app_token_sandbox_e2e");
  await page.getByLabel("User token").fill("user_token_sandbox_e2e");
  await page.getByRole("button", { name: "Enter tokens" }).click();
  await expect(page.getByText("Connected to Akahu")).toBeVisible();

  await page.getByRole("switch", { name: "Sync Everyday" }).click();
  await expect(page.getByText("Syncing Everyday")).toBeVisible();
  await expect(page.getByText(/\d+ new and \d+ updated transaction/)).toBeVisible({ timeout: 30_000 });

  await page.goto("/transactions");
  await expect(page.getByText("Acme Ltd Salary").first()).toBeVisible();
  noErrors();
});

test("connects through the Akahu sign-in flow", async ({ page }) => {
  const noErrors = watchForErrors(page);
  await signUp(page, "Wiremu");
  await page.goto("/connect");
  await page
    .getByRole("button", { name: /Connect with Akahu/ })
    .first()
    .click();
  await page
    .getByRole("button", { name: /Connect with Akahu/ })
    .last()
    .click();
  await expect(page.getByRole("heading", { name: "Akahu sandbox" })).toBeVisible();
  await page.getByRole("link", { name: "Approve access" }).click();
  await expect(page).toHaveURL(/\/connect\?connected=1/);
  await expect(page.getByText("Kia pai! Your bank is connected.")).toBeVisible();
  noErrors();
});
