/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 * Licensed under the Apache License, Version 2.0
 */

package com.mantraproductions.androidvu

/**
 * THE FIVE CHANNELS, AND THE MAPPING THE WHOLE APP RESTS ON.
 *
 * ### Why this file has no Android imports
 *
 * Every constant here is written as a bare integer rather than as `AudioManager.STREAM_MUSIC` or
 * `AudioAttributes.USAGE_MEDIA`. That is deliberate and it is the difference between a Test 1 that
 * runs and one that does not: a plain JVM unit test has no `android.jar`, so every Android constant
 * reads back as 0 and a mapping table built from them collapses into "everything is channel zero"
 * while still compiling and still passing.
 *
 * `four-tests.md`: *"If you cannot run the thing without starting the whole application, that is
 * itself a finding."* The numbers are stable platform ABI — they are in the AIDL and cannot change
 * without breaking every app ever shipped — so writing them out costs nothing and buys a test.
 *
 * ### Why the mapping is the thing worth testing
 *
 * `AudioManager.getActivePlaybackConfigurations()` is the only API that says *which* channel is
 * making a sound right now, and it says it as a `usage`, not as a stream. Sixteen usages have to be
 * folded into five letters, several of them are deprecated aliases of each other, and one — UNKNOWN
 * — is a genuine "we do not know" that must NOT be quietly filed under Media. Fold it wrongly and
 * every meter in the app points at the wrong channel while looking perfectly alive.
 */
enum class MaChannel(
    val letter: String,
    /** The `AudioManager.STREAM_*` constant, written out — see the file header. */
    val streamType: Int,
    val title: String,
    /** Baba's own words for what the channel is, from the briefing. Shown beside each tick box. */
    val what: String,
) {
    CALL("C", 0, "Call", "the other person's voice"),
    MEDIA("M", 3, "Media", "music, video, podcasts"),
    RING("R", 2, "Ring", "the ringtone"),
    NOTIFICATION("N", 5, "Notification", "message and app alerts"),
    ALARM("A", 4, "Alarm", "alarms and timers"),
}

/**
 * The result of reading one `AudioAttributes.usage`.
 *
 * `certain` is not decoration. UNKNOWN and the assistance usages route to a stream by convention
 * rather than by declaration, so a meter fed by one of those is showing a guess. The briefing is
 * explicit: *"Never invent motion."* A guess that is drawn identically to a fact is invented motion
 * with extra steps, so the uncertainty travels with the value instead of being dropped here.
 */
data class MaUsageMatch(
    val usage: Int,
    val usageName: String,
    val channel: MaChannel?,
    val certain: Boolean,
)

object MaUsage {

    // The AudioAttributes.USAGE_* constants, written out. 7, 8, 9 and 10 are the deprecated
    // notification family — deprecated in the SDK, still emitted by real apps on real phones, which
    // is why they are here rather than folded into 5.
    private val NAMES = mapOf(
        0 to "UNKNOWN",
        1 to "MEDIA",
        2 to "VOICE_COMMUNICATION",
        3 to "VOICE_COMMUNICATION_SIGNALLING",
        4 to "ALARM",
        5 to "NOTIFICATION",
        6 to "NOTIFICATION_RINGTONE",
        7 to "NOTIFICATION_COMMUNICATION_REQUEST",
        8 to "NOTIFICATION_COMMUNICATION_INSTANT",
        9 to "NOTIFICATION_COMMUNICATION_DELAYED",
        10 to "NOTIFICATION_EVENT",
        11 to "ASSISTANCE_ACCESSIBILITY",
        12 to "ASSISTANCE_NAVIGATION_GUIDANCE",
        13 to "ASSISTANCE_SONIFICATION",
        14 to "GAME",
        15 to "VIRTUAL_SOURCE",
        16 to "ASSISTANT",
    )

    /** Name for a usage, or a printable placeholder. Never throws, never invents a name. */
    fun name(usage: Int): String = NAMES[usage] ?: "UNRECOGNISED($usage)"

    /**
     * Fold a usage into one of the five channels.
     *
     * **Returns null rather than a default.** A usage this table has not seen is a usage from an
     * Android newer than this build, and the correct answer to "which of my five meters should move"
     * is *none of them*, not *the nearest one*. Test 1 asserts that on usage 99.
     */
    fun match(usage: Int): MaUsageMatch {
        val channel: MaChannel?
        val certain: Boolean
        when (usage) {
            1, 14 -> { channel = MaChannel.MEDIA; certain = true }
            2, 3 -> { channel = MaChannel.CALL; certain = true }
            4 -> { channel = MaChannel.ALARM; certain = true }
            5, 7, 8, 9, 10 -> { channel = MaChannel.NOTIFICATION; certain = true }
            6 -> { channel = MaChannel.RING; certain = true }
            // Routed to the music stream by platform convention, not by the app saying so. Drawn,
            // but drawn as a guess.
            0, 12, 16 -> { channel = MaChannel.MEDIA; certain = false }
            // Accessibility and sonification have their own streams and are none of the five.
            // VIRTUAL_SOURCE is not audible at all.
            11, 13, 15 -> { channel = null; certain = true }
            else -> { channel = null; certain = false }
        }
        return MaUsageMatch(usage, name(usage), channel, certain)
    }
}
