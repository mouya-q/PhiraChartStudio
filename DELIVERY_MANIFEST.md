# Delivery manifest

Snapshot: 2026-11-07

## Contents

- `app/`: Android application, Miuix Compose UI, SAF importer, MediaCodec analyzer adapter, Media3 player, AI client and Keystore-backed settings.
- `engine/`: pure Kotlin streaming analysis, BPM/phase estimation, onset detection, difficulty generator, RPE serializer and constrained validator.
- `docs/`: build guide, user manual, architecture, privacy and AI disclosures, rights disclaimer, dependency license inventory, FAQ and ADRs.
- `samples/ExampleChart.pez`: synthetic, copyright-free chart package; WAV pointer names are consistent in `info.yml` and RPE `META`.
- `.github/workflows/android.yml`: Android CI test and debug APK build workflow.

## Verified

- Pure Kotlin engine compilation with `kotlinc`.
- Executable smoke test and chart serialization validation.
- Tempo sweep across 13 tempo points from 55 to 200 BPM.
- Eight beat-phase cases across 22.05 kHz / 44.1 kHz sources.
- Difficulty monotonicity and negative-offset regression.
- Sample ZIP structure, CRC, JSON parse, YAML parse and resource pointer consistency.
- Android XML well-formedness and shell syntax of the Gradle bootstrap script.
- No TODO / FIXME / XXX / NotImplementedError markers in source.

## Not verified

- Gradle Android build, because this environment has no Android SDK or installed Gradle, and DNS cannot resolve `services.gradle.org` to bootstrap Gradle 9.6.0.
- APK installation, device playback / SAF / Keystore behavior, or real import and gameplay in Phira.

See `BUILD_STATUS.md` for exact results and release gates.
