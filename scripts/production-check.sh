#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
  export JAVA_HOME="/usr/lib/jvm/java-25-openjdk-amd64"
fi

./gradlew \
  spotlessCheck \
  :app:testDebugUnitTest \
  :app:compileDebugKotlin \
  :app:compileDebugAndroidTestKotlin \
  :app:lintDebug \
  :app:assembleDebug

echo
echo "Production check passed for debug/test/lint gates."
echo "Run './gradlew :app:assembleRelease' separately on a machine with keystore.properties."
