/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 * Licensed under the Apache License, Version 2.0
 */

package com.mantraproductions.androidvu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TEST 1 — THE MECHANISM, ALONE.
 *
 * No emulator, no device, no `android.jar`. This runs on the build machine in a second and it is
 * the only test in this build that runs at all, which is stated plainly here so that a green CI run
 * is not mistaken for a working app. Everything the probe does with the audio system is Test 2 and
 * Test 2 needs Baba's phone.
 *
 * `four-tests.md`: *"What could be true that would make this test pass and the feature still be
 * broken?"* — the answer these close is **the mapping is wrong**. They cannot close *nothing calls
 * it*, and they do not pretend to.
 */
class MaProbeTest {

    // ---------------------------------------------------------------- the mapping

    /** The case it is FOR. Every usage a real phone emits, folded to the right letter. */
    @Test
    fun everyStatedUsageLandsOnItsChannel() {
        assertEquals(MaChannel.MEDIA, MaUsage.match(1).channel)
        assertEquals(MaChannel.MEDIA, MaUsage.match(14).channel)
        assertEquals(MaChannel.CALL, MaUsage.match(2).channel)
        assertEquals(MaChannel.CALL, MaUsage.match(3).channel)
        assertEquals(MaChannel.ALARM, MaUsage.match(4).channel)
        assertEquals(MaChannel.NOTIFICATION, MaUsage.match(5).channel)
        assertEquals(MaChannel.RING, MaUsage.match(6).channel)
    }

    /**
     * THE CASE IT MUST REFUSE, and this is the one worth having.
     *
     * A usage from an Android newer than this build must move NO meter. The natural way to write
     * this table is with `else -> MEDIA`, because unknown audio usually is media — and that is
     * exactly the invented motion the briefing forbids: a meter that dances to a channel it has not
     * identified.
     */
    @Test
    fun anUnrecognisedUsageMovesNothing() {
        assertNull(MaUsage.match(99).channel)
        assertNull(MaUsage.match(17).channel)
        assertNull(MaUsage.match(-1).channel)
        assertFalse(MaUsage.match(99).certain)
        assertTrue(MaUsage.match(99).usageName.contains("99"))
    }

    /** The deprecated notification family is still emitted by real apps. All four fold to N. */
    @Test
    fun theDeprecatedNotificationFamilyStillFolds() {
        for (u in listOf(5, 7, 8, 9, 10)) {
            assertEquals("usage $u", MaChannel.NOTIFICATION, MaUsage.match(u).channel)
            assertTrue("usage $u", MaUsage.match(u).certain)
        }
    }

    /** Inferred is not certain, and the difference has to survive out of this function. */
    @Test
    fun conventionRoutedUsagesAreMarkedUncertain() {
        for (u in listOf(0, 12, 16)) {
            assertEquals("usage $u", MaChannel.MEDIA, MaUsage.match(u).channel)
            assertFalse("usage $u must not claim certainty", MaUsage.match(u).certain)
        }
    }

    /** Accessibility, sonification and virtual sources are none of the five, and we know it. */
    @Test
    fun theNonChannelsAreRefusedWithCertainty() {
        for (u in listOf(11, 13, 15)) {
            assertNull("usage $u", MaUsage.match(u).channel)
            assertTrue("usage $u", MaUsage.match(u).certain)
        }
    }

    /** Both sides of every boundary in the table, and the same input twice. */
    @Test
    fun theWholeRangeIsTotalAndIdempotent() {
        for (u in -2..20) {
            val a = MaUsage.match(u)
            val b = MaUsage.match(u)
            assertEquals("usage $u is not idempotent", a, b)
            assertEquals("usage $u lost its number", u, a.usage)
        }
    }

    /** The five channels carry the five stream constants from the briefing, in his words. */
    @Test
    fun theChannelTableMatchesTheBriefing() {
        assertEquals(listOf("C", "M", "R", "N", "A"), MaChannel.values().map { it.letter })
        assertEquals(listOf(0, 3, 2, 5, 4), MaChannel.values().map { it.streamType })
        assertEquals(5, MaChannel.values().map { it.streamType }.toSet().size)
        assertTrue(MaChannel.CALL.what.contains("other person"))
    }

    // ---------------------------------------------------------------- the verdict

    @Test
    fun theVerdictIgnoresOurOwnTone() {
        val ours = listOf(MaConfigReading(4, 4, isOwn = true))
        assertTrue(MaProbeReport.verdict(ours).contains("no foreign audio"))
    }

    @Test
    fun theVerdictCountsOnlyNamedForeignUsages() {
        val mixed = listOf(
            MaConfigReading(1, 2, isOwn = false),
            MaConfigReading(0, 0, isOwn = false),
            MaConfigReading(4, 4, isOwn = true),
        )
        val v = MaProbeReport.verdict(mixed)
        assertTrue(v, v.contains("1 of 2"))
    }

    // ---------------------------------------------------------------- the width

    /** EMPTY: nothing playing must not render as a confident nothing. */
    @Test
    fun anEmptyConfigListSaysSoRatherThanLookingClean() {
        val r = MaProbeReport.render(snapshot(configs = emptyList()))
        assertTrue(r, r.contains("configurations returned: 0"))
        assertTrue(r, r.contains("press REFRESH"))
    }

    /** ABSENT: a call that threw is not the same fact as a call that answered with nothing. */
    @Test
    fun aFailedCallIsNotAnEmptyResult() {
        val r = MaProbeReport.render(snapshot(configs = null))
        assertTrue(r, r.contains("the call itself failed"))
        assertFalse(r, r.contains("configurations returned"))
    }

    /**
     * NOTHING GOES OFF THE SCREEN, measured rather than reasoned about.
     *
     * Fed the worst real inputs there are: the thirty-four character usage name, an unrecognised
     * usage, a long device string. If any line of this needs [MaProbeReport.fit] the layout is
     * wrong and stacking it is the fix, not a narrower column.
     */
    @Test
    fun noLineOfARealisticReportEverNeedsTruncating() {
        val r = MaProbeReport.render(
            snapshot(
                device = "Nothing Phone (2a)",
                configs = listOf(
                    MaConfigReading(7, 4, isOwn = false),
                    MaConfigReading(999, 0, isOwn = false),
                    MaConfigReading(1, 2, isOwn = true),
                ),
            )
        )
        assertFalse("a line was truncated", r.contains("\u2026"))
        assertTrue(
            "widest line is " + MaProbeReport.widest(r),
            MaProbeReport.widest(r) <= MaProbeReport.WIDTH,
        )
    }

    /** ENORMOUS: the net itself, at both sides of its boundary. */
    @Test
    fun fitCutsOnlyWhatIsGenuinelyTooWide() {
        val exact = "x".repeat(MaProbeReport.WIDTH)
        assertEquals(exact, MaProbeReport.fit(exact))
        val over = "x".repeat(MaProbeReport.WIDTH + 1)
        assertEquals(MaProbeReport.WIDTH, MaProbeReport.fit(over).length)
        assertTrue(MaProbeReport.fit(over).endsWith("\u2026"))
    }

    /** Every one of the five appears in the volume block, in order, exactly once. */
    @Test
    fun allFiveChannelsReachTheReport() {
        val r = MaProbeReport.render(snapshot())
        for (c in MaChannel.values()) {
            assertEquals("channel " + c.letter, 1, Regex("(?m)^" + c.letter + " " + c.title).findAll(r).count())
        }
    }

    // ---------------------------------------------------------------- the fixture

    private fun snapshot(
        device: String = "Nothing Phone (2a)",
        configs: List<MaConfigReading>? = emptyList(),
    ) = MaSnapshot(
        device = device,
        androidRelease = "15",
        sdkInt = 35,
        appVersion = "1",
        takenAt = "2026-08-25 14:32:10",
        streams = MaChannel.values().map { MaStreamReading(it, 5, 15, 0, false) },
        mode = 0,
        ringerMode = 2,
        musicActive = false,
        configs = configs,
        visualiser = "not run",
    )
}
