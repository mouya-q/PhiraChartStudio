# Changelog

## 1.0.0 — 2026-11-07 — Initial source construction

- Created a standalone Android / MIUIX project and a pure Kotlin chart engine.
- Added streaming FFT / spectral-flux features, BPM and beat-phase estimation, onset detection and coarse energy segmentation.
- Added deterministic EZ / HD / IN chart generation and RPE JSON / `info.yml` output.
- Added Android SAF audio import, MediaCodec decoding and Media3 playback.
- Added chart lane preview, BPM / offset calibration and `.pez` package export.
- Added ZIP reopen / structural checks, strict JSON validation and project persistence.
- Added opt-in HTTPS AI arrangement hints with Android Keystore-backed token encryption and offline fallback.
- Added architecture / ADR / privacy / AI / rights / build / manual / FAQ / license documents and Android CI workflow.
- Validated the platform-independent engine using local synthetic audio regressions. Android build and in-app Phira import remain pending in the current authoring environment.

## 1.0.0 — CI verification (GitHub Actions)

- Published the source to `github.com/mouya-q/PhiraChartStudio` (`main`).
- The first CI runs failed before compilation, for two environment reasons and one code reason, all now fixed:
  - `android-actions/setup-android@v3` defaults to installing the retired `tools` package, which no longer exists in cmdline-tools 16.x, so `sdkmanager` exited 1 during setup. The workflow now requests only `platform-tools`.
  - `sdkmanager "platforms;android-37"` cannot resolve: the SDK repository publishes `platforms;android-37.0`, `37.1` and `37.2`, never a bare `android-37`. `compileSdk = 37` resolves against the platform already present on the runner image, so the workflow reports installed packages instead of installing them.
  - Four Kotlin compile errors in the app module (missing `StudioScreen` import, `matchParentSize` imported from a non-existent package, theme colours read inside non-composable `Canvas` lambdas, `ByteArray` returned into `String?` variables).
- Result: the `Android CI` workflow (run `42398514053`, commit `7d35eb75`) passes every step — tests, `:app:assembleDebug`, and artifact upload. The debug APK is 13,229,035 bytes.