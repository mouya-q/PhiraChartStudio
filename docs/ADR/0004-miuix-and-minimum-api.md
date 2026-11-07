# ADR-0004: Miuix UI with API 26 baseline

- **Status:** Accepted
- **Date:** 2026-11-07

## Context

The app should use MIUI / HyperOS visual components but remain installable on Android 8.0+ devices. Some newer runtime-shader blur APIs require a higher API level.

## Decision

Use MIUIX 0.9.4 Android components and dynamic System / Light / Dark modes. Keep minSdk 26. Use a small RenderEffect-blurred glow on API 31+ and a static diffuse fallback on earlier versions. Do not make the entire app depend on a backdrop-blur library that requires API 33.

## Consequences

- The same Compose navigation and core components run across supported versions.
- Newer devices get a more diffused hero effect; older devices receive a non-blurred visual fallback.
- Full backdrop blur is not claimed in this version.
