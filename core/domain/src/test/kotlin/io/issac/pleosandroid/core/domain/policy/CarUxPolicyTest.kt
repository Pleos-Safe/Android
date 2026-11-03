package io.issac.pleosandroid.core.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarUxPolicyTest {
    @Test
    fun unknownStateNeverUnlocksEvenWhenRawFlagsAreEmpty() {
        val unknown = CarUxPolicy(
            connected = true,
            known = false,
            requiresDistractionOptimization = false,
            activeRestrictions = 0,
        )

        assertTrue(unknown.restrictionsRequired)
        assertFalse(unknown.keyboardAllowed)
        assertFalse(unknown.videoAllowed)
        assertFalse(unknown.setupAllowed)
        assertTrue(unknown.contentLimited)
    }

    @Test
    fun serviceLossInvalidatesPreviouslyUnrestrictedSnapshot() {
        val parked = snapshot(requiresOptimization = false, flags = 0)
        val disconnected = parked.copy(connected = false)

        assertTrue(parked.videoAllowed)
        assertFalse(disconnected.videoAllowed)
        assertFalse(disconnected.keyboardAllowed)
        assertTrue(disconnected.restrictionsRequired)
    }

    @Test
    fun optimizationNotRequiredIgnoresRestrictionBits() {
        val unrestricted = snapshot(
            requiresOptimization = false,
            flags = CarUxPolicy.FULLY_RESTRICTED,
        )

        assertFalse(unrestricted.restrictionsRequired)
        assertTrue(unrestricted.keyboardAllowed)
        assertTrue(unrestricted.videoAllowed)
        assertTrue(unrestricted.setupAllowed)
        assertFalse(unrestricted.stringLengthLimited)
        assertFalse(unrestricted.contentLimited)
    }

    @Test
    fun mixedRestrictionsDisableOnlyTheirOwnFeatures() {
        // A regional policy can forbid video/setup while still allowing keyboard input.
        val mixed = snapshot(flags = 16 or 64)

        assertTrue(mixed.restrictionsRequired)
        assertFalse(mixed.videoAllowed)
        assertFalse(mixed.setupAllowed)
        assertTrue(mixed.keyboardAllowed)
        assertTrue(mixed.dialpadAllowed)
        assertTrue(mixed.filteringAllowed)
    }

    @Test
    fun requiredOptimizationDoesNotImplyAllFeaturesAreForbidden() {
        val optimized = snapshot(flags = 0)

        assertTrue(optimized.restrictionsRequired)
        assertTrue(optimized.keyboardAllowed)
        assertTrue(optimized.videoAllowed)
        assertTrue(optimized.setupAllowed)
    }

    @Test
    fun contentLimitsPreservePlatformProvidedBounds() {
        val limited = snapshot(flags = 4 or 32).copy(
            maxRestrictedStringLength = 80,
            maxCumulativeContentItems = 6,
            maxContentDepth = 3,
        )

        assertTrue(limited.stringLengthLimited)
        assertTrue(limited.contentLimited)
        assertEquals(80, limited.maxRestrictedStringLength)
        assertEquals(6, limited.maxCumulativeContentItems)
        assertEquals(3, limited.maxContentDepth)
        assertTrue(limited.videoAllowed)
    }

    @Test
    fun fullyRestrictedStateBlocksEveryMappedFeature() {
        val restricted = snapshot(flags = 511)

        assertFalse(restricted.keyboardAllowed)
        assertFalse(restricted.videoAllowed)
        assertFalse(restricted.setupAllowed)
        assertFalse(restricted.dialpadAllowed)
        assertFalse(restricted.filteringAllowed)
        assertFalse(restricted.textMessageAllowed)
        assertFalse(restricted.voiceTranscriptionAllowed)
        assertTrue(restricted.contentLimited)
        assertTrue(restricted.stringLengthLimited)
    }

    private fun snapshot(requiresOptimization: Boolean = true, flags: Int) = CarUxPolicy(
        connected = true,
        known = true,
        requiresDistractionOptimization = requiresOptimization,
        activeRestrictions = flags,
    )
}
