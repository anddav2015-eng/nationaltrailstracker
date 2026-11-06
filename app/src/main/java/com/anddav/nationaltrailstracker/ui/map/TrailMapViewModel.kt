package com.anddav.nationaltrailstracker.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anddav.nationaltrailstracker.data.location.LocationClient
import com.anddav.nationaltrailstracker.data.map.OfflineMapState
import com.anddav.nationaltrailstracker.data.map.OfflineMapStore
import com.anddav.nationaltrailstracker.domain.map.GeoBounds
import com.anddav.nationaltrailstracker.domain.map.GeoPoint
import com.anddav.nationaltrailstracker.domain.map.MapLegs
import com.anddav.nationaltrailstracker.domain.map.MapMarker
import com.anddav.nationaltrailstracker.domain.map.StageLocator
import com.anddav.nationaltrailstracker.domain.map.StagePosition
import com.anddav.nationaltrailstracker.model.Trail
import com.anddav.nationaltrailstracker.repo.TrailRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrailMapUiState(
    val isLoading: Boolean = true,
    val trail: Trail? = null,
    /** The highlighted stage, or null to show the whole trail. */
    val stageIndex: Int? = null,
    val wholeLine: List<GeoPoint> = emptyList(),
    val stageLine: List<GeoPoint> = emptyList(),
    val stageEnds: Pair<MapMarker, MapMarker>? = null,
    /** Where the camera starts: the stage, or the whole trail if there's no stage. */
    val initialBounds: GeoBounds? = null,
    val showLocation: Boolean = false,
    /** Where the walker is against the stage, along the map line; null until there's a fix. */
    val position: StagePosition? = null,
    /** Accuracy of the latest fix, metres, when known. */
    val accuracyMetres: Float? = null,
    val offline: OfflineMapState = OfflineMapState.Unknown,
    val message: String? = null,
)

class TrailMapViewModel(
    private val trailId: String,
    private val stageIndex: Int?,
    private val trailRepository: TrailRepository,
    private val locationClient: LocationClient,
    private val offlineMapStore: OfflineMapStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrailMapUiState(stageIndex = stageIndex))
    val uiState: StateFlow<TrailMapUiState> = _uiState.asStateFlow()

    private var stageLocator: StageLocator? = null
    private var downloadJob: Job? = null
    private val offlineKey = "$trailId/stage-${stageIndex ?: "all"}"

    init {
        viewModelScope.launch {
            val trail = trailRepository.trail(trailId)
            _uiState.update { it.copy(trail = trail) }
            if (!trailRepository.hasMap(trailId)) {
                _uiState.update { it.copy(isLoading = false, message = "There's no map for this trail yet.") }
                return@launch
            }
            load()
            _uiState.update { it.copy(offline = offlineMapStore.state(offlineKey)) }
        }
    }

    fun hasLocationPermission(): Boolean = locationClient.hasPermission()

    fun onShowLocation() {
        _uiState.update { it.copy(showLocation = true, message = null) }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update {
            if (granted) {
                it.copy(showLocation = true, message = null)
            } else {
                it.copy(message = "Location permission wasn't granted, so your position can't be shown.")
            }
        }
    }

    /** A new location fix from the map's location dot. */
    fun onLocation(lat: Double, lon: Double, accuracyMetres: Float?) {
        val locator = stageLocator ?: return
        _uiState.update { it.copy(position = locator.locate(lat, lon), accuracyMetres = accuracyMetres) }
    }

    /** Saves the base map around this stage (or the whole trail) for use without signal. */
    fun saveOffline() {
        val bounds = _uiState.value.initialBounds ?: return
        if (downloadJob?.isActive == true) return
        val area = GeoBounds(
            south = bounds.south - MapConfig.OFFLINE_MARGIN_LAT,
            west = bounds.west - MapConfig.OFFLINE_MARGIN_LON,
            north = bounds.north + MapConfig.OFFLINE_MARGIN_LAT,
            east = bounds.east + MapConfig.OFFLINE_MARGIN_LON,
        )
        downloadJob = viewModelScope.launch {
            offlineMapStore.download(
                key = offlineKey,
                styleUrl = MapConfig.STYLE_URL,
                bounds = area,
                minZoom = MapConfig.OFFLINE_MIN_ZOOM,
                maxZoom = MapConfig.OFFLINE_MAX_ZOOM,
            ).collect { state ->
                _uiState.update {
                    when (state) {
                        OfflineMapState.Saved -> it.copy(offline = state, message = "Saved for offline use.")
                        is OfflineMapState.Failed -> it.copy(offline = state, message = state.message)
                        else -> it.copy(offline = state)
                    }
                }
            }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    fun onBaseMapOffline() {
        _uiState.update { it.copy(message = "No connection: the base map isn't available, but the trail line is.") }
    }

    private suspend fun load() {
        val legs = try {
            trailRepository.mapLegs(trailId)
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, message = "Couldn't load the route line (${e.message}).") }
            return
        }
        val stageLegs = stageIndex?.let { MapLegs.stageLegs(legs, it) }.orEmpty()
        val whole = MapLegs.join(legs)
        val stage = MapLegs.join(stageLegs)
        stageLocator = if (stageIndex != null && stageLegs.isNotEmpty()) StageLocator(legs, stageIndex) else null
        _uiState.update {
            it.copy(
                isLoading = false,
                wholeLine = whole,
                stageLine = stage,
                stageEnds = MapLegs.stageEnds(stageLegs),
                initialBounds = MapLegs.bounds(stage.ifEmpty { whole }),
            )
        }
    }
}
