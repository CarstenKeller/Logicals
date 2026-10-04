package de.carstenkeller.logicals.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.carstenkeller.logicals.core.PuzzleType
import de.carstenkeller.logicals.data.ThemeMode
import de.carstenkeller.logicals.ui.Texte
import de.carstenkeller.logicals.ui.Texte.description
import de.carstenkeller.logicals.ui.Texte.title

@Composable
fun MainMenuScreen(
    onSelect: (PuzzleType) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    versionName: String,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(Texte.APP_NAME, style = MaterialTheme.typography.displaySmall)
            Text(
                Texte.MENU_SUBTITLE,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (type in PuzzleType.entries) {
                Card(onClick = { onSelect(type) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(type.title, style = MaterialTheme.typography.headlineSmall)
                        Text(type.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            ThemeSelector(themeMode, onThemeModeChange)
            Text(
                Texte.version(versionName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(Texte.THEME_TITLE, style = MaterialTheme.typography.titleSmall)
        val options = ThemeMode.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(
                        when (mode) {
                            ThemeMode.SYSTEM -> Texte.THEME_SYSTEM
                            ThemeMode.LIGHT -> Texte.THEME_LIGHT
                            ThemeMode.DARK -> Texte.THEME_DARK
                        },
                    )
                }
            }
        }
    }
}
