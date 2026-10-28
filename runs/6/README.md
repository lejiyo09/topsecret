# CI report — run #6

- commit: `e213a8080b668a459fcdd4e131a7a37453060f40` (main, event: push)
- run: https://github.com/lejiyo09/topsecret/actions/runs/40609668720
- app version: 1.0.1 · release signing: ephemeral

| job | result |
|---|---|
| build (compile, unit tests, lint, APKs) | success |
| device-test (emulator API 30 + 34) | success |
| publish (GitHub Release) | success exists |

## JVM unit tests
- tests: 16, failures: 0, errors: 0, skipped: 0

## Android Lint (debug)
- Warning: 22

## Release APK
```
file: app/build/outputs/apk/release/app-release.apk
size_bytes: 1820792
sha256: 001db22d7a10cad0e2a6225f57b824a0ddec829abeb8fbf392e4f0cbb7e59616

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
V2 Signer: certificate SHA-256 digest: 3126f2b978b8ef872baa62d30ed0cfea91286f43c1799acbcff69817114e444f
V2 Signer: certificate SHA-1 digest: 3e01a97d849114f826baba00462d47957a80d68a
V2 Signer: certificate MD5 digest: 723091c9c1cbdf1d531718435501df90
V2 Signer: key algorithm: RSA
V2 Signer: key size (bits): 3072
V2 Signer: public key SHA-256 digest: 83051d8f4eb1be8d487605478024f5dc1fee7ee91c3f66f9b6c5f19fc451585d
V2 Signer: public key SHA-1 digest: cfef9ca288e32dae54789f57288489f2dfa606cf
V2 Signer: public key MD5 digest: 7ec06153238a77979ef552bfecd7d77f

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
release_launch=pass (pid 5928 after 30 s, no crash)
release_page_rendered=pass (UI tree contains 'Example Domain')
```
Instrumentation output (tail):
```

io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest:..........

Time: 37.85

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
release_launch=pass (pid 5485 after 30 s, no crash)
release_page_rendered=unknown (WebView text not in accessibility tree)
```
Instrumentation output (tail):
```

io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest:..........

Time: 66.643

OK (10 tests)


```
![release smoke test API 34](release-smoke-api34.png)
