// Starttest der Web-App im Headless-Chrome: wartet, bis die App den Ladehinweis entfernt,
// protokolliert Konsolenmeldungen, Fehler und Anfragen an fremde Server und gibt
// Bildschirmfotos als Base64 ins Log aus (Artefakte sind aus der Entwicklungsumgebung
// nicht abrufbar). Aufruf: node web-starttest.mjs <url>
// Optional: SCREENSHOT_CLICKS="x,y;x,y" tippt nacheinander auf diese Punkte (CSS-Pixel)
// und macht nach jedem Tippen ein Bildschirmfoto.
import { chromium } from "playwright-core";

const [url] = process.argv.slice(2);
const origin = new URL(url).origin;
const browser = await chromium.launch({ channel: "chrome", args: ["--no-sandbox"] });
const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
page.on("console", (m) => console.log(`[console.${m.type()}] ${m.text()}`));
page.on("pageerror", (e) => console.log(`[pageerror] ${e.message}`));
page.on("requestfailed", (r) => console.log(`[requestfailed] ${r.url()} ${r.failure()?.errorText}`));
page.on("request", (r) => { if (!r.url().startsWith(origin)) console.log(`[fremder Server] ${r.url()}`); });
page.on("response", (r) => { if (r.status() >= 400) console.log(`[http ${r.status()}] ${r.url()}`); });

async function shot(name) {
    const data = (await page.screenshot({ type: "jpeg", quality: 55 })).toString("base64");
    console.log(`SCREENSHOT-BEGIN ${name}`);
    for (let i = 0; i < data.length; i += 2000) console.log("SCREENSHOT " + data.slice(i, i + 2000));
    console.log(`SCREENSHOT-END ${name}`);
}

let ok = false;
try {
    const start = Date.now();
    await page.goto(url);
    await page.waitForSelector("#loading", { state: "detached", timeout: 60000 });
    console.log(`Startzeit bis zur Anzeige: ${Date.now() - start} ms`);
    ok = true;
    await page.waitForTimeout(1500);
    await shot("start");
    const clicks = (process.env.SCREENSHOT_CLICKS || "").split(";").filter((c) => c.trim());
    for (const [i, c] of clicks.entries()) {
        const [x, y] = c.split(",").map(Number);
        await page.mouse.click(x, y);
        await page.waitForTimeout(2500);
        await shot(`klick-${i + 1}`);
    }
} catch (e) {
    console.log("Fehler: " + e.message);
    console.log("Ladetext: " + await page.textContent("#loading-text").catch(() => "?"));
    await shot("fehler").catch(() => {});
}
await browser.close();
console.log(ok ? "App gestartet." : "App nicht gestartet.");
process.exit(ok ? 0 : 1);
