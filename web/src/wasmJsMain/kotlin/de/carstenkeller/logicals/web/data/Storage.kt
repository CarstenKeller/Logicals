package de.carstenkeller.logicals.web.data

import kotlinx.browser.localStorage

/**
 * Dauerhafter Speicher im Browser (localStorage, nur auf diesem Gerät).
 *
 * Safari kann den Speicher einer Website nach 7 Tagen ohne Nutzung löschen; Web-Apps auf dem
 * Home-Bildschirm sind davon ausgenommen. Fehler (z. B. voller oder gesperrter Speicher) werden
 * geschluckt, damit das Spiel trotzdem läuft.
 */
internal object Storage {
    fun get(key: String): String? = try {
        localStorage.getItem(key)
    } catch (e: Throwable) {
        null
    }

    fun set(key: String, value: String) {
        try {
            localStorage.setItem(key, value)
        } catch (e: Throwable) {
            println("Speichern von $key fehlgeschlagen: ${e.message}")
        }
    }

    fun remove(key: String) {
        try {
            localStorage.removeItem(key)
        } catch (e: Throwable) {
            println("Löschen von $key fehlgeschlagen: ${e.message}")
        }
    }
}
