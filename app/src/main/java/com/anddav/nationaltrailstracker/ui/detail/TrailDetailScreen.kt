package com.anddav.nationaltrailstracker.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
            1 -> LandmarksTable(trail = trail, groups = uiState.landmarkGroups, modifier = Modifier.weight(1f))
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
    val stageColour = stageChipColour(trail.colour, row.stage.index, totalStages)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (row.log != null) WalkedRowBackground else Color.Transparent)
            .clickable(onClickLabel = "Log or edit this stage", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        StageNumberBadge(number = row.stage.index + 1, colour = stageColour)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append("${row.stage.fromLandmark.name} → ${row.stage.toLandmark.name}")
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append("  ·  %.1f mi".format(Locale.UK, row.stage.miles))
                    }
                },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Lozenge(
                    text = "%.1f mi cumulative".format(Locale.UK, row.cumulativeMiles),
                    background = stageColour.copy(alpha = 0.16f),
                    content = MaterialTheme.colorScheme.onSurface,
                )
                Lozenge(
                    text = "%.1f%% complete".format(Locale.UK, row.percentAtEnd),
                    background = stageColour.copy(alpha = 0.16f),
                    content = MaterialTheme.colorScheme.onSurface,
                )
                Lozenge(
                    text = "%.1f mi to go".format(Locale.UK, row.milesToEnd),
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TrailPositionStrip(
                startPercent = row.percentAtStart,
                endPercent = row.percentAtEnd,
                colour = stageColour,
            )
            row.stage.note?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
            row.log?.let { log ->
                val stepsText = log.steps?.let { " · %,d steps".format(Locale.UK, it) } ?: ""
                val actualMilesText = log.actualMiles?.let { " · %.1f mi actual".format(Locale.UK, it) } ?: ""
                Lozenge(
                    text = "✓ Walked ${UK_DATE_FORMAT.format(log.dateWalked)}$stepsText$actualMilesText",
                    background = WalkedGreen.copy(alpha = 0.15f),
                    content = WalkedGreen,
                    bold = true,
                )
            }
        }
    }
}

private val WalkedGreen = Color(0xFF1E6B34)
private val WalkedRowBackground = Color(0xFFE3F1E5)

/** White or near-black, whichever reads better on [background]. */
private fun readableTextOn(background: Color): Color =
    if (background.luminance() > 0.45f) Color(0xFF1C1B1F) else Color.White

@Composable
private fun StageNumberBadge(number: Int, colour: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(colour)
            .semantics(mergeDescendants = true) { contentDescription = "Stage $number" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            color = readableTextOn(colour),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
private fun Lozenge(text: String, background: Color, content: Color, bold: Boolean = false) {
    Text(
        text = text,
        color = content,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** A thin bar for the whole trail: earlier stages faded, this stage solid, the rest as track. */
@Composable
private fun TrailPositionStrip(startPercent: Double, endPercent: Double, colour: Color) {
    val before = (startPercent / 100).toFloat().coerceIn(0f, 1f)
    val stage = ((endPercent - startPercent) / 100).toFloat().coerceIn(0f, 1f)
    val after = (1f - before - stage).coerceAtLeast(0f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clearAndSetSemantics { },
    ) {
        if (before > 0f) {
            Box(Modifier.weight(before).fillMaxHeight().background(colour.copy(alpha = 0.35f)))
        }
        if (stage > 0f) {
            Box(Modifier.weight(stage).fillMaxHeight().background(colour))
        }
        if (after > 0f) {
            Spacer(Modifier.weight(after))
        }
    }
}

private val StageColumnWidth = 40.dp

@Composable
private fun LandmarksTable(trail: Trail, groups: List<LandmarkGroup>, modifier: Modifier = Modifier) {
    val totalStages = trail.defaultStages.size
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        stickyHeader { LandmarkHeaderRow() }
        items(groups, key = { group -> group.stageIndex?.let { "stage-$it" } ?: "unstaged-${group.rows.first().landmark.name}" }) { group ->
            LandmarkStageGroup(
                group = group,
                colour = group.stageIndex?.let { stageChipColour(trail.colour, it, totalStages) },
            )
            HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun LandmarkHeaderRow() {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .semantics { heading() },
            verticalAlignment = Alignment.Bottom,
        ) {
            HeaderCell("Stage", Modifier.width(StageColumnWidth), TextAlign.Center)
            HeaderCell("Landmark", Modifier.weight(2.2f).padding(start = 8.dp), TextAlign.Start)
            HeaderCell("Miles", Modifier.weight(1f), TextAlign.End)
            HeaderCell("Cumul.", Modifier.weight(1.1f), TextAlign.End)
            HeaderCell("%", Modifier.weight(0.8f), TextAlign.End)
            HeaderCell("To go", Modifier.weight(1.1f).padding(end = 12.dp), TextAlign.End)
        }
        HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, align: TextAlign) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        textAlign = align,
        modifier = modifier,
    )
}

/** One stage's landmarks, with a stage-number cell on the left spanning all of its rows. */
@Composable
private fun LandmarkStageGroup(group: LandmarkGroup, colour: Color?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(if (group.walked) WalkedRowBackground else Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .width(StageColumnWidth)
                .fillMaxHeight()
                .background(colour?.copy(alpha = 0.16f) ?: Color.Transparent)
                .padding(top = 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (group.stageIndex != null && colour != null) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(colour)
                        .semantics { contentDescription = "Stage ${group.stageIndex + 1}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (group.stageIndex + 1).toString(),
                        color = readableTextOn(colour),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            group.rows.forEach { row -> LandmarkTableRow(row) }
        }
    }
}

@Composable
private fun LandmarkTableRow(row: LandmarkRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(2.2f).padding(start = 8.dp)) {
            Text(row.landmark.name, style = MaterialTheme.typography.bodyMedium)
            row.landmark.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        NumberCell(row.milesFromPrevious?.let { "%.1f".format(Locale.UK, it) } ?: "–", Modifier.weight(1f))
        NumberCell("%.1f".format(Locale.UK, row.cumulativeMiles), Modifier.weight(1.1f))
        NumberCell("%.0f".format(Locale.UK, row.percent), Modifier.weight(0.8f))
        NumberCell("%.1f".format(Locale.UK, row.milesToEnd), Modifier.weight(1.1f).padding(end = 12.dp))
    }
}

@Composable
private fun NumberCell(text: String, modifier: Modifier) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = modifier)
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
