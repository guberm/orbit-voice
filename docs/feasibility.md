# Orbit Voice: verified integration constraints

Checked on October 12, 2026 against official OpenAI and Android documentation.

## Requested product

A Wear OS voice assistant with spoken questions and answers, multi-turn conversation,
OpenAI account sign-in, history visible in ChatGPT on other devices, low latency,
a quick-launch tile, a custom app icon, and a GitHub source repository and release.

## Findings

| Requirement | Supported route and limits |
| --- | --- |
| Sign in using a ChatGPT account without an API key | The official open-source Sign in with ChatGPT flow provides dynamic OAuth registration, PKCE, identity validation, and eligible ChatGPT plan usage. Account eligibility and actual authorization must be checked live. |
| Spoken questions | Wear OS provides system speech recognition. Speech service availability, language support, and offline support depend on the device. The ChatGPT plan usage flow itself does not accept audio inputs or transcription requests. |
| Spoken answers | Implemented using Android TextToSpeech and a sentence queue. Physical speech playback, installed voices, and watch audio output still require device verification. |
| Multi-turn dialogue | Send the required history in the Responses API input. Local persistence is possible; the app must keep account histories separate. |
| History in the ChatGPT account | The documented open-source flow does not grant access to ChatGPT conversations or account context and requires store=false. WebSocket continuation provides no persistent conversation storage. No supported route for the requested ChatGPT history synchronization was established. |
| Low latency | Streaming HTTP/SSE is documented for this OAuth flow. Responses WebSocket continuation is documented with connection-local state. This does not establish access to speech-to-speech Realtime through the same OAuth grant. |
| Wear OS quick launch | A native Wear OS Tile can launch the assistant. |

## Accepted scope and implementation

The user explicitly selected OpenAI account sign-in with local history. The app
implements encrypted phone history with resume, delete and Markdown export.

The implemented design is a native Wear OS client plus an Android companion for
browser-based account sign-in and streamed Responses requests.
The documented OAuth redirect is HTTP loopback on 127.0.0.1 with the path
/auth/callback. Its practical operation on Android must be verified; browser support
on watches must not be assumed. Credentials should stay in protected app storage,
never in source control, URLs, logs, or release artifacts.

## Current artifacts and verification

- Original artwork is integrated into both signed APKs and adaptive launcher icons.
- 29 JVM tests and 13 phone instrumented tests passed, including local HTTPS
  streaming, cancellation, context replay and encrypted persistence.
- Phone and Wear OS release builds and lint passed. Both APKs use the same
  release certificate; the private signing key stays outside the repository.
- The offline watch UI was inspected on a 384×384 Wear OS 6 round emulator.
  The talk button remains visible, and setup guidance, Options and refresh work.
- Real account authorization, live inference, paired-device transport and physical
  voice conversation have not been verified. See [testing evidence](testing.md).

## Primary sources

- https://developers.openai.com/siwc/quickstart
- https://developers.openai.com/siwc/token-sharing-open-source
- https://developers.openai.com/siwc/token-sharing-open-source/sign-in
- https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference
- https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions
- https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations
- https://developer.android.com/training/wearables/user-input/voice
- https://developer.android.com/training/wearables/data/client-types
- https://developer.android.com/jetpack/androidx/releases/wear-tiles
