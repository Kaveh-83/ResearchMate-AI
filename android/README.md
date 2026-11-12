# Android application

This directory contains the native Kotlin + Jetpack Compose Android client. It does not load the Streamlit website.

## Verified build

- CI build: successful on 2026-11-11
- Application ID: `ai.researchmate.mobile`
- Version: `0.2.0` (versionCode 2)
- Minimum Android: API 26 (Android 8.0)
- Compile/target SDK: API 36
- UI: Jetpack Compose + Material 3, Persian-first RTL
- API base URL compiled into the CI APK: `https://researchmate-ai.kavehjafari710.workers.dev`

The CI artifact is a debug APK for testing, not a signed Play Store release.

## Implemented

- Native home, search, and projects navigation.
- Persian RTL layout and custom launcher icon.
- OpenAlex literature-search client and result cards.
- Build-time API base URL configuration through `-PAPI_BASE_URL=...`.
- Clear user-facing messages for API/provider rate-limit errors.

## Current service status

The Cloudflare Worker is deployed and its `/v1/health` endpoint returns HTTP 200. The live literature-search endpoint currently returns HTTP 429 with the explicit message that the production Worker is not receiving the `OPENALEX_API_KEY` secret. The app build is correctly configured; search remains unavailable until that server-side secret is added to the exact Worker `researchmate-ai`.

## Not implemented yet

- Room database and persistent local projects/library.
- PDF import/storage and full-text retrieval.
- PDF extraction/OCR.
- AI synthesis and research-gap analysis.
- Report export.
- The current Projects screen is a placeholder, not persistent storage.

## Build

From this directory:

```bash
gradle assembleDebug --no-daemon -PAPI_BASE_URL=https://researchmate-ai.kavehjafari710.workers.dev
```

Do not embed provider API keys or secrets in the APK. Keep those on the server.
