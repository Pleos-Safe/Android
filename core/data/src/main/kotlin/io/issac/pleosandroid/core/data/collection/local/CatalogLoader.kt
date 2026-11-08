package io.issac.pleosandroid.core.data.collection.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.collection.model.CatalogPack
import io.issac.pleosandroid.core.domain.collection.model.Place
import javax.inject.Inject
import org.json.JSONObject

internal interface CatalogSource {
    fun load(): CatalogPack
    fun hasAsset(key: String): Boolean
}

internal class CatalogLoader @Inject constructor(@param:ApplicationContext private val context: Context) :
    CatalogSource {
    override fun load(): CatalogPack {
        val root = context.assets.open("collection/catalog.json").bufferedReader().use { JSONObject(it.readText()) }
        val places = root.getJSONArray("places").let { array ->
            (0 until array.length()).map { index ->
                val p = array.getJSONObject(index)
                Place(
                    p.getString("id"), p.getString("name"), p.getString("district"), p.getString("category"),
                    p.getDouble("latitude"), p.getDouble("longitude"), p.getDouble("radiusMeters"),
                    p.getLong("minimumDwellSeconds"), p.getString("imageKey"),
                    regionId = p.optString("regionId").ifEmpty { null },
                    rewardPack = p.optString("rewardPack").takeIf { it.isNotBlank() }?.let(PackType::valueOf),
                    cardPrintId = p.optString("cardPrintId").ifEmpty { null },
                )
            }
        }
        return CatalogPack(root.getInt("version"), places)
    }

    override fun hasAsset(key: String): Boolean =
        context.resources.getIdentifier(key, "drawable", context.packageName) != 0
}

internal fun JSONObject.intOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
