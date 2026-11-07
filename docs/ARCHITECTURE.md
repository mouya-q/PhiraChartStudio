# Architecture

## Goals

1. Offline generation must remain usable when the network is absent or AI is disabled.
2. Audio bytes should be decoded and processed incrementally.
3. The deterministic engine should be testable without Android classes.
4. Phira serialization / validation must be a separate boundary from analysis and UI.
5. Every optional network output must pass local bounds and structural validation before affecting generation.

## Layering

```text
Compose / MIUIX screens
        │ immutable StudioUiState + UI events
        ▼
StudioViewModel ─────────────── ExoPlayer (Media3)
        │
        ├── SAF document URI ── MediaExtractor / MediaCodec
        │                            │ mono PCM blocks
        │                            ▼
        │                    StreamingBeatAnalyzer
        │                            │ AnalysisResult
        │                            ▼
        ├── optional AI client ── ArrangementHints (bounded)
        │                            │
        ▼                            ▼
              ChartGenerator (pure Kotlin)
                         │ GeneratedChart
                ┌────────┴────────┐
                ▼                 ▼
          Preview canvas     RpeSerializer
                                  │ chart.json + info.yml
                                  ▼
                           PhiraExporter
                                  │
                         ZIP validation / .pez
```

## Modules

### `engine`

Pure Kotlin/JVM. It owns domain data classes, radix-2 FFT, streaming analysis, tempo / phase estimation, onset extraction, deterministic difficulty arrangement, RPE JSON serialization, strict JSON parsing and a supported-subset validator. It has no Android dependencies and can be run on a desktop JVM.

### `app/audio`

`MediaCodecAudioAnalyzer` uses `MediaExtractor` to identify the audio track, `MediaCodec` to decode, converts supported PCM encodings to mono float arrays, and passes short blocks to the engine. The app does not decode the entire file into one retained PCM array. Format renegotiation during a single stream is reported rather than silently mixing sample rates or channel layouts.

### `app/ai`

The optional client calls `/models` and `/chat/completions` on a configured HTTPS base URL. Requests use connection / read timeouts, one retry for transient I/O / HTTP 408 / 429 / 5xx failures, response-size bounds and no redirect following. It receives derived features only. It returns arrangement hints, not RPE data or arbitrary executable content.

### `app/data`

- `AiConfigStore`: non-secret preferences plus an AES-GCM encrypted API token. The AES key is generated in Android Keystore.
- `ProjectStore`: atomic JSON snapshot in app-private storage. It persists the URI and derived results, not original audio bytes.
- `PhiraExporter`: RPE / info.yml validation, generated background art, streaming ZIP output, post-write reopen and integrity checks.

### `app/ui`

MIUIX cards, buttons, navigation, switches, sliders and theming compose the product surfaces. Compose Canvas draws the energy envelope and simplified chart lanes. A light diffused glow is used for the hero treatment; it is not a full-screen backdrop blur.

## Threading

- UI and state updates: main dispatcher.
- MediaExtractor / MediaCodec decode and file I/O: `Dispatchers.IO`.
- Chart arrangement: `Dispatchers.Default`.
- HTTP API calls: `Dispatchers.IO`.
- Each analysis / export operation is a coroutine job with cancellation checks inside block-processing loops.
- StateFlow carries immutable snapshots to Compose.

## Memory model

Audio decoding emits short float blocks. The analyser retains a 2048-sample ring buffer and low-rate feature envelopes. No full-song PCM copy is kept. ZIP export copies the original audio via a 64 KiB buffer. The preview performs a binary search for the first visible note and draws only notes in the current time window.

## Phira serialization boundary

`RpeSerializer` creates one judge line with supported note types Tap (1), Hold (2), Flick (3), Drag (4), one BPM entry, RPE metadata and a root `info.yml`. The exporter adds audio and background resources to the root of the `.pez` ZIP. `ChartValidator` validates the subset this application emits; it does not claim to validate arbitrary third-party RPE features.

The official Phira source currently identifies RPE / PEC / PGR / PBC formats and parses `META.offset` in milliseconds. This application emits RPE only. It does not bundle or link the official parser. The source is GPL-3.0-only, so clean-room serialization avoids creating an unreviewed linking / source-copying decision.

## Data lifetime

- Raw audio: referenced via SAF URI and opened only for decoding, playback or export.
- Analysis: in memory, then project snapshot.
- Chart drafts: in memory, then project snapshot and eventual export.
- API token: encrypted at rest using AES-GCM; decrypted only when an AI task needs it.
- AI request: derived feature summary; no audio bytes, user account or file URI.

## UI material / blur strategy

The app targets API 26. The current Miuix `miuix-blur` runtime-shader implementation requires a newer Android API level (API 33). To keep API 26 support, the hero uses a small blurred glow layer on API 31+ and a static diffuse gradient on older systems. It does not capture or blur arbitrary content behind the card. A future backdrop blur would need a dedicated minSdk / runtime capability decision.
