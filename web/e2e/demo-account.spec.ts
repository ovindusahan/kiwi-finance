import { expect, test } from "@playwright/test";
import { signIn, watchForErrors } from "./support";

test.beforeEach(async ({ page }) => {
  await signIn(page);
});

test("the dashboard shows the demo money picture", async ({ page }) => {
  const noErrors = watchForErrors(page);
  await expect(page.getByRole("heading", { name: /, Aroha$/ })).toBeVisible();
  await expect(page.getByText("Cash on hand")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Coming up" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Insights" })).toBeVisible();
  noErrors();
});

test("every screen loads with real data", async ({ page }) => {
  const noErrors = watchForErrors(page);
  const screens: [string, RegExp | string][] = [
    ["/spending", "Where your money goes"],
    ["/transactions", "Every dollar, in one place"],
    ["/budget", "Your monthly budget"],
    ["/goals", "Your goals"],
    ["/goals/new", "What are you saving for?"],
    ["/pay", "What you actually take home"],
    ["/afford", "Can I afford it?"],
    ["/emergency-fund", "Your safety net"],
    ["/progress", "How you're tracking"],
    ["/learn", "Money, explained the Kiwi way"],
    ["/learn/kiwisaver-basics", /KiwiSaver/],
    ["/connect", "Your bank is connected"],
    ["/settings", "Settings"],
    ["/settings/account", "Your account"],
    ["/settings/profile", "Pay and tax"],
    ["/settings/income", "Your income"],
    ["/settings/accounts", "Your accounts"],
    ["/settings/rules", "Categories and rules"],
  ];
  for (const [path, heading] of screens) {
    await page.goto(path);
    await expect(page.getByRole("heading", { level: 1, name: heading })).toBeVisible();
    await expect(page.getByText("That didn't work")).toHaveCount(0);
  }
  noErrors();
});

test("asks whether a camera is affordable and saves it as a goal", async ({ page }) => {
  const noErrors = watchForErrors(page);
  await page.goto("/afford");
  await page.getByLabel("What would you like to buy?").fill("a new camera");
  await page.getByLabel("Price").fill("2500");
  await page.getByRole("button", { name: "Check it" }).click();
  await expect(page.getByText("Realistic date")).toBeVisible();
  await expect(page.getByText("How we worked this out")).toBeVisible();
  await page.getByRole("button", { name: "Save as a goal" }).click();
  await expect(page.getByText("Added to your goals")).toBeVisible();
  await page.getByRole("button", { name: "View your goals" }).click();
  // Each run adds another camera goal to the shared demo account, so look for the first.
  await expect(page.getByRole("heading", { name: "New camera" }).first()).toBeVisible();
  noErrors();
});

test("adds money to a goal and searches transactions", async ({ page }) => {
  const noErrors = watchForErrors(page);
  await page.goto("/goals");
  await page.getByRole("heading", { name: "New laptop" }).first().click();
  const details = page.getByRole("dialog");
  await details.getByLabel("Amount to add").fill("50");
  await details.getByRole("button", { name: "Add money" }).click();
  await expect(page.getByText(/Added \$50\.00 to/)).toBeVisible();

  await page.goto("/transactions");
  await page.getByLabel("Search transactions").fill("countdown");
  await expect(page.getByRole("button", { name: /Countdown/ }).first()).toBeVisible();
  await page
    .getByRole("button", { name: /Countdown/ })
    .first()
    .click();
  await expect(page.getByRole("dialog").getByText("Groceries")).toBeVisible();
  noErrors();
});
