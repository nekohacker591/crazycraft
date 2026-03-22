#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BOOTSTRAP_DIR="$ROOT_DIR/.bootstrap"
DOWNLOAD_DIR="$BOOTSTRAP_DIR/downloads"
TOOLS_DIR="$BOOTSTRAP_DIR/tools"
JAVA_DIR="$TOOLS_DIR/jdk-17.0.18+8"
GRADLE_VERSION="8.14.3"
GRADLE_DIR="$TOOLS_DIR/gradle-$GRADLE_VERSION"
GRADLE_DIST="gradle-$GRADLE_VERSION-bin.zip"
GRADLE_URL="https://services.gradle.org/distributions/$GRADLE_DIST"
JAVA_X64_URL='https://release-assets.githubusercontent.com/github-production-release-asset/372925194/7d5ba975-aab4-4394-b3bf-235dffeeb59d?sp=r&sv=2018-11-09&sr=b&spr=https&se=2026-03-22T19%3A44%3A35Z&rscd=attachment%3B+filename%3DOpenJDK17U-jdk_x64_linux_hotspot_17.0.18_8.tar.gz&rsct=application%2Foctet-stream&skoid=96c2d410-5711-43a1-aedd-ab1947aa7ab0&sktid=398a6654-997b-47e9-b12b-9515b896b4de&skt=2026-03-22T18%3A44%3A21Z&ske=2026-03-22T19%3A44%3A35Z&sks=b&skv=2018-11-09&sig=hfbeI9N8zh65wcHrsiNj5lZKsJEYWSox4uvWtV0T8dA%3D&jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmVsZWFzZS1hc3NldHMuZ2l0aHVidXNlcmNvbnRlbnQuY29tIiwia2V5Ijoia2V5MSIsImV4cCI6MTc3NDIwOTUwNywibmJmIjoxNzc0MjA1OTA3LCJwYXRoIjoicmVsZWFzZWFzc2V0cHJvZHVjdGlvbi5ibG9iLmNvcmUud2luZG93cy5uZXQifQ.mT7ILgm293VOk6Z0zxmhjy1aqy5AYzLVb_9ExFaMJNU&response-content-disposition=attachment%3B%20filename%3DOpenJDK17U-jdk_x64_linux_hotspot_17.0.18_8.tar.gz&response-content-type=application%2Foctet-stream'
JAVA_ARM_URL='https://release-assets.githubusercontent.com/github-production-release-asset/372925194/e4269bd9-cfb7-4b18-af33-49f7cabc15fd?sp=r&sv=2018-11-09&sr=b&spr=https&se=2026-03-22T19%3A48%3A43Z&rscd=attachment%3B+filename%3DOpenJDK17U-jdk_arm_linux_hotspot_17.0.18_8.tar.gz&rsct=application%2Foctet-stream&skoid=96c2d410-5711-43a1-aedd-ab1947aa7ab0&sktid=398a6654-997b-47e9-b12b-9515b896b4de&skt=2026-03-22T18%3A47%3A58Z&ske=2026-03-22T19%3A48%3A43Z&sks=b&skv=2018-11-09&sig=bzzUZz0kKMbUsvt%2FkekH0o3Pw6ImmTL%2FRga5tU3abmQ%3D&jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmVsZWFzZS1hc3NldHMuZ2l0aHVidXNlcmNvbnRlbnQuY29tIiwia2V5Ijoia2V5MSIsImV4cCI6MTc3NDIwOTM3OCwibmJmIjoxNzc0MjA1Nzc4LCJwYXRoIjoicmVsZWFzZWFzc2V0cHJvZHVjdGlvbi5ibG9iLmNvcmUud2luZG93cy5uZXQifQ.MFfGyV5aM1wEavyDhgtkO6rzSD4F-LoSQJzaxr66Jec&response-content-disposition=attachment%3B%20filename%3DOpenJDK17U-jdk_arm_linux_hotspot_17.0.18_8.tar.gz&response-content-type=application%2Foctet-stream'

mkdir -p "$DOWNLOAD_DIR" "$TOOLS_DIR"

need_cmd() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Missing required command: $1" >&2
        exit 1
    fi
}

need_cmd curl
need_cmd tar

extract_zip() {
    local archive="$1"
    local destination="$2"

    if command -v unzip >/dev/null 2>&1; then
        unzip -q "$archive" -d "$destination"
    elif command -v python3 >/dev/null 2>&1; then
        python3 - "$archive" "$destination" <<'PY'
import sys
import zipfile
zip_path, dest = sys.argv[1:3]
with zipfile.ZipFile(zip_path) as zf:
    zf.extractall(dest)
PY
    else
        echo "Need either unzip or python3 to extract $archive" >&2
        exit 1
    fi
}

ARCH="$(uname -m)"
case "$ARCH" in
    x86_64|amd64)
        JAVA_URL="$JAVA_X64_URL"
        JAVA_ARCHIVE="$DOWNLOAD_DIR/OpenJDK17U-jdk_x64_linux_hotspot_17.0.18_8.tar.gz"
        ;;
    aarch64|arm64)
        JAVA_URL="$JAVA_ARM_URL"
        JAVA_ARCHIVE="$DOWNLOAD_DIR/OpenJDK17U-jdk_arm_linux_hotspot_17.0.18_8.tar.gz"
        ;;
    *)
        echo "Unsupported Linux architecture: $ARCH" >&2
        exit 1
        ;;
esac

if [ ! -d "$JAVA_DIR" ]; then
    echo "Downloading Java 17 for $ARCH..."
    curl -L --fail --output "$JAVA_ARCHIVE" "$JAVA_URL"
    rm -rf "$JAVA_DIR"
    tar -xzf "$JAVA_ARCHIVE" -C "$TOOLS_DIR"
fi

GRADLE_ARCHIVE="$DOWNLOAD_DIR/$GRADLE_DIST"
if [ ! -d "$GRADLE_DIR" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    curl -L --fail --output "$GRADLE_ARCHIVE" "$GRADLE_URL"
    rm -rf "$GRADLE_DIR"
    extract_zip "$GRADLE_ARCHIVE" "$TOOLS_DIR"
fi

export JAVA_HOME="$JAVA_DIR"
export GRADLE_HOME="$GRADLE_DIR"
export PATH="$JAVA_HOME/bin:$GRADLE_HOME/bin:$PATH"

if [ -f /etc/ssl/certs/java/cacerts ]; then
    TRUST_STORE_FLAG="-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
    if [ -n "${JAVA_TOOL_OPTIONS:-}" ]; then
        export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS $TRUST_STORE_FLAG"
    else
        export JAVA_TOOL_OPTIONS="$TRUST_STORE_FLAG"
    fi
fi

cd "$ROOT_DIR"

echo "Using JAVA_HOME=$JAVA_HOME"
java -version
printf '\n'
gradle --version
printf '\n'
gradle clean build
