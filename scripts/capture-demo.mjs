import { chromium } from "../frontend/node_modules/playwright/index.mjs";
import { mkdir } from "node:fs/promises";
const url = new URL(process.env.DEMO_URL || "http://localhost:8085");
if (
  process.env.DEMO_CONFIRM_DISPOSABLE !== "YES" ||
  !["localhost", "127.0.0.1"].includes(url.hostname) ||
  !process.env.ADMIN_PASSWORD
)
  throw new Error(
    "Use a disposable loopback server, ADMIN_PASSWORD and DEMO_CONFIRM_DISPOSABLE=YES.",
  );
const browser = await chromium.launch({
  executablePath: process.env.CHROMIUM_PATH,
});
const page = await browser.newPage({ viewport: { width: 1366, height: 1000 } });
await page.goto(url.href);
if (
  (await page
    .locator("header")
    .evaluate((e) => getComputedStyle(e).display)) !== "flex"
)
  throw new Error("Production stylesheet not applied.");
await page
  .getByLabel("Administrator password")
  .fill(process.env.ADMIN_PASSWORD);
await page.getByRole("button", { name: "Open workspace" }).click();
async function job(title, specs) {
  await page.getByLabel("New job title").fill(title);
  await page.getByRole("button", { name: "Create job", exact: true }).click();
  await page.getByLabel("Print specifications", { exact: true }).fill(specs);
  await page
    .getByLabel("JPG or PNG preview")
    .setInputFiles(
      new URL("../frontend/e2e/fixtures/menu.png", import.meta.url).pathname,
    );
  await page.getByRole("button", { name: "Save new version" }).click();
  await page.getByText("Version 1 · PENDING").waitFor();
}
await job(
  "Olive Café · takeaway menus",
  "A5 · 250 gsm uncoated · 100 copies · double-sided · matte finish",
);
await page
  .getByRole("button", { name: "Create / replace review link" })
  .click();
const link = await page
  .getByRole("textbox", { name: "Review link" })
  .inputValue();
const customer = await browser.newContext({
  viewport: { width: 390, height: 844 },
});
const mobile = await customer.newPage();
await mobile.goto(link);
await mobile.getByLabel("Your name").waitFor();
await mkdir(new URL("../docs/evidence/", import.meta.url), { recursive: true });
const output = (name) =>
  new URL("../docs/evidence/" + name, import.meta.url).pathname;
await mobile.screenshot({
  path: output("customer-review-mobile.png"),
  fullPage: true,
});
await mobile.getByLabel("Your name").fill("Alex Customer");
await mobile.getByLabel("I checked this version").check();
await mobile.getByRole("button", { name: "Approve this version" }).click();
await mobile.getByText("Approved. Ready for the shop.").waitFor();
await mobile.screenshot({
  path: output("approved-mobile.png"),
  fullPage: true,
});
await mobile.emulateMedia({ media: "print" });
await mobile.screenshot({ path: output("approved-print.png"), fullPage: true });
await page.getByRole("button", { name: "← All jobs" }).click();
await job(
  "Olive Café · seasonal menu",
  "A5 · 200 gsm recycled · 50 copies · single-sided",
);
await page.getByRole("button", { name: "← All jobs" }).click();
await page
  .getByRole("heading", { name: "Print jobs" })
  .waitFor();
await page.screenshot({
  path: output("admin-work-list-desktop.png"),
  fullPage: true,
});
await page.getByRole("button", { name: /Olive Café · takeaway menus/ }).click();
await page.getByText("Version 1 · APPROVED").waitFor();
await page.screenshot({
  path: output("admin-version-desktop.png"),
  fullPage: true,
});
await browser.close();
console.log(
  "Saved five real application screenshots using fictional café data.",
);
