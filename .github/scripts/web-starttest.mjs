// Starttest der Web-App im Headless-Chrome: wartet, bis die App den Ladehinweis entfernt,
// und protokolliert Konsolenmeldungen und Fehler. Aufruf: node web-starttest.mjs <url> <screenshot>
import { chromium } from "playwright-core";

const [url, screenshot] = process.argv.slice(2);
const browser = await chromium.launch({ channel: "chrome", args: ["--no-sandbox"] });
const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 });
page.on("console", (m) => console.log(`[console.${m.type()}] ${m.text()}`));
page.on("pageerror", (e) => console.log(`[pageerror] ${e.message}`));
page.on("requestfailed", (r) => console.log(`[requestfailed] ${r.url()} ${r.failure()?.errorText}`));
page.on("response", (r) => { if (r.status() >= 400) console.log(`[http ${r.status()}] ${r.url()}`); });

let ok = false;
try {
    await page.goto(url);
    await page.waitForSelector("#loading", { state: "detached", timeout: 60000 });
    ok = true;
    await page.waitForTimeout(2000);
} catch (e) {
    console.log("Ladehinweis nicht verschwunden: " + e.message);
    console.log("Ladetext: " + await page.textContent("#loading-text").catch(() => "?"));
}
await page.screenshot({ path: screenshot });
await browser.close();
console.log(ok ? "App gestartet." : "App nicht gestartet.");
process.exit(ok ? 0 : 1);
