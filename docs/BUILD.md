# Build guide

## Toolchain

| Component | Pinned version | Reason |
|---|---:|---|
| JDK | 17 | AGP 9.5.0 supported runtime / compile toolchain |
| Android Gradle Plugin | 9.5.0 | Supports compile SDK API 37 |
| Gradle | 9.6.0 | Minimum/default pairing for AGP 9.5.0 |
| Kotlin | 2.4.20 | Kotlin / Compose compiler plugin version match |
| Android SDK Platform | 37 | App compile target |
| Android Build Tools | 36.0.0 | AGP 9.5 default build tools |
| minSdk / targetSdk | 26 / 36 | Android 8.0+ runtime support, target API 36 |
| Miuix | 0.9.4 | MIUI / HyperOS-style Compose components |
| Media3 ExoPlayer | 1.11.1 | Audio playback |

Use a current Android Studio release capable of importing AGP 9.5.0. On CI, `android.yml` installs the SDK packages and builds the debug APK.

## Commands

Windows PowerShell:

```powershell
.\gradlew.bat --version
.\gradlew.bat :engine:test
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Linux / macOS:

```bash
./gradlew --version
./gradlew :engine:test
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The first run downloads Gradle 9.6.0 and Maven dependencies. The bootstrap scripts verify the Gradle archive with the checksum published by Gradle. The standard `gradle-wrapper.properties` also records `distributionSha256Sum`; the included custom launcher performs its own independent checksum check because this source snapshot intentionally avoids committing the binary wrapper JAR.

## Outputs

```text
app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is for local testing only. A public release requires a release signing configuration, a controlled signing key, a release build, device testing and a completed third-party license report. No signing key is included in this repository.

## Android SDK installation

Install these SDK packages from Android Studio's SDK Manager:

- Android SDK Platform 37
- Android SDK Build-Tools 36.0.0
- Android SDK Platform-Tools (for optional device installation)

No NDK is required by this implementation.

## Local engine-only test without Android SDK

The `engine` module is pure Kotlin/JVM. On a machine with Kotlin compiler 2.4.20 or compatible Gradle, run `:engine:test`. This repository also includes executable runners for constrained/offline environments. They can be compiled using a local `kotlinc`:

```bash
mkdir -p build/local-engine
kotlinc engine/src/main/kotlin/com/mouya/phirachartstudio/engine/*.kt \
  -include-runtime -d build/local-engine/engine-core.jar
kotlinc engine/src/test/kotlin/com/mouya/phirachartstudio/engine/EngineSmokeTest.kt \
  -classpath build/local-engine/engine-core.jar -include-runtime \
  -d build/local-engine/smoke-runner.jar
java -cp build/local-engine/smoke-runner.jar:build/local-engine/engine-core.jar \
  com.mouya.phirachartstudio.engine.EngineSmokeTest
```

On Windows, use `;` instead of `:` in the Java classpath separator.

## Troubleshooting

- **Gradle distribution download fails:** check HTTPS access to `services.gradle.org`. The bootstrap stops if SHA-256 does not match.
- **Dependency resolution fails:** verify Google Maven, Maven Central and Gradle Plugin Portal access. Do not disable TLS validation.
- **Unsupported API 37:** install Platform 37 and use AGP 9.5.0 / Gradle 9.6.0.
- **Miuix / Compose API mismatch:** keep the pinned Miuix and Kotlin versions together; do not upgrade only one side.
- **Audio decoding error:** the Android device may not expose a decoder for that particular container / codec. Convert it to a common MP3, OGG or WAV file, then re-import.
- **SAF file disappears:** the app can persist a provider URI permission when the provider supports it, but cannot guarantee a third-party provider keeps the underlying document.

## What is not claimed

This snapshot has not produced an APK in the authoring container. The container has no Android SDK and no installed Gradle distribution; network access to fetch Gradle is unavailable there. The Gradle build should be run on a developer machine or CI before a release claim is made. See [BUILD_STATUS.md](../BUILD_STATUS.md).
