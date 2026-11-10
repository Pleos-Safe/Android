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
import io.issac.pleosandroid.core.domain.collection.repository.CollectionDemoRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
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
    private val profiles: CollectionDemoRepository,
) : ViewModel() {
    private val location = PlaceMapLocation(context, viewModelScope)
    private val visitArrival = MutableStateFlow<VisitArrival?>(null)
    private val navigationDelegate = lazy {
        PlaceMapNavigation(context, viewModelScope, ::onNavigationArrival).also { it.updateCatalog(this.places.value) }
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

    // Speed samples keep arriving in P. Keep the GPS subscription alive throughout the same stop.
    private val parkedVisit = combine(
        journeys.state,
        vehicleRepository.state,
        recorder.enabled,
        profiles.enabled,
    ) { journey, vehicle, enabled, demo ->
        journey.session?.takeIf { it.paused && !it.recoveryRequired && enabled && !demo && vehicle.allowsTabNavigation }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val visitFix = parkedVisit.flatMapLatest { session ->
        if (session == null) {
            flowOf(null)
        } else {
            val parkedAt = checkNotNull(session.pausedAtNanos)
            visitLocation.locations.scan<VisitLocation?, VisitLocation?>(null) { previous, next ->
                chooseParkedVisitFix(previous, next, parkedAt, SystemClock.elapsedRealtimeNanos())
            }.distinctUntilChanged()
                .transformLatest { fix ->
                    emit(fix)
                    if (fix != null) {
                        val ageNanos = SystemClock.elapsedRealtimeNanos() - fix.elapsedNanos
                        delay(((MAX_VISIT_FIX_AGE_NANOS - ageNanos) / 1_000_000L).coerceAtLeast(0))
                        emit(null)
                    }
                }.catch { emit(null) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), null)
    private val parkedGeneration = parkedVisit.map { it?.id }.distinctUntilChanged().map { id ->
        if (id == null) null else runCatching { progress.getDataGeneration() }.getOrNull()?.let { id to it }
    }
    private val visitRouteEvidence by lazy {
        combine(navigation.state, visitArrival) { route, arrival -> VisitRouteEvidence(route.routePlaceIds, arrival) }
    }
    val claimableVisit by lazy {
        combine(parkedGeneration, visitFix, visitRouteEvidence, places, visitInbox) {
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

    init {
        viewModelScope.launch {
            parkedVisit.collect {
                claimJob?.cancel()
                mutableVisitResult.value = null
            }
        }
    }

    fun claimVisit(visit: ClaimableVisit) {
        if (claimJob?.isActive == true) return
        claimJob = viewModelScope.launch {
            mutableVisitResult.value = try {
                val demo = profiles.enabled.first()
                val route = VisitRouteEvidence(navigation.state.value.routePlaceIds, visitArrival.value)
                val candidate = eligibleVisit(visitFix.value, route, listOf(visit.place))
                if (!demo && candidate?.first == visit.journeyId) {
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
        navigation.recheck()
    }

    private fun eligibleVisit(
        fix: VisitLocation?,
        route: VisitRouteEvidence,
        catalog: List<TravelPlace>,
    ): Pair<String, TravelPlace>? {
        if (!vehicleRepository.state.value.allowsTabNavigation || !recorder.enabled.value) return null
        val session = journeys.state.value.session ?: return null
        val place = selectParkedVisitPlace(session, fix, route, catalog, SystemClock.elapsedRealtimeNanos())
        return place?.let { session.id to it }
    }

    private fun onNavigationArrival(name: String) {
        val session = journeys.state.value.session?.takeUnless { it.finished || it.recoveryRequired } ?: return
        val arrivedName = name.trim()
        val place = places.value.singleOrNull { it.name.trim() == arrivedName } ?: return
        visitArrival.value = VisitArrival(session.id, place.id, SystemClock.elapsedRealtimeNanos())
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
