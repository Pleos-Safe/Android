package io.issac.pleosandroid.core.data.collection.local

import io.issac.pleosandroid.core.data.collection.mapper.toEntity
import io.issac.pleosandroid.core.database.dao.CollectionDao
import io.issac.pleosandroid.core.database.entity.CatalogStateEntity
import io.issac.pleosandroid.core.domain.collection.model.CatalogPack
import io.issac.pleosandroid.core.domain.collection.model.CatalogResult
import java.security.MessageDigest

internal fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

/** Validation precedes writes; callers enclose this in their transaction. */
internal suspend fun applyCatalog(
    dao: CollectionDao,
    pack: CatalogPack,
    hasAsset: (String) -> Boolean,
): CatalogResult {
    val hash = fingerprint(pack.toString())
    val current = dao.catalogState()
    if (current?.version == pack.version && current.hash == hash) return CatalogResult.Unchanged
    if (pack.version <= (current?.version ?: 0)) return CatalogResult.Rejected("Content version must increase")
    if (pack.places.distinctBy { it.id }.size !=
        pack.places.size
    ) {
        return CatalogResult.Rejected("Duplicate place identifiers")
    }
    if (pack.places.any {
            it.id.isBlank() || it.name.isBlank() || !it.latitude.isFinite() || !it.longitude.isFinite() ||
                it.latitude !in -90.0..90.0 || it.longitude !in -180.0..180.0 ||
                !it.radiusMeters.isFinite() || it.radiusMeters <= 0 || it.minimumDwellSeconds < 0 ||
                !hasAsset(it.imageKey)
        }
    ) {
        return CatalogResult.Rejected("Invalid place or missing image")
    }
    dao.putPlaces(pack.places.map { it.toEntity() })
    dao.putCatalogState(CatalogStateEntity(version = pack.version, hash = hash))
    dao.touch()
    return CatalogResult.Applied
}
