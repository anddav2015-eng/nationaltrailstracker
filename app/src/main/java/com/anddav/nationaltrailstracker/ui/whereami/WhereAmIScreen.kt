package com.anddav.nationaltrailstracker.ui.whereami

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anddav.nationaltrailstracker.model.Trail
import com.anddav.nationaltrailstracker.ui.parseHexColor
import com.anddav.nationaltrailstracker.ui.theme.TrailTrackerCream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private val WarningBackground = Color(0xFFFCE8D5)
private val WarningText = Color(0xFF7A3B00)

@Composable
fun WhereAmIScreen(
    viewModel: WhereAmIViewModel,
    /** Opens the map on the given stage with location on. */
    onShowOnMap: (stageIndex: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        viewModel.onPermissionResult(grants.values.any { it })
    }
    val trail = uiState.trail

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(trail?.let { parseHexColor(it.colour) } ?: MaterialTheme.colorScheme.primary)
                .padding(20.dp),
        ) {
            Text(
                text = "Where am I?",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                color = TrailTrackerCream,
                modifier = Modifier.semantics { heading() },
            )
            trail?.let { Text(text = it.name, color = TrailTrackerCream) }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val status = uiState.status) {
                WhereAmIStatus.Loading, WhereAmIStatus.Locating -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(if (status == WhereAmIStatus.Locating) "Getting a GPS fix…" else "Loading…")
                }

                WhereAmIStatus.NoRouteData -> Text(
                    "The route line for this trail hasn't been added to the app yet, so it can't work out " +
                        "where you are on it.",
                )

                WhereAmIStatus.NeedsPermission -> {
                    Text(
                        "To show where you are on the ${trail?.name ?: "trail"}, the app needs your location once, " +
                            "now. It checks your position against the route line on your phone. Your location isn't " +
                            "stored, sent anywhere or tracked in the background.",
                    )
                    Button(onClick = { permissionLauncher.launch(LOCATION_PERMISSIONS) }) {
                        Text("Allow location")
                    }
                }

                WhereAmIStatus.PermissionDenied -> {
                    Text(
                        "Location permission was turned down, so the app can't find you on the trail. You can allow " +
                            "it in the app's settings (Permissions → Location → \"Allow only while using the app\").",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { permissionLauncher.launch(LOCATION_PERMISSIONS) }) { Text("Try again") }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    ),
                                )
                            },
                        ) { Text("Open settings") }
                    }
                }

                is WhereAmIStatus.Failed -> {
                    Text(status.message)
                    Button(onClick = viewModel::locate) { Text("Try again") }
                }

                is WhereAmIStatus.Located -> {
                    if (trail != null) {
                        LocatedContent(
                            trail = trail,
                            totalMiles = uiState.totalMiles,
                            status = status,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.hasMap) {
                            Button(onClick = { onShowOnMap(status.position.stage?.index) }) { Text("Show on map") }
                        }
                        OutlinedButton(onClick = viewModel::locate) { Text("Check again") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocatedContent(trail: Trail, totalMiles: Double, status: WhereAmIStatus.Located) {
    val position = status.position
    if (position.isOffRoute) {
        Card(colors = CardDefaults.cardColors(containerColor = WarningBackground)) {
            Text(
                text = "You're about ${formatDistance(position.offRouteMetres)} from the ${trail.name}. " +
                    "The mileages below are for the nearest point on the route.",
                color = WarningText,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    InfoCard {
        Text(
            text = "Mile %.1f".format(Locale.UK, position.milesAlong),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            "of %.1f · %.1f miles to the end".format(Locale.UK, totalMiles, position.milesToTrailEnd),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    position.stage?.let { stage ->
        InfoCard {
            Text("Stage ${stage.index + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("${stage.fromLandmark.name} → ${stage.toLandmark.name}", fontWeight = FontWeight.SemiBold)
            position.milesToStageEnd?.let {
                Text(
                    text = "%.1f miles to the end of the stage".format(Locale.UK, it),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    InfoCard {
        position.landmarkBehind?.let {
            LandmarkLine(label = "Behind", name = it.name, miles = position.milesAlong - it.milesFromStart, suffix = "back")
        }
        position.landmarkAhead?.let {
            LandmarkLine(label = "Ahead", name = it.name, miles = it.milesFromStart - position.milesAlong, suffix = "to go")
        }
    }

    val accuracy = status.fix.accuracyMetres?.let { "±${formatDistance(it.toDouble())}" } ?: "unknown accuracy"
    val time = SimpleDateFormat("HH:mm", Locale.UK).format(Date(status.fix.timeMillis))
    Text(
        text = "GPS fix at $time, $accuracy." +
            if (status.approximateOnly) " Only approximate location is allowed, so this could be a mile or more out." else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = TrailTrackerCream)) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun LandmarkLine(label: String, name: String, miles: Double, suffix: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("$label: ", fontWeight = FontWeight.Bold)
        Text(name, modifier = Modifier.weight(1f))
        Text("%.1f mi %s".format(Locale.UK, miles, suffix))
    }
}

/** Metres under 1 km, otherwise km to one decimal place. */
private fun formatDistance(metres: Double): String =
    if (metres < 1000) "%.0f m".format(Locale.UK, metres) else "%.1f km".format(Locale.UK, metres / 1000)
