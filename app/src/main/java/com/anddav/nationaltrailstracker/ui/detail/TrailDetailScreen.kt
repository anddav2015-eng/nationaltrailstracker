package com.anddav.nationaltrailstracker.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anddav.nationaltrailstracker.data.local.StageLogEntity
import com.anddav.nationaltrailstracker.domain.StageMaths
import com.anddav.nationaltrailstracker.model.Trail
import com.anddav.nationaltrailstracker.ui.parseHexColor
import com.anddav.nationaltrailstracker.ui.stageChipColour
import com.anddav.nationaltrailstracker.ui.theme.TrailTrackerCream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val UK_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)

@Composable
fun TrailDetailScreen(
    viewModel: TrailDetailViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    if (uiState.isLoading || uiState.trail == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    TrailDetailContent(
        trail = uiState.trail!!,
        uiState = uiState,
        onSaveLog = viewModel::saveLog,
        onDeleteLog = viewModel::deleteLog,
        modifier = modifier,
    )
}

@Composable
private fun TrailDetailContent(
    trail: Trail,
    uiState: TrailDetailUiState,
    onSaveLog: (stageIndex: Int, dateWalked: LocalDate, steps: Int?, notes: String?, actualMiles: Double?) -> Unit,
    onDeleteLog: (StageLogEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trailColour = parseHexColor(trail.colour)
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedStageIndex by remember { mutableStateOf<Int?>(null) }
    var showFooter by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(trailColour)
                .padding(20.dp),
        ) {
            Text(
                text = trail.name,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                color = TrailTrackerCream,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = trail.subtitle, color = TrailTrackerCream, modifier = Modifier.weight(1f, fill = false))
                InfoButton(onClick = { showFooter = true })
            }
        }

        val showProgressTab = uiState.chartPoints.isNotEmpty()
        val tabTitles = if (showProgressTab) {
            listOf("Stages", "Landmarks", "Progress")
        } else {
            listOf("Stages", "Landmarks")
        }
        val clampedTab = selectedTab.coerceIn(0, tabTitles.lastIndex)

        SecondaryTabRow(selectedTabIndex = clampedTab) {
            tabTitles.forEachIndexed { index, title ->
                Tab(selected = clampedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
            }
        }

        when (clampedTab) {
            0 -> StagesList(
                trail = trail,
                rows = uiState.stageRows,
                onStageClick = { stageIndex -> selectedStageIndex = stageIndex },
                modifier = Modifier.weight(1f),
            )
            1 -> LandmarksTable(uiState.landmarkRows, modifier = Modifier.weight(1f))
            else -> ProgressChart(
                points = uiState.chartPoints,
                totalMiles = StageMaths.totalMiles(trail),
                modifier = Modifier.weight(1f).padding(16.dp),
            )
        }
    }

    if (showFooter) {
        AlertDialog(
            onDismissRequest = { showFooter = false },
            title = { Text("About this route") },
            text = { Text(trail.footer) },
            confirmButton = {
                TextButton(onClick = { showFooter = false }) { Text("Close") }
            },
        )
    }

    val selectedRow = uiState.stageRows.getOrNull(selectedStageIndex ?: -1)
    if (selectedRow != null) {
        LogStageSheet(
            stage = selectedRow.stage,
            existingLog = selectedRow.log,
            onDismiss = { selectedStageIndex = null },
            onSave = { date, steps, notes, actualMiles ->
                onSaveLog(selectedRow.stage.index, date, steps, notes, actualMiles)
                selectedStageIndex = null
            },
            onDelete = selectedRow.log?.let { log -> { onDeleteLog(log); selectedStageIndex = null } },
        )
    }
}

@Composable
private fun StagesList(
    trail: Trail,
    rows: List<StageRow>,
    onStageClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(rows, key = { it.stage.index }) { row ->
            StageRowItem(trail = trail, row = row, totalStages = rows.size, onClick = { onStageClick(row.stage.index) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun StageRowItem(trail: Trail, row: StageRow, totalStages: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .padding(end = 10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(stageChipColour(trail.colour, row.stage.index, totalStages))
                .fillMaxWidth(),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Stage ${row.stage.index + 1}: ${row.stage.fromLandmark.name} → ${row.stage.toLandmark.name}",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "%.1f mi · %.1f mi cumulative".format(Locale.UK, row.stage.miles, row.cumulativeMiles),
                style = MaterialTheme.typography.bodySmall,
            )
            row.stage.note?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
            row.log?.let { log ->
                val stepsText = log.steps?.let { " · $it steps" } ?: ""
                val actualMilesText = log.actualMiles?.let { " · %.1f mi actual".format(Locale.UK, it) } ?: ""
                Text(
                    text = "Walked ${UK_DATE_FORMAT.format(log.dateWalked)}$stepsText$actualMilesText",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun LandmarksTable(rows: List<LandmarkRow>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item { LandmarkHeaderRow() }
        items(rows) { row -> LandmarkTableRow(row) }
    }
}

@Composable
private fun LandmarkHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text("Landmark", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold)
        Text("Miles", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text("To end", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text("%", modifier = Modifier.weight(0.6f), fontWeight = FontWeight.Bold)
    }
    HorizontalDivider()
}

@Composable
private fun LandmarkTableRow(row: LandmarkRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(2f)) {
            Text(row.landmark.name, style = MaterialTheme.typography.bodyMedium)
            row.landmark.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Text("%.1f".format(Locale.UK, row.landmark.milesFromStart), modifier = Modifier.weight(1f))
        Text("%.1f".format(Locale.UK, row.milesToEnd), modifier = Modifier.weight(1f))
        Text("%.0f".format(Locale.UK, row.percent), modifier = Modifier.weight(0.6f))
    }
}

/** A circled "i" that opens the trail's small print. Drawn rather than taken from an icon
 * library, to avoid adding the material-icons dependency for one glyph. */
@Composable
private fun InfoButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClickLabel = "Show route notes", role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Route notes" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(width = 1.5.dp, color = TrailTrackerCream, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "i",
                color = TrailTrackerCream,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}
