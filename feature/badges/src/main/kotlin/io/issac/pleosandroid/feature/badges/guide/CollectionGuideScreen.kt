package io.issac.pleosandroid.feature.badges.guide

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.MilestoneCardFace
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosPlaceMap
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.feature.badges.R
import io.issac.pleosandroid.feature.badges.component.ArtStage
import io.issac.pleosandroid.feature.badges.component.Caption
import io.issac.pleosandroid.feature.badges.component.CollectionBack
import io.issac.pleosandroid.feature.badges.component.CollectionColumns
import io.issac.pleosandroid.feature.badges.component.CollectionPage
import io.issac.pleosandroid.feature.badges.component.CollectionTitle
import io.issac.pleosandroid.feature.badges.component.CollectionWindow
import io.issac.pleosandroid.feature.badges.component.VaultStage
import io.issac.pleosandroid.feature.badges.model.CardKind
import io.issac.pleosandroid.feature.badges.model.CollectionPlace

@Composable
internal fun EmptyCollection(window: CollectionWindow, onGuide: (CardKind) -> Unit) {
    val full = window.full
    CollectionColumns(full, first = {
        VaultStage(Modifier.fillMaxWidth(), highlighted = false) {
            Column(
                Modifier.fillMaxWidth().padding(PleosLayout.panelPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap),
            ) {
                Image(
                    painterResource(R.drawable.collection_empty_frame),
                    null,
                    Modifier.widthIn(max = window.printWidth(240.dp, 168.dp)).fillMaxWidth().aspectRatio(248f / 355f),
                )
                Caption("첫 장을 기다리는 자리")
            }
        }
    }, second = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
            CollectionTitle("당신의 여정이 한 장씩 쌓일 곳", full)
            Caption("다녀온 곳의 기억과 일상의 여정을 나만의 카드로 간직해 보세요.")
            PleosButton("장소 둘러보기", { onGuide(CardKind.Place) }, Modifier.fillMaxWidth(), showArrow = true)
            PleosTextLink("운전습관 기록 보기", { onGuide(CardKind.Safety) })
        }
    })
}

@Composable
internal fun CollectionGuideScreen(
    kind: CardKind,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safety = kind == CardKind.Safety
    CollectionPage(modifier) { window ->
        val full = window.full
        CollectionBack(stringResource(R.string.collection_title), onBack)
        CollectionColumns(full, first = {
            if (safety) {
                ArtStage(MilestoneCardFace.CommonBack, window.printWidth(252.dp, 166.dp), "")
            } else {
                VaultStage(Modifier.fillMaxWidth()) {
                    Image(
                        painterResource(R.drawable.collection_haeundae_beach),
                        "해운대 해변",
                        Modifier.fillMaxWidth().aspectRatio(if (full) 1.25f else 1.85f),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }, second = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
                Caption("아직 만나지 않은 한 장", accent = true)
                CollectionTitle(if (safety) "일상의 여정에도 나만의 카드가" else "해운대에서 만날 한 장", full)
                Caption(if (safety) "운전습관 기록에서 조건에 맞는 여정을 찾아 담아요." else "카드에 담길 장면은 아직 비밀이에요. 다녀온 뒤 방문 기록을 남겨보세요.")
                Caption(if (safety) "분석할 기록이 충분한 여정부터 확인해요." else "부산 · 해운대 해변")
                PleosButton(
                    if (safety) "운전습관 기록 보기" else "장소 살펴보기",
                    onContinue,
                    Modifier.fillMaxWidth(),
                    showArrow = true,
                )
            }
        })
    }
}

/** Figma's read-only Busan map with the selected place. The marker uses the same normalized art coordinates. */
@Composable
internal fun CollectedPlaceScreen(place: CollectionPlace, onBack: () -> Unit, modifier: Modifier = Modifier) {
    CollectionPage(modifier) { window ->
        val full = window.full
        CollectionBack("돌아가기", onBack)
        CollectionTitle(place.displayName, full)
        Caption("${place.district} · ${place.typeLabel}")
        Caption("${place.latitude}, ${place.longitude}")
        PleosPlaceMap(
            Modifier.widthIn(max = 900.dp * PleosTextStyles.scale).fillMaxWidth().aspectRatio(760f / 540f),
            caption = "부산 · 지도",
        ) {
            Image(
                painterResource(R.drawable.collection_busan_map),
                null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            if (place.mapX != null && place.mapY != null) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    Image(
                        painterResource(R.drawable.collection_map_pin),
                        place.displayName,
                        Modifier.offset(
                            x = maxWidth * (place.mapX ?: .5f) - 24.dp * PleosTextStyles.scale,
                            y =
                                maxHeight * (place.mapY ?: .5f) - 24.dp * PleosTextStyles.scale,
                        ).size(48.dp * PleosTextStyles.scale),
                    )
                }
            }
        }
    }
}
