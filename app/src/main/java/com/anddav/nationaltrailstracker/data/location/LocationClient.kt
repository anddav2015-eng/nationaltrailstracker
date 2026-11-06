package com.anddav.nationaltrailstracker.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One GPS fix. */
data class LocationFix(val lat: Double, val lon: Double, val accuracyMetres: Float?, val timeMillis: Long)

class LocationPermissionMissingException : Exception("Location permission not granted")

class LocationUnavailableException :
    Exception("Couldn't get a location fix. Check that location is turned on, then try again.")

/**
 * A single, foreground-only location request through the Fused Location Provider. Never
 * subscribes to updates and never runs in the background - see CLAUDE.md.
 */
class LocationClient(private val context: Context) {

    fun hasPermission(): Boolean =
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** True when the user only allowed approximate location (fixes may be a kilometre or more out). */
    fun isApproximateOnly(): Boolean =
        !isGranted(Manifest.permission.ACCESS_FINE_LOCATION) && isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    suspend fun currentLocation(): LocationFix {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            throw LocationPermissionMissingException()
        }
        val cancellation = CancellationTokenSource()
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancellation.cancel() }
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                .addOnSuccessListener { location ->
                    if (location == null) {
                        continuation.resumeWithException(LocationUnavailableException())
                    } else {
                        continuation.resume(
                            LocationFix(
                                lat = location.latitude,
                                lon = location.longitude,
                                accuracyMetres = if (location.hasAccuracy()) location.accuracy else null,
                                timeMillis = location.time,
                            ),
                        )
                    }
                }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
        }
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
