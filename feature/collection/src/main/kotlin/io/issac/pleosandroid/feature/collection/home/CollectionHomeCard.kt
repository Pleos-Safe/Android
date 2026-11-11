package io.issac.pleosandroid.feature.collection.home

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.cardui.component.Caption
import io.issac.pleosandroid.core.cardui.component.SectionTitle
import io.issac.pleosandroid.core.cardui.component.VaultStage
import io.issac.pleosandroid.core.cardui.print.CardPrintView
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.PackShelfRoute
import io.issac.pleosandroid.feature.collection.R
import io.issac.pleosandroid.feature.collection.pack.PackArtwork
import io.issac.pleosandroid.feature.collection.pack.title

@Composable
fun CollectionHomeCard(modifier: Modifier = Modifier) {
    val viewModel: CollectionHomeViewModel = hiltViewModel()
    val album by viewModel.album.collectAsStateWithLifecycle()
    val router = LocalNavigationRouter.current
    val card = album.featured
    val pack = album.nextPack
    VaultStage(modifier.fillMaxWidth(), highlighted = card != null || pack != null) {
        Row(
            Modifier.fillMaxWidth().padding(PleosLayout.panelPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PleosLayout.itemGap),
        ) {
            val art = Modifier.width(88.dp * PleosTextStyles.scale).aspectRatio(2f / 3f)
            when {
                card != null -> CardPrintView(card, art)
                pack != null -> PackArtwork(pack, art)
                else -> Image(painterResource(R.drawable.collection_empty_frame), null, art)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    card != null -> {
                        Caption("나의 대표 카드", accent = true)
                        SectionTitle("${card.print.title}  +${card.level}")
                    }

                    pack != null -> {
                        Caption("도착한 카드팩", accent = true)
                        SectionTitle(pack.title)
                    }

                    else -> Caption(album.message ?: "컬렉션에서 대표 카드를 골라 주세요.")
                }
                if (pack != null) {
                    PleosTextLink(
                        "팩 보관함 열기 · ${album.inboxSize}개",
                        { router.replaceAll(BadgesRoute, PackShelfRoute) },
                        Modifier.testTag("home-pack-shelf"),
                    )
                }
            }
        }
    }
}
