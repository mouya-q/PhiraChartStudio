# User manual

## 1. Import music

Open **Studio → Import audio**. Android's system document picker handles the file selection. The app reads the chosen audio through the document provider; it does not copy the raw track into its own project store.

The device's Android media codecs determine which formats can be decoded. MP3, OGG, WAV, FLAC and AAC file extensions are accepted by the `.pez` exporter. A container may still fail to decode on a device without a suitable codec.

## 2. Analyze

After import the app begins offline analysis automatically. You can also use **Analyze** or **Regenerate** on the track card. The progress stages cover opening the stream, reading the decoded audio, calculating rhythm features and creating the chart drafts.

Analysis extracts:

- BPM candidate and confidence estimate;
- beat phase / offset estimate;
- onset times from spectral flux and level change;
- RMS and low / mid / high spectral-energy envelopes;
- coarse eight-second energy sections.

The decoded PCM is fed to the analyser in short blocks. The application does not retain the complete decoded song in memory. Cancel stops the current analysis job; restarting performs a fresh pass from the beginning rather than resuming an uncommitted decoder cursor.

## 3. Calibrate

The analysis card shows BPM, onset count, duration and a confidence indicator. BPM controls adjust in 0.1 BPM steps. Offset controls adjust in 10 ms steps. Each change regenerates the three difficulties from the retained analysis features; it does not decode the file again.

A low confidence score or sparse onset list means the music did not produce a clear periodic signal. Listen to the track and compare the energy display before accepting the automatic estimate.

## 4. Choose a difficulty

The generator creates three independent drafts:

| Preset | Grid | Intention |
|---|---|---|
| EZ | Beat grid | Simpler primary-beat emphasis and fewer off-beat placements |
| HD | Half-beat grid | More syncopation and higher note density |
| IN | Quarter-beat grid | Highest density and more varied short patterns |

The generator uses onset proximity, energy, high / low band features, spacing limits and deterministic pattern choices. It does not use a trained neural model. These are automatic first drafts, not authoritative chart ratings.

## 5. Preview

Open **Preview** to inspect the selected difficulty. The lane canvas visualizes Tap, Hold, Flick and Drag objects on a simplified note highway. The audio player supports play / pause and scrubbing. Switch difficulty from the home screen, then return to Preview.

The preview is not a complete implementation of Phira's renderer, line-event engine or judgement rules. Always test the exported package inside Phira.

## 6. Export and import in Phira

Tap **Export .pez**. Pick a destination and filename in Android's system save dialog. The app creates a root-level package containing:

```text
info.yml
chart.json
background.png
song.<original-supported-extension>
```

The exporter checks the generated RPE JSON and `info.yml`, writes audio in 64 KiB blocks, then reopens the ZIP and checks its required entries, duplicates, paths, non-empty assets and chart structure. Cancellation or a failed post-write check deletes the partial output where the document provider allows deletion.

In Phira, use its local chart import flow to select the exported `.pez`. Play the chart. Check the first downbeat, long notes, dense sections, note visibility, timing and difficulty. Edit the chart further before publishing.

## 7. Optional AI assistance

Open **Settings → AI arrangement assist**. Configure a trusted HTTPS API base URL, model ID and API Key. Save, use **Test connection**, or fetch the model list. API keys are stored as AES-GCM ciphertext using an Android Keystore key.

When AI is enabled and configured, the app first completes the offline analysis, then sends BPM, confidence, duration, a sampled onset list and coarse energy segments. It requests only four bounded arrangement hints. The local generator validates and clamps the hints. If the service fails, the normal offline generator continues automatically.

## 8. Projects and settings

The most recent generated project is stored in app-private files. It contains the selected document URI, display name, analysis summary, chart drafts, difficulty choice and BPM / offset adjustments. Raw audio bytes are not copied. If the document provider revokes or deletes the source, re-import the track.

Appearance offers System, Light and Dark. The About page gives the product version and legal / privacy notes.
