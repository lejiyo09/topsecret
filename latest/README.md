# CI report — run #3

- commit: `112a2dcd2fc693ba0a16420237b0fc9d4578fd34` (main, event: push)
- run: https://github.com/lejiyo09/topsecret/actions/runs/40607295657
- app version: 1.0.0 · release signing: ephemeral

| job | result |
|---|---|
| build (compile, unit tests, lint, APKs) | success |
| device-test (emulator API 30 + 34) | failure |
| publish (GitHub Release) | skipped  |

## JVM unit tests
- tests: 15, failures: 0, errors: 0, skipped: 0

## Android Lint (debug)
- Warning: 22

## Release APK
```
file: app/build/outputs/apk/release/app-release.apk
size_bytes: 1820676
sha256: 41ad81eef36f1352b176b814a60ff1a043a21cb8af4e8f3ce609f17cc5941384

--- apksigner verify
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v3.2 scheme (APK Signature Scheme v3.2): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
V2 Signer: certificate DN: CN=Independent Browser (temporary CI key)
V2 Signer: certificate SHA-256 digest: d798d15e79303b118ad50516d4e5d95be3d247c57a00da55d128d7b04e76639f
V2 Signer: certificate SHA-1 digest: b8a6b7e643c54ae98f04bbda58fdac438775a4fc
V2 Signer: certificate MD5 digest: 0c311ac01aed601d1e8a857bc766d2eb
V2 Signer: key algorithm: RSA
V2 Signer: key size (bits): 3072
V2 Signer: public key SHA-256 digest: 4504469410aef2df25865bcddbfa80848270fc9a9287f29ad2f6eae9856881bd
V2 Signer: public key SHA-1 digest: 62c5c8c77e32f40b30a0b931e4ec5ef275100399
V2 Signer: public key MD5 digest: 65ae6f6a641e3f4d985acff2b5db6ca6

--- aapt2 dump badging (selected lines)
package: name='io.github.lejiyo09.independentbrowser' versionCode='10000' versionName='1.0.0' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
minSdkVersion:'26'
targetSdkVersion:'35'
uses-permission: name='android.permission.INTERNET'
uses-permission: name='android.permission.WRITE_EXTERNAL_STORAGE' maxSdkVersion='28'
uses-permission: name='android.permission.CAMERA'
uses-permission: name='android.permission.RECORD_AUDIO'
uses-permission: name='android.permission.MODIFY_AUDIO_SETTINGS'
uses-permission: name='io.github.lejiyo09.independentbrowser.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
application-label:'독립 브라우저'
launchable-activity: name='io.github.lejiyo09.independentbrowser.MainActivity'  label='' icon=''
uses-permission: name='android.permission.READ_EXTERNAL_STORAGE' maxSdkVersion='28'
feature-group: label=''
  uses-feature-not-required: name='android.hardware.camera'
  uses-feature-not-required: name='android.hardware.microphone'
  uses-feature-not-required: name='android.hardware.touchscreen'
supports-screens: 'small' 'normal' 'large' 'xlarge'
densities: '160' '240' '320' '480' '640' '65534'

native_code: none (same APK runs on Intel/AMD and ARM Chromebooks)
```

## Emulator API 30
```
api_level=30
android_release=11
webview=Current WebView package (name, version): (com.google.android.webview, 83.0.4103.106)
screen=Physical size: 2560x1800
instrumentation=FAIL
release_install=pass
release_launch=pass (pid 6579, no crash)
release_page_rendered=pass (UI tree contains 'Example Domain')
```
Instrumentation output (tail):
```

io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest:........
Error in invalidCertificateIsBlocked(io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest):
java.lang.AssertionError: Timed out after 45000ms waiting for: certificate error page
	at io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest.waitUntil(BrowserInstrumentedTest.kt:190)
	at io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest.waitUntil$default(BrowserInstrumentedTest.kt:184)
	at io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest.invalidCertificateIsBlocked(BrowserInstrumentedTest.kt:115)
.

Time: 81.682
There was 1 failure:
1) invalidCertificateIsBlocked(io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest)
java.lang.AssertionError: Timed out after 45000ms waiting for: certificate error page
	at io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest.waitUntil(BrowserInstrumentedTest.kt:190)
	at io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest.waitUntil$default(BrowserInstrumentedTest.kt:184)
	at io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest.invalidCertificateIsBlocked(BrowserInstrumentedTest.kt:115)

FAILURES!!!
Tests run: 10,  Failures: 1


```
![release smoke test API 30](release-smoke-api30.png)

## Emulator API 34
```
api_level=34
android_release=14
webview=Current WebView package (name, version): (com.google.android.webview, 113.0.5672.136)
screen=Physical size: 2560x1800
instrumentation=FAIL
release_install=pass
release_launch=pass (pid 6277, no crash)
release_page_rendered=pass (UI tree contains 'Example Domain')
```
Instrumentation output (tail):
```

io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest:........
Error in invalidCertificateIsBlocked(io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest):
java.lang.AssertionError: Timed out after 45000ms waiting for: certificate error page
	at io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest.waitUntil(BrowserInstrumentedTest.kt:190)
	at io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest.waitUntil$default(BrowserInstrumentedTest.kt:184)
	at io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest.invalidCertificateIsBlocked(BrowserInstrumentedTest.kt:115)
.

Time: 109.345
There was 1 failure:
1) invalidCertificateIsBlocked(io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest)
java.lang.AssertionError: Timed out after 45000ms waiting for: certificate error page
	at io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest.waitUntil(BrowserInstrumentedTest.kt:190)
	at io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest.waitUntil$default(BrowserInstrumentedTest.kt:184)
	at io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest.invalidCertificateIsBlocked(BrowserInstrumentedTest.kt:115)

FAILURES!!!
Tests run: 10,  Failures: 1


```
![release smoke test API 34](release-smoke-api34.png)
