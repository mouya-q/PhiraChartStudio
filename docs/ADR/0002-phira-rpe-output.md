# ADR-0002: Emit a constrained RPE Phira package

- **Status:** Accepted
- **Date:** 2026-11-07

## Context

Phira packages contain metadata and resource files. The official client has multiple parser paths and a metadata model. Linking the Rust parser into an Android Compose app would add a large native integration and GPL-3.0-only licensing obligations that have not been reviewed for this product.

## Decision

Emit a constrained RPE JSON chart plus root-level `info.yml`, music and generated background image. Keep serialization independent of the official client. Mirror only the required RPE structures and note types used by this generator. Validate the exact subset with a strict in-house JSON parser and metadata checks.

## Consequences

- One well-defined export target rather than several partially supported formats.
- No copied or linked Phira implementation code.
- In-house validation catches common structure / metadata errors but cannot guarantee that arbitrary Phira engine behavior is reproduced.
- A real import and playback check in the current stable Phira app is still mandatory before release.
