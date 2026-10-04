// Starttest der Web-App im Headless-Chrome.
//
// Pflicht: Die App startet (entfernt den Ladehinweis) ohne Seitenfehler.
// Zusätzlich (nur protokolliert): Durchklicken Menü → Sudoku → neues Rätsel → Ziffer → Hinweis.
// Bedienelemente werden über den Barrierefreiheits-Baum gefunden, den Compose ins DOM spiegelt;
// getippt wird auf deren Position im Canvas. Anfragen an fremde Server werden gemeldet.
// Aufruf: node web-starttest.mjs <url>
import { chromium } from "playwright-core";

const [url] = process.argv.slice(2);
const origin = new URL(url).origin;
const browser = await chromium.launch({ channel: "chrome", args: ["--no-sandbox"] });
const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, hasTouch: false });
let pageErrors = 0;
page.on("console", (m) => { if (!m.text().includes("WebGL")) console.log(`[console.${m.type()}] ${m.text()}`); });
page.on("pageerror", (e) => { pageErrors++; console.log(`[pageerror] ${e.message}`); });
page.on("requestfailed", (r) => console.log(`[requestfailed] ${r.url()} ${r.failure()?.errorText}`));
page.on("request", (r) => { if (!r.url().startsWith(origin)) console.log(`[fremder Server] ${r.url()}`); });
page.on("response", (r) => { if (r.status() >= 400) console.log(`[http ${r.status()}] ${r.url()}`); });

async function tree(title) {
    const snapshot = await page.locator("body").ariaSnapshot().catch((e) => `(kein Baum: ${e.message})`);
    console.log(`----- ${title} -----\n${snapshot}`);
}

async function tap(text, options = {}) {
    const el = page.getByText(text, { exact: options.exact ?? true }).first();
    const box = await el.boundingBox({ timeout: 5000 });
    if (!box) throw new Error(`"${text}" nicht gefunden`);
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    console.log(`Getippt: "${text}" bei ${Math.round(box.x + box.width / 2)},${Math.round(box.y + box.height / 2)}`);
    await page.waitForTimeout(options.wait ?? 1500);
}

let started = false;
try {
    const t0 = Date.now();
    await page.goto(url);
    await page.waitForSelector("#loading", { state: "detached", timeout: 60000 });
    console.log(`Startzeit bis zur Anzeige: ${Date.now() - t0} ms`);
    started = true;
    await page.waitForTimeout(1500);
    await tree("Hauptmenü");

    await tap("Sudoku");
    await tree("Sudoku-Startseite");
    await tap("Rätsel generieren", { wait: 4000 });
    await tree("Spiel");
    await tap("💡 Hinweis", { wait: 3000 });
    await tree("Spiel mit Hinweis");
} catch (e) {
    console.log(started ? `FLOW-FEHLER: ${e.message}` : `Start fehlgeschlagen: ${e.message}`);
    if (!started) console.log("Ladetext: " + await page.textContent("#loading-text").catch(() => "?"));
}
await browser.close();
const ok = started && pageErrors === 0;
console.log(ok ? "App gestartet." : "App nicht (fehlerfrei) gestartet.");
process.exit(ok ? 0 : 1);
