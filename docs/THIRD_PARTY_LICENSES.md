# Third-party software and license inventory

This is the direct dependency inventory declared in the source snapshot. Transitive dependency notices and the exact resolved versions must be generated from a successful release dependency graph before publishing an APK. The current authoring environment could not resolve the Android Gradle graph, so this list is not a substitute for a generated `licenses` report.

| Dependency | Use | Upstream license (project-level) |
|---|---|---|
| Android Gradle Plugin | Android build tooling | Apache-2.0 |
| Gradle | Build system | Apache-2.0 |
| Kotlin / Compose compiler plugin | Language and Compose compilation | Apache-2.0 |
| AndroidX Activity / Core / Lifecycle | Activity integration, compatibility, lifecycle | Apache-2.0 |
| AndroidX Media3 ExoPlayer | Music playback | Apache-2.0 |
| Kotlin Coroutines | Async tasks | Apache-2.0 |
| Miuix UI Android | MIUI / HyperOS-style Compose UI | Apache-2.0 |
| JUnit 5 | Engine unit tests | EPL-2.0 (JUnit project) |
| AndroidX Test / Espresso | Android instrumentation test dependencies | Apache-2.0 |

The engine contains original Kotlin implementation of radix-2 FFT, spectral flux / tempo analysis, onset processing, chart arrangement, RPE serialization and a constrained JSON validator. It does not copy or link the Phira Rust parser. The Phira source repository declares GPL-3.0-only; only its public format/model behavior is referenced for interoperability.

Before a public release:

1. Run Gradle dependency insight / dependency reports against the release variant.
2. Generate and review a complete direct + transitive license manifest, including any embedded notice files.
3. Include required license texts and notices in the APK / repository release assets.
4. Confirm the final `miuix-ui-android` artifact POM and all transitive components match the license inventory.
