package io.issac.pleosandroid.feature.badges

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.cards.AlbumSnapshot
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.cards.ReceivedPack
import io.issac.pleosandroid.feature.badges.album.PackOpening
import io.issac.pleosandroid.feature.badges.album.PackShelf
import java.time.Instant

private val regions = PackType.entries.map { ReceivedPack(it.name, it, "방문 보상", Instant.EPOCH) }

@Preview(name = "컬렉션 팩 보관함", widthDp = 1216, heightDp = 680)
@Composable
private fun PackShelfPreview() {
    PleosTheme { PackShelf(AlbumSnapshot(0L, null, regions, emptyList(), emptyList()), {}, {}, {}, {}) }
}

@Preview(name = "밀봉 팩 · 전체", widthDp = 1216, heightDp = 680)
@Preview(name = "밀봉 팩 · 분할", widthDp = 520, heightDp = 616)
@Composable
private fun SealedPackPreview() {
    PleosTheme { PackOpening(regions.first(), null, false, {}, {}, {}) }
}
