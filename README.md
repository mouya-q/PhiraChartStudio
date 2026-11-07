# Phira Chart Studio

**Phira Chart Studio** is an offline-first Android tool for turning a locally selected audio track into a first-pass Phira chart package. The app is built with Kotlin, Jetpack Compose and MIUIX. Music decoding and analysis stay on the device; an optional OpenAI-compatible API can suggest bounded arrangement parameters, but the offline generator remains the source of truth.

> **Build / release status (2026-11-07):** the Kotlin engine and its local regression runners have been compiled and executed in the current environment. The Android Gradle build, APK installation and import into the real Phira app have **not** been run here because this container has no Android SDK or preinstalled Gradle and cannot download the 134 MiB Gradle distribution. Do not treat this source snapshot as a signed or device-verified release. See [BUILD_STATUS.md](BUILD_STATUS.md).

## What is implemented

- Import one local audio track through Android's Storage Access Framework; persist the read grant where the provider allows it.
- Decode audio in short output buffers with Android `MediaExtractor` / `MediaCodec`; convert PCM to mono features without holding the entire decoded song in memory.
- Estimate tempo and beat phase; extract spectral-flux onsets, energy envelope and coarse energy sections.
- Generate deterministic EZ / HD / IN chart drafts offline, with distinct grid resolutions, minimum spacing, densities and note-kind heuristics.
- Calibrate BPM in 0.1 BPM steps and beat offset in 10 ms steps; regenerate all difficulties from the same analysis.
- Preview the generated lanes while the imported audio plays; seek, pause and switch difficulty.
- Export a `.pez` ZIP package with `info.yml`, `chart.json`, generated `background.png` and the original supported audio file. The exporter streams the audio in 64 KiB blocks, reopens the resulting archive and validates its internal file set and RPE subset.
- Optionally request constrained arrangement hints from an OpenAI-compatible HTTPS API. The request contains derived timing / energy features only, never the audio bytes. Invalid, unavailable or timed-out AI responses fall back to the offline generator.
- Store the last generated project and settings locally. The API token is encrypted with AES-GCM using an Android Keystore key; no plaintext token preference is written.

## Important scope boundaries

This is an automatic chart-drafting tool, **not** a full replacement for a dedicated Phigros/RPE editor. The current preview is a lightweight lane/timing review surface; it is not Phira's game renderer or judgement engine. There is no per-note drag editor, custom line-event editor, collaborative workflow or chart publishing client. Generated charts are drafts and should be reviewed and play-tested before publication.

The built-in compatibility validator checks the generated RPE subset and ZIP layout against the public Phira client data model. It does not execute Phira's Rust parser. A real import/play test in the current stable Phira app remains a release gate.

## Project layout

```text
PhiraChartStudio/
├── app/                       # Android application, Miuix UI and platform adapters
│   └── src/main/java/com/mouya/phirachartstudio/
│       ├── audio/              # MediaExtractor / MediaCodec decoder
│       ├── ai/                 # Optional OpenAI-compatible client
│       ├── data/               # Keystore, project persistence and .pez exporter
│       ├── ui/                 # Studio, preview and settings screens
│       ├── MainActivity.kt
│       └── StudioViewModel.kt
├── engine/                    # Platform-independent Kotlin analysis / generation core
│   └── src/main/kotlin/.../engine/
│       ├── Radix2Fft.kt
│       ├── StreamingBeatAnalyzer.kt
│       ├── ChartGenerator.kt
│       ├── RpeSerializer.kt
│       ├── ChartValidator.kt
│       └── StrictJson.kt
├── docs/                      # Architecture, ADRs, user / privacy / build guides
├── samples/                   # Synthetic, copyright-free example .pez package
└── .github/workflows/         # Android CI build and test workflow
```

## Quick start

Requirements: Android Studio with a JDK 17 runtime, Android SDK Platform 37, Android Build Tools 36.0.0, and network access for the first Gradle/Maven dependency resolution.

```bash
# Windows
.\gradlew.bat :engine:test :app:testDebugUnitTest :app:assembleDebug

# Linux / macOS
./gradlew :engine:test :app:testDebugUnitTest :app:assembleDebug
```

The Gradle 9.6.0 bootstrap script verifies the distribution against the official SHA-256 before executing it. Gradle 9.6.0 and AGP 9.5.0 are a matched pair; AGP 9.5 supports API 37 and requires Gradle 9.6.0 / JDK 17. Kotlin 2.4.20 is compatible with Gradle 9.6.0. See [Build guide](docs/BUILD.md).

APK output, after a successful Android build:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## First-use workflow

1. Open **Studio** and tap **Import audio**.
2. Select a track that the device's media codecs can decode. Keep the file accessible to the system document provider.
3. Let the offline analysis finish. Cancel is available during decoding / feature extraction.
4. Review BPM and beat offset. Use the small adjustment controls if the detected grid does not follow the music.
5. Select EZ, HD or IN and open **Preview**. Listen, seek and compare the three chart densities.
6. Tap **Export .pez** and choose a destination.
7. In Phira, import the exported chart package from local files. Play it in the actual game and inspect timing, note readability and difficulty before sharing or publishing.

See [User manual](docs/USER_MANUAL.md) and [FAQ](docs/FAQ.md).

## Privacy and AI

Audio is selected using Android's system document picker and is not copied into the app's project store. The app persists the URI, title, duration, analysis features and generated chart draft. Internet permission exists only for the optional AI provider. When enabled, only a small, sampled feature summary is sent to the configured HTTPS endpoint. The source audio is never uploaded by this app. Read [Privacy policy](docs/PRIVACY.md) and [AI data-use statement](docs/AI_DATA.md).

## Phira format references

The exporter uses a root-level `info.yml` plus an RPE JSON chart, the music resource and a background image. Its model and supported format identifiers were checked against the official Phira client source and current stable release documentation:

- [Phira releases](https://github.com/TeamFlos/phira/releases)
- [Chart metadata model](https://github.com/TeamFlos/phira/blob/main/prpr/src/info.rs)
- [Format inference and RPE parser dispatch](https://github.com/TeamFlos/phira/blob/main/prpr/src/scene/game.rs)
- [RPE parser](https://github.com/TeamFlos/phira/blob/main/prpr/src/parse/rpe.rs)
- [ZIP import path](https://github.com/TeamFlos/phira/blob/main/phira/src/scene.rs)

The project does not link Phira's GPL code or copy its parser. It generates a constrained RPE subset and validates that subset independently. See [ADR-0002](docs/ADR/0002-phira-rpe-output.md).

## Sample package

`samples/ExampleChart.pez` contains a synthetic 120 BPM click-and-pad WAV, a generated background and a sample RPE chart. It contains no commercial audio. Its ZIP structure and CRC are checked in the repository. It is an example package, not evidence of a successful import into a Phira installation.

## License

The original Phira Chart Studio source in this repository is MIT-licensed; see [LICENSE](LICENSE). Third-party libraries retain their own licenses. See [Third-party notices](docs/THIRD_PARTY_LICENSES.md). The Phira game and its source remain the property of their respective authors and are not bundled here.
