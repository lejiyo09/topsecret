#!/usr/bin/env bash
# Runs on a booted emulator: instrumentation tests (debug build) and a smoke test of the
# exact release APK that gets published. Results go to ci-out/ for the report.
set -u
API="$1"
OUT=ci-out
mkdir -p "$OUT"
APKS=build-output/app/build/outputs/apk
APP_ID=io.github.lejiyo09.independentbrowser
DEBUG_ID="$APP_ID.debug"
SUMMARY="$OUT/summary-api$API.txt"
status=0

adb wait-for-device
{
  echo "api_level=$API"
  echo "android_release=$(adb shell getprop ro.build.version.release | tr -d '\r')"
  echo "webview=$(adb shell dumpsys webviewupdate | grep -m1 'Current WebView package' | tr -d '\r' | sed 's/^ *//')"
  echo "screen=$(adb shell wm size | tr -d '\r')"
} > "$SUMMARY"

# 1) Instrumentation tests
adb install -r -t "$APKS/debug/app-debug.apk" > "$OUT/install-debug-api$API.txt" 2>&1
adb install -r -t "$APKS/androidTest/debug/app-debug-androidTest.apk" >> "$OUT/install-debug-api$API.txt" 2>&1
adb logcat -c
adb shell am instrument -w -e debug false "$DEBUG_ID.test/androidx.test.runner.AndroidJUnitRunner" \
  | tr -d '\r' | tee "$OUT/instrumentation-api$API.txt"
if grep -q '^OK (' "$OUT/instrumentation-api$API.txt"; then
  echo "instrumentation=pass ($(grep '^OK (' "$OUT/instrumentation-api$API.txt"))" >> "$SUMMARY"
else
  echo "instrumentation=FAIL" >> "$SUMMARY"
  status=1
fi
adb logcat -d > "$OUT/logcat-instrumentation-api$API.txt"
adb uninstall "$DEBUG_ID.test" > /dev/null 2>&1 || true
adb uninstall "$DEBUG_ID" > /dev/null 2>&1 || true

# 2) Release APK smoke test
adb logcat -c
if adb install -r "$APKS/release/app-release.apk" 2>&1 | tee "$OUT/install-release-api$API.txt" | grep -q Success; then
  echo "release_install=pass" >> "$SUMMARY"
else
  echo "release_install=FAIL" >> "$SUMMARY"
  status=1
fi
adb shell am start -W -a android.intent.action.VIEW -d "https://example.com/" -n "$APP_ID/.MainActivity" \
  | tr -d '\r' > "$OUT/am-start-api$API.txt" 2>&1
sleep 20
pid="$(adb shell pidof "$APP_ID" | tr -d '\r')"
adb exec-out screencap -p > "$OUT/release-smoke-api$API.png"
adb logcat -d -b crash > "$OUT/crash-buffer-api$API.txt" 2>&1
adb logcat -d > "$OUT/logcat-release-api$API.txt"
if [ -n "$pid" ] && ! grep -q "$APP_ID" "$OUT/crash-buffer-api$API.txt"; then
  echo "release_launch=pass (pid $pid, no crash)" >> "$SUMMARY"
else
  echo "release_launch=FAIL (pid '$pid')" >> "$SUMMARY"
  status=1
fi
if grep -q "Example Domain" <(adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml); then
  echo "release_page_rendered=pass (UI tree contains 'Example Domain')" >> "$SUMMARY"
else
  echo "release_page_rendered=unknown (WebView text not in accessibility tree)" >> "$SUMMARY"
fi

cat "$SUMMARY"
exit $status
