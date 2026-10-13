# Third-party speech components

- whisper.cpp v1.9.5, commit `d1be6fde11ac6e0407606b4e42fe72d34add8037`:
  https://github.com/ggml-org/whisper.cpp — MIT, see the submodule LICENSE.
- OpenAI Whisper multilingual base model: https://github.com/openai/whisper — MIT.
  Converted model source: https://huggingface.co/ggerganov/whisper.cpp,
  revision `5359861c739e955e79d9a303bcbc70fb988958b1`.
- Silero VAD: https://github.com/snakers4/silero-vad — MIT.
  Converted model source: https://huggingface.co/ggml-org/whisper-vad,
  revision `9ffd54a1e1ee413ddf265af9913beaf518d1639b`.
- Test speech recordings: https://huggingface.co/spaces/k2-fsa/spoken-language-identification,
  revision `3c09c87fdfa5e98063dadff5ce3ffea173d0fa76`, declared Apache-2.0.
  Used only by instrumented tests, not bundled in release APKs.

Model binaries are downloaded and SHA-256-verified by `gradle/speech-models.gradle`.
The full MIT license texts for bundled speech components are provided in
`mobile/src/main/assets/licenses/` and ship inside the phone APK.
