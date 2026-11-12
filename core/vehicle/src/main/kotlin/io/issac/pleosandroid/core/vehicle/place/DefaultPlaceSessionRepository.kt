package io.issac.pleosandroid.core.vehicle.place

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimResult
import io.issac.pleosandroid.core.domain.collection.repository.CollectionDemoRepository
import io.issac.pleosandroid.core.domain.collection.repository.PlaceRepository
import io.issac.pleosandroid.core.domain.collection.repository.ProgressRepository
import io.issac.pleosandroid.core.domain.collection.repository.VisitLocationSource
import io.issac.pleosandroid.core.domain.collection.repository.VisitPackRepository
import io.issac.pleosandroid.core.domain.driving.repository.DrivingRecorder
import io.issac.pleosandroid.core.domain.driving.repository.JourneyRepository
import io.issac.pleosandroid.core.domain.place.ClaimableVisit
import io.issac.pleosandroid.core.domain.place.DrivingSafetyMessage
import io.issac.pleosandroid.core.domain.place.JourneyArrivalSource
import io.issac.pleosandroid.core.domain.place.PlaceLocationState
import io.issac.pleosandroid.core.domain.place.PlaceMapPosition
import io.issac.pleosandroid.core.domain.place.PlaceNavigationState
import io.issac.pleosandroid.core.domain.place.PlaceSessionRepository
import io.issac.pleosandroid.core.domain.place.TravelPlace
import io.issac.pleosandroid.core.domain.repository.RegionVisitPackSource
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import io.issac.pleosandroid.core.vehicle.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Native subscriptions exist only while a home or exploration ViewModel owns a lease. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class DefaultPlaceSessionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val vehicle: VehicleRepository,
    private val regions: RegionVisitPackSource,
    private val placesRepository: PlaceRepository,
    private val recorder: DrivingRecorder,
    private val journeys: JourneyRepository,
    private val visitLocation: VisitLocationSource,
    private val packs: VisitPackRepository,
    private val progress: ProgressRepository,
    private val demo: CollectionDemoRepository,
) : PlaceSessionRepository,
    JourneyArrivalSource {
    private val owners = PlaceSessionOwners(::newRuntime)
    private val runtime = owners.runtime

    override val places = observe(emptyList<TravelPlace>()) { it.places }
    override val catalogError = observe(false) { it.catalogError }
    override val locationState = observe<PlaceLocationState>(PlaceLocationState.Loading) { it.locationState }
    override val navigationState = observe(PlaceNavigationState()) { it.navigationState }
    override val drivingSafetyMessage = observe<DrivingSafetyMessage?>(null) { it.drivingSafetyMessage }
    override val claimableVisit = observe<ClaimableVisit?>(null) { it.claimableVisit }
    override val visitResult = observe<VisitPackClaimResult?>(null) { it.visitResult }
    override val drivingPosition = observe<PlaceMapPosition?>(null) { it.drivingPosition }
    override val drivingPlace = observe<TravelPlace?>(null) { it.drivingPlace }
    override val regionVisitPackCounts = regions.regionVisitPackCounts.catch { emit(emptyMap()) }

    override fun acquire(): AutoCloseable = owners.acquire()

    override fun canInteract(): Boolean = vehicle.state.value.allowsTabNavigation

    override fun retryCatalog() = dispatch { it.retryCatalog() }

    override fun refreshLocation(owner: String) = dispatch { it.refreshLocation(owner) }

    override fun stopLocation(owner: String) = dispatch { it.stopLocation(owner) }

    override fun request(place: TravelPlace) = dispatch { it.request(place) }

    override fun recheck() = dispatch { it.recheck() }

    override fun refreshParkedVisit() = dispatch { it.refreshParkedVisit() }

    override fun claimVisit(visit: ClaimableVisit) = dispatch { it.claimVisit(visit) }

    override fun startDrivingOverview(journeyId: Long, owner: String) = dispatch {
        it.startDrivingOverview(journeyId, owner)
    }

    override fun stopDrivingOverview(journeyId: Long, owner: String) = dispatch {
        it.stopDrivingOverview(journeyId, owner)
    }

    override fun requestDrivingPlace(place: TravelPlace, hasDestination: Boolean) = dispatch {
        it.requestDrivingPlace(place, hasDestination)
    }

    override fun recheckWhileDriving() = dispatch { it.recheckWhileDriving() }

    override fun arrivals(): Flow<Unit> = flow {
        val lease = acquire()
        try {
            emitAll(checkNotNull(runtime.value).arrivals())
        } finally {
            lease.close()
        }
    }

    private fun newRuntime() = PlaceSessionRuntime(
        context,
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        vehicle,
        placesRepository,
        recorder,
        journeys,
        visitLocation,
        packs,
        progress,
        demo,
    )

    private fun dispatch(action: (PlaceSessionRuntime) -> Unit) {
        val current = runtime.value ?: return
        applicationScope.launch(Dispatchers.Main.immediate) {
            if (runtime.value === current) action(current)
        }
    }

    private fun <T> observe(initial: T, source: (PlaceSessionRuntime) -> Flow<T>) = runtime
        .flatMapLatest { value -> if (value == null) flowOf(initial) else source(value) }
        .stateIn(applicationScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), initial)
}
