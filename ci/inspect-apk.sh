#!/usr/bin/env bash
# Records signature and manifest facts about the release APK for the CI report.
set -euo pipefail
BT="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
APK=app/build/outputs/apk/release/app-release.apk
mkdir -p ci-out
{
  echo "file: $APK"
  echo "size_bytes: $(stat -c %s "$APK")"
  echo "sha256: $(sha256sum "$APK" | cut -d' ' -f1)"
  echo
  echo "--- apksigner verify"
  "$BT/apksigner" verify --verbose --print-certs "$APK" | grep -vE '^WARNING: META-INF'
  echo
  echo "--- aapt2 dump badging (selected lines)"
  "$BT/aapt2" dump badging "$APK" |
    grep -E "^ *(package|sdkVersion|targetSdkVersion|minSdkVersion|uses-permission|uses-feature|uses-feature-not-required|uses-implied-feature|feature-group|application-label:|launchable-activity|native-code|supports-screens|densities)" || true
  echo
  if "$BT/aapt2" dump badging "$APK" | grep -q '^native-code'; then
    echo "native_code: yes"
  else
    echo "native_code: none (same APK runs on Intel/AMD and ARM Chromebooks)"
  fi
} > ci-out/apk-info.txt
cat ci-out/apk-info.txt
