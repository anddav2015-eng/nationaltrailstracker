package com.anddav.nationaltrailstracker.data.map

import android.content.Context
import com.anddav.nationaltrailstracker.domain.map.GeoBounds
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.coroutines.resume

sealed interface OfflineMapState {
    data object Unknown : OfflineMapState
    data object NotSaved : OfflineMapState
    /** [percent] is null until MapLibre knows how much there is to fetch. */
    data class Downloading(val percent: Int?) : OfflineMapState
    data object Saved : OfflineMapState
    data class Failed(val message: String) : OfflineMapState
}

/** One saved area, for the Settings list. */
data class SavedOfflineMap(
    val key: String,
    /** Bytes downloaded for this area. Areas can share tiles, so sizes may overlap a little. */
    val sizeBytes: Long,
    /** False if the download was paused before it finished. */
    val isComplete: Boolean,
)

/**
 * Saves the base map for an area so it works without signal, using MapLibre's offline regions.
 * Each area is keyed (e.g. "thames-path/stage-3") so it can be found again. Call from the main
 * thread; MapLibre's offline API delivers its callbacks there.
 */
class OfflineMapStore(context: Context) {
    private val appContext = context.applicationContext
    private val manager: OfflineManager by lazy {
        MapLibre.getInstance(appContext)
        OfflineManager.getInstance(appContext)
    }

    suspend fun state(key: String): OfflineMapState {
        val region = findRegion(key) ?: return OfflineMapState.NotSaved
        val status = regionStatus(region) ?: return OfflineMapState.NotSaved
        return if (status.isComplete) OfflineMapState.Saved else OfflineMapState.NotSaved
    }

    /** Every saved (or part-saved) area. */
    suspend fun list(): List<SavedOfflineMap> = allRegions().map { region ->
        val status = regionStatus(region)
        SavedOfflineMap(
            key = region.metadata.decodeToString(),
            sizeBytes = status?.completedResourceSize ?: 0L,
            isComplete = status?.isComplete == true,
        )
    }

    /** Deletes the saved area; returns false if it couldn't be deleted. */
    suspend fun delete(key: String): Boolean {
        val region = findRegion(key) ?: return true
        return suspendCancellableCoroutine { cont ->
            region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() {
                    cont.resume(true)
                }

                override fun onError(error: String) {
                    cont.resume(false)
                }
            })
        }
    }

    /** Downloads (or resumes) the area. Cancelling the flow pauses the download. */
    fun download(key: String, styleUrl: String, bounds: GeoBounds, minZoom: Double, maxZoom: Double): Flow<OfflineMapState> =
        callbackFlow {
            trySend(OfflineMapState.Downloading(null))
            val region = findRegion(key) ?: createRegion(key, styleUrl, bounds, minZoom, maxZoom)
            if (region == null) {
                trySend(OfflineMapState.Failed("Couldn't start the download."))
                close()
                return@callbackFlow
            }
            region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                override fun onStatusChanged(status: OfflineRegionStatus) {
                    if (status.isComplete) {
                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        trySend(OfflineMapState.Saved)
                        close()
                    } else {
                        val required = status.requiredResourceCount
                        val percent = if (required > 0 && status.isRequiredResourceCountPrecise) {
                            (100 * status.completedResourceCount / required).toInt()
                        } else {
                            null
                        }
                        trySend(OfflineMapState.Downloading(percent))
                    }
                }

                override fun onError(error: OfflineRegionError) {
                    // MapLibre retries transient errors itself; only report, don't stop.
                    trySend(OfflineMapState.Downloading(null))
                }

                override fun mapboxTileCountLimitExceeded(limit: Long) {
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    trySend(OfflineMapState.Failed("The area is too big to save ($limit tiles)."))
                    close()
                }
            })
            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
            awaitClose {
                region.setObserver(null)
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            }
        }

    private suspend fun findRegion(key: String): OfflineRegion? =
        allRegions().firstOrNull { it.metadata.decodeToString() == key }

    private suspend fun allRegions(): List<OfflineRegion> = suspendCancellableCoroutine { cont ->
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                cont.resume(offlineRegions?.toList().orEmpty())
            }

            override fun onError(error: String) {
                cont.resume(emptyList())
            }
        })
    }

    private suspend fun regionStatus(region: OfflineRegion): OfflineRegionStatus? = suspendCancellableCoroutine { cont ->
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                cont.resume(status)
            }

            override fun onError(error: String?) {
                cont.resume(null)
            }
        })
    }

    private suspend fun createRegion(
        key: String,
        styleUrl: String,
        bounds: GeoBounds,
        minZoom: Double,
        maxZoom: Double,
    ): OfflineRegion? = suspendCancellableCoroutine { cont ->
        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl,
            LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west),
            minZoom,
            maxZoom,
            appContext.resources.displayMetrics.density,
        )
        manager.createOfflineRegion(definition, key.encodeToByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                cont.resume(offlineRegion)
            }

            override fun onError(error: String) {
                cont.resume(null)
            }
        })
    }
}
