package com.anddav.nationaltrailstracker.ui.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anddav.nationaltrailstracker.domain.ChartPoint
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart

/** Cumulative miles vs. date, with a reference line at the trail's full length. Hidden by the
 * caller until there's at least one log - see CLAUDE.md's v1 screen list. */
@Composable
fun ProgressChart(points: List<ChartPoint>, totalMiles: Double, modifier: Modifier = Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(points, totalMiles) {
        val firstDay = points.first().date.toEpochDay().toDouble()
        val lastDay = points.last().date.toEpochDay().toDouble()
        modelProducer.runTransaction {
            lineModel {
                series(points.map { it.date.toEpochDay().toDouble() }, points.map { it.cumulativeMiles })
                series(listOf(firstDay, lastDay), listOf(totalMiles, totalMiles))
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(),
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp),
    )
}
