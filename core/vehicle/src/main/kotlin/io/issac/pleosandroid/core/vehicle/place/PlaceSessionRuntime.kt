package io.issac.pleosandroid.core.vehicle.place

import android.content.Context
import android.os.SystemClock
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
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.place.ClaimableVisit
import io.issac.pleosandroid.core.domain.place.DrivingRecommendationSession
import io.issac.pleosandroid.core.domain.place.DrivingSafetyMessage
import io.issac.pleosandroid.core.domain.place.MAX_VISIT_FIX_AGE_NANOS
import io.issac.pleosandroid.core.domain.place.PlaceMapPosition
import io.issac.pleosandroid.core.domain.place.TravelPlace
import io.issac.pleosandroid.core.domain.place.VisitArrival
import io.issac.pleosandroid.core.domain.place.VisitRouteEvidence
import io.issac.pleosandroid.core.domain.place.allowsDrivingPlaceRecommendation
import io.issac.pleosandroid.core.domain.place.chooseParkedVisitFix
import io.issac.pleosandroid.core.domain.place.nearbyDistanceMeters
import io.issac.pleosandroid.core.domain.place.selectParkedVisitPlace
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
internal class PlaceSessionRuntime(
    context: Context,
    private val scope: CoroutineScope,
    private val vehicleRepository: VehicleRepository,
    placeRepository: PlaceRepository,
    private val recorder: DrivingRecorder,
    private val journeys: JourneyRepository,
    visitLocation: VisitLocationSource,
    private val visitPacks: VisitPackRepository,
    private val progress: ProgressRepository,
) : AutoCloseable {
    private val location = PlaceMapLocation(context, scope)
    private val visitArrival = MutableStateFlow<VisitArrival?>(null)
    private val arrivalEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var arrivedJourneyId: String? = null
    private val navigationDelegate = lazy {
        PlaceMapNavigation(context, scope, ::onNavigationArrival).also { it.updateCatalog(this.places.value) }
    }
    private val navigation by navigationDelegate
    private val parkedMapOwners = mutableSetOf<String>()
    private val drivingOwners = mutableMapOf<String, Long>()
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
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val locationState = location.state
    val navigationState get() = navigation.state
    val drivingSafetyMessage = mutableSafetyMessage.asStateFlow()
    val visitInbox = visitPacks.observeInbox().catch { emit(emptyList()) }
        .stateIn(scope, SharingStarted.WhileSubscribed(), emptyList())

    // Speed samples keep arriving in P. Keep the GPS subscription alive throughout the same stop.
    private val parkedVisit = combine(
        journeys.state,
        vehicleRepository.state,
        recorder.enabled,
    ) { journey, vehicle, enabled ->
        journey.session?.takeIf { it.paused && !it.recoveryRequired && enabled && vehicle.allowsTabNavigation }
    }.stateIn(scope, SharingStarted.Eagerly, null)
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
    }.stateIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), null)
    private val parkedGeneration = parkedVisit.map { it?.id }.distinctUntilChanged().map { id ->
        if (id == null) null else dataGeneration()?.let { id to it }
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
        }.stateIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), null)
    }
    private val mutableVisitResult = MutableStateFlow<VisitPackClaimResult?>(null)
    val visitResult = mutableVisitResult.asStateFlow()
    private var claimJob: Job? = null

    val drivingPosition = combine(drivingJourney, location.freshFix) { journeyId, fix ->
        if (journeyId != null) currentPosition(fix) else null
    }.stateIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), null)

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
        }.stateIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), null)
    }

    init {
        scope.launch {
            recorder.observations.collect { observation ->
                if (drivingJourney.value == observation.startedAtMillis) {
                    messageJob?.cancel()
                    mutableSafetyMessage.value = when (observation.kind) {
                        DrivingObservationKind.Acceleration -> DrivingSafetyMessage.Acceleration
                        DrivingObservationKind.Braking -> DrivingSafetyMessage.Braking
                    }
                    messageJob = scope.launch {
                        delay(5_000)
                        mutableSafetyMessage.value = null
                    }
                }
            }
        }
        scope.launch {
            this@PlaceSessionRuntime.places.collect { list ->
                if (navigationDelegate.isInitialized()) navigation.updateCatalog(list)
            }
        }

        scope.launch {
            journeys.state.map { it.vehicle.motion.motion }.distinctUntilChanged().collect { motion ->
                if (motion == VehicleMotion.Moving) {
                    visitArrival.value = null
                    arrivedJourneyId = null
                }
            }
        }
        scope.launch {
            parkedVisit.collect {
                claimJob?.cancel()
                mutableVisitResult.value = null
            }
        }
    }

    fun claimVisit(visit: ClaimableVisit) {
        if (claimJob?.isActive == true) return
        claimJob = scope.launch {
            mutableVisitResult.value = try {
                val route = VisitRouteEvidence(navigation.state.value.routePlaceIds, visitArrival.value)
                val candidate = eligibleVisit(visitFix.value, route, listOf(visit.place))
                if (candidate?.first == visit.journeyId) {
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

    fun arrivals(): Flow<Unit> = flow {
        navigation.recheck()
        emitAll(arrivalEvents)
    }

    fun refreshParkedVisit() {
        navigation.recheck()
    }

    fun canInteract(): Boolean = vehicleRepository.state.value.allowsTabNavigation

    fun retryCatalog() {
        if (canInteract()) catalogRefresh.value += 1
    }

    fun refreshLocation(owner: String) {
        if (!canInteract()) return
        if (catalogError.value) retryCatalog()
        parkedMapOwners += owner
        location.refresh()
    }

    fun stopLocation(owner: String) {
        parkedMapOwners -= owner
        closeUnusedLocation()
    }

    fun request(place: TravelPlace) = navigation.request(place) { canInteract() && place in places.value }

    fun recheck() {
        if (canInteract()) navigation.recheck()
    }

    fun startDrivingOverview(journeyId: Long, owner: String) {
        if (drivingJourney.value != journeyId) {
            messageJob?.cancel()
            mutableSafetyMessage.value = null
        }
        drivingOwners.entries.removeAll { it.value != journeyId }
        drivingOwners[owner] = journeyId
        drivingJourney.value = journeyId
        location.refresh()
        navigation.recheck()
    }

    fun stopDrivingOverview(journeyId: Long, owner: String) {
        if (drivingOwners[owner] != journeyId) return
        drivingOwners.remove(owner)
        if (drivingOwners.values.any { it == drivingJourney.value }) return
        drivingJourney.value = null
        messageJob?.cancel()
        mutableSafetyMessage.value = null
        closeUnusedLocation()
    }

    fun requestDrivingPlace(place: TravelPlace, hasDestination: Boolean) {
        navigation.request(place) {
            val route = navigation.state.value
            val position = currentPosition(location.freshFix.value)
            canRecommendWhileDriving() && route.hasDestination == hasDestination &&
                route.hasWaypointCapacity && place.id !in route.routePlaceIds && drivingPlace.value == place &&
                position != null && place.nearbyDistanceMeters(position) != null
        }
    }

    fun recheckWhileDriving() {
        if (canRecommendWhileDriving()) navigation.recheck()
    }

    override fun close() {
        scope.launch(Dispatchers.Main.immediate) {
            location.close()
            if (navigationDelegate.isInitialized()) navigation.close()
            scope.cancel()
        }
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
        arrivedJourneyId = session.id
        arrivalEvents.tryEmit(Unit)
        val arrivedName = name.trim()
        val place = places.value.singleOrNull { it.name.trim() == arrivedName } ?: return
        visitArrival.value = VisitArrival(session.id, place.id, SystemClock.elapsedRealtimeNanos())
    }

    private fun closeUnusedLocation() {
        if (parkedMapOwners.isEmpty() && drivingJourney.value == null) location.close()
    }

    private fun canRecommendWhileDriving(): Boolean {
        val journey = journeys.state.value
        val session = journey.session ?: return false
        val stoppedAtArrival = journey.vehicle.motion.motion == VehicleMotion.Stopped &&
            arrivedJourneyId == session.id
        return drivingJourney.value != null && !session.finished && !stoppedAtArrival &&
            vehicleRepository.state.value.allowsDrivingPlaceRecommendation
    }

    private suspend fun dataGeneration(): String? = try {
        progress.getDataGeneration()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun currentPosition(fix: FreshPlaceFix?): PlaceMapPosition? =
        fix?.takeIf { it.isFresh() && location.hasPermission() }?.position
}
