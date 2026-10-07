import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
const png = readFileSync("e2e/fixtures/menu.png");
async function login(page: any) {
  await page.goto("/");
  await expect(page.locator("header")).toHaveCSS("display", "flex");
  await page
    .getByLabel("Administrator password")
    .fill(process.env["ADMIN_PASSWORD"] || "local-test-password-only");
  await page.getByRole("button", { name: "Open workspace" }).click();
  await expect(
    page.getByRole("heading", { name: "Print jobs" }),
  ).toBeVisible();
}
async function create(page: any) {
  await page.getByLabel("New job title").fill("Olive Café · menus");
  await page.getByRole("button", { name: "Create job", exact: true }).click();
  await page
    .getByLabel("Print specifications", { exact: true })
    .fill("A5 · 250 gsm uncoated · 100 copies · double-sided");
  await page
    .getByLabel("JPG or PNG preview")
    .setInputFiles({ name: "menu.png", mimeType: "image/png", buffer: png });
  await page.getByRole("button", { name: "Save new version" }).click();
  await expect(page.getByText("Version 1 · PENDING")).toBeVisible();
}
async function share(page: any) {
  await page
    .getByRole("button", { name: "Create / replace review link" })
    .first()
    .click();
  return await page.getByRole("textbox", { name: "Review link" }).inputValue();
}
test("mobile approval, retry, immutable result and printable summary", async ({
  page,
  browser,
}) => {
  await login(page);
  await create(page);
  const url = await share(page);
  const customer = await browser.newContext({
    viewport: { width: 390, height: 844 },
  });
  const p = await customer.newPage();
  await p.goto(url);
  await expect(
    p.getByRole("button", { name: "Approve this version" }),
  ).toBeDisabled();
  await p.getByLabel("Your name").fill("Alex Customer");
  await p.getByLabel("I checked this version").check();
  await p.getByRole("button", { name: "Approve this version" }).click();
  await expect(p.getByText("Approved. Ready for the shop.")).toBeVisible();
  await p.reload();
  await expect(
    p.getByRole("heading", { name: "Approval record" }),
  ).toBeVisible();
  await p.screenshot({
    path: "test-results/approved-mobile.png",
    fullPage: true,
  });
  await p.emulateMedia({ media: "print" });
  await expect(
    p.getByRole("heading", { name: "Approval record" }),
  ).toBeVisible();
  await p.screenshot({
    path: "test-results/approved-print.png",
    fullPage: true,
  });
  await page.reload();
  await page
    .getByRole("button", { name: /Olive Café/ })
    .first()
    .click();
  await expect(
    page.getByRole("button", { name: "Save new version" }),
  ).toHaveCount(0);
  await customer.close();
});
test("revision, superseded link and revoked link recovery", async ({
  page,
  browser,
}) => {
  await login(page);
  await create(page);
  const first = await share(page);
  const context = await browser.newContext();
  const customer = await context.newPage();
  await customer.goto(first);
  await customer.getByLabel("Your name").fill("Alex");
  await customer
    .getByLabel("Notes or requested changes")
    .fill("Please correct the phone number.");
  await customer.getByRole("button", { name: "Request a revision" }).click();
  await expect(customer.getByText("Changes requested.")).toBeVisible();
  await page.getByRole("button", { name: "Save new version" }).click();
  await expect(page.getByText("Version 2 · PENDING")).toBeVisible();
  const second = await share(page);
  await page.getByRole("button", { name: "Save new version" }).click();
  await expect(page.getByText("Version 3 · PENDING")).toBeVisible();
  await customer.goto(second);
  await expect(
    customer.getByText("A newer version is available."),
  ).toBeVisible();
  const third = await share(page);
  await page
    .getByRole("button", { name: "Revoke review link" })
    .first()
    .click();
  await customer.goto(third);
  await expect(customer.getByRole("alert")).toContainText(
    "This link is unavailable",
  );
  await context.close();
});
test("invalid password, private API, forged upload and disconnected retry", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.locator("header")).toHaveCSS("display", "flex");
  await page.getByLabel("Administrator password").fill("incorrect");
  await page.getByRole("button", { name: "Open workspace" }).click();
  await expect(page.getByRole("alert")).toContainText("Sign in");
  expect((await page.request.get("/api/admin/jobs")).status()).toBe(401);
  await login(page);
  await page.getByLabel("New job title").fill("Validation sample");
  await page.getByRole("button", { name: "Create job", exact: true }).click();
  await page.getByLabel("Print specifications", { exact: true }).fill("A5");
  await page.getByLabel("JPG or PNG preview").setInputFiles({
    name: "fake.png",
    mimeType: "image/png",
    buffer: Buffer.from("<script>alert(1)</script>"),
  });
  await page.getByRole("button", { name: "Save new version" }).click();
  await expect(page.getByRole("alert")).toContainText("Unable to save");
  await page
    .getByLabel("JPG or PNG preview")
    .setInputFiles({ name: "proof.png", mimeType: "image/png", buffer: png });
  await page.route("**/api/admin/jobs/*/versions", (route) => route.abort());
  await page.getByRole("button", { name: "Save new version" }).click();
  await expect(page.getByRole("alert")).toContainText("Connection failed");
  await expect(page.getByRole("button", {name:"Save new version"})).toBeEnabled();
  await page.unroute("**/api/admin/jobs/*/versions");
  await page.getByRole("button", { name: "Save new version" }).click();
  await expect(page.getByText("Version 1 · PENDING")).toBeVisible();
});

test("keyboard navigation and focused accessibility audit", async ({
  page,
  browser,
}) => {
  const { default: AxeBuilder } = await import("@axe-core/playwright");
  await page.goto("/");
  await expect(page.locator("header")).toHaveCSS("display", "flex");
  await page.keyboard.press("Tab");
  await expect(
    page.getByRole("link", { name: "P Print Approval System" }),
  ).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(page.getByLabel("Administrator password")).toBeFocused();
  await page.keyboard.type(
    process.env["ADMIN_PASSWORD"] || "local-test-password-only",
  );
  expect(
    (
      await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
        .analyze()
    ).violations,
  ).toEqual([]);
  await page.keyboard.press("Tab");
  await page.keyboard.press("Enter");
  await expect(
    page.getByRole("heading", { name: "Print jobs" }),
  ).toBeVisible();
  expect(
    (
      await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
        .analyze()
    ).violations,
  ).toEqual([]);
  await create(page);
  expect(
    (
      await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
        .analyze()
    ).violations,
  ).toEqual([]);
  const url = await share(page);
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
  });
  const customer = await context.newPage();
  await customer.goto(url);
  await expect(customer.getByLabel("Your name")).toBeVisible();
  expect(
    (
      await new AxeBuilder({ page: customer })
        .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
        .analyze()
    ).violations,
  ).toEqual([]);
  await customer.getByLabel("Your name").focus();
  await customer.keyboard.type("Keyboard Customer");
  await customer.keyboard.press("Tab");
  await expect(customer.getByLabel("Notes or requested changes")).toBeFocused();
  await customer.keyboard.press("Tab");
  await customer.keyboard.press("Space");
  await expect(customer.getByLabel("I checked this version")).toBeChecked();
  await expect(customer.getByRole("button", {name:"Approve this version"})).toBeEnabled();
  await customer.keyboard.press("Tab");
  await expect(customer.getByRole("button", {name:"Approve this version"})).toBeFocused();
  await customer.keyboard.press("Enter");
  await expect(
    customer.getByText("Approved. Ready for the shop."),
  ).toBeVisible();
  expect(
    (
      await new AxeBuilder({ page: customer })
        .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
        .analyze()
    ).violations,
  ).toEqual([]);
  await context.close();
});
