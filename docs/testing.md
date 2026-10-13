# Verification evidence

Local checks performed on October 12, 2026. This document distinguishes tested
application behavior from account/device integration that still needs a real user.

| Check | Actual result |
| --- | --- |
| JVM core tests | 29 passed. OAuth/PKCE transaction checks, signed JWT identity checks, SSE completion/errors, conversation replay, speech chunking, and stale/out-of-order message protection. |
| Android instrumented tests | 13 passed on Medium_Phone_API_36.1, Android 16. |
| Android Keystore storage | Round trip, plaintext absence, tamper detection, file-swapping rejection, account isolation, restart/resume and local deletion passed. |
| Local HTTPS request pipeline | Account-specific catalog JSON, two completed context-preserving turns, interrupted stream, late usage-limit error, account mismatch and request cancellation passed using MockWebServer with synthetic credentials. |
| Phone UI | Empty-history navigation, the system back action, and the explanatory dialog passed executable UIAutomator tests. |
| ARTEMIS exploration | Initial phone screen, hierarchy, scrolling and buttons explored before test code was authored. A later Flash run hit a provider LLM timeout; it is not counted as a passed application check. |
| Debug builds and lint | Phone and watch builds passed; both lint tasks passed with warnings. Warnings include pinned dependency updates and intentionally English UI strings. |
| Signed release builds | Phone and watch release APKs passed apksigner verification with the same local RSA signing certificate. Private key stays outside the repository. SHA256SUMS.txt accompanies release assets. |
| Wear OS round-screen UI | Signed APK installed on Orbit_Wear_OS_6 (Android 16), 384×384 round display. ARTEMIS screenshot/hierarchy and ADB interactions verified the visible talk button, offline setup guard, Options scrolling and Refresh connection. The autonomous Flash task exhausted its provider quota and is not counted as a passed agent run. |

The screenshots in `screenshots/phone.png` and `screenshots/watch.png` are from the
actual apps on their respective emulators. Test storage uses isolated cache directories;
no real account credentials are needed or used.

## Not verified live

- A real user's browser authorization and plan-sharing consent.
- Current account eligibility, quota, model selection and live OpenAI inference.
- Bluetooth/Wear OS Data Layer pairing between a physical phone and watch.
- Physical microphone recognition, installed speech languages, watch speaker or
  Bluetooth audio output, and repeated hands-free voice turns.
- Latency, battery usage, device-specific background service policy, and behavior
  on every supported Android/Wear OS version.
- Tile rendering and invocation by the Wear OS tile host (the provider compiles).

These are release limitations. Successful builds and synthetic HTTPS tests do not
prove a complete spoken OpenAI conversation on physical devices.

Release certificate SHA-256 (both APKs):
`5a6ddb109e098abe07368a587bcd64cea464dcf314c4b565bf26bbee5fc559f5`.

## Reproduce the checks

```powershell
.\gradlew.bat :core:test :mobile:lintDebug :wear:lintDebug :mobile:assembleDebug :wear:assembleDebug
$env:ANDROID_SERIAL = 'YOUR_DEDICATED_PHONE_EMULATOR'
.\gradlew.bat :mobile:connectedDebugAndroidTest
.\scripts\build-release.ps1
```

Reports are generated under `core/build/reports/tests`,
`mobile/build/reports/androidTests/connected`, and each app module's `build/reports`.
GitHub Actions repeats the JVM, lint and debug-build checks. Local device tests
are separate from hosted CI; only a completed successful run should be reported
as passed hosted CI.
