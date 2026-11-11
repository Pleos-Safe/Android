package io.issac.pleosandroid.core.vehicle.place

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.content.pm.PackageManager
import io.issac.pleosandroid.core.domain.place.PLEOS_LOCATION_PERMISSION
import io.issac.pleosandroid.core.domain.place.PlaceLocationState
import io.issac.pleosandroid.core.domain.place.PlaceMapPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class PlaceMapLocation(private val context: Context, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow<PlaceLocationState>(PlaceLocationState.Loading)
    private val mutableFreshFix = MutableStateFlow<FreshPlaceFix?>(null)
    private var sdk: FusedLocation? = null
    private var listener: FusedLocationListener? = null
    private var timeout: Job? = null
    private var fixExpiry: Job? = null

    val state = mutableState.asStateFlow()
    val freshFix = mutableFreshFix.asStateFlow()

    fun hasPermission(): Boolean =
        context.checkSelfPermission(PLEOS_LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun refresh() {
        if (!hasPermission()) {
            close()
            mutableState.value = PlaceLocationState.PermissionRequired
            return
        }
        if (listener != null) return
        mutableState.value = PlaceLocationState.Loading
        try {
            val provider = FusedLocation(context)
            sdk = provider
            if (!provider.isSupported()) {
                close()
                mutableState.value = PlaceLocationState.Unavailable
                return
            }
            provider.initialize()
            val callback = createListener()
            listener = callback
            timeout = scope.launch {
                delay(10_000)
                if (mutableState.value == PlaceLocationState.Loading) {
                    mutableState.value = PlaceLocationState.Unavailable
                }
            }
            provider.registerFusedLocationCallback(callback)
        } catch (_: Exception) {
            close()
            mutableState.value = PlaceLocationState.Unavailable
        }
    }

    fun close() {
        clearFreshFix()
        timeout?.cancel()
        timeout = null
        val callback = listener
        listener = null
        val provider = sdk
        sdk = null
        if (provider != null) {
            if (callback != null) runCatching { provider.unregisterFusedLocationCallback(callback) }
            runCatching { provider.release() }
        }
    }

    private fun createListener() = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            val callback = this
            scope.launch {
                if (listener === callback) acceptFix(location)
            }
        }

        override fun onError(exception: Exception) {
            val callback = this
            scope.launch {
                if (listener === callback) {
                    close()
                    mutableState.value = PlaceLocationState.Unavailable
                }
            }
        }
    }

    private fun acceptFix(location: LocationData?) {
        if (location == null || location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) {
            clearFreshFix()
            return
        }
        val position = PlaceMapPosition(location.latitude, location.longitude)
        timeout?.cancel()
        mutableState.value = PlaceLocationState.Available(position)
        updateFreshFix(position, location.timestampMillis)
    }

    private fun updateFreshFix(position: PlaceMapPosition, timestampMillis: Long) {
        val fix = FreshPlaceFix(position, timestampMillis)
        val ageMillis = System.currentTimeMillis() - timestampMillis
        if (!fix.isFresh() || !hasPermission()) {
            clearFreshFix()
            return
        }
        fixExpiry?.cancel()
        mutableFreshFix.value = fix
        fixExpiry = scope.launch {
            delay(FIX_MAX_AGE_MS - ageMillis + 1)
            mutableFreshFix.value = null
        }
    }

    private fun clearFreshFix() {
        fixExpiry?.cancel()
        fixExpiry = null
        mutableFreshFix.value = null
    }
}

internal data class FreshPlaceFix(val position: PlaceMapPosition, val timestampMillis: Long) {
    fun isFresh(): Boolean = System.currentTimeMillis() - timestampMillis in 0..FIX_MAX_AGE_MS
}

private const val FIX_MAX_AGE_MS = 30_000L
