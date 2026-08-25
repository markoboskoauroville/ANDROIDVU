# AndroidVU — HANDOFF

**Read `NEXT_DEFAULTS.md` beside this for why each decision was made, including what was tried and
rejected. This file is only where things stand.**

Marko Boško · Mantra Productions · Zagreb

---

## What the app is meant to become

Five hairline VU meters, **one device pixel wide**, drawn vertically as a system overlay on top of
whatever is on screen, each showing one Android audio channel:

| Letter | Channel | Stream | What it is |
|---|---|---|---|
| C | Call | `STREAM_VOICE_CALL` (0) | the other person's voice |
| M | Media | `STREAM_MUSIC` (3) | music, video, podcasts |
| R | Ring | `STREAM_RING` (2) | the ringtone |
| N | Notification | `STREAM_NOTIFICATION` (5) | message and app alerts |
| A | Alarm | `STREAM_ALARM` (4) | alarms and timers |

The first sits on the left edge, the last on the right edge, the middle three spread equally between.
Controls: a master on/off, five tick boxes, an editable X per meter, and a Quick Settings tile.

## Where it actually is: v1 is a PROBE and draws no meters

**Android has no `getStreamLevel()`.** There is no per-stream output level for an ordinary app. Baba's
own instruction was to measure before designing:

> *"MEASURE FIRST on my phone. Write a tiny probe that reports, per stream, what can actually be
> read. Tell me the results before you design around them."*

So v1 is that instrument and nothing else. No overlay window is created, `SYSTEM_ALERT_WINDOW` is not
requested, and no meter is drawn. Drawing five meters before measuring would mean animating four of
them from a volume setting and a boolean, which is exactly the invented motion the briefing forbids —
and much harder to take off the screen than to never put there.

## The one question v1 exists to answer

**Does `AudioManager.getActivePlaybackConfigurations()` report a usable `usage` for OTHER apps?**

It is the only public call that says *which* channel is sounding right now, and Android sanitises
another app's `AudioAttributes` before handing them to any app without `MODIFY_AUDIO_ROUTING`, which
is a signature permission this app will never hold.

- **If usage survives** — all five channels get an honest active/idle, and the design is five bars
  with real state, no `MediaProjection`, no consent theatre.
- **If it does not** — four of the five can never show more than a volume setting, and the app is a
  different thing.

The report's `VERDICT:` line answers exactly this, counting only foreign configurations.

## What the probe reads

1. **Volume settings** — `getStreamVolume` / `getStreamMaxVolume` / `getStreamMinVolume` (API 28+,
   `-1` below that, never a guessed zero) / `isStreamMute`, for all five.
2. **Mode** — `getMode()`, `getRingerMode()`, `isMusicActive()`.
3. **Active playback** — every configuration, its raw usage and content type, folded to a channel
   letter and marked `certain` or `INFERRED`.
4. **TONE** — plays a quiet 1.5 s alarm-usage tone so the API can be exercised on demand rather than
   waiting for the phone to ring. Proves the call is wired; proves **nothing** about sanitisation,
   because an app always sees its own attributes in full.
5. **VISUALIZER** — `Visualizer` on session 0, behind the only permission in the app. Expected to be
   blocked or silent on modern Android, measured anyway. Session 0 is the whole output mix, so if it
   returned real samples it would be one honest level — and combined with the active-playback list
   naming the single channel sounding, that is a real meter for the common case, and the only route
   to honest motion that does not go through `MediaProjection`.

## The fork waiting on Baba

Real media level needs `MediaProjection` + `AudioPlaybackCaptureConfiguration`. On Android 14 that is
a `mediaProjection` foreground service, a permanent screencast notification, and **fresh consent
every session** — it cannot be silently resumed. Apps that opt out of capture go silent inside it
while the meter reads zero, which is the wrong degradation.

**Not built, deliberately**, because it is expensive, untestable from a sandbox, and possibly
unnecessary depending on what the verdict line says.

## Where things live

```
app/src/main/java/.../MaChannels.kt      the five channels, and usage -> channel. NO Android
                                          imports, so Test 1 can run on the build machine
app/src/main/java/.../MaProbeReport.kt    snapshot -> 44-column text. Pure
app/src/main/java/.../MainActivity.kt     the only screen. Reads the audio system
app/src/test/java/.../MaProbeTest.kt      Test 1
.github/workflows/build.yml               G1, G2, G3, G5, G7 + release
```

The VU meter itself is **not written yet**. When it is, it is lifted from
`TTT_MINI/app/src/main/kotlin/dev/patrickgold/florisboard/dictate/overlay/MaRecordingLine.kt` —
`MaVuView` already supports vertical, with its 40 ms redraw, fast-attack/slow-release asymmetry,
0.6 dB peak decay and `FLOOR_DB = -54f`. The window flags come from the same file, with
`TYPE_ACCESSIBILITY_OVERLAY` swapped for `TYPE_APPLICATION_OVERLAY` since AndroidVU has no reason to
demand accessibility.

## What Baba does next

Install, open, then in any order: play music, let a notification arrive, start a call, fire an alarm
— pressing **REFRESH** (or **LIVE**) each time. Then **COPY** and paste the report back.

The reading that matters most is with **another app playing audio**, because that is the only case
where sanitisation applies.
