package io.issac.pleosandroid.core.vehicle.datasource

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import io.issac.pleosandroid.core.domain.collection.repository.VisitLocation
import io.issac.pleosandroid.core.domain.collection.repository.VisitLocationSource
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/** FusedLocation confidence has no metric accuracy; visit proof requires an Android accuracy-bearing fix. */
internal class AndroidVisitLocationSource @Inject constructor(@param:ApplicationContext private val context: Context) :
    VisitLocationSource {
    override val locations = observeVisitLocations(
        hasPermission = {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        },
        updates = ::locationUpdates,
    )

    private fun locationUpdates() = callbackFlow {
        trySend(null)
        // Permission may change between the observation poll and listener registration.
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            close()
            return@callbackFlow
        }
        val manager = context.getSystemService(LocationManager::class.java)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(
                    location.takeIf { it.hasAccuracy() }?.let {
                        VisitLocation(it.latitude, it.longitude, it.accuracy.toDouble(), it.elapsedRealtimeNanos)
                    },
                )
            }
            override fun onProviderDisabled(provider: String) {
                trySend(null)
            }
            override fun onProviderEnabled(provider: String) = Unit

            @Deprecated("Required on Android 9")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        try {
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (manager.allProviders.contains(provider)) {
                    manager.requestLocationUpdates(provider, 1_000, 0f, listener, Looper.getMainLooper())
                }
            }
        } catch (_: SecurityException) {
            trySend(null)
        } catch (_: IllegalArgumentException) {
            trySend(null)
        }
        awaitClose { manager.removeUpdates(listener) }
    }
}
