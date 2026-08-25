# AndroidVU — NEXT_DEFAULTS

**What was decided, why, and what was tried and rejected. Newest at the top.**

---

## v1 — 25.8.2026 — the probe

### Decided: v1 measures and draws nothing

The briefing ordered it and the reason holds independently. Android exposes no per-stream output
level. If five meters are drawn first, four of them have to be animated by the volume *setting* and a
boolean, and a bar that moves because the setting changed is a convincing lie about a level. Once it
is on screen it is far harder to remove than to never draw.

**Rejected: build the five meters and animate the four honest ones "later".** That is the same
decision with the expensive half postponed, and the postponed half is the one that would have been
argued about.

### Decided: the probe is v1 of AndroidVU, not a separate throwaway repo

One repository, one version line. The probe screen stays as a diagnostics page in later versions — it
is the thing that will answer "why is R not moving" in six months on a different Android.

**Rejected: `ANDROIDVU_PROBE` as its own repo.** Two repositories for one app is two places to drift,
and the manifest's Lego rule is explicit that one repository beats two copies with a rule about
keeping them in step.

### Decided: `MaChannels.kt` writes Android constants out as bare integers

`AudioManager.STREAM_MUSIC` and `AudioAttributes.USAGE_MEDIA` are unavailable to a plain JVM unit
test — there is no `android.jar`, so every one of them reads back as **0**. A mapping table built
from them collapses into "everything is channel zero" while still compiling and still passing.

Writing the numbers out is what makes Test 1 possible at all, and the numbers are stable platform ABI
that cannot change without breaking every app ever shipped. The cost is that the file must be read
against the SDK if a new usage is added; that cost is named in the file's own header.

### Decided: an unrecognised usage moves NO meter

`else -> null`, not `else -> MEDIA`. Unknown audio usually *is* media, which is exactly why the
tempting default is dangerous: it produces a meter dancing to a channel it has not identified. Test 1
asserts it on usage 99, and CI breaks this very line on every build to prove the test is watching.

### Decided: every check is made to fail on purpose AND pass on purpose, in CI, on every build

Both of this month's lessons applied from build one.

- **G3** runs the suite green, then `sed`s the refusal line into `else -> MEDIA`, requires the suite
  to go **red**, restores the file with `git checkout`, and requires green again. If the `sed` no
  longer matches the source the step fails loudly rather than silently proving nothing — that failure
  mode is the whole point.
- **G2** builds a key-shaped canary *from parts at run time* and requires the scanner to find exactly
  one hit before scanning the real artefact. Assembled from parts because a stored key-shaped literal
  makes the scanner go red on every future delivery, and a check that cries wolf gets skimmed
  (§15a).
- The `|| true` on G2's grep is load-bearing and carries the comment saying so, because the recorded
  failure was a secret scanner that **failed the build because the artefact was clean**.

A test that has never been seen fail is a rumour. These are seen to fail on every build, not once on
the day they were written.

**Rejected: proving it by pushing a deliberately broken commit and then a fix.** Two CI runs, a
messy history, and it proves the check worked *that day*. A self-contained proof runs forever.

**Not done, and it is the honest gap: the false-positive rate of these checks was not measured across
the codebase first.** §14 says measure a new check before adding it — one produced 434 false
positives and had to be deleted. Here the codebase is three files and the checks are counted greps,
so the risk is small, but it is a rule that was not followed rather than one that did not apply.

### Decided: R8 off for the probe, on from the first version that draws a meter

`delivery-gate.md` §9.3 is right that a benchmark on a debug build measures a program you are not
shipping — but this build is not measuring itself, it is measuring **Android**, and a shrinker that
renames and inlines is one more variable between the reading and the truth.

### Decided: no AndroidX, no Compose, no Material library — one test dependency

The screen is five buttons and a TextView on `android.app.Activity` with the platform Material theme,
which minSdk 26 already has. `MaRecordingLine.kt` already argues that its meter is drawn with a
Canvas rather than composed because an overlay window has no lifecycle owner for Compose to attach
to, so the real meters will not need Compose either.

### Decided: the report is built to 44 columns and it is measured, not reasoned about

`design-language.md`, **NOTHING GOES OFF THE SCREEN**, stated 25.8.2026. At 14sp monospace on a 390dp
phone about forty-four characters fit. Test 1 renders the worst realistic input — the thirty-four
character `NOTIFICATION_COMMUNICATION_REQUEST`, an unrecognised usage, a long device name — and fails
if any line needs truncating.

The long usage name is **stacked** onto its own line rather than squeezed beside its index, because
§10 is explicit that narrowing columns does not remove clipping, it moves it to a different word.

`fit()` exists only as a last defence against a device name longer than any phone I have seen, and
Test 1 asserts a realistic report never reaches it.

### Decided: the version lives in exactly one file

`gradle.properties`'s `appVersion` is read by `build.gradle.kts` for `versionCode`, `versionName` and
therefore the line printed at the top of every report, and by the workflow for the APK filename and
the release tag. Bumping one line moves all four. `versioning.md` §3 records the trap: TTT mini says
build 127 in every document while the phone's app list says 1.0.

**Rejected: renaming the APK inside Gradle via `applicationVariants` or `VariantOutputImpl`.** Both
are internal AGP API in 8.5 and both break on an AGP upgrade. The workflow reads the same one
property, so no hand types the number and §6's requirement is met without an unstable API.

### Decided: keystore generated fresh, 4096-bit RSA, held only as an encrypted repository secret

Every build is signed identically so a new version installs straight over the old one. Same pattern as
`TTT_MINI`, different key — different app, different id, and neither can ever overwrite the other.

**The cost, stated plainly: the key exists only inside GitHub's secret store.** It is write-only —
nobody, including Marko, can read it back out. If the repository or the secret is lost, no future
build can ever upgrade an installed AndroidVU and the only path is a new applicationId. If that
matters, export it deliberately and store it elsewhere; that is a separate act from publishing.
Its SHA-256 fingerprint is printed in every CI log, which is the record that all builds share a hand.

### Decided: `isOwn` is attributed by assumption, and the assumption is written down

`AudioPlaybackConfiguration` exposes no owning package or uid to an ordinary app — there is no getter
for it. So while our own test tone sounds, a configuration carrying the alarm usage is *assumed* to be
it. If a real alarm fires in the same two seconds the attribution is wrong.

This is why the verdict counts **foreign** configurations rather than trusting the flag the other
way: a wrong `isOwn` can only make the verdict more conservative, never more confident.

### Decided: `MediaProjection` media capture NOT built in v1

Real media level needs `AudioPlaybackCaptureConfiguration`, which on Android 14 means a
`mediaProjection` foreground service, a permanent screencast notification, and **fresh consent every
session** — it cannot be silently resumed. Apps that opt out go silent inside it while the meter reads
zero.

It is a large amount of code that cannot be tested at all from here, and whether it is needed depends
on the verdict this very build produces. Shipping it blind alongside the thing that decides whether
it is needed is backwards.

### Decided: `minVolume` reports `-1` below API 28 rather than 0

`getStreamMinVolume` arrived in API 28. Below that the answer is not zero, it is unknown, and a
guessed zero is the same class of mistake as a guessed level.

### Decided: a failed call and an empty result render differently

`getActivePlaybackConfigurations()` throwing is not the same fact as it answering with nothing, and
some OEM builds throw. Flattening both into "no audio" would answer the question this build exists to
ask with a shrug. Test 1 covers both branches.

### Decided: the live monitor stops itself after 60 seconds

A self-rescheduling `Runnable` is a `while (true)` wearing a `Handler`. G5 and JPL rule 2 want a bound
a reader can see, so it has a literal `maxTicks = 300` at 200 ms. A probe left ticking in a pocket is
a flat battery and then a bug report about the wrong thing. It also stops in `onPause`, because the
ticker holds the Activity.

### Decided: three bars in the icon, not five

`app-icon.md` §2 arithmetic: 108 viewport, 72 mask, 42 symbol box at 33…75 on both axes, so
42 / 72 = **0.583**, inside the 0.55–0.60 that well-drawn icons land in. Gold `#E8B15C` on `#12161E`
is 9.38:1.

**Rejected: five bars, one per channel.** §4 records a 2×2 grid abandoned for `GROQ_EXPLORER` because
at 48 px four shapes are four grey smudges. Five hairlines at launcher size is a smear. Three says
*meter* and survives 48 px.

**Not verified: the icon was not rendered and measured.** The ratio is arithmetic on the path data,
which app-icon.md §3 explicitly says is not good enough — it asks for the bounding box of the
rendered image. That check needs a render and is listed under NOT TESTED.

### Decided: private repository

`MANIFEST_INTRO` is explicit that the other direction is fine and this one is not reversible without
asking. Release download links still work in a browser signed in to GitHub, which is how every other
APK in this account is delivered.
