package io.issac.pleosandroid.feature.home.driving

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosRestrictionGlyph
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.place.PlaceNavigationOperation
import io.issac.pleosandroid.feature.home.R

internal enum class JourneyRestriction(@param:StringRes val title: Int, @param:StringRes val body: Int) {
    Moving(R.string.journey_restriction_moving_title, R.string.journey_restriction_moving_body),
    Stopped(R.string.journey_restriction_stopped_title, R.string.journey_restriction_stopped_body),
    ReadyToDrive(R.string.journey_restriction_ready_title, R.string.journey_restriction_ready_body),
    Arrived(R.string.journey_restriction_arrived_title, R.string.journey_restriction_arrived_body),
    Unconfirmed(R.string.journey_restriction_unconfirmed_title, R.string.journey_restriction_unconfirmed_body),
}

@Composable
internal fun JourneyRestrictionScreen(restriction: JourneyRestriction, modifier: Modifier = Modifier) {
    val colors = PleosTheme.colors
    Box(
        modifier = modifier.fillMaxSize().pleosShellBackground().padding(
            start = PleosSpacing.space24,
            top = PleosSpacing.space16,
            end = PleosSpacing.space24,
            bottom = PleosSpacing.space56,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(
                max = 480.dp * PleosTextStyles.scale,
            ).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PleosRestrictionGlyph(isMoving = restriction == JourneyRestriction.Moving)
            Spacer(Modifier.height(PleosSpacing.space32))
            Text(
                text = stringResource(restriction.title),
                style = PleosTextStyles.titleSplit,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(PleosSpacing.space16))
            Text(
                text = stringResource(restriction.body),
                style = PleosTextStyles.meta,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(PleosSpacing.space64))
            HorizontalDivider(color = colors.borderDefault)
            Spacer(Modifier.height(PleosSpacing.space16))
            Text(
                text = stringResource(R.string.journey_restriction_navigation),
                style = PleosTextStyles.meta,
                color = colors.textDisabled,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun DrivingRecommendation(
    placeName: String?,
    hasDestination: Boolean?,
    operation: PlaceNavigationOperation,
    onRequest: () -> Unit,
    onRecheck: () -> Unit,
    distanceKm: Double? = null,
) {
    val status = when (operation) {
        PlaceNavigationOperation.Idle -> null
        PlaceNavigationOperation.Checking -> R.string.driving_recommendation_checking
        PlaceNavigationOperation.Requesting -> R.string.driving_recommendation_requesting
        PlaceNavigationOperation.Applied -> R.string.driving_recommendation_applied
        PlaceNavigationOperation.Rejected -> R.string.driving_recommendation_rejected
        PlaceNavigationOperation.Unconfirmed -> R.string.driving_recommendation_unconfirmed
        PlaceNavigationOperation.Unavailable -> R.string.driving_recommendation_unavailable
    }
    val showRequest = hasDestination != null && when (operation) {
        PlaceNavigationOperation.Idle, PlaceNavigationOperation.Applied, PlaceNavigationOperation.Rejected -> true
        else -> false
    }
    val showRecheck = operation == PlaceNavigationOperation.Unconfirmed ||
        operation == PlaceNavigationOperation.Unavailable
    if (status == null && (placeName == null || !showRequest)) {
        NoDrivingRecommendation()
        return
    }

    val colors = PleosTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PleosSpacing.space8),
    ) {
        if (status != null) {
            Text(
                text = stringResource(status),
                style = PleosTextStyles.meta,
                color = colors.textSecondary,
            )
        }
        if (placeName != null && (showRequest || operation == PlaceNavigationOperation.Requesting)) {
            Text(
                text = placeName,
                style = PleosTextStyles.body,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (distanceKm != null && showRequest) {
                Text(
                    text = stringResource(R.string.driving_recommendation_distance, distanceKm),
                    style = PleosTextStyles.meta,
                    color = colors.textSecondary,
                )
            }
            if (showRequest) {
                PleosButton(
                    text = stringResource(
                        if (hasDestination ==
                            true
                        ) {
                            R.string.place_map_add_waypoint
                        } else {
                            R.string.place_map_set_destination
                        },
                    ),
                    onClick = onRequest,
                    modifier = Modifier.fillMaxWidth(),
                    showArrow = true,
                )
            }
        } else if (showRecheck) {
            PleosButton(text = stringResource(R.string.place_map_check_route), onClick = onRecheck)
        }
    }
}

@Composable
internal fun NoDrivingRecommendation() {
    Text(
        text = stringResource(R.string.driving_recommendation_empty),
        style = PleosTextStyles.meta,
        color = PleosTheme.colors.textSecondary,
    )
}
