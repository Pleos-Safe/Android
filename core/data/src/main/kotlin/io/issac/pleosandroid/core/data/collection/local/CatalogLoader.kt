package io.issac.pleosandroid.core.data.collection.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.issac.pleosandroid.core.domain.collection.model.CardDefinition
import io.issac.pleosandroid.core.domain.collection.model.CardFront
import io.issac.pleosandroid.core.domain.collection.model.CardKind
import io.issac.pleosandroid.core.domain.collection.model.CardLevelRule
import io.issac.pleosandroid.core.domain.collection.model.CardTemplate
import io.issac.pleosandroid.core.domain.collection.model.CatalogPack
import io.issac.pleosandroid.core.domain.collection.model.Place
import java.time.Instant
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
                )
            }
        }
        val cards = root.getJSONArray("cards").let { array ->
            (0 until array.length()).map { index ->
                val c = array.getJSONObject(index)
                CardDefinition(
                    c.getString("id"),
                    CardKind.valueOf(c.getString("kind")),
                    c.optString("placeId").ifEmpty { null },
                    CardFront(
                        c.getString("title"),
                        c.getString("subtitle"),
                        c.getString("context"),
                        c.getString("series"),
                        c.getString("artworkKey"),
                        c.getString("glossKey"),
                        c.getString("engravingKey"),
                        CardTemplate.valueOf(c.getString("template")),
                    ),
                    ruleVersion = 1,
                )
            }
        }
        val rules = root.getJSONArray("rules").let { array ->
            (0 until array.length()).map { index ->
                val r = array.getJSONObject(index)
                CardLevelRule(
                    r.getString("cardId"),
                    r.getInt("level"),
                    r.getInt("version"),
                    r.getInt("requiredCount"),
                    Instant.parse(r.getString("eligibleFrom")),
                    r.intOrNull("minimumScore"),
                    r.optLong("minimumDistanceMeters"),
                    r.intOrNull("maximumHarshStarts"),
                )
            }
        }
        return CatalogPack(root.getInt("version"), places, cards, rules)
    }

    override fun hasAsset(key: String): Boolean =
        context.resources.getIdentifier(key, "drawable", context.packageName) != 0
}

internal fun JSONObject.intOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
