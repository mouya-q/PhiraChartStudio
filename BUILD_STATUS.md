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

## Not verified here

| Check | Status | Reason |
|---|---|---|
| `:engine:test` via Gradle | NOT RUN | No Gradle installation; Gradle 9.6.0 download unavailable in this container (`curl: (6) Could not resolve host: services.gradle.org`) |
| `:app:testDebugUnitTest` | NOT RUN | Requires Gradle and Android dependencies |
| `:app:assembleDebug` | NOT RUN | No Android SDK / platform 37 in the container |
| APK install and runtime smoke test | NOT RUN | No Android device / emulator / ADB |
| SAF picker and persisted URI grant | NOT RUN | Android runtime test unavailable |
| MediaCodec and ExoPlayer real-device coverage | NOT RUN | Android runtime test unavailable |
| Keystore AES-GCM behavior on device | NOT RUN | Android runtime test unavailable |
| Actual Phira stable-client `.pez` import / play test | NOT RUN | No Phira runtime available in the container |

## Release gate

This repository snapshot is **source-complete enough for developer build and review, but not release-certified**. Before a product release, run the Gradle CI workflow, test on API 26 / API 33+ devices, test several real audio codecs and provider URIs, import / play the sample and at least one user-generated package in the current stable Phira release, validate all dependency licenses and create a signed release build.
