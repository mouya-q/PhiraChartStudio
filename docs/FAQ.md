# FAQ

## Does it work without AI?

Yes. Audio analysis, all three difficulty drafts, calibration, preview and export are local. AI is optional.

## Does it upload my song?

No. The current AI client sends only BPM, duration, sampled onset times and energy summaries. It never sends audio bytes.

## Why can the BPM be correct but the chart still feel off?

Tempo and beat phase are separate estimates. Music may contain pickups, swing, rubato, layered percussion or a weak downbeat. Listen to the track and adjust BPM / offset before exporting.

## Why are EZ, HD and IN not just the same chart with different names?

They use different rhythmic grid denominators, spacing thresholds, density limits, lane counts and note-selection rules. They are still automatically arranged drafts; note-by-note human review is recommended.

## Why does a particular audio file fail?

Decoding depends on Android's available system codecs and the source container. Try a common MP3, OGG or PCM WAV file. Export also checks the file extension before copying the original audio into the Phira package.

## Why is the preview not identical to Phira?

It is an inspection canvas, not Phira's rendering / judgement engine. It helps review rough lanes and timing only. Import and play the exported chart in Phira for the authoritative experience.

## Is every generated package guaranteed to import?

No such claim is made yet. The app validates its own generated RPE subset and the ZIP structure. A real Phira-client import test is still required before calling a release device-verified.

## Can I edit individual notes?

Not in this version. You can adjust BPM, offset and difficulty. The current preview is read-only; it is not a full chart editor.

## Why is there no full backdrop blur on older Android?

The app supports API 26. The current Miuix blur module uses newer runtime-shader APIs and requires a newer minimum Android version. The hero uses a limited blur/glow treatment on Android 12+ and a static diffuse fallback on older versions.

## Does cancelling analysis resume from the same byte offset?

No. Cancellation stops the current decode job. Starting analysis again reads the track from the beginning. A completed project is saved and can be restored after process recreation.

## Where can I test the example?

Use `samples/ExampleChart.pez`. It contains synthetic, copyright-free test audio and generated chart resources. Successful internal ZIP validation is not the same as a completed Phira app import test.
