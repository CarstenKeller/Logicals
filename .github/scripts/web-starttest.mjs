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

/** Tippt auf den ersten Button, dessen Name mit [name] beginnt (Position im Canvas). */
async function tap(name, wait = 1500) {
    const pattern = name instanceof RegExp ? name : new RegExp("^" + name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));
    const el = page.getByRole("button", { name: pattern }).first();
    let box = await el.boundingBox({ timeout: 5000 });
    if (!box) throw new Error(`Button "${name}" nicht gefunden`);
    // Außerhalb des sichtbaren Bereichs: mit dem Mausrad dorthin scrollen.
    const viewport = page.viewportSize();
    if (box.y < 0 || box.y + box.height > viewport.height || (box.x === 0 && box.y === 0)) {
        await page.mouse.move(viewport.width / 2, viewport.height / 2);
        await page.mouse.wheel(0, 2000);
        await page.waitForTimeout(800);
        box = await el.boundingBox({ timeout: 5000 });
        if (!box) throw new Error(`Button "${name}" nach dem Scrollen nicht gefunden`);
    }
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    console.log(`Getippt: "${name}" bei ${Math.round(box.x + box.width / 2)},${Math.round(box.y + box.height / 2)}`);
    await page.waitForTimeout(wait);
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
    await tap("Rätsel generieren", 4000);
    await tree("Spiel");
    await tap("💡 Hinweis", 3000);
    await tree("Spiel mit Hinweis");
    await tap(/eintragen$/);
    await tree("Hinweis übernommen");
    await tap("Zurück");
    await tree("Sudoku-Startseite mit gespeichertem Stand");
    await tap("Zurück");

    // Übrige Rätsel: neues Rätsel erzeugen, Spielbildschirm prüfen, zurück ins Menü.
    for (const name of ["Kakuro", "Futoshiki", "KenKen", "Skyscraper", "Catsweeper"]) {
        const t = Date.now();
        await tap(name);
        await tap("Rätsel generieren", 500);
        await page.getByText(/^Zeit /).first().waitFor({ timeout: 60000 });
        console.log(`${name}: Spiel nach ${Date.now() - t} ms bereit`);
        await page.waitForTimeout(500);
        await tap("Zurück");
        await tap("Zurück");
    }
    await tree("Hauptmenü am Ende");
} catch (e) {
    console.log(started ? `FLOW-FEHLER: ${e.message}` : `Start fehlgeschlagen: ${e.message}`);
    if (!started) console.log("Ladetext: " + await page.textContent("#loading-text").catch(() => "?"));
}
await browser.close();
const ok = started && pageErrors === 0;
console.log(ok ? "App gestartet." : "App nicht (fehlerfrei) gestartet.");
process.exit(ok ? 0 : 1);
