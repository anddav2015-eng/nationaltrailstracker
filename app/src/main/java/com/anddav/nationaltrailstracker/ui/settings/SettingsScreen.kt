package com.anddav.nationaltrailstracker.ui.settings

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anddav.nationaltrailstracker.ui.theme.TrailTrackerCream
import com.anddav.nationaltrailstracker.ui.theme.TrailTrackerDarkGreen
import kotlinx.coroutines.launch

/** What a confirmation dialog is asking about. */
private sealed interface PendingConfirm {
    data object Import : PendingConfirm
    data class RemoveOne(val row: OfflineMapRow) : PendingConfirm
    data object RemoveAll : PendingConfirm
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf<PendingConfirm?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val result = runCatching {
                val json = viewModel.exportBackup()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    ?: error("Couldn't open the file for writing")
            }
            snackbarHostState.showSnackbar(
                if (result.isSuccess) "Backup exported" else "Export failed: ${result.exceptionOrNull()?.message}",
            )
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val result = runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Couldn't open the file for reading")
                viewModel.importBackup(text)
            }
            snackbarHostState.showSnackbar(
                if (result.isSuccess) "Backup imported" else "Import failed: ${result.exceptionOrNull()?.message}",
            )
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Settings",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                color = TrailTrackerCream,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrailTrackerDarkGreen)
                    .padding(20.dp)
                    .semantics { heading() },
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionHeading("Backup")
                Text(
                    "Save your logged stages to a file, or restore them from one. Importing replaces everything " +
                        "you've logged with the backup's contents.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportLauncher.launch("national-trails-backup.json") }) { Text("Export backup") }
                    OutlinedButton(onClick = { pending = PendingConfirm.Import }) { Text("Import backup") }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SectionHeading("Offline maps")
                Text(
                    "Maps saved with \"Save map for offline use\" on a stage's map. They're kept until you remove them.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                when {
                    uiState.offlineMapsLoading -> CircularProgressIndicator()
                    uiState.offlineMaps.isEmpty() -> Text(
                        "No maps saved.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> {
                        uiState.offlineMaps.forEach { row ->
                            OfflineMapCard(
                                row = row,
                                size = size(row.sizeBytes),
                                enabled = !uiState.removing,
                                onRemove = { pending = PendingConfirm.RemoveOne(row) },
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${uiState.offlineMaps.size} saved · ${size(uiState.offlineMapsTotalBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(onClick = { pending = PendingConfirm.RemoveAll }, enabled = !uiState.removing) {
                                Text("Remove all")
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    pending?.let { confirm ->
        val (title, body, action) = when (confirm) {
            PendingConfirm.Import -> Triple(
                "Import a backup?",
                "This replaces all the stages you've logged with the ones in the backup file.",
                "Choose file",
            )
            is PendingConfirm.RemoveOne -> Triple(
                "Remove this offline map?",
                listOfNotNull(confirm.row.title, confirm.row.subtitle).joinToString(", ") +
                    ". It can be saved again from the map.",
                "Remove",
            )
            PendingConfirm.RemoveAll -> Triple(
                "Remove all offline maps?",
                "${uiState.offlineMaps.size} saved maps (${size(uiState.offlineMapsTotalBytes)}). " +
                    "They can be saved again from each stage's map.",
                "Remove all",
            )
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null
                        when (confirm) {
                            PendingConfirm.Import -> importLauncher.launch(arrayOf("application/json"))
                            is PendingConfirm.RemoveOne -> viewModel.removeOfflineMap(confirm.row.key)
                            PendingConfirm.RemoveAll -> viewModel.removeAllOfflineMaps()
                        }
                    },
                ) { Text(action) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun OfflineMapCard(row: OfflineMapRow, size: String, enabled: Boolean, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = TrailTrackerCream)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.title, fontWeight = FontWeight.SemiBold)
                row.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    if (row.isComplete) size else "$size · download not finished",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRemove, enabled = enabled) { Text("Remove") }
        }
    }
}
