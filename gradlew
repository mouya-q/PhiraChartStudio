#!/usr/bin/env sh
# Small bootstrap wrapper for the pinned Gradle distribution; no wrapper JAR is required.
set -eu
ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=9.6.0
CACHE_ROOT=${GRADLE_USER_HOME:-"$HOME/.gradle"}/phira-chart-studio
GRADLE_HOME="$CACHE_ROOT/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"
EXPECTED_SHA256="bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01"
if [ ! -x "$GRADLE_BIN" ]; then
    mkdir -p "$CACHE_ROOT"
    ZIP="$CACHE_ROOT/gradle-$GRADLE_VERSION-bin.zip"
    URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    echo "Downloading Gradle $GRADLE_VERSION..."
    if command -v curl >/dev/null 2>&1; then
        curl --fail --location --retry 3 --output "$ZIP" "$URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$ZIP" "$URL"
    else
        echo "curl or wget is required to bootstrap Gradle." >&2
        exit 1
    fi
    if ! command -v unzip >/dev/null 2>&1; then
        echo "unzip is required to install the Gradle distribution." >&2
        exit 1
    fi
    if command -v sha256sum >/dev/null 2>&1; then
        ACTUAL_SHA256=$(sha256sum "$ZIP" | awk '{print $1}')
    elif command -v shasum >/dev/null 2>&1; then
        ACTUAL_SHA256=$(shasum -a 256 "$ZIP" | awk '{print $1}')
    elif command -v openssl >/dev/null 2>&1; then
        ACTUAL_SHA256=$(openssl dgst -sha256 "$ZIP" | awk '{print $NF}')
    else
        echo "A SHA-256 utility (sha256sum, shasum or openssl) is required." >&2
        rm -f "$ZIP"
        exit 1
    fi
    if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
        echo "Gradle distribution SHA-256 mismatch; refusing to execute it." >&2
        rm -f "$ZIP"
        exit 1
    fi
    TMP="$CACHE_ROOT/extract-$GRADLE_VERSION"
    rm -rf "$TMP"
    mkdir -p "$TMP"
    unzip -q "$ZIP" -d "$TMP"
    mv "$TMP/gradle-$GRADLE_VERSION" "$GRADLE_HOME"
    rmdir "$TMP"
    rm -f "$ZIP"
fi
exec "$GRADLE_BIN" -p "$ROOT_DIR" "$@"
