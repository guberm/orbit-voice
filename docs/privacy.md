# Privacy

- The Android phone contacts auth.openai.com for account authorization, token
  renewal, and sign-out, and api.openai.com for the model catalog and streamed responses.
- Automatic speech recognition records bounded microphone segments and sends watch
  audio to the paired phone through an ephemeral Wear OS ChannelClient connection.
  Multilingual Whisper and Silero VAD run locally on the phone. Audio is held in
  memory and discarded after the turn; it is not saved to history or sent to OpenAI.
  Explicit system-service language options use Android's installed recognizer,
  which may send audio to its service provider.
- Recognized text, selected conversational context, and generated responses are
  processed by OpenAI under the account's authorized ChatGPT plan permissions.
- Histories are stored locally in encrypted app storage, separated by account
  registration. They are not synchronized with ChatGPT history.
- The paired phone and watch exchange audio, transcript text, and conversation
  control events through Google's Wear OS Data Layer. No OpenAI access, refresh,
  or ID token is sent to the watch. Model weights are bundled only with the phone APK.
- Android's installed TextToSpeech engine speaks answers. Voice downloads and
  network usage depend on the selected engine and language.
- Orbit Voice includes no analytics, advertisements, telemetry, or application
  trace uploads. Android, Google services, and OpenAI retain their own policies.
- Export is an explicit user action. Exported Markdown is plaintext and may
  include sensitive conversation content. Choose its destination accordingly.
- Deleting a conversation removes the local app copy. It cannot delete copies
  the user has exported.
- Disconnecting an account attempts to revoke its renewable OAuth session.
  When remote revocation fails, the app reports that fact and offers the
  ChatGPT settings link for manual disconnection.
