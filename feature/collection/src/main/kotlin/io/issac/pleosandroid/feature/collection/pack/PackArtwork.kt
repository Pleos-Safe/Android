package io.issac.pleosandroid.feature.collection.pack

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.SparseArray
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import io.issac.pleosandroid.core.designsystem.R as DesignR
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.cards.ReceivedPack
import io.issac.pleosandroid.feature.collection.R
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

@Composable
internal fun PackArtwork(pack: ReceivedPack, modifier: Modifier = Modifier) {
    val face = rememberPackFace(pack)
    Image(remember(face) { face.asImageBitmap() }, pack.title, modifier, contentScale = ContentScale.Fit)
}

@Composable
internal fun rememberPackFace(pack: ReceivedPack): Bitmap {
    val res = LocalResources.current
    val art = when (pack.packType.takeUnless { pack.id.startsWith("legacy:") }) {
        PackType.Seoul -> R.drawable.pack_seoul
        PackType.Busan -> R.drawable.pack_busan
        PackType.Jeju -> R.drawable.pack_jeju
        PackType.Hyundai -> R.drawable.pack_hyundai
        null -> DesignR.drawable.chagok_common_back
    }
    return remember(res, art) { PackFaces.load(res, art) }
}

/** Share the nodpi print between shelf entries and the tear mesh, releasing unused textures under memory pressure. */
private object PackFaces {
    private const val VECTOR_MAX_WIDTH = 720
    private const val VECTOR_MAX_HEIGHT = 1080
    private val cache = SparseArray<WeakReference<Bitmap>>()

    @Synchronized
    fun load(res: Resources, art: Int): Bitmap {
        cache[art]?.get()?.let { return it }
        val hyundai = art == R.drawable.pack_hyundai
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inMutable = hyundai
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val face = BitmapFactory.decodeResource(res, art, options) ?: renderDrawable(res, art)
        if (hyundai) {
            val logo = res.getDrawable(R.drawable.hyundai_pack_emblem, null).mutate()
            val width = (face.width * .155f).roundToInt()
            val height = (width * 20f / 39f).roundToInt()
            val left = (face.width * .505f).roundToInt()
            val top = (face.height * .172f).roundToInt()
            logo.setBounds(left, top, left + width, top + height)
            logo.alpha = 228
            logo.draw(Canvas(face))
        }
        cache.put(art, WeakReference(face))
        return face
    }

    private fun renderDrawable(res: Resources, art: Int): Bitmap {
        val drawable = res.getDrawable(art, null).mutate()
        val intrinsicWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: VECTOR_MAX_WIDTH
        val intrinsicHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: VECTOR_MAX_HEIGHT
        // Bound pixels independently of resource density while preserving the drawable's aspect ratio.
        val scale = minOf(VECTOR_MAX_WIDTH.toFloat() / intrinsicWidth, VECTOR_MAX_HEIGHT.toFloat() / intrinsicHeight)
        val width = (intrinsicWidth * scale).roundToInt().coerceIn(1, VECTOR_MAX_WIDTH)
        val height = (intrinsicHeight * scale).roundToInt().coerceIn(1, VECTOR_MAX_HEIGHT)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(bitmap))
        }
    }
}

internal val ReceivedPack.title: String get() = if (id.startsWith("legacy:")) {
    "이전 소장 카드"
} else {
    packType?.packTitle ?: "소장 카드"
}
