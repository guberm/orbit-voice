# Security

Report security issues privately to the repository owner through GitHub.
Do not attach tokens, authorization callback URLs, account files, recordings,
conversation exports, or signing keys to public issues.

Orbit Voice uses the official OpenAI OAuth public-client registration flow.
An API key and a client secret are not required. OpenAI credentials remain on
the Android phone. Watch/phone messages contain conversation data, not credentials.
Tokens and local histories are encrypted at rest with an Android Keystore key;
Android cloud backup is disabled. Exported Markdown is intentionally plaintext
and is saved only to a destination the user chooses.

This is an independent application, not an official OpenAI product.
