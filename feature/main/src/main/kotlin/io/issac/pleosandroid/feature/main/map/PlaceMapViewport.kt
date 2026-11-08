package io.issac.pleosandroid.feature.main.map

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.issac.pleosandroid.core.designsystem.component.PleosInfoPanel
import io.issac.pleosandroid.core.designsystem.component.PleosMapPin
import io.issac.pleosandroid.core.designsystem.component.PleosPlaceMap
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.feature.main.R
import kotlin.math.roundToInt
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView

@Composable
internal fun PlaceMapViewport(
    places: List<TravelPlace>,
    selectedPlaceId: String?,
    onPlaceSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    currentPosition: PlaceMapPosition? = null,
    focusPosition: PlaceMapPosition? = null,
    onVisibleBoundsChanged: (PlaceMapBounds) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var savedCamera by rememberSaveable { mutableStateOf(BusanCamera) }
    val mapView = remember(context, lifecycle) {
        MapLibre.getInstance(context.applicationContext)
        val options = MapLibreMapOptions.createFromAttributes(context)
            .camera(savedCamera)
            .textureMode(true)
            .compassEnabled(false)
            .rotateGesturesEnabled(false)
            .tiltGesturesEnabled(false)
            .attributionEnabled(true)
        MapView(context, options).apply { onCreate(null) }
    }
    ObserveMapViewLifecycle(mapView, lifecycle)

    var map by remember(mapView) { mutableStateOf<MapLibreMap?>(null) }
    var basemapLoading by remember(mapView) { mutableStateOf(true) }
    var basemapFailed by remember(mapView) { mutableStateOf(false) }
    var pinPoints by remember(mapView) { mutableStateOf(emptyMap<String, IntOffset?>()) }
    var locationPoint by remember(mapView) { mutableStateOf<IntOffset?>(null) }
    val latestPlaces by rememberUpdatedState(places)
    val latestPosition by rememberUpdatedState(currentPosition)
    val publishBounds by rememberUpdatedState(onVisibleBoundsChanged)

    fun updateMarkerPoints(activeMap: MapLibreMap) {
        pinPoints = latestPlaces.associate { place ->
            place.id to activeMap.project(place.latitude, place.longitude)
        }
        locationPoint = latestPosition?.let { activeMap.project(it.latitude, it.longitude) }
    }

    DisposableEffect(mapView) {
        val failed = MapView.OnDidFailLoadingMapListener {
            basemapLoading = false
            basemapFailed = true
        }
        val loaded = MapView.OnDidFinishLoadingMapListener {
            basemapLoading = false
            basemapFailed = false
        }
        mapView.addOnDidFailLoadingMapListener(failed)
        mapView.addOnDidFinishLoadingMapListener(loaded)
        mapView.getMapAsync { readyMap ->
            if (mapView.isDestroyed) return@getMapAsync
            readyMap.setStyle(OPEN_FREE_MAP_DARK)
            map = readyMap
        }
        onDispose {
            mapView.removeOnDidFailLoadingMapListener(failed)
            mapView.removeOnDidFinishLoadingMapListener(loaded)
        }
    }
    LaunchedEffect(map, places, currentPosition) {
        map?.let(::updateMarkerPoints)
    }
    LaunchedEffect(map, focusPosition) {
        if (focusPosition != null) {
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(focusPosition.latitude, focusPosition.longitude), 11.5),
            )
        }
    }
    DisposableEffect(map, mapView) {
        val activeMap = map ?: return@DisposableEffect onDispose { }
        var lastPublishedBounds: PlaceMapBounds? = null

        fun cameraSettled() {
            savedCamera = activeMap.cameraPosition
            updateMarkerPoints(activeMap)
            if (mapView.width > 0 && mapView.height > 0) {
                val bounds = activeMap.projection.visibleRegion.latLngBounds
                val visibleBounds = PlaceMapBounds(
                    south = bounds.latitudeSouth,
                    west = bounds.longitudeWest,
                    north = bounds.latitudeNorth,
                    east = bounds.longitudeEast,
                )
                if (lastPublishedBounds != visibleBounds) {
                    lastPublishedBounds = visibleBounds
                    publishBounds(visibleBounds)
                }
            }
        }

        val moveListener = MapLibreMap.OnCameraMoveListener { updateMarkerPoints(activeMap) }
        val idleListener = MapLibreMap.OnCameraIdleListener(::cameraSettled)
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> cameraSettled() }
        activeMap.addOnCameraMoveListener(moveListener)
        activeMap.addOnCameraIdleListener(idleListener)
        mapView.addOnLayoutChangeListener(layoutListener)
        cameraSettled()
        onDispose {
            activeMap.removeOnCameraMoveListener(moveListener)
            activeMap.removeOnCameraIdleListener(idleListener)
            mapView.removeOnLayoutChangeListener(layoutListener)
        }
    }

    PleosPlaceMap(
        modifier = modifier,
        onZoomIn = map?.let { activeMap -> { activeMap.animateCamera(CameraUpdateFactory.zoomIn()) } },
        onZoomOut = map?.let { activeMap -> { activeMap.animateCamera(CameraUpdateFactory.zoomOut()) } },
        onLocate = currentPosition?.let { position ->
            map?.let { activeMap ->
                {
                    val target = LatLng(position.latitude, position.longitude)
                    activeMap.animateCamera(CameraUpdateFactory.newLatLng(target))
                }
            }
        },
    ) {
        AndroidView(factory = { mapView }, modifier = Modifier.matchParentSize())
        PlaceMapMarkers(
            places = places,
            selectedPlaceId = selectedPlaceId,
            pinPoints = pinPoints,
            locationPoint = locationPoint,
            onPlaceSelected = onPlaceSelected,
            modifier = Modifier.matchParentSize(),
        )
        if (basemapLoading) {
            PleosInfoPanel(
                Modifier.align(Alignment.TopStart)
                    .padding(start = PleosSpacing.space16, top = PleosSpacing.space16, end = PleosSpacing.space64),
            ) {
                Text(
                    stringResource(R.string.place_map_basemap_loading),
                    style = PleosTextStyles.body,
                    color = PleosTheme.colors.textPrimary,
                )
            }
        } else if (basemapFailed) {
            PleosInfoPanel(
                Modifier.align(Alignment.TopStart)
                    .padding(start = PleosSpacing.space16, top = PleosSpacing.space16, end = PleosSpacing.space64)
                    .widthIn(max = 280.dp),
            ) {
                Text(
                    stringResource(R.string.place_map_basemap_failed),
                    style = PleosTextStyles.body,
                    color = PleosTheme.colors.textPrimary,
                )
                PleosTextLink(
                    stringResource(R.string.place_map_basemap_retry),
                    onClick = {
                        basemapFailed = false
                        basemapLoading = true
                        map?.setStyle(OPEN_FREE_MAP_DARK)
                    },
                    enabled = map != null,
                )
            }
        }
    }
}

private fun MapLibreMap.project(latitude: Double, longitude: Double): IntOffset? {
    val point = projection.toScreenLocation(LatLng(latitude, longitude))
    return if (point.x.isFinite() && point.y.isFinite()) IntOffset(point.x.roundToInt(), point.y.roundToInt()) else null
}

@Composable
private fun PlaceMapMarkers(
    places: List<TravelPlace>,
    selectedPlaceId: String?,
    pinPoints: Map<String, IntOffset?>,
    locationPoint: IntOffset?,
    onPlaceSelected: (String) -> Unit,
    modifier: Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            if (locationPoint != null) CurrentLocationDot()
            places.forEach { place ->
                key(place.id) {
                    PleosMapPin(
                        label = place.name,
                        selected = place.id == selectedPlaceId,
                        onClick = { onPlaceSelected(place.id) },
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val markerConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val markers = measurables.map { it.measure(markerConstraints) }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        layout(width, height) {
            locationPoint?.let { point ->
                if (point.x in 0 until width && point.y in 0 until height) {
                    val dot = markers.first()
                    dot.place(point.x - dot.width / 2, point.y - dot.height / 2)
                }
            }
            val firstPin = if (locationPoint == null) 0 else 1
            places.forEachIndexed { index, place ->
                val point = pinPoints[place.id] ?: return@forEachIndexed
                if (point.x in 0 until width && point.y in 0 until height) {
                    val marker = markers[index + firstPin]
                    marker.place(
                        point.x - marker.width / 2,
                        point.y - marker.height / 2,
                        zIndex = if (place.id == selectedPlaceId) 1f else 0f,
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentLocationDot() {
    val colors = PleosTheme.colors
    Box(
        Modifier.size(24.dp).background(colors.actionPrimary.copy(alpha = .25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(10.dp).background(colors.actionPrimary, CircleShape))
    }
}

@Composable
private fun ObserveMapViewLifecycle(mapView: MapView, lifecycle: Lifecycle) {
    DisposableEffect(mapView, lifecycle) {
        var started = false
        var resumed = false

        fun pause() {
            if (resumed) {
                mapView.onPause()
                resumed = false
            }
        }

        fun stop() {
            pause()
            if (started) {
                mapView.onStop()
                started = false
            }
        }

        fun destroy() {
            stop()
            if (!mapView.isDestroyed) mapView.onDestroy()
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    mapView.onStart()
                    started = true
                }

                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    resumed = true
                }

                Lifecycle.Event.ON_PAUSE -> pause()

                Lifecycle.Event.ON_STOP -> stop()

                Lifecycle.Event.ON_DESTROY -> destroy()

                else -> Unit
            }
        }
        val application = mapView.context.applicationContext
        val memoryCallback = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            override fun onLowMemory() {
                if (!mapView.isDestroyed) mapView.onLowMemory()
            }
        }
        lifecycle.addObserver(observer)
        application.registerComponentCallbacks(memoryCallback)
        onDispose {
            lifecycle.removeObserver(observer)
            application.unregisterComponentCallbacks(memoryCallback)
            destroy()
        }
    }
}

private const val OPEN_FREE_MAP_DARK = "https://tiles.openfreemap.org/styles/dark"
private val BusanCamera = CameraPosition.Builder()
    .target(LatLng(35.17, 129.125))
    .zoom(11.5)
    .build()
