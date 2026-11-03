package io.issac.pleosandroid.car

/** A platform snapshot. Unknown or disconnected states must never unlock restricted UI. */
data class CarUxPolicy(
    val connected: Boolean = false,
    val known: Boolean = false,
    val requiresDistractionOptimization: Boolean = true,
    val activeRestrictions: Int = FULLY_RESTRICTED,
    val maxRestrictedStringLength: Int = 0,
    val maxCumulativeContentItems: Int = 0,
    val maxContentDepth: Int = 0,
    val message: String = "차량 UX 제한을 아직 확인하지 못했습니다.",
) {
    val restrictionsRequired: Boolean
        get() = !connected || !known || requiresDistractionOptimization

    val keyboardAllowed: Boolean get() = allows(NO_KEYBOARD)
    val videoAllowed: Boolean get() = allows(NO_VIDEO)
    val setupAllowed: Boolean get() = allows(NO_SETUP)
    val dialpadAllowed: Boolean get() = allows(NO_DIALPAD)
    val filteringAllowed: Boolean get() = allows(NO_FILTERING)
    val textMessageAllowed: Boolean get() = allows(NO_TEXT_MESSAGE)
    val voiceTranscriptionAllowed: Boolean get() = allows(NO_VOICE_TRANSCRIPTION)
    val stringLengthLimited: Boolean get() = !allows(LIMIT_STRING_LENGTH)
    val contentLimited: Boolean get() = !allows(LIMIT_CONTENT)

    private fun allows(flag: Int): Boolean = connected && known &&
        (!requiresDistractionOptimization || activeRestrictions and flag == 0)

    companion object {
        // Public AAOS CarUxRestrictions bit values; kept here so this model is Android-independent.
        const val NO_DIALPAD = 1
        const val NO_FILTERING = 2
        const val LIMIT_STRING_LENGTH = 4
        const val NO_KEYBOARD = 8
        const val NO_VIDEO = 16
        const val LIMIT_CONTENT = 32
        const val NO_SETUP = 64
        const val NO_TEXT_MESSAGE = 128
        const val NO_VOICE_TRANSCRIPTION = 256
        const val FULLY_RESTRICTED = 511
    }
}
