package de.carstenkeller.logicals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.carstenkeller.logicals.ui.LogicalsApp
import de.carstenkeller.logicals.ui.theme.LogicalsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LogicalsTheme {
                LogicalsApp()
            }
        }
    }
}
