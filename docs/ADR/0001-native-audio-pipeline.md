# ADR-0001: Android system decoding and streaming feature analysis

- **Status:** Accepted
- **Date:** 2026-11-07

## Context

A phone app must import normal audio files without bundling a large Python runtime or decoding an entire song into a giant PCM array. Decoder availability varies by Android version and device.

## Decision

Use `MediaExtractor` and `MediaCodec` to decode audio through Android's platform codecs. Convert output to short mono float blocks. Feed a 2048-sample radix-2 FFT / spectral-flux analyser that retains only a low-rate feature envelope. Keep the analysis engine in a pure Kotlin/JVM module.

Use Media3 ExoPlayer independently for preview playback.

## Consequences

- Low dependency weight and no mandatory native DSP library.
- No whole-song PCM accumulation; cancellation can be checked between decoder buffers.
- Unsupported codec / output format is reported rather than silently misread.
- Codec support depends on the device and container. The app can recommend conversion for an unsupported source.
- The analyser keeps low-rate features for later chart generation; very long sources still use feature memory proportional to duration.
