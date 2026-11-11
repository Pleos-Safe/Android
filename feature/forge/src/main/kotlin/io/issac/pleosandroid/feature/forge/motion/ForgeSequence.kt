package io.issac.pleosandroid.feature.forge.motion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.domain.cards.ForgeReceipt
import kotlin.math.PI
import kotlin.math.sin

internal enum class ForgeFinish { Print, Metal, Prism, FullArt, Crown, Laurel, Beyond }

internal enum class SkyFinish { Eclipse, Aurora, Constellation }

/** One saved clock covers both increments, including the pause on the intermediate card. */
internal class ForgePlayback(receipt: ForgeReceipt) {
    val first: ForgeSequence
    val last: ForgeSequence
    private val lastStartsMs: Int

    init {
        if (receipt.outcome == ForgeOutcome.GreatSuccess && receipt.after.toLong() - receipt.before == 2L) {
            val intermediate = receipt.after - 1
            first = ForgeSequence(receipt.before, intermediate, ForgeOutcome.Success)
            last = ForgeSequence(intermediate, receipt.after, ForgeOutcome.GreatSuccess, bonus = true)
            lastStartsMs = first.durationMs
        } else {
            first = ForgeSequence(receipt.before, receipt.after, receipt.outcome)
            last = first
            lastStartsMs = 0
        }
    }

    val durationMs = lastStartsMs + last.durationMs

    fun sequenceAt(progress: Float) = if (progress * durationMs < lastStartsMs) first else last

    fun localTime(sequence: ForgeSequence, progress: Float): Float {
        val start = if (sequence === last) lastStartsMs else 0
        return ((progress * durationMs - start) / sequence.durationMs).coerceIn(0f, 1f)
    }

    fun enter(progress: Float) = first.enter(localTime(first, progress))
    fun exit(progress: Float) = last.exit(localTime(last, progress))
}

internal class ForgeSequence(val before: Int, val after: Int, val outcome: ForgeOutcome, val bonus: Boolean = false) {
    val rising = after > before
    private val beforeStage = (before / 5).coerceIn(0, 5)
    private val afterStage = (after / 5).coerceIn(0, 5)
    val finish = when {
        rising && before >= 25 -> ForgeFinish.Beyond
        rising && afterStage > beforeStage -> ForgeFinish.entries[afterStage]
        else -> ForgeFinish.Print
    }
    val sky = if (finish == ForgeFinish.Beyond) {
        SkyFinish.entries[(after - 26).mod(SkyFinish.entries.size)]
    } else {
        null
    }
    val etchesFrame = rising && afterStage > beforeStage
    val expandsArt = beforeStage < 3 && afterStage >= 3
    val contractsArt = beforeStage >= 3 && afterStage < 3
    val levelLine = when (outcome) {
        ForgeOutcome.Destroyed -> "+$before  →  흔적 +$after"
        ForgeOutcome.Hold -> "+$after 유지"
        ForgeOutcome.Restored -> "흔적  →  +$after"
        else -> "+$before  →  +$after"
    }

    // The first wind-up depends only on the starting card. A bonus catches the light again briefly.
    val chargeDepth = ((before.coerceIn(0, 25) + 1) / 5).coerceAtMost(5)
    private val windupMs = if (bonus) 240 else 1160 + 110 * chargeDepth + if (before >= 25) 140 else 0
    private val suspensionMs = if (bonus) 70 else 195 + 25 * chargeDepth
    private val releaseMs = when (finish) {
        ForgeFinish.Print -> when (outcome) {
            ForgeOutcome.Destroyed -> 1780 + 60 * beforeStage
            ForgeOutcome.GreatSuccess -> 1500
            ForgeOutcome.Hold -> 1230 + 40 * beforeStage
            ForgeOutcome.Drop -> 1480 + 50 * beforeStage
            else -> 980
        }

        ForgeFinish.Beyond -> 2050 + (after - 26).coerceIn(0, 12) * 16

        else -> 1280 + 160 * afterStage
    }
    private val impactMs = windupMs + suspensionMs
    private val titleMs = impactMs + (
        releaseMs * when {
            etchesFrame -> .80f
            outcome == ForgeOutcome.Destroyed -> .76f
            outcome == ForgeOutcome.Drop -> .68f
            outcome == ForgeOutcome.Hold -> .62f
            outcome == ForgeOutcome.GreatSuccess -> .70f
            else -> .58f
        }
        ).toInt()
    private val returnMs = impactMs + releaseMs + 660
    val durationMs = returnMs + 460
    val titleAt = titleMs.toFloat() / durationMs
    val power = when {
        finish == ForgeFinish.Beyond -> 1f

        outcome == ForgeOutcome.Hold || outcome == ForgeOutcome.Drop || outcome == ForgeOutcome.Destroyed ->
            .38f + .095f * beforeStage

        else -> (.22f + finish.ordinal * .145f).coerceAtMost(.96f)
    }

    // Rotate the diffraction pattern at each unbounded level without adding more drawing work.
    val azimuth = ((after.toLong() * 137L) % 360L).toFloat()
    val accent = when (outcome) {
        ForgeOutcome.Hold -> Color(0xFFA6D2E0)

        ForgeOutcome.Drop -> Color(0xFFFFAD79)

        ForgeOutcome.Destroyed -> Color(0xFFFF626E)

        ForgeOutcome.GreatSuccess -> Color(0xFFFFCE6C)

        ForgeOutcome.Restored -> Color(0xFF96EFCD)

        ForgeOutcome.Success -> when (finish) {
            ForgeFinish.Prism, ForgeFinish.FullArt -> Color(0xFFB8B2FF)

            ForgeFinish.Crown, ForgeFinish.Laurel -> Color(0xFFFFD18B)

            ForgeFinish.Beyond -> when (sky) {
                SkyFinish.Aurora -> Color(0xFFABF6F0)
                SkyFinish.Constellation -> Color(0xFFD6C7FF)
                else -> Color(0xFFFFD18B)
            }

            else -> Color(0xFF94F2D7)
        }
    }
    val secondary = when (outcome) {
        ForgeOutcome.Drop, ForgeOutcome.Destroyed -> Color(0xFFB26881)
        ForgeOutcome.GreatSuccess -> Color(0xFFFF937D)
        ForgeOutcome.Hold -> Color(0xFF7E9BB7)
        else -> if (finish.ordinal >= ForgeFinish.Prism.ordinal) Color(0xFF97EAE4) else Color(0xFF95ACE1)
    }

    /** The same view normal tilts the physical print and moves its reflected rays. */
    fun cardRotation(t: Float, tremor: Offset = chargeShake(t)): Offset {
        val u = release(t)
        val recoil = pulse(u, 0f, .14f, .48f)
        val turn = easeBetween(impactMs + releaseMs * .34f, returnMs + 105f, t * durationMs)
        val arc = sin(PI.toFloat() * turn)
        val travel = sin(2f * PI.toFloat() * turn) * arc
        val amplitude = when (outcome) {
            ForgeOutcome.Destroyed -> 8f
            ForgeOutcome.Hold -> 9f
            ForgeOutcome.GreatSuccess -> 12f
            else -> 8f + 3f * power
        }
        val kick = when (outcome) {
            ForgeOutcome.Hold -> -6.5f * holdCompression(u) + 9f * holdRebound(u)
            ForgeOutcome.GreatSuccess -> -11f * pulse(u, 0f, .24f, .67f)
            ForgeOutcome.Drop -> -5f * fallDisplacement(u)
            ForgeOutcome.Destroyed -> 13f * fractureShock(u) - 5f * pulse(u, .20f, .39f, .64f)
            else -> (3f + 5f * power) * recoil
        }
        val fall = if (outcome == ForgeOutcome.Drop) -18f * fallDisplacement(u) else 0f
        return Offset(-5f * pressure(t) + 4f * tremor.y + kick + travel * amplitude, fall - arc * 3f + 2.8f * tremor.x)
    }

    // A physical clock keeps the vibration and incoming light identical for every unrevealed outcome.
    fun gathering(t: Float) = (t * durationMs / impactMs).coerceIn(0f, 1f)

    fun chargeShake(t: Float): Offset {
        val energy = pressure(t)
        if (energy <= 0f) return Offset.Zero
        val seconds = t * durationMs * .001f
        val phase = (2f * PI.toFloat()) * seconds * (3.4f + 2.5f * seconds.coerceAtMost(1.9f))
        val hold = easeBetween(windupMs.toFloat(), impactMs.toFloat(), t * durationMs)
        val strength = energy * easeBetween(.12f, .88f, charge(t)) * (1f - .5f * hold) * (.58f + .085f * chargeDepth)
        return Offset(
            strength * (.70f * sin(phase) + .30f * sin(.61f * phase + 1.2f)),
            strength * (.64f * sin(.82f * phase + .7f) + .36f * sin(1.18f * phase - .8f)),
        )
    }

    fun charge(t: Float) = easeBetween(0f, windupMs.toFloat(), t * durationMs)
    fun release(t: Float) = ((t * durationMs - impactMs) / releaseMs).coerceIn(0f, 1f)
    fun pressure(t: Float) = charge(t) * (1f - easeBetween(0f, .17f, release(t)))
    fun enter(t: Float) = easeBetween(0f, 370f, t * durationMs)
    fun exit(t: Float) = easeBetween(returnMs.toFloat(), durationMs.toFloat(), t * durationMs)
    fun title(t: Float) = easeBetween(titleMs.toFloat(), titleMs + 245f, t * durationMs)
    fun letterGlint(t: Float) = easeBetween(titleMs + 130f, titleMs + 760f, t * durationMs)
    fun exposure(t: Float): Float = when (outcome) {
        ForgeOutcome.Drop -> easeBetween(.13f, .73f, release(t))
        ForgeOutcome.Destroyed -> easeBetween(.24f, .83f, release(t))
        else -> easeBetween(.10f, .78f, release(t))
    }
}

internal fun easeBetween(start: Float, end: Float, value: Float): Float {
    val unit = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return unit * unit * (3f - 2f * unit)
}

internal fun pulse(value: Float, start: Float, peak: Float, end: Float) =
    easeBetween(start, peak, value) * (1f - easeBetween(peak, end, value))

// Normalized release beats shared by the card, its reflection and the light cast onto the stage.
internal fun holdCompression(u: Float) = pulse(u, .01f, .27f, .56f)
internal fun holdRebound(u: Float) = pulse(u, .27f, .52f, .96f)
internal fun landingShock(u: Float) = pulse(u, .46f, .53f, .86f)
internal fun fallDisplacement(u: Float): Float {
    val descent = easeBetween(.11f, .48f, u)
    return descent * descent * (1f - easeBetween(.48f, .94f, u)) - .38f * pulse(u, .48f, .68f, .96f)
}

internal const val FRACTURE_X = .62f
internal const val FRACTURE_Y = .405f
internal fun fractureShock(u: Float) = pulse(u, .09f, .20f, .48f)
