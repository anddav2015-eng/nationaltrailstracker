package com.anddav.nationaltrailstracker.ui.trails

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anddav.nationaltrailstracker.ui.parseHexColor
import com.anddav.nationaltrailstracker.ui.theme.TrailTrackerCream
import com.anddav.nationaltrailstracker.ui.theme.TrailTrackerDarkGreen
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun TrailsListScreen(
    viewModel: TrailsListViewModel,
    onTrailClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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

    Box(modifier = modifier.fillMaxSize()) {
        TrailsListContent(
            uiState = uiState,
            onTrailClick = onTrailClick,
            onExportClick = { exportLauncher.launch("national-trails-backup.json") },
            onImportClick = { importLauncher.launch(arrayOf("application/json")) },
        )
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TrailsListContent(
    uiState: TrailsListUiState,
    onTrailClick: (String) -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TrailTrackerDarkGreen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "National Trails",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                color = TrailTrackerCream,
                modifier = Modifier.weight(1f),
            )
            BackupMenu(onExportClick = onExportClick, onImportClick = onImportClick)
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TrailTrackerCream)
            }
            return
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(uiState.trails, key = { it.id }) { trail ->
                TrailRow(trail = trail, onClick = { onTrailClick(trail.id) })
            }
        }

        Text(
            text = "${uiState.trailsComplete} of ${uiState.trailsTotal} complete · " +
                "${formatMiles(uiState.totalMilesWalked)} miles walked",
            color = TrailTrackerCream,
            fontFamily = FontFamily.Serif,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun TrailRow(trail: TrailListItem, onClick: () -> Unit) {
    val cardColour = if (trail.isComplete) {
        parseHexColor(trail.colourHex).copy(alpha = 0.18f).compositeOver(TrailTrackerCream)
    } else {
        TrailTrackerCream
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColour),
        elevation = CardDefaults.cardElevation(defaultElevation = if (trail.isComplete) 4.dp else 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompletionIndicator(
                isComplete = trail.isComplete,
                accentColor = parseHexColor(trail.colourHex),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = trail.name,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                )
                Text(
                    text = if (trail.stagesTotal == 0) {
                        "Stages not planned yet"
                    } else {
                        "${trail.stagesDone} of ${trail.stagesTotal} stages · " +
                            "${formatMiles(trail.milesWalked)} / ${formatMiles(trail.totalMiles)} miles"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = { (trail.percentComplete / 100.0).toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    color = parseHexColor(trail.colourHex),
                    trackColor = Color(0xFFE0DACD),
                )
            }
        }
    }
}

private fun formatMiles(miles: Double): String = String.format(Locale.UK, "%.1f", miles)

/** Export/import, kept as a plain overflow menu rather than the full Settings screen (that's a
 * later milestone) - see CLAUDE.md's v1 screen list. */
@Composable
private fun BackupMenu(onExportClick: () -> Unit, onImportClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            text = "⋮",
            color = TrailTrackerCream,
            fontWeight = FontWeight.Bold,
            fontSize = MaterialTheme.typography.headlineSmall.fontSize,
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics { contentDescription = "More options" },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Export backup") },
                onClick = { expanded = false; onExportClick() },
            )
            DropdownMenuItem(
                text = { Text("Import backup") },
                onClick = { expanded = false; onImportClick() },
            )
        }
    }
}

/** A simple checkbox-style completion indicator: filled circle when complete, a ring when not. */
@Composable
private fun CompletionIndicator(isComplete: Boolean, accentColor: Color) {
    val description = if (isComplete) "Complete" else "Not complete"
    if (isComplete) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(accentColor)
                .semantics { contentDescription = description },
        )
    } else {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .border(2.dp, accentColor, CircleShape)
                .semantics { contentDescription = description },
        )
    }
}