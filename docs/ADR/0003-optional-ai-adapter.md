# ADR-0003: Keep AI outside the critical generation path

- **Status:** Accepted
- **Date:** 2026-11-07

## Context

AI endpoints may be unavailable, slow, incompatible with a requested response format or produce unsafe / malformed output. Users must still be able to make a chart offline.

## Decision

The offline generator owns BPM grid, note placement, lane selection and serialization. The optional HTTPS API receives derived audio features and can return only four bounded arrangement hints. The app validates / clamps those hints, retries only transient failures once, and falls back automatically.

## Consequences

- AI outages do not block analysis or chart generation.
- No raw audio is uploaded by the current integration.
- Some API providers do not support `/models` or JSON mode; manual model ID entry remains available.
- The user is responsible for the privacy / age / usage terms of the configured AI provider.
