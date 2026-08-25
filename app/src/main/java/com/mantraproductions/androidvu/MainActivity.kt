/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 * Licensed under the Apache License, Version 2.0
 */

package com.mantraproductions.androidvu

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ANDROIDVU v1 — THE PROBE, AND ONLY THE PROBE.
 *
 * ### Why the first version of a meter app draws no meters
 *
 * The briefing is explicit about the order: *"MEASURE FIRST on my phone. Write a tiny probe that
 * reports, per stream, what can actually be read. Tell me the results before you design around
 * them."* That is `four-tests.md` Test 1 applied to a platform rather than to a function — a real
 * test of the mechanism before the build, not a claim that it works.
 *
 * There is a specific thing being avoided here. Android has no `getStreamLevel()`. If I draw five
 * meters first and measure afterwards, four of them will be animated by something — and the only
 * things available to animate them with are the volume SETTING and a boolean. That is the
 * convincing lie the briefing rules out, and it is much harder to take away once it is on screen
 * than it is to never draw.
 *
 * ### What is being asked of the phone
 *
 * One question above all others: does `AudioManager.getActivePlaybackConfigurations()` report a
 * usable `usage` for OTHER apps' audio? Android sanitises those attributes for any app without
 * MODIFY_AUDIO_ROUTING, which is a signature permission this app will never hold. If usage
 * survives, all five channels get an honest active/idle and the design is five bars with real
 * state. If it does not, four channels can only ever show a volume setting, and that is a very
 * different app.
 *
 * ### What this screen is NOT
 *
 * Not the overlay, not the tile, not the meters. No SYSTEM_ALERT_WINDOW is requested and no window
 * is added. Nothing here is a draft of the real interface; it is an instrument, and it says so.
 */
class MainActivity : Activity() {

    private lateinit var audio: AudioManager
    private lateinit var out: TextView
    private lateinit var liveButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private var live = false
    private var ticks = 0

    /** Set only while our own test tone is sounding. See [readConfigs]. */
    private var ownToneRunning = false
    private var visualiserResult = "not run"

    // A screen refreshed five times a second is a screen he cannot read, and the numbers here move
    // on the scale of a phone ringing rather than of a waveform. 200ms.
    private val tickMs = 200L

    // EVERY LOOP HAS A BOUND. delivery-gate.md G5, and JPL rule 2 behind it. The live monitor is a
    // self-rescheduling Runnable, which is a while(true) wearing a Handler, so it gets a ceiling: at
    // 200ms this stops itself after a minute. A probe left ticking in a pocket is a flat battery and
    // a bug report about the wrong thing.
    private val maxTicks = 300

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            ticks++
            if (live && ticks < maxTicks) {
                handler.postDelayed(this, tickMs)
            } else if (live) {
                setLive(false)
                toast("Live monitor stopped after 60s")
            }
        }
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            val p = dp(12)
            setPadding(p, p, p, p)
        }

        // NOTHING APPEARS, NOTHING DISAPPEARS. design-language.md §1. Every control is built in this
        // one pass and is present from the first frame; the live button changes its ink, never its
        // existence, so the row never reflows and nothing below it slides.
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("REFRESH") { refresh() })
        liveButton = button("LIVE") { setLive(!live) }
        row.addView(liveButton)
        row.addView(button("TONE") { playTestTone() })
        row.addView(button("VISUALIZER") { runVisualiser() })
        row.addView(button("COPY") { copyOut() })
        root.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            }
        )

        out = TextView(this).apply {
            setTextColor(INK)
            // Monospace, because the report is a column of numbers and a proportional font turns a
            // column into a drift. 14sp is the size the 44-character line was measured at.
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 14f
            setTextIsSelectable(true)
            val p = dp(8)
            setPadding(p, p, p, p)
        }
        root.addView(
            ScrollView(this).apply {
                addView(out)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                ).apply { weight = 1f }
            }
        )

        setContentView(root)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        // The ticker holds this Activity. Left running behind a lock screen it is a leak with a
        // schedule. G6's handle check, applied before there is anything to soak.
        setLive(false)
    }

    // ------------------------------------------------------------------ reading

    private fun refresh() {
        out.text = MaProbeReport.render(snapshot())
    }

    private fun snapshot(): MaSnapshot {
        val streams = MaChannel.values().map { c ->
            MaStreamReading(
                channel = c,
                volume = safeInt { audio.getStreamVolume(c.streamType) },
                maxVolume = safeInt { audio.getStreamMaxVolume(c.streamType) },
                // getStreamMinVolume arrived in API 28. Below that the answer is not zero, it is
                // unknown, and reporting a guessed zero is the same class of mistake as a guessed
                // level. -1 renders as what it is.
                minVolume = if (Build.VERSION.SDK_INT >= 28) safeInt { audio.getStreamMinVolume(c.streamType) } else -1,
                muted = runCatching { audio.isStreamMute(c.streamType) }.getOrNull(),
            )
        }
        return MaSnapshot(
            device = (Build.MANUFACTURER + " " + Build.MODEL).trim(),
            androidRelease = Build.VERSION.RELEASE ?: "?",
            sdkInt = Build.VERSION.SDK_INT,
            appVersion = BuildConfig.VERSION_NAME,
            takenAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK).format(Date()),
            streams = streams,
            mode = safeInt { audio.mode },
            ringerMode = safeInt { audio.ringerMode },
            musicActive = runCatching { audio.isMusicActive }.getOrDefault(false),
            configs = readConfigs(),
            visualiser = visualiserResult,
        )
    }

    /**
     * The decisive reading.
     *
     * Returns null when the call itself failed — which is not the same fact as an empty list, and
     * the report says so differently. Some OEM builds throw here rather than returning nothing, and
     * a catch that flattened both into "no audio" would answer the one question this build exists
     * to ask with a shrug.
     *
     * **On `isOwn`, and its honest limits.** `AudioPlaybackConfiguration` exposes no owning package
     * or uid to an ordinary app — there is no getter for it. So ours is attributed by the only
     * means available: while our own test tone is sounding, a configuration carrying the alarm usage
     * is assumed to be it. If a real alarm fires in the same two seconds the attribution is wrong.
     * That is recorded here rather than hidden, and it is why the verdict counts foreign
     * configurations rather than trusting the flag in the other direction.
     */
    private fun readConfigs(): List<MaConfigReading>? = try {
        audio.activePlaybackConfigurations.map { c ->
            val a = c.audioAttributes
            MaConfigReading(
                usage = a.usage,
                contentType = a.contentType,
                isOwn = ownToneRunning && a.usage == 4,
            )
        }
    } catch (t: Throwable) {
        null
    }

    private fun safeInt(block: () -> Int): Int = runCatching(block).getOrDefault(-1)

    // ------------------------------------------------------------------ the two experiments

    /**
     * THE CONTROLLED EXPERIMENT.
     *
     * Waiting for the phone to ring in order to find out whether ringing is visible is not a test,
     * it is a vigil. This makes a sound on a channel of our choosing, on demand, so the API can be
     * exercised without arranging the world first.
     *
     * What it proves: that `getActivePlaybackConfigurations` is wired at all, and that the alarm
     * usage arrives intact for an app reading its OWN audio. What it emphatically does NOT prove is
     * the sanitisation question, because an app always sees its own attributes in full. For that,
     * play music in another app and press REFRESH — the verdict line counts only foreign entries.
     *
     * Quiet on purpose, and short: ToneGenerator at 40 of 100 on the alarm stream, 1.5 seconds.
     */
    private fun playTestTone() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 40)
            ownToneRunning = true
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 1500)
            // Read while it is still sounding — a snapshot taken after it stops shows nothing, which
            // is the shape of a test that passes for the wrong reason.
            handler.postDelayed({ refresh() }, 400)
            handler.postDelayed({
                ownToneRunning = false
                runCatching { tone.release() }
                refresh()
            }, 2000)
            toast("Alarm-usage tone, 1.5s")
        } catch (t: Throwable) {
            ownToneRunning = false
            toast("Tone failed: " + t.javaClass.simpleName)
        }
    }

    /**
     * THE VISUALIZER, ON SESSION 0.
     *
     * The briefing already expects this to be blocked or empty on a modern Android, and it is
     * measured anyway, because "expected to be blocked" and "measured as blocked" are different
     * kinds of sentence and only one of them can be written into a design.
     *
     * It is worth the microphone permission for one reason: session 0 is the whole OUTPUT MIX, not
     * one stream. If it returned real samples it would give a genuine level — one level, for
     * whatever is currently sounding — and combined with the active-playback list saying WHICH
     * single channel is sounding, that is a real meter for the common case of one thing at a time.
     * That is the only route to honest motion that does not go through MediaProjection, so it gets
     * measured rather than assumed.
     *
     * Reported as a peak deviation from the 128 midpoint over ten samples. A flat 0 means the buffer
     * came back silent, which is the documented modern behaviour and is a RESULT, not a failure.
     */
    private fun runVisualiser() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            toast("Grant the microphone, then press VISUALIZER again")
            return
        }
        visualiserResult = "running..."
        refresh()
        Thread {
            val result = measureVisualiser()
            handler.post {
                visualiserResult = result
                refresh()
            }
        }.start()
    }

    private fun measureVisualiser(): String {
        var v: Visualizer? = null
        return try {
            v = Visualizer(0)
            v.captureSize = Visualizer.getCaptureSizeRange()[0]
            v.enabled = true
            val buf = ByteArray(v.captureSize)
            var peak = 0
            var reads = 0
            // BOUNDED: ten reads, fixed. Not "until it sees something", which never ends on a
            // silenced buffer and is the exact NEVER ANSWERS shape four-tests.md Test 3 names.
            for (i in 0 until 10) {
                val status = v.getWaveForm(buf)
                if (status == Visualizer.SUCCESS) {
                    reads++
                    for (b in buf) {
                        val d = kotlin.math.abs((b.toInt() and 0xFF) - 128)
                        if (d > peak) peak = d
                    }
                }
                Thread.sleep(50)
            }
            v.enabled = false
            if (reads == 0) "no successful reads of 10" else "peak $peak of 127, $reads/10 reads"
        } catch (t: Throwable) {
            "blocked: " + t.javaClass.simpleName
        } finally {
            runCatching { v?.release() }
        }
    }

    // ------------------------------------------------------------------ plumbing

    private fun setLive(on: Boolean) {
        if (on == live) return
        live = on
        ticks = 0
        // COLOUR CARRIES STATE, and it is the only channel that does. design-language.md §3. Amber
        // is the lit thing everywhere in this account, so it is amber here too.
        liveButton.setTextColor(if (on) AMBER else INK)
        handler.removeCallbacks(ticker)
        if (on) handler.post(ticker)
    }

    private fun copyOut() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("AndroidVU probe", out.text))
        toast("Report copied")
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        setTextColor(INK)
        setBackgroundColor(SLATE)
        textSize = 13f
        gravity = Gravity.CENTER
        val m = dp(4)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(m, 0, m, 0) }
        setOnClickListener { onClick() }
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private companion object {
        // The house palette, from design-language.md §3.
        val BG = Color.parseColor("#0B0D10")
        val INK = Color.parseColor("#F2DDB4")
        val AMBER = Color.parseColor("#F59E0B")
        val SLATE = Color.parseColor("#23303D")
    }
}
