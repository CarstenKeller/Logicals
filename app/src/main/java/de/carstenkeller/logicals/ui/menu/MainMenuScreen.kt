package de.carstenkeller.logicals.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.carstenkeller.logicals.R
import de.carstenkeller.logicals.core.PuzzleType
import de.carstenkeller.logicals.ui.descriptionRes
import de.carstenkeller.logicals.ui.titleRes

@Composable
fun MainMenuScreen(onSelect: (PuzzleType) -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(R.string.menu_subtitle),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (type in PuzzleType.entries) {
                Card(onClick = { onSelect(type) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(type.titleRes), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(type.descriptionRes), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
