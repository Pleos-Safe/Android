package io.issac.pleosandroid.feature.badges.model

import java.time.LocalDate

/** The debug shelf includes an unopened card and one pending evolution for every level. */
internal object CollectionSeed {
    val representativeId = CollectionCardDesign.Haeundae.id

    val cards = listOf(
        CollectionCard(
            CollectionCardDesign.Haeundae,
            earnedLevel = 3,
            revealedLevel = 2,
            memories = listOf(
                memory("visit-haeundae-1", "10-18", "처음 간직한 날", "해운대에서 시작된 한 장"),
                memory("visit-haeundae-2", "10-25", "다시 만난 해운대", "해운대 해변 · 방문 기록"),
                memory("visit-haeundae-3", "11-02", "오늘의 해운대", "기장 숙소로 가던 길"),
            ),
        ),
        placeCard(CollectionCardDesign.Gwangalli, "10-19", "10-31"),
        placeCard(CollectionCardDesign.Cheongsapo, "10-20", "10-28", revealedLevel = 1),
        placeCard(CollectionCardDesign.Huinnyeoul, "10-21", "10-24", "10-27", "11-01", revealedLevel = 3),
        placeCard(CollectionCardDesign.Igidae, "10-22"),
        CollectionCard(
            CollectionCardDesign.SafeHome,
            earnedLevel = 5,
            revealedLevel = 4,
            memories = listOf(
                memory("journey-home-1", "10-24", "처음 간직한 날", "일상의 여정에서 만난 한 장"),
                memory("journey-home-3", "10-25", "주말의 귀갓길", "일정한 속도로 돌아온 저녁"),
                memory("journey-home-4", "10-27", "비 오는 귀갓길", "차간 거리를 지키며 돌아온 저녁"),
                memory("journey-home-5", "10-28", "익숙한 귀갓길", "부드럽게 마친 저녁 여정"),
                memory("journey-home-2", "10-30", "다섯 번째 귀가", "저녁에 차분히 마친 여정"),
            ),
        ),
        CollectionCard(
            CollectionCardDesign.GentleStart,
            1,
            1,
            listOf(memory("journey-start-1", "10-26", "처음 간직한 날", "여유롭게 출발한 아침 여정")),
        ),
        placeCard(CollectionCardDesign.Songdo, "11-02", revealedLevel = null),
    )

    private fun placeCard(
        design: CollectionCardDesign,
        first: String,
        vararg revisits: String,
        revealedLevel: Int? = revisits.size + 1,
    ): CollectionCard {
        val place = requireNotNull(design.place).displayName
        val records = buildList {
            add(memory("${design.id}-1", first, "처음 간직한 날", "${place}에서 시작된 한 장"))
            revisits.forEachIndexed { index, day ->
                add(memory("${design.id}-${index + 2}", day, "다시 만난 $place", "${place}에 다시 남긴 방문 기록"))
            }
        }
        return CollectionCard(design, records.size, revealedLevel, records)
    }

    private fun memory(id: String, day: String, title: String, detail: String) =
        CardMemory(id, LocalDate.parse("2026-$day"), title, detail)
}
