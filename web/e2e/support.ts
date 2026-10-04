import { expect, type Page } from "@playwright/test";

export const DEMO = { email: "demo@kiwifinance.nz", password: "kiwi-demo-2026" };

export async function signIn(page: Page, email = DEMO.email, password = DEMO.password) {
  await page.goto("/sign-in");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/\/home/);
}

export async function signUp(page: Page, name = "Tama") {
  const email = `e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
  await page.goto("/sign-up");
  await page.getByLabel("What should we call you?").fill(name);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill("correct-horse-battery");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL(/\/welcome/);
  return email;
}

/** Fails the test if the page logs an error, so broken screens can't pass quietly. */
export function watchForErrors(page: Page) {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => {
    if (message.type() === "error" && !/status of (404|422)/.test(message.text()))
      errors.push(message.text());
  });
  return () => expect(errors, errors.join("\n")).toEqual([]);
}
