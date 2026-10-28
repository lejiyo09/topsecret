#!/usr/bin/env bash
# Publishes a GitHub Release for app version $VERSION, only once per version and only after
# the build and all emulator test jobs succeeded (enforced by the workflow's `needs`).
set -euo pipefail
TAG="v$VERSION"
APK=artifacts/build-output/app/build/outputs/apk/release/app-release.apk

if gh release view "$TAG" > /dev/null 2>&1; then
  echo "Release $TAG already exists - bump appVersionName in app/build.gradle.kts to publish a new one."
  echo "result=exists" >> "$GITHUB_OUTPUT"
  exit 0
fi

mkdir -p dist
cp "$APK" dist/IndependentBrowser.apk
cp launcher/IndependentBrowser-Launcher.html dist/
(cd dist && sha256sum IndependentBrowser.apk IndependentBrowser-Launcher.html > SHA256SUMS.txt)

{
  echo "## 독립 브라우저 $VERSION"
  echo
  echo "Chrome과 방문 기록·쿠키·캐시·로그인 세션을 공유하지 않는 Android(WebView) 브라우저입니다."
  echo
  echo "### 이 릴리스의 검증 결과 (GitHub Actions run #$GITHUB_RUN_NUMBER)"
  echo '```'
  cat artifacts/device-api*/summary-api*.txt 2>/dev/null || echo "(no emulator summary)"
  echo '```'
  echo "- 단위 테스트, Lint, 디버그/릴리스 빌드: 통과"
  echo "- 실제 Chromebook 기기에서의 설치·실행은 CI에서 검증할 수 없습니다. docs/설치-가이드.md 의 점검표로 직접 확인하세요."
  echo
  echo "### 서명"
  if [ "$SIGNING_MODE" = "permanent" ]; then
    echo "저장소 시크릿의 고정 릴리스 키로 서명되었습니다."
  else
    echo "⚠ 임시(1회용) 키로 서명되었습니다. 다음 버전으로 업데이트하려면 기존 앱을 삭제 후 다시 설치해야 하며, 이때 앱 데이터(로그인·북마크)가 지워집니다."
  fi
  echo
  echo "### 파일"
  echo "- \`IndependentBrowser.apk\` — 실제 브라우저 앱 (Android 8.0 이상, ChromeOS의 Android 앱 지원 필요)"
  echo "- \`IndependentBrowser-Launcher.html\` — 다운로드/실행 도우미 페이지 (브라우저가 아님, Chrome에서 열림)"
  echo "- \`SHA256SUMS.txt\` — 파일 무결성 확인용 해시"
  echo
  echo "\`\`\`"
  cat dist/SHA256SUMS.txt
  echo "\`\`\`"
} > notes.md

gh release create "$TAG" dist/* --title "독립 브라우저 $VERSION" --notes-file notes.md --target "$GITHUB_SHA"
echo "result=created" >> "$GITHUB_OUTPUT"
