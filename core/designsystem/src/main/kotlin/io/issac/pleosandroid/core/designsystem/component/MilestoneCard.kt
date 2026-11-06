package io.issac.pleosandroid.core.designsystem.component

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.boundingRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.issac.pleosandroid.core.designsystem.R
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

enum class MilestoneCardAppearance(internal val level: Int, internal val isSafety: Boolean) {
    PlaceLevel1(1, false),
    PlaceLevel2(2, false),
    PlaceLevel3(3, false),
    PlaceLevel4(4, false),
    PlaceLevel5(5, false),
    SafetyLevel1(1, true),
    SafetyLevel2(2, true),
    SafetyLevel3(3, true),
    SafetyLevel4(4, true),
    SafetyLevel5(5, true),
    ;

    companion object {
        fun forLevel(level: Int, safety: Boolean): MilestoneCardAppearance =
            entries.first { it.level == level && it.isSafety == safety }
    }
}

sealed interface MilestoneCardFace {
    sealed interface Printed : MilestoneCardFace {
        val level: Int
        val seriesLabel: String

        @get:DrawableRes val glossMask: Int?

        @get:DrawableRes val engravingMask: Int?
    }

    data class Front(
        val appearance: MilestoneCardAppearance,
        val artwork: Painter,
        val contextLabel: String,
        val title: String,
        val subtitle: String,
        override val seriesLabel: String,
        val categoryLabel: String? = null,
        @param:DrawableRes override val glossMask: Int? = null,
        @param:DrawableRes override val engravingMask: Int? = null,
    ) : Printed {
        override val level get() = appearance.level
    }

    /** Illustrated 360 × 520 postcard from the expanded collection. */
    data class Postcard(
        val artwork: Painter,
        val title: String,
        val subtitle: String,
        val contextLabel: String,
        val categoryLabel: String,
        override val seriesLabel: String,
        override val level: Int,
        @param:DrawableRes override val glossMask: Int? = null,
        @param:DrawableRes override val engravingMask: Int? = null,
    ) : Printed

    data object CommonBack : MilestoneCardFace
}

/** Draws a face at its design aspect ratio. Size and interaction are supplied by the caller. */
@Composable
fun MilestoneCard(face: MilestoneCardFace, modifier: Modifier = Modifier, lightAngle: (() -> Float)? = null) {
    val ambient = if (lightAngle == null && face is MilestoneCardFace.Printed && face.level >= 4) {
        rememberMilestoneCardLight(ambient = true, initialAngle = -17.5f)
    } else {
        null
    }
    MilestoneCardPrint(face, modifier) { lightAngle?.invoke() ?: ambient?.value ?: RESTING_LIGHT_ANGLE }
}

@Composable
internal fun MilestoneCardPrint(face: MilestoneCardFace, modifier: Modifier, lightAngle: () -> Float) {
    val printed = face as? MilestoneCardFace.Printed
    val material = printed?.let { BlackLabel.fromLevel(it.level) }
    val stock = when (face) {
        is MilestoneCardFace.Front -> if (face.appearance.isSafety) CardStock.Safety else CardStock.Place
        else -> CardStock.Postcard
    }
    val plate = material?.plate(stock)
    val frame = when {
        printed != null && material != null && plate != null -> {
            val maskId = when (material) {
                BlackLabel.Silver -> printed.glossMask
                BlackLabel.Onyx -> printed.engravingMask ?: printed.glossMask
                BlackLabel.Sapphire -> printed.glossMask
            }
            val spot = maskId?.let { ImageBitmap.imageResource(it).asAndroidBitmap() }
            val relief = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                plate.relief?.let { ImageBitmap.imageResource(it).asAndroidBitmap() }
            } else {
                null
            }
            Modifier.blackLabelFinish(material, stock, relief, spot, lightAngle)
        }

        printed?.level == 2 -> Modifier.blueFoilFrame(stock)

        else -> Modifier
    }
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(MILESTONE_CARD_ASPECT_RATIO)
            .clipToBounds()
            .then(frame)
            .semantics(mergeDescendants = true) {},
        contentAlignment = AbsoluteAlignment.TopLeft,
    ) {
        val scale = maxWidth.value / CARD_WIDTH
        when (face) {
            is MilestoneCardFace.Front -> FrontFace(face, scale, stock, material, plate)
            is MilestoneCardFace.Postcard -> PostcardFace(face, scale, material, plate)
            MilestoneCardFace.CommonBack -> CommonBackFace(scale)
        }
    }
}

@Composable
private fun PostcardFace(face: MilestoneCardFace.Postcard, scale: Float, material: BlackLabel?, plate: PrintPlate?) {
    val muted = material?.secondaryInk ?: Color(0xFF7A949F)
    CardPlane(plate?.paper ?: R.drawable.milestone_postcard_plain)
    CardArtwork(face.artwork, CardStock.Postcard)
    CardText(face.contextLabel, scale, 26, 25, 128, 20, 12, muted)
    CardText(face.categoryLabel, scale, 204, 25, 130, 20, 12, muted, alignment = TextAlign.Right)
    CardText(face.title, scale, 26, 406, 308, 36, 24, material?.titleInk ?: Color(0xFF173D4E), weight = FontWeight.Bold)
    CardText(face.subtitle, scale, 26, 445, 308, 22, 13, muted)
    CardText(stringResource(R.string.milestone_card_level, face.level), scale, 26, 488, 82, 23, 11, muted)
    CardText(face.seriesLabel, scale, 110, 490, 224, 20, 9, muted, alignment = TextAlign.Right)
}

@Composable
private fun FrontFace(
    face: MilestoneCardFace.Front,
    scale: Float,
    stock: CardStock,
    material: BlackLabel?,
    plate: PrintPlate?,
) {
    when {
        plate != null -> CardPlane(plate.paper)

        stock == CardStock.Safety -> {
            if (face.level == 1) SafetyBase() else CardPlane(R.drawable.milestone_safety_5_base)
            CardPlane(R.drawable.milestone_safety_detail_overlay)
        }

        face.level == 1 -> {
            CardPlane(R.drawable.milestone_place_1_base)
            CardPlane(R.drawable.milestone_place_1_overlay)
        }

        else -> {
            CardPlane(R.drawable.milestone_place_2_base)
            CardPlane(R.drawable.milestone_place_2_overlay)
        }
    }
    CardArtwork(face.artwork, stock)
    if (stock == CardStock.Safety) SafetyLabels(face, scale, material) else PlaceLabels(face, scale, material)
}

@Composable
private fun CardArtwork(artwork: Painter, stock: CardStock) {
    val window = remember(stock) { Path().apply { addRoundRect(stock.artwork) } }
    Canvas(Modifier.fillMaxSize()) {
        scale(size.width / CARD_WIDTH, size.height / CARD_HEIGHT, pivot = Offset.Zero) {
            clipPath(window) {
                translate(stock.ink.left, stock.ink.top) {
                    with(artwork) { draw(size = stock.ink.size) }
                }
            }
        }
    }
}

@Composable
private fun SafetyBase() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scale = size.width / CARD_WIDTH
        drawRoundRect(
            color = Color(0xFF7D9DAC),
            cornerRadius = CornerRadius(20f * scale),
        )
        val rim = 5f * scale
        drawRoundRect(
            color = Color(0xFFE6EFF0),
            topLeft = Offset(rim, rim),
            size = Size(size.width - rim * 2, size.height - rim * 2),
            cornerRadius = CornerRadius(15f * scale),
        )
    }
}

@Composable
private fun PlaceLabels(face: MilestoneCardFace.Front, scale: Float, material: BlackLabel?) {
    CardText(face.contextLabel, scale, 28, 30, 127, 20, 12, material?.secondaryInk ?: Color(0xFF65778A))
    face.categoryLabel?.let {
        CardText(
            it, scale, 205, 30, 127, 20, 12,
            material?.secondaryInk ?: Color(
                0xFF65778A,
            ),
            alignment = TextAlign.Right,
        )
    }
    CardText(face.title, scale, 28, 414, 304, 37, 28, material?.titleInk ?: Color(0xFF132C40), weight = FontWeight.Bold)
    CardText(face.subtitle, scale, 29, 453, 303, 26, 14, material?.secondaryInk ?: Color(0xFF687B8B))
    CardText(
        text = stringResource(R.string.milestone_card_level, face.appearance.level),
        scale = scale,
        x = 28,
        y = 490,
        width = 100,
        height = 25,
        fontSize = 12,
        color = material?.secondaryInk ?: Color(0xFF425F73),
    )
    CardText(
        text = face.seriesLabel,
        scale = scale,
        x = 138,
        y = 492,
        width = 194,
        height = 24,
        fontSize = 10,
        color = material?.secondaryInk ?: Color(0xFF7A8D9A),
        alignment = TextAlign.Right,
    )
}

@Composable
private fun SafetyLabels(face: MilestoneCardFace.Front, scale: Float, material: BlackLabel?) {
    CardText(face.contextLabel, scale, 22, 20, 250, 26, 15, material?.secondaryInk ?: Color(0xFF466778))
    CardText(face.title, scale, 22, 384, 316, 38, 27, material?.titleInk ?: Color(0xFF17384A), weight = FontWeight.Bold)
    CardText(face.subtitle, scale, 22, 424, 316, 37, 16, material?.secondaryInk ?: Color(0xFF527084))
    CardText(
        text = stringResource(R.string.milestone_card_level, face.appearance.level),
        scale = scale,
        x = 22,
        y = 476,
        width = 110,
        height = 31,
        fontSize = 16,
        color = material?.secondaryInk ?: Color(0xFF527084),
    )
    CardText(
        text = face.seriesLabel,
        scale = scale,
        x = 148,
        y = 492,
        width = 190,
        height = 24,
        fontSize = 10,
        color = material?.secondaryInk ?: Color(0xFF708E9C),
        alignment = TextAlign.Right,
    )
}

@Composable
private fun CommonBackFace(scale: Float) {
    CardPlane(R.drawable.milestone_common_back)
    CardText(
        text = stringResource(R.string.milestone_card_back_title),
        scale = scale,
        x = 32,
        y = 328,
        width = 296,
        height = 35,
        fontSize = 18,
        color = Color(0xFFB3CED2),
        alignment = TextAlign.Center,
    )
    CardText(
        text = stringResource(R.string.milestone_card_back_subtitle),
        scale = scale,
        x = 32,
        y = 366,
        width = 296,
        height = 31,
        fontSize = 16,
        color = Color(0xFF7396A6),
        alignment = TextAlign.Center,
    )
}

@Composable
private fun CardPlane(drawable: Int) {
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
private fun CardText(
    text: String,
    scale: Float,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    fontSize: Int,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    alignment: TextAlign = TextAlign.Left,
) {
    val preferredSize = (fontSize * scale).sp
    BasicText(
        text = text,
        modifier = Modifier
            .absoluteOffset(x = (x * scale).dp, y = (y * scale).dp)
            .width((width * scale).dp)
            .heightIn(max = (height * scale).dp)
            .clipToBounds(),
        style = TextStyle(
            color = color,
            fontSize = preferredSize,
            fontWeight = weight,
            textAlign = alignment,
        ),
        autoSize = TextAutoSize.StepBased(
            minFontSize = (fontSize * scale * 0.8f).sp,
            maxFontSize = preferredSize,
            stepSize = 0.25.sp,
        ),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

internal const val CARD_WIDTH = 360f
internal const val CARD_HEIGHT = 520f
internal const val FOIL_RIM_INSET = 5f
internal const val FOIL_RIM_WIDTH = 4f
const val MILESTONE_CARD_ASPECT_RATIO = CARD_WIDTH / CARD_HEIGHT

internal enum class CardStock(
    val paper: Color,
    val corner: Float,
    val trim: Float,
    val artwork: RoundRect,
    val ink: Rect = artwork.boundingRect,
    val picture: Rect? = null,
    val pictureCorner: Float = 0f,
) {
    Place(
        Color(0xFFEDEEEA),
        24f,
        9.5f,
        artwork = RoundRect(Rect(30f, 76f, 330f, 384f), CornerRadius(6f)),
        ink = Rect(24f, 62f, 336f, 510.5f),
        picture = Rect(25f, 71f, 335f, 389f),
        pictureCorner = 8f,
    ),
    Postcard(
        Color(0xFFF1F2EB),
        18f,
        7f,
        artwork = RoundRect(Rect(26f, 68f, 334f, 386f), CornerRadius(7f)),
        picture = Rect(23f, 65f, 337f, 389f),
        pictureCorner = 9f,
    ),
    Safety(
        Color(0xFFE8F1F2),
        20f,
        7f,
        artwork = RoundRect(
            Rect(20f, 62f, 340f, 362f),
            topLeft = CornerRadius(10f),
            topRight = CornerRadius(10f),
            bottomRight = CornerRadius.Zero,
            bottomLeft = CornerRadius.Zero,
        ),
    ),
}

private val blueFoil = listOf(
    Color(0xFF0752B9),
    Color(0xFF39D9FF),
    Color(0xFFE9FDFF),
    Color(0xFF1582CE),
    Color(0xFF6CE6FF),
)
private fun Modifier.blueFoilFrame(stock: CardStock): Modifier = drawWithCache {
    val widthScale = size.width / CARD_WIDTH
    val heightScale = size.height / CARD_HEIGHT
    val paperCorner = CornerRadius(stock.corner * widthScale, stock.corner * heightScale)
    // Keep the printed interior and trim off the different rims baked into the original exports.
    val trimX = stock.trim * widthScale
    val trimY = stock.trim * heightScale
    val trimCorner = stock.corner - stock.trim
    val interior = Path().apply {
        addRoundRect(
            RoundRect(
                Rect(trimX, trimY, size.width - trimX, size.height - trimY),
                CornerRadius(trimCorner * widthScale, trimCorner * heightScale),
            ),
        )
    }
    // The print gradient traverses the same number of stops along the top and left edges.
    val diagonal = Offset(CARD_HEIGHT, CARD_WIDTH)
    val foil = Brush.linearGradient(
        colors = blueFoil,
        end = diagonal * (2f * CARD_WIDTH * CARD_HEIGHT / diagonal.getDistanceSquared()),
    )
    // The Level 2 master places a 4-unit stroke 5 units inside its 360 × 520 print.
    val rimBounds = Rect(0f, 0f, CARD_WIDTH, CARD_HEIGHT).deflate(FOIL_RIM_INSET)
    val rim = Stroke(width = FOIL_RIM_WIDTH)
    val keyline = Stroke(width = 3f)
    onDrawWithContent {
        drawRoundRect(stock.paper, cornerRadius = paperCorner)
        clipPath(interior) { this@onDrawWithContent.drawContent() }
        scale(widthScale, heightScale, pivot = Offset.Zero) {
            drawRoundRect(
                brush = foil,
                topLeft = rimBounds.topLeft,
                size = rimBounds.size,
                cornerRadius = CornerRadius(stock.corner - FOIL_RIM_INSET),
                style = rim,
            )
            stock.picture?.let { picture ->
                drawRoundRect(
                    brush = foil,
                    topLeft = picture.topLeft,
                    size = picture.size,
                    cornerRadius = CornerRadius(stock.pictureCorner),
                    style = keyline,
                )
            }
        }
    }
}

@Preview(name = "Milestone card • place front", group = "Basic faces")
@Composable
private fun MilestonePlaceFrontPreview() {
    PleosTheme {
        MilestoneCard(
            face = previewFace(MilestoneCardAppearance.PlaceLevel1),
            modifier = Modifier.width(240.dp),
        )
    }
}

@Preview(name = "Milestone card • common back", group = "Basic faces")
@Composable
private fun MilestoneCommonBackPreview() {
    PleosTheme {
        MilestoneCard(
            face = MilestoneCardFace.CommonBack,
            modifier = Modifier.width(240.dp),
        )
    }
}

@Preview(name = "Milestone card • all front variants and back", group = "Variants")
@Composable
private fun MilestoneVariantGalleryPreview() {
    PleosTheme {
        Surface {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MilestoneCardAppearance.entries.groupBy { it.isSafety }.values.forEach { family ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        family.forEach { appearance ->
                            MilestoneCard(
                                face = previewFace(appearance),
                                modifier = Modifier.width(160.dp),
                            )
                        }
                    }
                }
                MilestoneCard(MilestoneCardFace.CommonBack, Modifier.width(160.dp))
            }
        }
    }
}

@Preview(name = "Milestone card • compact with larger text", group = "Variants")
@Composable
private fun MilestoneCompactLongTitlePreview() {
    PleosTheme {
        val density = LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.6f)) {
            MilestoneCard(
                face = previewFace(MilestoneCardAppearance.PlaceLevel3).copy(
                    contextLabel = "부산광역시 해운대구 · 해운대",
                    title = "해운대에서 다시 만난 바다의 파도를 기억하다",
                    subtitle = "부산의 여름과 오래 간직하고 싶은 여정의 기억",
                ),
                modifier = Modifier.width(112.dp),
            )
        }
    }
}

@Composable
private fun previewFace(appearance: MilestoneCardAppearance): MilestoneCardFace.Front = if (appearance.isSafety) {
    MilestoneCardFace.Front(
        appearance = appearance,
        artwork = painterResource(R.drawable.milestone_art_safety),
        glossMask = R.drawable.milestone_gloss_safety,
        engravingMask = R.drawable.milestone_engraving_safety,
        contextLabel = "운전습관 · 귀가",
        title = "안전한 귀가",
        subtitle = "여정을 차분히 마친 기록",
        seriesLabel = "SAFETY / SMOOTH / 001",
    )
} else {
    MilestoneCardFace.Front(
        appearance = appearance,
        artwork = painterResource(R.drawable.milestone_art_haeundae),
        glossMask = R.drawable.milestone_gloss_haeundae,
        engravingMask = R.drawable.milestone_engraving_haeundae,
        contextLabel = "부산 · 해운대",
        title = "해운대의 파도",
        subtitle = "부산의 여름을 간직하다",
        seriesLabel = "MILESTONE / 001",
        categoryLabel = "장소의 기억",
    )
}
