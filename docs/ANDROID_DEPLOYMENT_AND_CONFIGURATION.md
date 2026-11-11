# Android app configuration and deployment boundary

## User-facing behavior

Ordinary users install the APK and use the shared ResearchMate service. They do not create a Cloudflare account, paste a Worker URL, or provide an OpenAlex key.

The API endpoint is compiled into each app build. It is intentionally not editable in the ordinary search screen.

## Build-time configuration

The endpoint is supplied by the build operator:

```bash
./gradlew :app:assembleDebug -PAPI_BASE_URL=https://your-service.example
```

If `API_BASE_URL` is omitted, the app remains installable and local UI remains usable, but online literature search displays a clear not-configured message.

Do not put provider secrets in `API_BASE_URL`. This value is a public service address, not a credential. OpenAlex/provider credentials belong on the server side.

## Hosting independence

The Android client calls the stable ResearchMate API contract, not Cloudflare-specific APIs. The host can be replaced without changing screens or research logic, provided the replacement implements the same routes and response schema.

## Privacy defaults

- Imported PDFs and local project data remain on-device by default.
- Search terms are sent to the ResearchMate API when the user starts an online search.
- Do not upload full paper text or personal documents without a separate explicit consent step.
- Never log API keys, authorization headers, or uploaded document contents.

## Current implementation boundary

This is a configuration and UX foundation only. It does not mean a public API has been deployed. The Worker remains provisional until deployed and tested from Iranian mobile and fixed-line networks.
