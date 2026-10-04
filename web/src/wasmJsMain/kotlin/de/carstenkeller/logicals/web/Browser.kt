package de.carstenkeller.logicals.web

import kotlinx.browser.document
import org.w3c.dom.events.Event

/** Kleine Brücke zur Seite (index.html) und zu Browser-Ereignissen. */
internal object Browser {
    /** Build-Nummer, von der CI in index.html eingetragen. */
    val version: String get() = jsVersion()

    /** Entfernt den Ladehinweis aus index.html, sobald die App gezeichnet wird. */
    fun hideLoadingScreen() {
        document.getElementById("loading")?.remove()
    }

    /** Färbt Seitenhintergrund und Statusleiste (theme-color) passend zur Darstellung. */
    fun setPageColor(cssColor: String) {
        jsSetPageColor(cssColor)
    }

    val pageHidden: Boolean get() = jsPageHidden()

    private val visibilityListeners = mutableListOf<() -> Unit>()

    init {
        // Ein einziger Browser-Listener; Kotlin-Lambdas werden beim Übergeben an JS jedes Mal
        // neu verpackt und ließen sich sonst nicht wieder abmelden.
        document.addEventListener("visibilitychange", { _: Event -> visibilityListeners.toList().forEach { it() } })
    }

    /** Meldet, wenn die Seite unsichtbar wird oder wieder erscheint (App-Wechsel, Sperre). */
    fun onVisibilityChange(listener: () -> Unit): () -> Unit {
        visibilityListeners += listener
        return { visibilityListeners -= listener }
    }
}

private fun jsVersion(): String = js("(globalThis.LOGICALS_VERSION || 'lokal')")

private fun jsPageHidden(): Boolean = js("document.hidden")

private fun jsSetPageColor(color: String): Unit = js(
    """{
        document.documentElement.style.backgroundColor = color;
        document.body.style.backgroundColor = color;
        document.querySelectorAll('meta[name="theme-color"]').forEach(function (m) { m.setAttribute('content', color); });
    }""",
)
