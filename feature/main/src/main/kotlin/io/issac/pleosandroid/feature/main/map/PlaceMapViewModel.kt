package io.issac.pleosandroid.feature.main.map

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.issac.pleosandroid.core.domain.collection.model.PlaceQuery
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimRequest
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimResult
import io.issac.pleosandroid.core.domain.collection.repository.PlaceRepository
import io.issac.pleosandroid.core.domain.collection.repository.ProgressRepository
import io.issac.pleosandroid.core.domain.collection.repository.VisitLocation
import io.issac.pleosandroid.core.domain.collection.repository.VisitLocationSource
import io.issac.pleosandroid.core.domain.collection.repository.VisitPackRepository
import io.issac.pleosandroid.core.domain.driving.repository.DrivingObservationKind
import io.issac.pleosandroid.core.domain.driving.repository.DrivingRecorder
import io.issac.pleosandroid.core.domain.driving.repository.JourneyRepository
import io.issac.pleosandroid.core.domain.repository.BadgeRegionSource
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import io.issac.pleosandroid.feature.main.DrivingSafetyMessage
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Keep one route writer and one pending request across the parked map and driving home.
internal val LocalPlaceMapViewModel = staticCompositionLocalOf<PlaceMapViewModel?> { null }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class PlaceMapViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val vehicleRepository: VehicleRepository,
    badgeRegionSource: BadgeRegionSource,
    placeRepository: PlaceRepository,
    private val recorder: DrivingRecorder,
    private val journeys: JourneyRepository,
    visitLocation: VisitLocationSource,
    private val visitPacks: VisitPackRepository,
    private val progress: ProgressRepository,
) : ViewModel() {
    private val location = PlaceMapLocation(context, viewModelScope)
    private val navigationDelegate = lazy {
        PlaceMapNavigation(context, viewModelScope).also { it.updateCatalog(this.places.value) }
    }
    private val navigation by navigationDelegate
    private var parkedMapVisible = false
    private val drivingJourney = MutableStateFlow<Long?>(null)
    private val recommendationSession = DrivingRecommendationSession()
    private var messageJob: Job? = null
    private val mutableSafetyMessage = MutableStateFlow<DrivingSafetyMessage?>(null)

    private val catalogRefresh = MutableStateFlow(0)
    private val mutableCatalogError = MutableStateFlow(false)
    val catalogError = mutableCatalogError.asStateFlow()
    val places = catalogRefresh.flatMapLatest {
        placeRepository.observePlaces(PlaceQuery()).onStart { mutableCatalogError.value = false }.catch {
            mutableCatalogError.value = true
            emit(emptyList())
        }
    }.map { rows ->
        rows.map { row ->
            val p = row.place
            TravelPlace(
                p.id,
                p.name,
                p.latitude,
                p.longitude,
                p.district,
                p.category,
                p.imageKey,
                row.ownedCardIds.isNotEmpty(),
                p.regionId,
                p.radiusMeters,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            recorder.observations.collect { observation ->
                if (drivingJourney.value == observation.startedAtMillis) {
                    messageJob?.cancel()
                    mutableSafetyMessage.value = when (observation.kind) {
                        DrivingObservationKind.Acceleration -> DrivingSafetyMessage.Acceleration
                        DrivingObservationKind.Braking -> DrivingSafetyMessage.Braking
                    }
                    messageJob = viewModelScope.launch {
                        delay(5_000)
                        mutableSafetyMessage.value = null
                    }
                }
            }
        }
        viewModelScope.launch {
            this@PlaceMapViewModel.places.collect { list ->
                if (navigationDelegate.isInitialized()) navigation.updateCatalog(list)
            }
        }
    }

    val locationState = location.state
    val navigationState get() = navigation.state
    val badgeRegionIds = badgeRegionSource.collectedRegionIds.catch { emit(emptySet()) }
    val drivingSafetyMessage = mutableSafetyMessage.asStateFlow()
    val visitInbox = visitPacks.observeInbox().catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    private val parkedVisit =
        combine(journeys.state, vehicleRepository.state, recorder.enabled) { journey, vehicle, enabled ->
            journey.session?.takeIf { it.paused && !it.recoveryRequired && enabled && vehicle.allowsTabNavigation }
        }
    private val visitFix = parkedVisit.flatMapLatest { session ->
        if (session == null) flowOf(null) else visitLocation.locations.catch { emit(null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), null)
    private val parkedGeneration = parkedVisit.map { it?.id }.distinctUntilChanged().map { id ->
        if (id == null) null else runCatching { progress.getDataGeneration() }.getOrNull()?.let { id to it }
    }
    val claimableVisit by lazy {
        combine(parkedGeneration, visitFix, navigation.state, places, visitInbox) {
                generation,
                fix,
                route,
                catalog,
                inbox,
            ->
            if (generation == null) return@combine null
            val (id, token) = generation
            val claimedIds = inbox.filter { it.journeyId == id }.mapTo(mutableSetOf()) { it.placeId }
            eligibleVisit(fix, route, catalog.filterNot { it.id in claimedIds })
                ?.takeIf { it.first == id }?.let { ClaimableVisit(id, it.second, token) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), null)
    }
    private val mutableVisitResult = MutableStateFlow<VisitPackClaimResult?>(null)
    val visitResult = mutableVisitResult.asStateFlow()
    private var claimJob: Job? = null

    fun claimVisit(visit: ClaimableVisit) {
        if (claimJob?.isActive == true) return
        claimJob = viewModelScope.launch {
            mutableVisitResult.value = try {
                if (eligibleVisit(visitFix.value, navigation.state.value, listOf(visit.place)) ==
                    (visit.journeyId to visit.place)
                ) {
                    visitPacks.claim(
                        VisitPackClaimRequest(
                            visit.journeyId,
                            visit.place.id,
                            visit.place.name,
                            visit.place.category,
                            visit.generation,
                        ),
                    )
                } else {
                    VisitPackClaimResult.InvalidInput
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                VisitPackClaimResult.StorageFailure
            }
        }
    }

    fun refreshParkedVisit() {
        mutableVisitResult.value = null
        navigation.recheck()
    }

    private fun eligibleVisit(
        fix: VisitLocation?,
        route: PlaceNavigationState,
        catalog: List<TravelPlace>,
    ): Pair<String, TravelPlace>? {
        val session = journeys.state.value.session ?: return null
        val parkedAt = session.pausedAtNanos ?: return null
        if (!vehicleRepository.state.value.allowsTabNavigation || !recorder.enabled.value || session.recoveryRequired ||
            !session.paused || fix == null || fix.elapsedNanos < parkedAt ||
            SystemClock.elapsedRealtimeNanos() - fix.elapsedNanos !in 0..10_000_000_000L ||
            fix.accuracyMeters !in 0.0..100.0
        ) {
            return null
        }
        val position = PlaceMapPosition(fix.latitude, fix.longitude)
        val nearby = catalog.asSequence().filter { it.id in route.routePlaceIds }.map { place ->
            place to position.distanceMetersTo(PlaceMapPosition(place.latitude, place.longitude))
        }.filter { (place, distance) -> distance + fix.accuracyMeters <= place.visitRadiusMeters }
            .minByOrNull { it.second }?.first ?: return null
        return session.id to nearby
    }

    val drivingPosition = combine(drivingJourney, location.freshFix) { journeyId, fix ->
        if (journeyId != null) currentPosition(fix) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), null)

    val drivingPlace by lazy {
        combine(drivingJourney, location.freshFix, navigation.state, vehicleRepository.state, this.places) {
                journeyId,
                fix,
                route,
                vehicle,
                catalog,
            ->
            if (journeyId == null || !vehicle.allowsDrivingPlaceRecommendation) {
                null
            } else {
                recommendationSession.select(journeyId, currentPosition(fix), route, catalog)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), null)
    }

    fun canInteract(): Boolean = vehicleRepository.state.value.allowsTabNavigation

    fun retryCatalog() {
        if (canInteract()) catalogRefresh.value += 1
    }

    fun refreshLocation() {
        if (!canInteract()) return
        if (catalogError.value) retryCatalog()
        parkedMapVisible = true
        location.refresh()
    }

    fun stopLocation() {
        parkedMapVisible = false
        if (drivingJourney.value == null) location.close()
    }

    fun request(place: TravelPlace) = navigation.request(place) { canInteract() && place in places.value }

    fun recheck() {
        if (canInteract()) navigation.recheck()
    }

    fun startDrivingOverview(journeyId: Long) {
        drivingJourney.value = journeyId
        location.refresh()
        navigation.recheck()
    }

    fun stopDrivingOverview(journeyId: Long) {
        if (drivingJourney.value != journeyId) return
        drivingJourney.value = null
        messageJob?.cancel()
        mutableSafetyMessage.value = null
        if (!parkedMapVisible) location.close()
    }

    fun requestDrivingPlace(place: TravelPlace, hasDestination: Boolean, isAllowed: () -> Boolean) {
        navigation.request(place) {
            val route = navigation.state.value
            val position = currentPosition(location.freshFix.value)
            drivingJourney.value != null && isAllowed() && route.hasDestination == hasDestination &&
                route.hasWaypointCapacity && place.id !in route.routePlaceIds && drivingPlace.value == place &&
                position != null && place.nearbyDistanceMeters(position) != null
        }
    }

    fun recheckWhileDriving(isAllowed: () -> Boolean) {
        if (drivingJourney.value != null && isAllowed()) navigation.recheck()
    }

    private fun currentPosition(fix: FreshPlaceFix?): PlaceMapPosition? =
        fix?.takeIf { it.isFresh() && location.hasPermission() }?.position

    override fun onCleared() {
        location.close()
        if (navigationDelegate.isInitialized()) navigation.close()
    }
}

internal data class ClaimableVisit(val journeyId: String, val place: TravelPlace, val generation: String)
