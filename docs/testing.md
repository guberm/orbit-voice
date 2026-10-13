# Verification evidence

This document distinguishes tested application behavior from account/device
integration that still needs a real user.

## Companion residency, watch surfaces and recognition fixes (October 13, 2026)

Added after the 0.1.0-alpha.2 checkpoint; not tagged as a release.

| Check | Actual result |
| --- | --- |
| JVM core tests | 40 passed (`:core:test`). The speech endpoint set now covers a quiet utterance barely above the room noise (it must open a turn and must not be cut short), obvious silence (never opens one), a tap (not an utterance) and the ten-second cap on a noisy room. |
| Phone residency without the UI | After installing the phone APK with its UI never opened, the update broadcast armed `InferenceService` (`isForeground=true`, `types=0x00000010` connectedDevice) while the launcher held the foreground. |
| Watch session with the phone app closed | Launching exactly what the tile and the watch-face shortcut launch produced `Listening...` plus the account and model on the watch, supplied by the resident phone service. |
| Watch-face shortcut | `VoiceShortcutService` installs with `com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER` and the wear manifest declares `SUPPORTED_TYPES=ICON,SMALL_IMAGE,SHORT_TEXT,LONG_TEXT`. Placing it in a watch-face slot is the user's step; Wear has no programmatic add-tile or add-complication API. |
| Russian speech on the watch | Real watch audio transcribed correctly: `decode samples=42880 ms=4080` then `decoded lang=ru chars=15` ("Проверка связи."), followed by a spoken Russian answer ("Слышу тебя хорошо, связь в порядке."). |
| Measured cost of one turn | transfer 0.15-1.0 s; decoding 3.7-4.1 s for 2.7-3.7 s of audio; OpenAI first token 2.8-4.1 s, completion 2.9-4.8 s; speech engine start 2.6 s (5.2 s before the voice was pre-warmed when the transcript language arrives). |
| Defects found and fixed in this pass | (1) the audio path called `Engine.askAudio` on a null engine, so a watch turn hung with no reply; (2) a tile or shortcut launch failed with "Speech output is not ready" while TextToSpeech was still binding; (3) a relaunch while the task was open ignored `start_voice` (no `onNewIntent`); (4) trimming the captured tail by the silence counter discarded the loudest speech frames - the capture log counted speech while the payload sent was silence; (5) the onset gate reset on every sub-threshold frame, so quiet speech never opened a turn and its frames were dropped by the 0.3 s pre-roll - now the pre-roll keeps three seconds and the gate decays instead of resetting; (6) `VoiceSession.pause()` ran speech-engine calls on the UI thread and produced launch ANRs. |
| Privacy | The diagnostic audio dump and the recognized-text logcat line used while measuring were removed before the final build; recordings stay in memory and only recognized text is stored. |

Still unverified in this build:

- A complete spoken dialogue without pauses: recognition and the answer both work, but latency
  (about 10 s end to end) has not been reduced to conversational speed.
- The boot path: `adb shell am broadcast` cannot send the protected `BOOT_COMPLETED` action, so
  only the `MY_PACKAGE_REPLACED` arming path is proven.
- Watch-face shortcut rendering by a real watch face, and the tile inside the carousel.
- Latency, battery and thermal behaviour across a longer conversation.

## 0.1.0-alpha.2 checkpoint (October 13, 2026)

Offline multilingual recognition replaced the native automatic-language route, whose
failure on physical devices is recorded in the [voice architecture research](research/voice-architecture-2026-10-13.md).

| Check | Actual result |
| --- | --- |
| JVM core tests | 38 passed. OAuth/PKCE, signed JWT identity, SSE completion/errors, conversation replay, speech chunking, stale-response rejection, the watch speech endpoint and the audio wire format. |
| Android instrumented tests | 26 passed on a physical Pixel 9 Pro XL, Android 17. Includes the offline audio pipeline, Russian → English → Russian switching on recorded samples, silence handling, cancellation, encrypted storage and phone UI. |
| Speech endpoint regression | The shipped 0.1.0-alpha.1 threshold logic fails two regression tests (`.artifacts-endpoint-red.log`); the shipped logic passes all five (`.artifacts-endpoint-green.log`). |
| Offline recognition on real hardware | Russian speech recorded on a Pixel Watch 3 (`capture frames=382 peakRms=526 speech=true bytes=54400`) was transcribed to Cyrillic on the phone and shown on the watch. The same phrase spoken into the phone transcribed correctly once the phone's speech language was set to Automatic. |
| Recognition timing | 1.92 s of a real utterance decoded in 2.30 s on a Pixel 9 Pro XL (`OrbitAsr: decode samples=30720 threads=4 ms=2297`); watch to phone audio transfer about 1.2 s; model load from disk about 0.3 s. |
| Thread scaling | The same recorded-sample test took 11.9 s with 4 decode threads and 13.0 s with 6, so the shipped build keeps 4. |
| Lint and release builds | Both lint tasks and signed release builds passed; both APKs carry the alpha.1 certificate. |
| Blocked | The assistant's answer half of the loop could not be checked in this build: the account's ChatGPT plan limit was reached, and the app reported the usage-limit error on both devices. |

Not verified live in this checkpoint:

- A complete spoken dialogue, from speech to a spoken assistant answer, on paired
  physical devices.
- Mid-conversation Russian ↔ English switching on the watch, and names, numbers and
  short answers.
- Screen-off and lock behaviour, Tile host rendering, and latency or battery
  measurements across a longer conversation.

## 0.1.0-alpha.1 checkpoint (October 12, 2026)

Local checks performed on October 12, 2026. This is the original release checkpoint;
the offline recognition pipeline above did not exist yet.

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
