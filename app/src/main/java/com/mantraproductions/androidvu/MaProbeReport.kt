/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 * Licensed under the Apache License, Version 2.0
 */

package com.mantraproductions.androidvu

/**
 * THE REPORT, AS A PURE FUNCTION.
 *
 * Everything the probe reads goes into [MaSnapshot], which holds no Android types at all, and comes
 * out of [MaProbeReport.render] as text. So the whole of the formatting — the part that will
 * actually be read, and the part where an empty list silently renders as a confident nothing — is
 * Test 1 material, running on a build machine with no phone attached.
 *
 * ### Forty-four columns, and why that is a rule and not a preference
 *
 * `design-language.md`: **NOTHING GOES OFF THE SCREEN.** At 14sp monospace on a 390dp phone about
 * forty-four characters fit. Every line this file emits is built to that width, and [widest] is
 * asserted against it in Test 1, because a report that clips is a report he has to photograph to
 * show me.
 */
data class MaStreamReading(
    val channel: MaChannel,
    val volume: Int,
    val maxVolume: Int,
    val minVolume: Int,
    /** Null where the call did not answer. Never guessed, never defaulted to zero. */
    val muted: Boolean?,
)

data class MaConfigReading(
    val usage: Int,
    val contentType: Int,
    /** True when this configuration belongs to AndroidVU itself — see the test-tone experiment. */
    val isOwn: Boolean,
)

data class MaSnapshot(
    val device: String,
    val androidRelease: String,
    val sdkInt: Int,
    val appVersion: String,
    val takenAt: String,
    val streams: List<MaStreamReading>,
    val mode: Int,
    val ringerMode: Int,
    val musicActive: Boolean,
    /** Null when the call threw. An empty list means it answered, with nothing. Different facts. */
    val configs: List<MaConfigReading>?,
    val visualiser: String,
)

object MaProbeReport {

    const val WIDTH = 44

    private val MODES = mapOf(
        0 to "NORMAL", 1 to "RINGTONE", 2 to "IN_CALL", 3 to "IN_COMMUNICATION", 4 to "CALL_SCREENING",
    )
    private val RINGER = mapOf(0 to "SILENT", 1 to "VIBRATE", 2 to "NORMAL")
    private val CONTENT = mapOf(
        0 to "UNKNOWN", 1 to "SPEECH", 2 to "MUSIC", 3 to "MOVIE", 4 to "SONIFICATION",
    )

    /** Longest line in a rendered report. Test 1 asserts this stays inside [WIDTH]. */
    fun widest(report: String): Int = report.lines().maxOfOrNull { it.length } ?: 0

    /**
     * The last defence against a clipped line, and nothing more than that.
     *
     * Every line below is BUILT to fit; this only catches a device name longer than any phone I
     * have seen. `design-language.md` is explicit that narrowing columns moves clipping rather than
     * removing it, so a line that would genuinely not fit is STACKED at the point it is built, not
     * squeezed here. Test 1 asserts that a realistic report never needs this function at all.
     */
    fun fit(t: String): String = if (t.length <= WIDTH) t else t.take(WIDTH - 1) + "\u2026"

    fun render(s: MaSnapshot): String {
        val b = StringBuilder()
        fun line(t: String = "") = b.append(fit(t)).append('\n')
        fun rule(t: String) = line("-- $t")

        line("ANDROIDVU PROBE v${s.appVersion}")
        line(s.device)
        line("Android ${s.androidRelease} (API ${s.sdkInt})")
        line(s.takenAt)
        line()

        rule("VOLUME SETTINGS  a setting, not a level")
        for (r in s.streams) {
            val mute = when (r.muted) {
                null -> "mute ?"
                true -> "MUTED"
                false -> "mute no"
            }
            // Padded to fixed columns so five rows read as a column, not as five sentences.
            line(
                r.channel.letter + " " + r.channel.title.padEnd(13) +
                    (r.volume.toString() + "/" + r.maxVolume).padEnd(7) +
                    "min " + r.minVolume.toString().padEnd(3) + mute
            )
        }
        line()

        rule("MODE")
        line("audio mode    " + (MODES[s.mode] ?: "?") + " (" + s.mode + ")")
        line("ringer mode   " + (RINGER[s.ringerMode] ?: "?") + " (" + s.ringerMode + ")")
        line("isMusicActive " + s.musicActive)
        line()

        rule("ACTIVE PLAYBACK  the decisive one")
        if (s.configs == null) {
            // NOT the same fact as an empty list, and not rendered as one. Some OEM builds throw
            // here instead of returning nothing, and flattening the two together would answer the
            // question this whole build exists to ask with a shrug.
            line("the call itself failed. No reading.")
        } else {
            // The count is printed before anything is said about it. delivery-gate.md: a check that
            // found nothing and a check that did not run look identical from outside.
            line("configurations returned: " + s.configs.size)
            if (s.configs.isEmpty()) {
                line("nothing is playing, or nothing is")
                line("visible to us. Play something and")
                line("press REFRESH before believing it.")
            }
            for ((i, c) in s.configs.withIndex()) {
                val m = MaUsage.match(c.usage)
                val owner = if (c.isOwn) "  [ours]" else ""
                // STACKED, not squeezed. NOTIFICATION_COMMUNICATION_REQUEST is thirty-four
                // characters on its own; put it on the same line as its index and its owner tag and
                // the row is off the screen on his phone, which is the exact failure
                // design-language.md was rewritten for on 25.8.2026.
                line("[" + (i + 1) + "] usage " + c.usage + owner)
                line("  " + m.usageName)
                val dest = when {
                    m.channel == null -> "  -> no channel of the five"
                    m.certain -> "  -> " + m.channel.letter + "  certain"
                    else -> "  -> " + m.channel.letter + "  INFERRED, not stated"
                }
                line(dest)
                line("  content " + c.contentType + " " + (CONTENT[c.contentType] ?: "?"))
            }
            line()
            line(verdict(s.configs))
        }
        line()

        rule("VISUALIZER  session 0, the whole mix")
        line(s.visualiser)
        line()
        line("Press COPY and paste this back.")
        return b.toString()
    }

    /**
     * THE ONE SENTENCE THIS WHOLE BUILD EXISTS TO PRODUCE.
     *
     * Android sanitises another app's `AudioAttributes` before handing them to a listener without
     * MODIFY_AUDIO_ROUTING. What it strips is documented loosely and differs by version, and the
     * whole design forks on whether `usage` survives: if it does, five honest channels are free; if
     * it does not, four of the five can never say more than "the volume is set to this".
     *
     * The evidence is read here rather than reasoned about: a foreign configuration reporting a
     * usage other than UNKNOWN is usage surviving. Nothing is concluded from our own tone, because
     * an app always sees its own attributes in full and that would prove only that the API exists.
     */
    fun verdict(configs: List<MaConfigReading>): String {
        val foreign = configs.filter { !it.isOwn }
        if (foreign.isEmpty()) return "VERDICT: no foreign audio to judge."
        val named = foreign.count { it.usage != 0 }
        return "VERDICT: usage readable on " + named + " of " +
            foreign.size + " foreign."
    }
}
