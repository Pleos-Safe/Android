package io.issac.pleosandroid.feature.main.map

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class PlaceNavigationOperation {
    Idle,
    Checking,
    Requesting,
    Applied,
    Rejected,
    Unconfirmed,
    Unavailable,
}

internal data class PlaceNavigationState(
    val hasDestination: Boolean? = null,
    val operation: PlaceNavigationOperation = PlaceNavigationOperation.Checking,
    val routePlaceIds: Set<String> = emptySet(),
    val destinationName: String? = null,
    val waypointNames: List<String> = emptyList(),
    val hasWaypointCapacity: Boolean = true,
)

internal class PlaceMapNavigation(context: Context, private val scope: CoroutineScope) {
    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(PlaceNavigationState())
    val state: StateFlow<PlaceNavigationState> = mutableState.asStateFlow()

    private var connection: Connection? = null
    private var route: RouteSnapshot? = null
    private var read: RouteRead? = null
    private var pending: PendingChange? = null
    private var connectionTimeout: Job? = null
    private var readTimeout: Job? = null
    private var requestTimeout: Job? = null
    private var routeRevision = 0L
    private var closed = false
    private var catalog: List<TravelPlace> = emptyList()

    fun updateCatalog(places: List<TravelPlace>) = onMain {
        catalog = places
        update(state.value.operation)
    }

    init {
        onMain(::connect)
    }

    fun request(place: TravelPlace, canRequest: () -> Boolean) = onMain {
        val previous = route ?: return@onMain
        if (closed || read != null || pending != null || !canRequest()) return@onMain
        if (!hasPermission(ROUTE_SEARCH_PERMISSION)) {
            update(PlaceNavigationOperation.Unavailable)
            return@onMain
        }
        update(PlaceNavigationOperation.Requesting)
        readRoute(PlaceNavigationOperation.Unavailable) { current ->
            if (!canRequest() || !current.sameRoute(previous)) {
                update(PlaceNavigationOperation.Rejected)
            } else {
                sendRequest(place, current)
            }
        }
    }

    fun recheck() = onMain {
        if (closed || read != null || state.value.operation == PlaceNavigationOperation.Requesting) return@onMain
        if (connection == null) {
            connect()
        } else if (connection?.ready == true) {
            update(PlaceNavigationOperation.Checking)
            if (pending == null) refreshRoute() else verifyRequest(reconcile = true)
        }
    }

    fun close() = onMain {
        closed = true
        connectionTimeout?.cancel()
        readTimeout?.cancel()
        requestTimeout?.cancel()
        read = null
        pending = null
        disconnect()
    }

    private fun connect() {
        if (closed || connection != null) return
        if (!hasPermission(ROUTE_PERMISSION)) {
            update(PlaceNavigationOperation.Unavailable)
            return
        }
        val source = try {
            Connection(NaviHelper(applicationContext))
        } catch (_: RuntimeException) {
            update(PlaceNavigationOperation.Unavailable)
            return
        }
        connection = source
        update(PlaceNavigationOperation.Checking)
        connectionTimeout = schedule(CONNECTION_TIMEOUT_MS) { connectionFailed(source) }
        try {
            source.sdk.initialize(
                onConnected = {
                    source.dispatch {
                        connectionTimeout?.cancel()
                        try {
                            source.sdk.addListener(source.listener)
                            source.ready = true
                            refreshRoute()
                        } catch (_: RuntimeException) {
                            connectionFailed(source)
                        }
                    }
                },
                onFailed = { source.dispatch { connectionFailed(source) } },
            )
        } catch (_: RuntimeException) {
            connectionFailed(source)
        }
    }

    private fun connectionFailed(source: Connection) {
        if (connection !== source) return
        connectionTimeout?.cancel()
        disconnect()
        update(PlaceNavigationOperation.Unavailable)
    }

    private fun disconnect() {
        val source = connection ?: return
        connection = null
        if (source.ready) runCatching { source.sdk.removeListener(source.listener) }
        runCatching { source.sdk.release() }
    }

    private fun refreshRoute(success: PlaceNavigationOperation = PlaceNavigationOperation.Idle) {
        readRoute(PlaceNavigationOperation.Unavailable) { update(success) }
    }

    private fun routeChanged() {
        routeRevision++
        if (read != null) return
        if (pending != null) {
            verifyRequest()
        } else {
            val outcome = when (state.value.operation) {
                PlaceNavigationOperation.Applied, PlaceNavigationOperation.Rejected -> state.value.operation
                else -> PlaceNavigationOperation.Idle
            }
            refreshRoute(outcome)
        }
    }

    private fun readRoute(
        failure: PlaceNavigationOperation,
        retryOnChange: Boolean = true,
        onResult: (RouteSnapshot) -> Unit,
    ) {
        val source = connection?.takeIf { it.ready }
        if (source == null) {
            update(failure)
            return
        }
        val query = RouteRead(routeRevision, failure, retryOnChange, onResult)
        read = query
        readTimeout = schedule(ROUTE_READ_TIMEOUT_MS) { failRead(query) }
        try {
            source.sdk.getRouteStateInfo()
        } catch (_: RuntimeException) {
            failRead(query)
        }
    }

    private fun readRouteDetails(info: RouteStateInfo) {
        val query = read ?: return
        if (query.awaitingDetails) return
        when {
            info.naviMode == NaviMode.ROUTE_GUIDING || info.naviMode == NaviMode.SIMULATION_GUIDING -> {
                val sdk = connection?.sdk ?: return
                query.awaitingDetails = true
                try {
                    sdk.getDestinationInfo()
                    sdk.getWaypointInfo()
                } catch (_: RuntimeException) {
                    failRead(query)
                }
            }

            !info.isRouting -> completeRead(query, RouteSnapshot())
        }
    }

    private fun completeDetails(query: RouteRead) {
        val destination = query.destination ?: return
        val waypoints = query.waypoints ?: return
        completeRead(query, RouteSnapshot(destination, waypoints))
    }

    private fun completeRead(query: RouteRead, snapshot: RouteSnapshot) {
        if (query.revision != routeRevision && !query.retryOnChange) {
            failRead(query)
            return
        }
        readTimeout?.cancel()
        read = null
        if (query.revision != routeRevision) {
            readRoute(query.failure, retryOnChange = false, onResult = query.onResult)
            return
        }
        route = snapshot
        query.onResult(snapshot)
    }

    private fun failRead(query: RouteRead) {
        if (read !== query) return
        readTimeout?.cancel()
        read = null
        if (state.value.operation != PlaceNavigationOperation.Unconfirmed) update(query.failure)
    }

    private fun sendRequest(place: TravelPlace, previous: RouteSnapshot) {
        val sdk = connection?.sdk ?: return update(PlaceNavigationOperation.Unavailable)
        val point = RoutePoint(place.latitude, place.longitude, place.name)
        if (!point.hasCoordinates) {
            update(PlaceNavigationOperation.Rejected)
            return
        }
        val alreadyOnRoute = previous.destination?.matches(point) == true || previous.waypoints.any {
            it.matches(point)
        }
        if (alreadyOnRoute) {
            update(PlaceNavigationOperation.Applied)
            return
        }
        if (previous.waypoints.size >= MAX_WAYPOINTS) {
            update(PlaceNavigationOperation.Rejected)
            return
        }
        val change = PendingChange(point, previous)
        pending = change
        try {
            if (previous.destination == null) {
                sdk.requestRoute(
                    RouteInfo(
                        latitude = place.latitude,
                        longitude = place.longitude,
                        displayLatitude = null,
                        displayLongitude = null,
                        poiName = place.name,
                        poiId = "",
                        address = "",
                        poiSubId = "",
                    ),
                )
            } else {
                sdk.addWaypoint(
                    RequestWaypointInfo(
                        latitude = place.latitude,
                        longitude = place.longitude,
                        poiName = place.name,
                        waypointIndex = if (previous.waypoints.isEmpty()) WaypointIndex.FIRST else WaypointIndex.SECOND,
                        poiId = "",
                        address = "",
                        poiSubId = "",
                    ),
                )
            }
        } catch (_: SecurityException) {
            finishRequest(PlaceNavigationOperation.Rejected)
            return
        } catch (_: RuntimeException) {
            update(PlaceNavigationOperation.Unconfirmed)
            return
        }
        requestTimeout = schedule(REQUEST_TIMEOUT_MS) {
            if (pending === change) update(PlaceNavigationOperation.Unconfirmed)
        }
    }

    private fun verifyRequest(reconcile: Boolean = false) {
        val change = pending ?: return
        val failure = if (reconcile || state.value.operation == PlaceNavigationOperation.Unconfirmed) {
            PlaceNavigationOperation.Unconfirmed
        } else {
            PlaceNavigationOperation.Requesting
        }
        readRoute(failure) { current ->
            when {
                change.matches(current) -> finishRequest(PlaceNavigationOperation.Applied)

                change.routeError && current.sameRoute(change.previous) ->
                    finishRequest(PlaceNavigationOperation.Rejected)

                reconcile -> finishRequest(PlaceNavigationOperation.Idle)
            }
        }
    }

    private fun finishRequest(result: PlaceNavigationOperation) {
        requestTimeout?.cancel()
        pending = null
        update(result)
    }

    private fun update(operation: PlaceNavigationOperation) {
        val snapshot = route
        val routePlaces = if (snapshot?.destination == null) {
            emptySet()
        } else {
            val points = listOf(snapshot.destination) + snapshot.waypoints
            catalog.filter { place ->
                val candidate = RoutePoint(place.latitude, place.longitude, place.name)
                points.any { it.matches(candidate) }
            }.mapTo(mutableSetOf()) { it.id }
        }
        mutableState.value = PlaceNavigationState(
            hasDestination = snapshot?.let { it.destination != null },
            operation = operation,
            routePlaceIds = routePlaces,
            destinationName = snapshot?.destination?.name,
            waypointNames = snapshot?.waypoints.orEmpty().map { it.name },
            hasWaypointCapacity = snapshot == null || snapshot.waypoints.size < MAX_WAYPOINTS,
        )
    }

    private fun hasPermission(permission: String): Boolean =
        applicationContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == mainHandler.looper) action() else mainHandler.post(action)
    }

    private fun schedule(milliseconds: Long, action: () -> Unit): Job = scope.launch(Dispatchers.Main.immediate) {
        delay(milliseconds)
        action()
    }

    private inner class Connection(val sdk: NaviHelper) {
        var ready = false

        fun dispatch(action: () -> Unit) {
            mainHandler.post { if (!closed && connection === this) action() }
        }

        val listener = object : NaviHelperEventListener {
            override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = dispatch {
                readRouteDetails(routeStateInfo)
            }

            override fun onDestinationInfo(destinationInfo: DestinationInfo) = dispatch {
                val query = read?.takeIf { it.awaitingDetails } ?: return@dispatch
                val point = RoutePoint(destinationInfo.latitude, destinationInfo.longitude, destinationInfo.name)
                if (point.hasCoordinates) {
                    query.destination = point
                    completeDetails(query)
                }
            }

            override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) = dispatch {
                val query = read?.takeIf { it.awaitingDetails } ?: return@dispatch
                val points = waypointInfoList.map { RoutePoint(it.latitude, it.longitude, it.name) }
                if (points.all(RoutePoint::valid)) {
                    query.waypoints = points
                    completeDetails(query)
                }
            }

            override fun onDestinationChanged() = dispatch(::routeChanged)
            override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) = dispatch(::routeChanged)
            override fun onRoutingCompleted() = dispatch(::routeChanged)
            override fun onRouteCancelled() = dispatch(::routeChanged)
            override fun onRouteEnded() = dispatch(::routeChanged)

            override fun onError(errorCode: NaviErrorCode) = dispatch {
                val change = pending ?: return@dispatch
                if (errorCode != NaviErrorCode.CAUSE_SEARCH_TRIP) {
                    // The SDK does not attach a request ID to errors. Read back the actual route before rollback.
                    change.routeError = true
                    routeChanged()
                }
            }
        }
    }

    private class RouteRead(
        val revision: Long,
        val failure: PlaceNavigationOperation,
        val retryOnChange: Boolean,
        val onResult: (RouteSnapshot) -> Unit,
        var awaitingDetails: Boolean = false,
        var destination: RoutePoint? = null,
        var waypoints: List<RoutePoint>? = null,
    )

    private data class RouteSnapshot(
        val destination: RoutePoint? = null,
        val waypoints: List<RoutePoint> = emptyList(),
    ) {
        fun sameRoute(other: RouteSnapshot): Boolean {
            if (destination == null) return other.destination == null
            return other.destination?.matches(destination) == true &&
                waypoints.size == other.waypoints.size &&
                waypoints.zip(other.waypoints).all { (point, otherPoint) -> point.matches(otherPoint) }
        }
    }

    private class PendingChange(val place: RoutePoint, val previous: RouteSnapshot, var routeError: Boolean = false) {
        fun matches(current: RouteSnapshot): Boolean {
            val oldDestination = previous.destination ?: return current.destination?.matches(place) == true
            return current.destination?.matches(oldDestination) == true &&
                current.waypoints.size == previous.waypoints.size + 1 &&
                current.waypoints.last().matches(place) &&
                previous.waypoints.zip(current.waypoints).all { (old, new) -> old.matches(new) }
        }
    }

    private companion object {
        const val ROUTE_PERMISSION = "pleos.car.permission.NAVI_ROUTE"
        const val ROUTE_SEARCH_PERMISSION = "pleos.car.permission.NAVI_ROUTE_SEARCH"
        const val CONNECTION_TIMEOUT_MS = 8_000L
        const val ROUTE_READ_TIMEOUT_MS = 3_000L

        // Route calculation on the Pleos emulator can take more than ten seconds.
        const val REQUEST_TIMEOUT_MS = 30_000L
        const val MAX_WAYPOINTS = 2
    }
}
