# Privacy policy

**Effective date:** 2026-11-07

Phira Chart Studio is designed for local, offline-first use.

## Information processed on device

When you import a track, Android provides a document URI and read access. The app reads the audio for analysis, playback and export. It records the display name, duration, URI, analysis features, selected difficulty and generated chart drafts in app-private storage so the latest project can be restored. The original audio bytes are not copied into the project store.

The app does not request contacts, camera, microphone recording, location, phone state or broad shared-storage access. It does not include advertising or an analytics SDK in this source snapshot.

## Optional AI service

AI assistance is disabled by default. If enabled and configured, derived analysis features (BPM, confidence, duration, sampled onset timestamps and coarse energy sections) are sent to the HTTPS endpoint you configure. The source audio, file URI and display name are not included in that request. The provider may process request metadata under its own terms; review its privacy policy before use.

## API key storage

The API token is encrypted with AES-GCM using an AES key generated in Android Keystore. The encrypted value and IV are stored in private preferences. The app does not intentionally log the token or include it in diagnostic messages. Android Keystore protection depends on device security and OS integrity.

## Network and permissions

The app declares `INTERNET` solely to support the optional AI feature. Cleartext traffic is disabled and the AI client requires HTTPS. No network request is needed for offline audio analysis or offline generation.

## Retention and deletion

The most recent project snapshot remains in app-private storage until replaced or the application data is cleared. Use the AI settings page to remove the saved API key. Uninstalling or clearing app data removes the application's private data. The source audio remains wherever it was originally stored, outside the app's ownership.

## Children's privacy

The app does not provide user accounts, public profiles, social feeds or in-app purchases. Do not enter personal information into chart names, API prompts or third-party API configuration. For AI services, use an endpoint and account that you are authorized to use, and follow the provider's age and consent requirements.

## Changes

This document must be updated when data flows, permissions, AI payloads, analytics or storage behavior change. The policy describes this source snapshot and should be re-reviewed before distribution.
