#!/usr/bin/env bash
# Collects every job's outcome into a Markdown report and pushes it to the `ci-reports`
# branch (runs/<run number>/ and latest/), so results are readable without Actions logs.
set -u
RUN="$GITHUB_RUN_NUMBER"
A=artifacts
R=report
mkdir -p "$R"

python3 - "$A" > "$R/unit-and-lint.md" <<'PY'
import glob, sys, xml.etree.ElementTree as ET
a = sys.argv[1]
files = glob.glob(f"{a}/build-output/app/build/test-results/testDebugUnitTest/*.xml")
t = f = e = s = 0
failed = []
for p in files:
    r = ET.parse(p).getroot()
    t += int(r.get("tests", 0)); f += int(r.get("failures", 0)); e += int(r.get("errors", 0)); s += int(r.get("skipped", 0))
    for c in r.iter("testcase"):
        if c.find("failure") is not None or c.find("error") is not None:
            failed.append(f"{c.get('classname')}.{c.get('name')}")
print("## JVM unit tests")
if files:
    print(f"- tests: {t}, failures: {f}, errors: {e}, skipped: {s}")
    for x in failed:
        print(f"  - FAILED: {x}")
else:
    print("- no unit test results found (build did not reach the test task)")
lint = glob.glob(f"{a}/build-output/app/build/reports/lint-results-debug.xml")
print("\n## Android Lint (debug)")
if lint:
    counts = {}
    issues = ET.parse(lint[0]).getroot().findall("issue")
    for i in issues:
        counts[i.get("severity")] = counts.get(i.get("severity"), 0) + 1
    print("- " + (", ".join(f"{k}: {v}" for k, v in sorted(counts.items())) or "no issues"))
    for i in issues:
        if i.get("severity") in ("Error", "Fatal"):
            loc = i.find("location")
            where = f"{loc.get('file','').split('/app/')[-1]}:{loc.get('line','')}" if loc is not None else ""
            print(f"  - {i.get('severity')}: {i.get('id')} {where} — {i.get('message')}")
else:
    print("- no lint report found")
PY

{
  echo "# CI report — run #$RUN"
  echo
  echo "- commit: \`$GITHUB_SHA\` (${GITHUB_REF_NAME}, event: $GITHUB_EVENT_NAME)"
  echo "- run: $GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"
  echo "- app version: ${VERSION:-?} · release signing: ${SIGNING_MODE:-?}"
  echo
  echo "| job | result |"
  echo "|---|---|"
  echo "| build (compile, unit tests, lint, APKs) | $BUILD_RESULT |"
  echo "| device-test (emulator API 30 + 34) | $DEVICE_RESULT |"
  echo "| publish (GitHub Release) | $PUBLISH_RESULT ${PUBLISH_DETAIL:-} |"
  echo
  cat "$R/unit-and-lint.md"
  echo
  echo "## Release APK"
  echo '```'
  cat "$A/build-output/ci-out/apk-info.txt" 2>/dev/null || echo "(not built)"
  echo '```'
  for s in "$A"/device-api*/summary-api*.txt; do
    [ -f "$s" ] || continue
    api="$(basename "$s" .txt | sed 's/summary-api//')"
    echo
    echo "## Emulator API $api"
    echo '```'
    cat "$s"
    echo '```'
    echo "Instrumentation output (tail):"
    echo '```'
    tail -n 40 "$A/device-api$api/instrumentation-api$api.txt" 2>/dev/null
    echo '```'
    echo "![release smoke test API $api](release-smoke-api$api.png)"
  done
  if [ "$BUILD_RESULT" != "success" ]; then
    echo
    echo "## Build errors"
    echo '```'
    grep -E '^e: |error:|FAILED|What went wrong' -A4 "$A/build-output/ci-out/gradle-build.log" 2>/dev/null | head -120
    echo '```'
  fi
} > "$R/README.md"

cp "$A"/device-api*/release-smoke-api*.png "$R/" 2>/dev/null || true
cp "$A"/device-api*/instrumentation-api*.txt "$R/" 2>/dev/null || true
cp "$A"/device-api*/crash-buffer-api*.txt "$R/" 2>/dev/null || true
tail -n 400 "$A/build-output/ci-out/gradle-build.log" > "$R/gradle-build-tail.log" 2>/dev/null || true
cat "$R/README.md"

# Push to the ci-reports branch using the credentials actions/checkout configured.
HEADER="$(git config --get http.https://github.com/.extraheader || true)"
ORIGIN="$(git remote get-url origin)"
WORK="$(mktemp -d)"
cd "$WORK"
git init -q
git remote add origin "$ORIGIN"
[ -n "$HEADER" ] && git config http.https://github.com/.extraheader "$HEADER"
git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
if git fetch -q --depth 1 origin ci-reports 2>/dev/null; then
  git checkout -q -b ci-reports FETCH_HEAD
else
  git checkout -q --orphan ci-reports
fi
rm -rf latest "runs/$RUN"
mkdir -p latest "runs/$RUN"
cp -r "$GITHUB_WORKSPACE/$R/." latest/
cp -r "$GITHUB_WORKSPACE/$R/." "runs/$RUN/"
git add -A
git commit -q -m "CI report for run #$RUN ($GITHUB_SHA)"
for i in 1 2 3; do git push -q origin ci-reports && break; sleep $((i * 5)); git pull -q --rebase origin ci-reports || true; done
