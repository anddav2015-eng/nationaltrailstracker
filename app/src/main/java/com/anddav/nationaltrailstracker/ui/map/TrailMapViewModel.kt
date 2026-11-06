package com.anddav.nationaltrailstracker.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anddav.nationaltrailstracker.data.location.LocationClient
import com.anddav.nationaltrailstracker.domain.map.GeoBounds
import com.anddav.nationaltrailstracker.domain.map.GeoPoint
import com.anddav.nationaltrailstracker.domain.map.MapLegs
import com.anddav.nationaltrailstracker.domain.map.MapLineVersion
import com.anddav.nationaltrailstracker.domain.map.MapMarker
import com.anddav.nationaltrailstracker.model.Trail
import com.anddav.nationaltrailstracker.repo.TrailRepository
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
    val versions: List<MapLineVersion> = emptyList(),
    val version: MapLineVersion? = null,
    val wholeLine: List<GeoPoint> = emptyList(),
    val stageLine: List<GeoPoint> = emptyList(),
    val stageEnds: Pair<MapMarker, MapMarker>? = null,
    /** Where the camera starts: the stage, or the whole trail if there's no stage. */
    val initialBounds: GeoBounds? = null,
    val showLocation: Boolean = false,
    val message: String? = null,
)

class TrailMapViewModel(
    private val trailId: String,
    private val stageIndex: Int?,
    private val trailRepository: TrailRepository,
    private val locationClient: LocationClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrailMapUiState(stageIndex = stageIndex))
    val uiState: StateFlow<TrailMapUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val trail = trailRepository.trail(trailId)
            val versions = trailRepository.mapLineVersions(trailId)
            _uiState.update { it.copy(trail = trail, versions = versions) }
            val first = versions.firstOrNull()
            if (first == null) {
                _uiState.update { it.copy(isLoading = false, message = "There's no map for this trail yet.") }
            } else {
                load(first)
            }
        }
    }

    /** Switches the route line (for comparing versions). The camera stays where it is. */
    fun selectVersion(version: MapLineVersion) {
        if (version != _uiState.value.version) viewModelScope.launch { load(version) }
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

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    fun onBaseMapOffline() {
        _uiState.update { it.copy(message = "No connection: the base map isn't available, but the trail line is.") }
    }

    private suspend fun load(version: MapLineVersion) {
        val legs = try {
            trailRepository.mapLegs(trailId, version)
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, message = "Couldn't load the route line (${e.message}).") }
            return
        }
        val stageLegs = stageIndex?.let { MapLegs.stageLegs(legs, it) }.orEmpty()
        val whole = MapLegs.join(legs)
        val stage = MapLegs.join(stageLegs)
        _uiState.update {
            it.copy(
                isLoading = false,
                version = version,
                wholeLine = whole,
                stageLine = stage,
                stageEnds = MapLegs.stageEnds(stageLegs),
                initialBounds = MapLegs.bounds(stage.ifEmpty { whole }),
            )
        }
    }
}
