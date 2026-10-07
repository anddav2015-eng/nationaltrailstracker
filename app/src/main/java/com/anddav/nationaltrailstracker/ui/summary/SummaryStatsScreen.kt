package com.anddav.nationaltrailstracker.ui.summary

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anddav.nationaltrailstracker.domain.SummaryStats
import com.anddav.nationaltrailstracker.ui.theme.TrailTrackerCream
import com.anddav.nationaltrailstracker.ui.theme.TrailTrackerDarkGreen
import java.util.Locale

@Composable
fun SummaryStatsScreen(
    viewModel: SummaryStatsViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TrailTrackerDarkGreen),
    ) {
        Text(
            text = "Summary",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = MaterialTheme.typography.headlineMedium.fontSize,
            color = TrailTrackerCream,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .semantics { heading() },
        )
        val stats = uiState.stats
        if (uiState.isLoading || stats == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TrailTrackerCream)
            }
            return
        }
        SummaryCard(
            stats = stats,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SummaryCard(stats: SummaryStats, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TrailTrackerCream),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "${formatMiles(stats.milesWalked)} miles walked",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
            )
            Text(
                text = "${formatPercent(stats.percentOfTotalMiles)} of ${formatMiles(stats.totalMiles)} miles " +
                    "across all trails",
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { (stats.percentOfTotalMiles / 100.0).toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                color = TrailTrackerDarkGreen,
                trackColor = Color(0xFFE0DACD),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            StatRow(label = "Steps", value = String.format(Locale.UK, "%,d", stats.stepsWalked))
            StatRow(label = "Miles to go", value = formatMiles(stats.milesRemaining))
            StatRow(label = "Stages walked", value = "${stats.stagesDone} of ${stats.stagesTotal}")
            StatRow(label = "Trails complete", value = "${stats.trailsComplete} of ${stats.trailsTotal}")
            StatRow(label = "Trails started", value = "${stats.trailsStarted} of ${stats.trailsTotal}")

            if (stats.trailsUnplanned > 0) {
                val trailWord = if (stats.trailsUnplanned == 1) "trail" else "trails"
                Text(
                    text = "Total miles leave out ${stats.trailsUnplanned} $trailWord whose stages " +
                        "aren't planned yet.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatMiles(miles: Double): String = String.format(Locale.UK, "%,.1f", miles)

private fun formatPercent(percent: Double): String = String.format(Locale.UK, "%.1f%%", percent)
