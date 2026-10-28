# CI report — run #5

- commit: `cfc5988f91a08e414535cb11c61ab8fd093aedf7` (main, event: push)
- run: https://github.com/lejiyo09/topsecret/actions/runs/40609026100
- app version: 1.0.1 · release signing: ephemeral

| job | result |
|---|---|
| build (compile, unit tests, lint, APKs) | success |
| device-test (emulator API 30 + 34) | failure |
| publish (GitHub Release) | skipped  |

## JVM unit tests
- tests: 16, failures: 0, errors: 0, skipped: 0

## Android Lint (debug)
- Warning: 22

## Release APK
```
file: app/build/outputs/apk/release/app-release.apk
size_bytes: 1820792
sha256: 794ea992d53e5f8310941a6f92f63ab795473a968ada95209e5cf5afb34dc45c

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
V2 Signer: certificate SHA-256 digest: 348a5943cd004ce26c0f3aa8d3ccd2b149cc89ac49ce14e913027f23d587d4a9
V2 Signer: certificate SHA-1 digest: 84f7656b6db86dbd10903c1bb5fbd5f5d5d33d41
V2 Signer: certificate MD5 digest: 132cf19dcd2dca298094d52ff8a40765
V2 Signer: key algorithm: RSA
V2 Signer: key size (bits): 3072
V2 Signer: public key SHA-256 digest: 4d8d4b0de9c839b2018e2964582c7c34398ad84adfcb0635f8e5fa8187817318
V2 Signer: public key SHA-1 digest: ba671718a1f8cbabfcd4baaf4b4c7decddc243a8
V2 Signer: public key MD5 digest: bd2a7b6608b0b384879d3b15158564a0

--- aapt2 dump badging (selected lines)
package: name='io.github.lejiyo09.independentbrowser' versionCode='10001' versionName='1.0.1' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
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
instrumentation=pass (OK (10 tests))
release_install=pass
release_launch=pass (pid 6234, no crash)
release_page_rendered=pass (UI tree contains 'Example Domain')
```
Instrumentation output (tail):
```

io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest:..........

Time: 35.706

OK (10 tests)


```
![release smoke test API 30](release-smoke-api30.png)

## Emulator API 34
```
api_level=34
android_release=14
webview=Current WebView package (name, version): (com.google.android.webview, 113.0.5672.136)
screen=Physical size: 2560x1800
instrumentation=pass (OK (10 tests))
release_install=pass
release_launch=FAIL (pid '')
release_page_rendered=unknown (WebView text not in accessibility tree)
```
Instrumentation output (tail):
```

io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest:..........

Time: 61.65

OK (10 tests)


```
![release smoke test API 34](release-smoke-api34.png)
