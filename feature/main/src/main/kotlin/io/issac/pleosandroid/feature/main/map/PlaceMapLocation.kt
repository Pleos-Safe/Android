package io.issac.pleosandroid.feature.main.map

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val PLEOS_LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"

internal sealed interface PlaceLocationState {
    data object PermissionRequired : PlaceLocationState
    data object Loading : PlaceLocationState
    data object Unavailable : PlaceLocationState
    data class Available(val position: PlaceMapPosition) : PlaceLocationState
}

internal class PlaceMapLocation(private val context: Context, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow<PlaceLocationState>(PlaceLocationState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableFreshFix = MutableStateFlow<FreshPlaceFix?>(null)
    val freshFix = mutableFreshFix.asStateFlow()

    fun hasPermission(): Boolean =
        context.checkSelfPermission(PLEOS_LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED

    private var sdk: FusedLocation? = null
    private var listener: FusedLocationListener? = null
    private var timeout: Job? = null
    private var fixExpiry: Job? = null

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
            val callback = object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    val callback = this
                    scope.launch {
                        if (listener !== callback) return@launch
                        if (location == null) {
                            clearFreshFix()
                            return@launch
                        }
                        val position = PlaceMapPosition(location.latitude, location.longitude)
                        if (position.latitude in -90.0..90.0 &&
                            position.longitude in -180.0..180.0
                        ) {
                            timeout?.cancel()
                            mutableState.value = PlaceLocationState.Available(position)
                            updateFreshFix(position, location.timestampMillis)
                        } else {
                            clearFreshFix()
                        }
                    }
                }

                override fun onError(exception: Exception) {
                    val callback = this
                    scope.launch {
                        if (listener !== callback) return@launch
                        close()
                        mutableState.value = PlaceLocationState.Unavailable
                    }
                }
            }
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
}

internal data class FreshPlaceFix(val position: PlaceMapPosition, val timestampMillis: Long) {
    fun isFresh(): Boolean = System.currentTimeMillis() - timestampMillis in 0..FIX_MAX_AGE_MS
}

private const val FIX_MAX_AGE_MS = 30_000L
