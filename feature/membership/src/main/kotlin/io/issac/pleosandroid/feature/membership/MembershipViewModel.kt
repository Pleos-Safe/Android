package io.issac.pleosandroid.feature.membership

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.driving.repository.DrivingRecorder
import io.issac.pleosandroid.core.domain.membership.Membership
import io.issac.pleosandroid.core.domain.membership.MembershipAction
import io.issac.pleosandroid.core.domain.membership.MembershipChange
import io.issac.pleosandroid.core.domain.membership.MembershipRepository
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface MembershipLoad {
    data object Loading : MembershipLoad
    data object Failed : MembershipLoad
    data class Ready(val membership: Membership) : MembershipLoad
}

internal data class MembershipUiState(
    val load: MembershipLoad = MembershipLoad.Loading,
    val parked: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val recordingEnabled: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class MembershipViewModel @Inject constructor(
    private val repository: MembershipRepository,
    private val vehicle: VehicleRepository,
    recorder: DrivingRecorder,
) : ViewModel() {
    private val attempt = MutableStateFlow(0)
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val membership = attempt.flatMapLatest {
        repository.observeMembership()
            .map<Membership, MembershipLoad> { MembershipLoad.Ready(it) }
            .onStart { emit(MembershipLoad.Loading) }
            .catch { emit(MembershipLoad.Failed) }
    }
    val state = combine(
        membership,
        vehicle.state.map {
            it.allowsTabNavigation
        },
        busy,
        error,
        recorder.enabled,
        ::MembershipUiState,
    )
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MembershipUiState())

    fun retry() {
        error.value = null
        attempt.value++
    }

    fun confirm(action: MembershipAction, shown: Membership) {
        if (!vehicle.state.value.allowsTabNavigation || !busy.compareAndSet(false, true)) return
        error.value = null
        viewModelScope.launch {
            try {
                if (!vehicle.state.value.allowsTabNavigation) return@launch
                error.value = when (repository.change(action, shown)) {
                    MembershipChange.Applied -> null
                    MembershipChange.Changed -> "구독 상태가 바뀌었어요. 현재 상태를 확인해 주세요."
                    MembershipChange.Unavailable -> "변경을 저장하지 못했어요. 다시 시도해 주세요."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error.value = "구독 상태를 확인하지 못했어요. 다시 시도해 주세요."
            } finally {
                busy.value = false
            }
        }
    }
}
