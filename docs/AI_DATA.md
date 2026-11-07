# AI data-use statement

AI is an optional arrangement assistant. It is not required to import audio, analyze tempo, generate any difficulty, preview a chart or export a `.pez` package.

## Data sent

When the user enables AI and saves a valid HTTPS API configuration, the app sends a compact JSON summary containing:

- estimated BPM and tempo confidence;
- audio duration;
- onset count and a sampled subset of onset timestamps (at most 160 values);
- up to 40 coarse energy segments with start, end, energy and label.

The request does **not** contain raw audio samples, a local document URI, the original filename, Android identifiers or user account information.

## Data returned

The service is asked to return four numeric arrangement hints: density multiplier, Hold bias, Flick bias and syncopation. The app validates the response, clamps the hints to local ranges, and passes them to the deterministic chart generator. AI cannot return arbitrary RPE JSON or execute code inside the app.

## Failure and fallback

The client uses timeouts and a single retry for transient network failures / HTTP 408 / 429 / 5xx. Invalid output, non-transient failures, absent key or network unavailability all result in the offline generator being used. AI failure does not block chart generation.

## Endpoint and key handling

Only HTTPS URLs are accepted. Redirects are not followed. The API key is sent as a Bearer authorization header to the configured origin and stored locally as AES-GCM ciphertext backed by Android Keystore. The user is responsible for checking that the configured provider is trusted and permitted for their use.

## Provider independence

The application uses OpenAI-compatible `/models` and `/chat/completions` routes. Not every vendor supports both routes or JSON-mode responses. Users may manually enter the model ID when listing models is unsupported. A failed connection test does not disable offline functionality.
