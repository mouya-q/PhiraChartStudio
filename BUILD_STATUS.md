# Build and verification status

**Snapshot date:** 2026-11-07

## Verified in the current authoring container

| Check | Result | Evidence |
|---|---|---|
| Pure Kotlin engine compilation | PASS | `kotlinc` compiled all engine main sources |
| Synthetic engine smoke test | PASS | 120 BPM source, RPE JSON + info.yml validation |
| Tempo sweep | PASS | 55, 60, 75, 90, 100, 110, 120, 128, 140, 150, 160, 180 and 200 BPM fixtures |
| Phase-offset sweep | PASS | 22.05 kHz and 44.1 kHz; 0, 130, 230 and 370 ms target phases; observed circular error approximately 4.8–9.4 ms |
| EZ / HD / IN differentiation | PASS | Synthetic 120 BPM run produced 55 / 110 / 165 notes |
| Sample `.pez` CRC / ZIP structure | PASS | Exact four-entry root, CRC integrity, RPE JSON parse, `info.yml` and `META.song` both reference the embedded `song.wav`; SHA-256 `ebd2d895470210da8c85cf1a4b6748f3debbe91ad8985208817e1f8cbb9c4a1b` |
| Strict JSON negative case | PASS | Public validator rejects malformed RPE JSON |
| Manifest/resource consistency | PASS | Sample `info.yml` and RPE `META.song` both point to the included `song.wav` |

The local engine tests are useful but do not prove Android-specific behavior.

## Verified on GitHub Actions CI

The `Android CI` workflow runs on `ubuntu-latest` with JDK 17, Gradle 9.6.0 and the
runner's preinstalled Android SDK. Verified by run
[`42398514053`](https://github.com/mouya-q/PhiraChartStudio/actions/runs/42398514053)
(commit `7d35eb75a5f799c8159b691da1c1d59276466c6e`) — every step green:

| Check | Result | Evidence |
|---|---|---|
| `:engine:test` via Gradle | PASS | `> Task :engine:test`, followed by `BUILD SUCCESSFUL in 28s` |
| `:app:testDebugUnitTest` | PASS (no sources) | `> Task :app:testDebugUnitTest NO-SOURCE` — the app module declares no JVM unit tests yet |
| `:app:assembleDebug` | PASS | `BUILD SUCCESSFUL in 43s` |
| Debug APK produced | PASS | `app/build/outputs/apk/debug/app-debug.apk`, artifact `phira-chart-studio-debug`, 13,229,035 bytes, SHA-256 of the artifact zip `c66522cf682a408361f0d97281a2b7f80a3941d821f8008c8ac8f37018a89370` |

Two CI-only defects were found and fixed before the green run:

- `android-actions/setup-android@v3` installs the retired `tools` SDK package by default; it
  no longer exists in cmdline-tools 16.x, so `sdkmanager` exited 1 during setup. The workflow
  now passes `packages: 'platform-tools'`.
- `sdkmanager "platforms;android-37"` cannot resolve — the SDK repository publishes
  `platforms;android-37.0`, `android-37.1` and `android-37.2`, never a bare `android-37`.
  `compileSdk = 37` resolves against the platform already present on the runner image, so the
  workflow reports installed packages instead of installing them.

Four app-module Kotlin compile errors surfaced once compilation actually ran, and were fixed:

| File | Error | Fix |
|---|---|---|
| `MainActivity.kt` | `Unresolved reference 'StudioScreen'` | Added the missing `com.mouya.phirachartstudio.ui.StudioScreen` import |
| `ui/StudioScreen.kt` | `Unresolved reference 'matchParentSize'` | Removed the bogus `androidx.compose.foundation.layout.matchParentSize` import; it is a `BoxScope` member |
| `ui/StudioScreen.kt` | `@Composable invocations can only happen from the context of a @Composable function` in `ProgressTrack` | Read `MiuixTheme.colorScheme` before the `Canvas` block, where the non-composable draw lambda cannot |
| `data/PhiraExporter.kt` | `Assignment type mismatch: actual type is 'ByteArray', but 'String?' was expected` | Changed `infoText` / `chartText` to `ByteArray?` to match `readEntryLimited` |

## Not verified here

Nothing below has been run; the authoring container has no device, emulator or ADB, and the
CI job only reaches the debug-build stage.

| Check | Status | Reason |
|---|---|---|
| APK install and runtime smoke test | NOT RUN | No Android device / emulator / ADB |
| SAF picker and persisted URI grant | NOT RUN | Android runtime test unavailable |
| MediaCodec and ExoPlayer real-device coverage | NOT RUN | Android runtime test unavailable |
| Keystore AES-GCM behavior on device | NOT RUN | Android runtime test unavailable |
| Actual Phira stable-client `.pez` import / play test | NOT RUN | No Phira runtime available in the container |
| `:app` JVM unit tests | NOT PRESENT | The app module currently declares no unit-test sources |

## Release gate

This repository snapshot is **source-complete enough for developer build and review, but not release-certified**. Before a product release, run the Gradle CI workflow, test on API 26 / API 33+ devices, test several real audio codecs and provider URIs, import / play the sample and at least one user-generated package in the current stable Phira release, validate all dependency licenses and create a signed release build.