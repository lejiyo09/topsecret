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
grep -E "IndependentBrowser|TestRunner: (started|failed|finished)|AndroidRuntime|cr_.*(SSL|Cert|net::ERR)" \
  "$OUT/logcat-instrumentation-api$API.txt" | tail -n 300 > "$OUT/logcat-excerpt-api$API.txt" || true
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
  2>&1 | tr -d '\r' > "$OUT/am-start-api$API.txt"
# Sample the process every 2 s for 30 s so a late death is distinguishable from a launch that never happened.
seen=0
pid=""
: > "$OUT/pid-trace-api$API.txt"
for i in $(seq 1 15); do
  sleep 2
  pid="$(adb shell pidof "$APP_ID" | tr -d '\r')"
  echo "t=$((i * 2))s pid='$pid'" >> "$OUT/pid-trace-api$API.txt"
  [ -n "$pid" ] && seen=1
done
adb exec-out screencap -p > "$OUT/release-smoke-api$API.png"
adb logcat -d -b crash > "$OUT/crash-buffer-api$API.txt" 2>&1
adb logcat -d > "$OUT/logcat-release-api$API.txt"
grep -E "$APP_ID|ANR in|lowmemorykiller|Force stopping|has died|Killing" "$OUT/logcat-release-api$API.txt" \
  | grep -vE "chromium|cr_" | tail -n 200 > "$OUT/logcat-release-excerpt-api$API.txt" || true
if [ -n "$pid" ] && ! grep -q "$APP_ID" "$OUT/crash-buffer-api$API.txt"; then
  echo "release_launch=pass (pid $pid after 30 s, no crash)" >> "$SUMMARY"
else
  echo "release_launch=FAIL (seen_running=$seen, pid at 30 s='$pid')" >> "$SUMMARY"
  status=1
fi
if grep -q "Example Domain" <(adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml); then
  echo "release_page_rendered=pass (UI tree contains 'Example Domain')" >> "$SUMMARY"
else
  echo "release_page_rendered=unknown (WebView text not in accessibility tree)" >> "$SUMMARY"
fi

cat "$SUMMARY"
exit $status
