package io.issac.pleosandroid.feature.main.map

import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class JourneyArrivalMonitor @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun arrivals(): Flow<Unit> = callbackFlow {
        if (context.checkSelfPermission(ROUTE_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            close()
            return@callbackFlow
        }

        val helper = try {
            NaviHelper(context)
        } catch (_: RuntimeException) {
            close()
            return@callbackFlow
        }
        val listener = object : NaviHelperEventListener {
            override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
                trySend(Unit)
            }

            override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
                trySend(Unit)
            }
        }

        var registrationAttempted = false
        try {
            try {
                helper.initialize(
                    onConnected = {
                        launch {
                            registrationAttempted = true
                            try {
                                helper.addListener(listener)
                            } catch (_: RuntimeException) {
                                close()
                            }
                        }
                    },
                    onFailed = { close() },
                )
            } catch (_: RuntimeException) {
                close()
            }
            awaitClose()
        } finally {
            if (registrationAttempted) runCatching { helper.removeListener(listener) }
            runCatching { helper.release() }
        }
    }.conflate().flowOn(Dispatchers.Main.immediate)

    private companion object {
        const val ROUTE_PERMISSION = "pleos.car.permission.NAVI_ROUTE"
    }
}
