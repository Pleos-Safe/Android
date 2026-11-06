package io.issac.pleosandroid.feature.badges.model

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.issac.pleosandroid.core.designsystem.R as DesignR
import io.issac.pleosandroid.core.designsystem.component.MilestoneCardAppearance
import io.issac.pleosandroid.core.designsystem.component.MilestoneCardFace
import io.issac.pleosandroid.feature.badges.R

internal val CollectionCardDesign.Companion.Haeundae: CollectionCardDesign
    get() = CollectionCardDesign(
        "place-haeundae-001",
        "해운대의 파도",
        "부산의 여름을 간직하다",
        "부산 · 해운대",
        "MILESTONE / 001",
        CollectionPlace.Haeundae,
        DesignR.drawable.milestone_art_haeundae,
        DesignR.drawable.milestone_gloss_haeundae,
        DesignR.drawable.milestone_engraving_haeundae,
        postcard = false,
    )

internal val CollectionCardDesign.Companion.Gwangalli: CollectionCardDesign
    get() = CollectionCardDesign(
        "place-gwangalli-001",
        "광안대교의 밤",
        "다녀온 풍경을 간직하다",
        "부산 · 수영구",
        "MILESTONE / GWANGALLI",
        CollectionPlace.Gwangalli,
        R.drawable.collection_art_gwangalli,
        R.drawable.collection_gloss_gwangalli,
        R.drawable.collection_engraving_gwangalli,
    )

internal val CollectionCardDesign.Companion.Cheongsapo: CollectionCardDesign
    get() = CollectionCardDesign(
        "place-cheongsapo-001",
        "청사포의 빛",
        "다녀온 풍경을 간직하다",
        "부산 · 해운대구",
        "MILESTONE / CHEONGSAPO",
        CollectionPlace.Cheongsapo,
        R.drawable.collection_art_cheongsapo,
        R.drawable.collection_gloss_cheongsapo,
        R.drawable.collection_engraving_cheongsapo,
    )

internal val CollectionCardDesign.Companion.Huinnyeoul: CollectionCardDesign
    get() = CollectionCardDesign(
        "place-huinnyeoul-001",
        "흰여울의 골목",
        "다녀온 풍경을 간직하다",
        "부산 · 영도구",
        "MILESTONE / HUINNYEOUL",
        CollectionPlace.Huinnyeoul,
        R.drawable.collection_art_huinnyeoul,
        R.drawable.collection_gloss_huinnyeoul,
        R.drawable.collection_engraving_huinnyeoul,
    )

internal val CollectionCardDesign.Companion.Igidae: CollectionCardDesign
    get() = CollectionCardDesign(
        "place-igidae-001",
        "이기대의 바람",
        "다녀온 풍경을 간직하다",
        "부산 · 남구",
        "MILESTONE / IGIDAE",
        CollectionPlace.Igidae,
        R.drawable.collection_art_igidae,
        R.drawable.collection_gloss_igidae,
        R.drawable.collection_engraving_igidae,
    )

internal val CollectionCardDesign.Companion.SafeHome: CollectionCardDesign
    get() = CollectionCardDesign(
        "safety-smooth-001",
        "안전한 귀가",
        "여정을 차분히 마친 기록",
        "운전습관 · 귀가",
        "SAFETY / SMOOTH / 001",
        null,
        DesignR.drawable.milestone_art_safety,
        DesignR.drawable.milestone_gloss_safety,
        DesignR.drawable.milestone_engraving_safety,
        postcard = false,
    )

internal val CollectionCardDesign.Companion.GentleStart: CollectionCardDesign
    get() = CollectionCardDesign(
        "safety-gentle-start-001",
        "차분한 출발",
        "일상에 남긴 작은 여유",
        "운전습관 · 출발",
        "MILESTONE / GENTLE START",
        null,
        R.drawable.collection_art_gentle_start,
        R.drawable.collection_gloss_gentle_start,
        R.drawable.collection_engraving_gentle_start,
    )

internal val CollectionCardDesign.Companion.Songdo: CollectionCardDesign
    get() = CollectionCardDesign(
        "place-songdo-001",
        "송도의 푸른 길",
        "다녀온 풍경을 간직하다",
        "부산 · 서구",
        "MILESTONE / SONGDO",
        CollectionPlace.Songdo,
        R.drawable.collection_art_songdo,
        R.drawable.collection_gloss_songdo,
        R.drawable.collection_engraving_songdo,
    )
