package io.issac.pleosandroid.feature.badges.notice

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.issac.pleosandroid.core.designsystem.theme.PleosShape
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

/** Mount once in the app shell; the durable queue is not collected independently by each feature. */
@Composable
fun CollectionRewardNoticeHost(modifier: Modifier = Modifier) {
    val viewModel: RewardNoticeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state.notice?.id, state.allowed, state.failedAttempt, lifecycle) {
        val notice = state.notice ?: return@LaunchedEffect
        if (!state.allowed) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val message = if (notice.fromLevel ==
                0
            ) {
                "${notice.label} · 새 카드를 얻었어요"
            } else {
                "${notice.label} · ${notice.toLevel}레벨 달성"
            }
            host.showSnackbar(
                if (state.failedAttempt == 0) message else "$message · 확인 상태를 저장하지 못했어요",
                actionLabel = if (state.failedAttempt == 0) "확인" else "다시 확인",
                withDismissAction = true,
                duration = SnackbarDuration.Indefinite,
            )
            viewModel.handleIntent(AcknowledgeNotice(notice.id))
        }
    }
    val colors = PleosTheme.colors
    SnackbarHost(host, modifier) { notice ->
        Snackbar(
            snackbarData = notice,
            modifier = Modifier.widthIn(max = 640.dp * PleosTextStyles.scale)
                .border(1.dp, colors.borderControl, PleosShape.button),
            shape = PleosShape.button,
            containerColor = colors.surface,
            contentColor = colors.textPrimary,
            actionColor = colors.actionPrimary,
            dismissActionContentColor = colors.textSecondary,
        )
    }
}
