package com.galmarino.vialix.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import com.stadiamaps.ferrostar.core.location.NavigationLocationProviding
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Live positions from Google Play's fused location provider, for devices that have Play Services.
 * Same contract as Ferrostar's `AndroidLocationProvider`, which `AppGraph` falls back to when
 * [create] returns `null`: a flow of `android.location.Location`, the last known fix first.
 *
 * Callers only reach this after the location permission is granted (the ViewModel gates its
 * collection on it and Ferrostar calls [lastLocation] from `startNavigation`, gated the same way),
 * hence the `MissingPermission` suppressions; a permission revoked while the process lives
 * surfaces as a `SecurityException`, which [lastLocation] turns into `null`.
 */
class FusedLocationProvider private constructor(
    private val client: FusedLocationProviderClient,
) : NavigationLocationProviding {

    @SuppressLint("MissingPermission")
    override suspend fun lastLocation(): Location? =
        try {
            client.lastLocation.awaitOrNull()
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission missing", e)
            null
        }

    @SuppressLint("MissingPermission")
    override fun locationUpdates(intervalMillis: Long): Flow<Location> = callbackFlow {
        lastLocation()?.let { trySend(it) }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis).build()
        val callback =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.locations.forEach { trySend(it) }
                }
            }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    companion object {
        private const val TAG = "Location"

        /** `null` when Google Play Services is missing or unusable; the caller falls back to `LocationManager`. */
        fun create(context: Context): FusedLocationProvider? {
            val status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
            if (status != ConnectionResult.SUCCESS) {
                Log.i(TAG, "Play Services unavailable (status $status); using LocationManager")
                return null
            }
            Log.i(TAG, "Using Google Play fused location provider")
            return FusedLocationProvider(LocationServices.getFusedLocationProviderClient(context))
        }
    }
}

/** Suspends until [this] settles; failure and cancellation both read as "no result". */
private suspend fun <T> Task<T>.awaitOrNull(): T? =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resume(null) }
        addOnCanceledListener { continuation.resume(null) }
    }
