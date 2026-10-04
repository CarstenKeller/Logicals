package de.carstenkeller.logicals.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import de.carstenkeller.logicals.web.ui.WebApp
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(requireNotNull(document.getElementById("app"))) {
        WebApp()
    }
}
