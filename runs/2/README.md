# CI report — run #2

- commit: `72cb02ee8528b0f2b6803ee095006fe564386447` (main, event: push)
- run: https://github.com/lejiyo09/topsecret/actions/runs/40606347121
- app version: 1.0.0 · release signing: ephemeral

| job | result |
|---|---|
| build (compile, unit tests, lint, APKs) | success |
| device-test (emulator API 30 + 34) | success |
| publish (GitHub Release) | success created |

## JVM unit tests
- tests: 15, failures: 0, errors: 0, skipped: 0

## Android Lint (debug)
- Warning: 22

## Release APK
```
file: app/build/outputs/apk/release/app-release.apk
size_bytes: 1820676
sha256: f85c5bfd6ce25a4b743f89316e10cc63d6390acafbb493b2ab8547a4a673545b

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
V2 Signer: certificate SHA-256 digest: 5c9046ca095f9b1cad76b13bc64c4e4a196f6ce7e579613229c6263500304409
V2 Signer: certificate SHA-1 digest: 2d4a3b0c6171221c71e4431d8d94f306ecf5955b
V2 Signer: certificate MD5 digest: 026ba9b5d019b7b9c075e4b32369b8fd
V2 Signer: key algorithm: RSA
V2 Signer: key size (bits): 3072
V2 Signer: public key SHA-256 digest: f12c02e8c8cdc9bb9d0ddc02745e1eb69524a5b09db1a1392488bc88718cd3f9
V2 Signer: public key SHA-1 digest: 254104402711bcc92951673be1e997ab6a9b8f5e
V2 Signer: public key MD5 digest: df4189621897a9934fd18ef684e52892

--- aapt2 dump badging (selected lines)
package: name='io.github.lejiyo09.independentbrowser' versionCode='10000' versionName='1.0.0' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
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
release_launch=pass (pid 5421, no crash)
release_page_rendered=pass (UI tree contains 'Example Domain')
```
Instrumentation output (tail):
```

io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest:..........

Time: 28.838

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
release_launch=pass (pid 5555, no crash)
release_page_rendered=unknown (WebView text not in accessibility tree)
```
Instrumentation output (tail):
```

io.github.lejiyo09.independentbrowser.BrowserInstrumentedTest:..........

Time: 70.208

OK (10 tests)


```
![release smoke test API 34](release-smoke-api34.png)
