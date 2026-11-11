package io.issac.pleosandroid.feature.home.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosInfoPanel
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimResult
import io.issac.pleosandroid.core.domain.collection.model.VisitPackPolicy

@Composable
fun VisitPackRewardCard(
    placeName: String?,
    result: VisitPackClaimResult?,
    onClaim: (() -> Unit)?,
    onOpenPack: ((String) -> Unit)? = null,
) {
    if (placeName == null && result == null) return
    val message = when (result) {
        is VisitPackClaimResult.Applied, is VisitPackClaimResult.AlreadyClaimed -> "방문 카드팩 1개를 받았어요"

        VisitPackClaimResult.DailyLimitReached -> "오늘 방문팩 ${VisitPackPolicy.DAILY_CLAIM_LIMIT}개를 모두 받았어요"

        VisitPackClaimResult.RegionCooldown ->
            "같은 지역의 방문 카드팩은 마지막 수령 후 ${VisitPackPolicy.REGION_COOLDOWN_DAYS}일이 지나면 다시 받을 수 있어요"

        VisitPackClaimResult.InvalidInput -> "주차와 현재 위치를 다시 확인해 주세요"

        VisitPackClaimResult.StaleGeneration, VisitPackClaimResult.StorageFailure -> "카드팩을 저장하지 못했어요. 다시 시도해 주세요"

        null -> "방문 보상을 받을 수 있어요"
    }
    val receipt = when (result) {
        is VisitPackClaimResult.Applied -> result.receipt
        is VisitPackClaimResult.AlreadyClaimed -> result.receipt
        else -> null
    }
    val displayName = receipt?.placeTitle ?: placeName
    PleosInfoPanel(Modifier.fillMaxWidth()) {
        Text(message, style = PleosTextStyles.section, color = PleosTheme.colors.textPrimary)
        if (displayName != null) {
            val detail = if (result == null && onClaim != null) {
                "$displayName · 하루 최대 ${VisitPackPolicy.DAILY_CLAIM_LIMIT}팩"
            } else {
                displayName
            }
            Text(detail, style = PleosTextStyles.body, color = PleosTheme.colors.textSecondary)
        }
        if (receipt != null && onOpenPack != null) {
            PleosButton("받은 팩 열어보기", { onOpenPack(receipt.albumPackId) }, Modifier.fillMaxWidth(), showArrow = true)
        } else if (onClaim != null && result !is VisitPackClaimResult.DailyLimitReached &&
            result !is VisitPackClaimResult.RegionCooldown
        ) {
            PleosButton("카드팩 받기", onClaim, modifier = Modifier.fillMaxWidth(), showArrow = true)
        }
    }
}
