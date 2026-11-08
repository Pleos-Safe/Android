package io.issac.pleosandroid.core.data.cards

import io.issac.pleosandroid.core.database.dao.AlbumDao
import io.issac.pleosandroid.core.database.entity.CopyEntity
import io.issac.pleosandroid.core.database.entity.ForgeEntity
import io.issac.pleosandroid.core.database.entity.PackEntity
import io.issac.pleosandroid.core.domain.cards.CardRules
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome

/** Curated cards belong exclusively to the demo database. */
internal suspend fun AlbumDao.prepareDemoCopies(now: Long) {
    sample(now, "demo:trace", "busan-gwangalli", 7, trace = true)
    sample(now, "demo:material", "busan-gwangalli", 0)
    sample(now, "demo:rare", "busan-haeundae", 15, variant = true)
    sample(now, "demo:epic", "busan-bukangi", 25, variant = true)
    prepareHyundaiDemoCards(now)
}

/** Adds the new pack once for profiles seeded on previous versions, including already-opened packs. */
internal suspend fun AlbumDao.ensureHyundaiDemoPack() {
    val now = System.currentTimeMillis()
    grant(PackEntity("demo:Hyundai:0", "Hyundai", "체험용 방문 보상", now))
    prepareHyundaiDemoCards(now)
}

private suspend fun AlbumDao.prepareHyundaiDemoCards(now: Long) {
    sample(now, "demo:studio", "hyundai-motorstudio-goyang", 10)
    sample(now, "demo:epit", "epit-busan-dongbaek", 5)
}

private suspend fun AlbumDao.sample(
    now: Long,
    id: String,
    printId: String,
    level: Int,
    variant: Boolean = false,
    trace: Boolean = false,
) {
    if (copy(id) != null) return
    val print = regionalPrints.first { it.id == printId }
    val packType = requireNotNull(print.packType)
    val origin = PackEntity("demo:origin:$id", packType.name, "체험용 소장 카드", now, print.place)
    grant(origin)
    val status = if (trace) CopyStatus.Trace else CopyStatus.Owned
    add(
        CopyEntity(
            id, origin.id, printId, variant, level, if (trace) level + 5 else level,
            status.name, if (trace) 1 else 0, now, now,
        ),
    )
    if (trace) {
        log(
            ForgeEntity(
                "demo:destruction", id, level + 5, level, ForgeOutcome.Destroyed.name,
                CardRules.cost(print.baseGrade, level + 5), now, true, null,
            ),
        )
    }
}
