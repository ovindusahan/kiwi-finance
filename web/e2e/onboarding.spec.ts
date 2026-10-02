import { expect, test } from "@playwright/test";
import { signUp, watchForErrors } from "./support";

test("a new person signs up, fills in their profile and income, and signs out", async ({ page }) => {
  const noErrors = watchForErrors(page);
  await signUp(page, "Tama");

  await expect(page.getByRole("heading", { name: "Let's set up Kiwi Finance" })).toBeVisible();
  await page.getByLabel("How often you're paid").selectOption("FORTNIGHTLY");
  await page.getByRole("switch", { name: "I'm in KiwiSaver" }).click();
  await page.getByRole("button", { name: "Continue" }).click();

  await expect(page.getByRole("heading", { name: "Your income" })).toBeVisible();
  await page.getByLabel("Pay before tax").fill("72000");
  await expect(page.getByText(/take-home each/)).toBeVisible();
  await page.getByRole("button", { name: "Continue" }).click();

  await expect(page.getByRole("heading", { name: "Your accounts" })).toBeVisible();
  await page.getByLabel("Account name").fill("Rainy day");
  await page.getByLabel("Type").selectOption({ label: "Savings" });
  await page.getByLabel("Balance").fill("1500");
  await page.getByRole("button", { name: "Add account" }).click();
  await expect(page.getByText("Account added")).toBeVisible();
  await page.getByRole("button", { name: "Continue" }).click();

  await expect(page.getByRole("heading", { name: "Your safety net" })).toBeVisible();
  await expect(page.getByRole("radio", { name: /Rainy day/ })).toBeChecked();
  await page.getByRole("button", { name: "Use this account" }).click();
  await expect(page.getByRole("heading", { name: "You're all set" })).toBeVisible();
  await page.getByRole("button", { name: "Go to my home" }).click();
  await expect(page).toHaveURL(/\/home/);
  await expect(page.getByRole("heading", { name: "Finish setting up" })).toBeVisible();

  await page.goto("/settings/profile");
  await page.getByLabel("How often you're paid").selectOption("FORTNIGHTLY");
  await page.getByLabel("Region").selectOption("WELLINGTON");
  await page.getByRole("button", { name: "Save profile" }).click();
  await expect(page.getByText("Profile saved")).toBeVisible();

  await page.goto("/settings/income");
  await expect(page.getByText("Main job")).toBeVisible();
  await expect(page.getByText("take-home a year")).toBeVisible();

  await page.goto("/settings/account");
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download my data" }).click();
  expect((await download).suggestedFilename()).toMatch(/^kiwi-finance-data-\d{4}-\d{2}-\d{2}\.json$/);

  await page.getByRole("main").getByRole("button", { name: "Sign out" }).click();
  await expect(page).toHaveURL(/\/sign-in/);
  await page.goto("/home");
  await expect(page).toHaveURL(/\/sign-in\?next=%2Fhome/);
  noErrors();
});

test("the pay calculator works without an account", async ({ page }) => {
  const noErrors = watchForErrors(page);
  await page.goto("/pay-calculator");
  await page.getByLabel("Your pay before tax").fill("85000");
  await expect(page.getByText("You take home")).toBeVisible();
  await expect(page.getByRole("cell", { name: "Income tax (PAYE)" })).toBeVisible();
  await page.getByRole("switch", { name: "I'm repaying a student loan" }).click();
  await expect(page.getByRole("cell", { name: "Student loan" })).toBeVisible();
  await page.getByRole("button", { name: "Add a loan repayment" }).click();
  await page.getByLabel("Loan 1 name").fill("Car loan");
  await page.getByLabel("Amount", { exact: true }).fill("400");
  await expect(page.getByRole("cell", { name: "Loan repayments" })).toBeVisible();
  await expect(page.getByText(/left to spend after/)).toBeVisible();
  noErrors();
});

test("creates a first goal with a suggested monthly amount", async ({ page }) => {
  const noErrors = watchForErrors(page);
  await signUp(page, "Aroha");
  await page.goto("/goals");
  await page.getByRole("button", { name: "Set a goal" }).click();
  await page.getByRole("button", { name: /^Travel/ }).click();
  await page.getByLabel("Name").fill("Japan trip");
  await page.getByLabel("Target").fill("6000");
  await page.getByLabel("Put aside each month").fill("200");
  await expect(page.getByText(/you'll have it by/)).toBeVisible();
  await page.getByRole("button", { name: "Create goal" }).click();
  await expect(page.getByText("Goal created. Let's go!")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Japan trip" })).toBeVisible();

  await page.getByRole("heading", { name: "Japan trip" }).click();
  const details = page.getByRole("dialog", { name: "Japan trip" });
  await details.getByLabel("Amount to add").fill("6000");
  await details.getByRole("button", { name: "Add money" }).click();
  const celebration = page.getByRole("dialog", { name: /Japan trip is fully funded/ });
  await expect(celebration).toBeVisible();
  await celebration.getByRole("button", { name: "Done" }).click();
  await expect(page.getByRole("heading", { name: /Goals you've reached/ })).toBeVisible();
  noErrors();
});

test("changes their name, email and password", async ({ page }) => {
  const noErrors = watchForErrors(page);
  const email = await signUp(page, "Mere");
  await page.goto("/settings/account");
  await expect(page.getByRole("heading", { level: 1, name: "Your account" })).toBeVisible();

  await page.getByLabel("Your name").fill("Mere Parata");
  const newEmail = `renamed-${email}`;
  await page.getByLabel("Email").fill(newEmail);
  await page.getByLabel("Your password, to confirm the new email").fill("correct-horse-battery");
  await page.getByRole("button", { name: "Save details" }).click();
  await expect(page.getByText("Your details are saved")).toBeVisible();
  await expect(page.getByRole("main").getByText("Mere Parata").first()).toBeVisible();

  await page.getByLabel("Current password").fill("wrong-password-here");
  await page.getByLabel("New password", { exact: true }).fill("a-brand-new-password");
  await page.getByLabel("New password again").fill("a-brand-new-password");
  await page.getByRole("button", { name: "Change password" }).click();
  await expect(page.getByText("That password isn't right.")).toBeVisible();
  await page.getByLabel("Current password").fill("correct-horse-battery");
  await page.getByRole("button", { name: "Change password" }).click();
  await expect(page.getByText(/Password changed/)).toBeVisible();

  await page.getByRole("main").getByRole("button", { name: "Sign out" }).click();
  await expect(page).toHaveURL(/\/sign-in/);
  await page.getByLabel("Email").fill(newEmail);
  await page.getByLabel("Password").fill("a-brand-new-password");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).not.toHaveURL(/\/sign-in/);
  noErrors();
});

test("plans a car with finance and saves it as a goal", async ({ page }) => {
  const noErrors = watchForErrors(page);
  await signUp(page, "Tipene");
  await page.goto("/goals/new");
  await page.getByRole("button", { name: /^A car/ }).click();
  await page.getByLabel("Price of the car").fill("18000");
  await expect(page.getByText("Finance options")).toBeVisible();
  await page.getByRole("button", { name: "7 years" }).click();
  await expect(page.getByRole("button", { name: "7 years" })).toHaveAttribute("aria-pressed", "true");
  await expect(page.getByText("Loan repayment")).toBeVisible();
  await page.getByRole("button", { name: "Save as a goal" }).click();
  await expect(page.getByText("Car added to your goals")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Car" })).toBeVisible();
  noErrors();
});

test("sets a budget before there is any spending history", async ({ page }) => {
  const noErrors = watchForErrors(page);
  await signUp(page, "Mere");
  await page.goto("/budget");
  await expect(page.getByText("There isn't enough transaction history")).toBeVisible();
  await page.getByLabel("Rent", { exact: true }).fill("1800");
  await page.getByLabel("Groceries", { exact: true }).fill("600");
  await page.getByRole("button", { name: "Remove Fuel" }).click();
  await page.getByLabel("Add a category").selectOption({ label: "Clothing" });
  await page.getByLabel("Clothing", { exact: true }).fill("80");
  await page.getByRole("button", { name: "Create budget" }).click();
  await expect(page.getByText("Budget created")).toBeVisible();
  await expect(page.getByText("$2,480", { exact: true }).first()).toBeVisible();

  await page.getByRole("button", { name: "Edit budget" }).click();
  await page.getByLabel("Groceries", { exact: true }).fill("650");
  await page.getByRole("button", { name: "Save budget" }).click();
  await expect(page.getByText("Budget saved")).toBeVisible();
  await expect(page.getByText("$2,530", { exact: true }).first()).toBeVisible();
  noErrors();
});

test("customises the home screen and keeps the layout", async ({ page }) => {
  await signUp(page, "Ana");
  await page.goto("/home");
  await expect(page.getByRole("heading", { name: "Coming up" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Kiwi Score" })).toHaveCount(0);

  await page.getByRole("button", { name: "Customise" }).click();
  await page.getByRole("button", { name: "Remove Coming up" }).click();
  await page.getByRole("button", { name: "Add Kiwi Score" }).click();
  await page
    .getByRole("group", { name: "Size of Kiwi Score" })
    .getByRole("button", { name: "Full width" })
    .click();
  await page.getByRole("button", { name: "Save layout" }).click();
  await expect(page.getByText("Home screen saved")).toBeVisible();

  await expect(page.getByRole("heading", { name: "Coming up" })).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "Kiwi Score" })).toBeVisible();

  await page.reload();
  await expect(page.getByRole("heading", { name: "Kiwi Score" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Coming up" })).toHaveCount(0);

  await page.getByRole("button", { name: "Customise" }).click();
  await page.getByRole("button", { name: "Reset to default" }).click();
  await expect(page.getByText("Home screen reset")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Coming up" })).toBeVisible();
});

test("chooses an emergency fund account and moves money into it", async ({ page }) => {
  const noErrors = watchForErrors(page);
  await signUp(page, "Rangi");
  await page.goto("/home");
  await expect(page.getByRole("link", { name: "Choose an account" })).toBeVisible();

  const accounts: [string, string, string][] = [
    ["Everyday", "Everyday", "2000"],
    ["Rainy day", "Savings", "500"],
  ];
  for (const [name, type, balance] of accounts) {
    await page.goto("/settings/accounts");
    await page.getByRole("button", { name: "Add account" }).click();
    const dialog = page.getByRole("dialog");
    await dialog.getByLabel("Name").fill(name);
    await dialog.getByLabel("Type").selectOption({ label: type });
    await dialog.getByLabel("Current balance").fill(balance);
    await dialog.getByRole("button", { name: "Add account" }).click();
    await expect(page.getByText("Account added")).toBeVisible();
  }

  await page.goto("/emergency-fund");
  await expect(page.getByRole("radio", { name: /Rainy day/ })).toBeChecked();
  await page.getByRole("button", { name: "Use this account" }).click();
  await expect(page.getByText("Rainy day is now your emergency fund")).toBeVisible();

  await page.getByRole("button", { name: "Add money" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Amount").fill("300");
  await dialog.getByRole("button", { name: "Move money" }).click();
  await expect(page.getByText(/Moved \$300.00 to Rainy day/)).toBeVisible();
  await expect(
    page.getByRole("region", { name: "Emergency fund account" }).getByText("$800.00"),
  ).toBeVisible();
  await expect(page.getByText("Transfer from Everyday")).toBeVisible();

  await page.goto("/home");
  await expect(page.getByRole("link", { name: "Choose an account" })).toHaveCount(0);
  noErrors();
});

test("asks what an ATM withdrawal was spent on", async ({ page }) => {
  const noErrors = watchForErrors(page);
  await signUp(page, "Moana");
  await page.goto("/settings/accounts");
  await page.getByRole("button", { name: "Add account" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Name").fill("Everyday");
  await dialog.getByLabel("Type").selectOption({ label: "Everyday" });
  await dialog.getByLabel("Current balance").fill("500");
  await dialog.getByRole("button", { name: "Add account" }).click();
  await expect(page.getByText("Account added")).toBeVisible();

  await page.goto("/transactions");
  await page.getByRole("button", { name: "Add", exact: true }).click();
  const add = page.getByRole("dialog");
  await add.getByLabel("Description").fill("ATM WITHDRAWAL QUEEN ST");
  await add.getByRole("button", { name: "Money out" }).click();
  await add.getByLabel("Amount").fill("120");
  await add.getByRole("button", { name: "Add transaction" }).click();
  await expect(add).toHaveCount(0);

  await page.goto("/home");
  await expect(page.getByText(/You took out \$120 in cash/)).toBeVisible();
  await page.getByRole("button", { name: "Sort out cash" }).click();
  const cash = page.getByRole("dialog");
  await cash.getByLabel("Category 1").selectOption({ label: "Groceries" });
  await cash.getByLabel("Amount 1").fill("80");
  await expect(cash.getByText("$40.00 stays in your cash wallet.")).toBeVisible();
  await cash.getByRole("button", { name: "Save" }).click();
  await expect(page.getByText(/Your cash wallet now has \$40\.00/)).toBeVisible();
  await expect(page.getByText(/You took out \$120 in cash/)).toHaveCount(0);
  noErrors();
});
