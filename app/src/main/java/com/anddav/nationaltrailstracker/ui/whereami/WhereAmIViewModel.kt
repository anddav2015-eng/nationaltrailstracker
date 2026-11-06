package com.anddav.nationaltrailstracker.ui.whereami

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anddav.nationaltrailstracker.data.location.LocationClient
import com.anddav.nationaltrailstracker.data.location.LocationFix
import com.anddav.nationaltrailstracker.data.location.LocationPermissionMissingException
import com.anddav.nationaltrailstracker.domain.RouteLocator
import com.anddav.nationaltrailstracker.domain.TrailPosition
import com.anddav.nationaltrailstracker.model.Trail
import com.anddav.nationaltrailstracker.repo.TrailRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface WhereAmIStatus {
    data object Loading : WhereAmIStatus

    /** This trail's route line hasn't been built yet (tools/build_geo.py). */
    data object NoRouteData : WhereAmIStatus

    /** Show the explanation and the "Allow location" button. */
    data object NeedsPermission : WhereAmIStatus

    /** The user said no; explain how to turn it on in Settings. */
    data object PermissionDenied : WhereAmIStatus

    data object Locating : WhereAmIStatus

    data class Located(val position: TrailPosition, val fix: LocationFix, val approximateOnly: Boolean) :
        WhereAmIStatus

    data class Failed(val message: String) : WhereAmIStatus
}

data class WhereAmIUiState(
    val trail: Trail? = null,
    val totalMiles: Double = 0.0,
    val status: WhereAmIStatus = WhereAmIStatus.Loading,
)

class WhereAmIViewModel(
    private val trailId: String,
    private val trailRepository: TrailRepository,
    private val locationClient: LocationClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WhereAmIUiState())
    val uiState: StateFlow<WhereAmIUiState> = _uiState.asStateFlow()

    private var locator: RouteLocator? = null
    private var locateJob: Job? = null

    init {
        viewModelScope.launch {
            val trail = trailRepository.trail(trailId)
            val route = trailRepository.routeLine(trail)
            locator = route?.let(::RouteLocator)
            _uiState.value = WhereAmIUiState(
                trail = trail,
                totalMiles = trail.landmarks.lastOrNull()?.milesFromStart ?: 0.0,
                status = when {
                    route == null -> WhereAmIStatus.NoRouteData
                    !locationClient.hasPermission() -> WhereAmIStatus.NeedsPermission
                    else -> WhereAmIStatus.Locating
                },
            )
            // The walker tapped "Where am I?" to get here, so fetch straight away if allowed.
            if (_uiState.value.status == WhereAmIStatus.Locating) locate()
        }
    }

    /** Called with the result of the system permission dialog. */
    fun onPermissionResult(granted: Boolean) {
        if (granted) locate() else updateStatus(WhereAmIStatus.PermissionDenied)
    }

    fun locate() {
        val trail = _uiState.value.trail ?: return
        val locator = locator ?: return
        locateJob?.cancel()
        locateJob = viewModelScope.launch {
            updateStatus(WhereAmIStatus.Locating)
            val status = try {
                val fix = locationClient.currentLocation()
                val position = withContext(Dispatchers.Default) {
                    TrailPosition.from(trail, locator.locate(fix.lat, fix.lon))
                }
                WhereAmIStatus.Located(position, fix, approximateOnly = locationClient.isApproximateOnly())
            } catch (e: CancellationException) {
                throw e
            } catch (_: LocationPermissionMissingException) {
                WhereAmIStatus.NeedsPermission
            } catch (e: Exception) {
                WhereAmIStatus.Failed(e.message ?: "Couldn't get a location fix.")
            }
            updateStatus(status)
        }
    }

    private fun updateStatus(status: WhereAmIStatus) {
        _uiState.value = _uiState.value.copy(status = status)
    }
}
