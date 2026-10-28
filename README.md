# 독립 브라우저 (Independent Browser)

Chrome과 **방문 기록·쿠키·캐시·로그인 세션을 공유하지 않는** Chromebook/Android용 브라우저 앱입니다.
Kotlin + Android System WebView로 만들었고, GitHub Actions에서 빌드·테스트한 뒤 GitHub Releases에 APK로 배포합니다.

> ⚠ **설치 전에 꼭 읽어 주세요.** 이 앱은 Android 앱입니다. Google Play 스토어(Android 앱 기능)와 Linux 개발 환경이
> 모두 막힌 Chromebook에는 **설치할 수 없습니다.** → [설치 차단 시 대안](docs/설치-차단-시-대안.md)

## 받기

| 파일 | 무엇인가 |
|---|---|
| [`IndependentBrowser.apk`](https://github.com/lejiyo09/topsecret/releases/latest/download/IndependentBrowser.apk) | **실제 브라우저 앱** (Android 8.0+, ChromeOS Android 앱 지원 필요) |
| [`IndependentBrowser-Launcher.html`](https://github.com/lejiyo09/topsecret/releases/latest/download/IndependentBrowser-Launcher.html) | 파일 앱에서 클릭하는 **다운로드·실행 도우미** (Chrome에서 열림, 브라우저 아님) |
| [릴리스 페이지](https://github.com/lejiyo09/topsecret/releases/latest) | 변경 내역, 테스트 결과, `SHA256SUMS.txt` |

## 문서

1. [기술 선택 보고서](docs/기술-선택-보고서.md) — 4가지 후보 비교와 선택 이유, 설치 장벽 분석
2. [설치 및 실행 가이드](docs/설치-가이드.md) — 다운로드/설치/실행 단계와 실기기 점검표
3. [설치 차단 시 대안](docs/설치-차단-시-대안.md) — 정책을 우회하지 않는 승인된 대안
4. [빌드 및 테스트 기록](docs/빌드-및-테스트-기록.md) — CI 결과를 있는 그대로 기록

## 기능

- 주소창(URL/검색어 자동 판별), 뒤로·앞으로·새로고침·홈, 검색 엔진 선택(Google·DuckDuckGo·Bing·네이버)
- 여러 탭(탭 줄, 닫은 탭 다시 열기, 탭 복원), 사용자 클릭으로 연 팝업만 허용
- 북마크, 방문 기록(검색·삭제)
- 방문 기록 / 쿠키·사이트 데이터 / 캐시 / 양식 데이터 삭제, "다시 시작할 때 모두 삭제" 옵션
- Chromebook 키보드·마우스: Ctrl+T/W/Tab/L/R/D/H/F, Alt+←/→, 우클릭 메뉴, Ctrl+클릭 새 탭, 휠 클릭으로 탭 닫기
- 보안: 인증서 오류 사이트 항상 차단(우회 불가), 혼합 콘텐츠 차단, Safe Browsing, 로컬 파일 접근 차단,
  다운로드 전 확인(파일명·크기·출처·위험 확장자·HTTP 경고), 외부 앱 링크는 클릭+확인 후에만, 클라우드 백업 제외

## 데이터가 어디에 저장되나

| 데이터 | 위치 (앱 전용 샌드박스) |
|---|---|
| 쿠키, localStorage, IndexedDB, 로그인 세션 | `/data/data/io.github.lejiyo09.independentbrowser/app_webview/` |
| HTTP 캐시 | `/data/data/io.github.lejiyo09.independentbrowser/cache/` |
| 방문 기록, 북마크 | `/data/data/io.github.lejiyo09.independentbrowser/databases/browser.db` |
| 설정, 열린 탭 | `/data/data/io.github.lejiyo09.independentbrowser/shared_prefs/` |

이 앱은 Chrome의 데이터에 접근하지 않습니다. 웹사이트 서버에 저장되는 기록(예: Google 계정 '내 활동')과
학교·회사·네트워크가 남기는 기록은 이 앱으로 삭제하거나 막을 수 없습니다.

## 소스 구조

```
app/src/main/java/io/github/lejiyo09/independentbrowser/
  MainActivity.kt        탭, 툴바, 단축키, 컨텍스트 메뉴, 권한·파일 선택·전체 화면
  BrowserTab.kt          탭 상태 + WebViewClient(인증서 차단, 외부 링크) + WebChromeClient(팝업, 진행률)
  UrlInput.kt            주소창 해석, 검색 엔진, 데스크톱 User-Agent
  BrowserDatabase.kt     방문 기록·북마크 (SQLite)
  DataCleaner.kt         기록·쿠키·캐시·양식 데이터 삭제
  DownloadHandler.kt     다운로드 확인 대화상자 → DownloadManager
  DownloadPolicy.kt      파일명 정리, 위험 확장자 판별
  ExternalLinks.kt       mailto:/intent: 등 외부 앱 링크 처리
  EntryListActivity.kt   방문 기록/북마크 화면
  SettingsActivity.kt    설정 화면
app/src/test/            JVM 단위 테스트
app/src/androidTest/     에뮬레이터 계측 테스트
.github/workflows/android.yml   빌드 → 에뮬레이터(API 30·34) 테스트 → 릴리스 → 보고서
ci/                      CI 스크립트
launcher/                다운로드·실행 도우미 HTML
```

## 직접 빌드

GitHub에 푸시하면 Actions가 자동으로 빌드합니다. 로컬에서는 JDK 17 + Android SDK 35가 필요합니다.

```bash
./gradlew testDebugUnitTest assembleRelease
```

`app/build.gradle.kts`의 `appVersionName`을 올리고 `main`에 푸시하면, 모든 테스트가 통과한 경우에만 `v<버전>` 릴리스가 만들어집니다.

### 고정 서명 키 (권장)

저장소 시크릿이 없으면 CI는 매번 **임시 키**로 서명합니다. 이 경우 새 버전으로 업데이트하려면 앱을 지우고 다시 설치해야 하고
앱 데이터가 사라집니다. 업데이트를 이어 가려면 저장소 Settings → Secrets and variables → Actions에 다음을 등록하세요.

| 시크릿 | 값 |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | PKCS12 키스토어 파일의 base64 |
| `SIGNING_STORE_PASSWORD` | 키스토어 비밀번호 |
| `SIGNING_KEY_ALIAS` | 키 별칭 |
| `SIGNING_KEY_PASSWORD` | 키 비밀번호 (PKCS12는 스토어 비밀번호와 동일) |
