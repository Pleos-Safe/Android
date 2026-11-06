package io.issac.pleosandroid.feature.badges.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.designsystem.component.MilestoneCard
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.feature.badges.R
import io.issac.pleosandroid.feature.badges.component.Caption
import io.issac.pleosandroid.feature.badges.component.SectionTitle
import io.issac.pleosandroid.feature.badges.component.VaultStage

/** Compact parked-home slot. Uses the same public face and selection as the collection. */
@Composable
fun CollectionHomeCard(modifier: Modifier = Modifier) {
    val viewModel: CollectionHomeViewModel = hiltViewModel()
    val observed by viewModel.card.collectAsStateWithLifecycle()
    val card = observed
    VaultStage(modifier.fillMaxWidth(), highlighted = card != null) {
        Row(
            Modifier.fillMaxWidth().padding(PleosLayout.panelPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PleosLayout.itemGap),
        ) {
            val artModifier = Modifier.width(88.dp * PleosTextStyles.scale)
            if (card != null) {
                MilestoneCard(card.face(), artModifier)
            } else {
                Image(painterResource(R.drawable.collection_empty_frame), null, artModifier.aspectRatio(248f / 355f))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Caption(if (card != null) "나의 대표 카드" else "첫 장을 기다리는 자리", accent = card != null)
                if (card != null) SectionTitle(card.publicTitle)
            }
        }
    }
}
