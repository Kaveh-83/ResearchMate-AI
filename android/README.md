# Android application

This directory contains the native Android client. It is a Kotlin + Jetpack Compose application and does not load the Streamlit website.

## Verified build status

- The debug APK was built successfully by GitHub Actions on 2026-11-11.
- Application ID: `ai.researchmate.mobile`
- Version: `0.1.0` (versionCode 1)
- Minimum Android: API 26 (Android 8.0)
- Compile/target SDK: API 36
- UI: Jetpack Compose + Material 3, Persian-first RTL
- Build command: `gradle assembleDebug --no-daemon`

The CI workflow publishes a short-lived debug APK artifact. It is an installable development build, not a signed Play Store release.

## Implemented in this build

- Native home, search, and projects navigation.
- Persian RTL layout.
- OpenAlex literature-search client and result cards.
- Build-time API base URL configuration through `-PAPI_BASE_URL=https://your-service.example`.
- Clear not-configured state when no API host is compiled into the build.

## Not implemented yet

- The shared API service is not deployed.
- Search will not work in the distributed APK until a reachable API base URL is configured at build time.
- Local Room database, PDF import/storage, full-text retrieval, PDF extraction/OCR, AI synthesis, research-gap analysis, and report export are not yet implemented.
- The current Projects screen is a placeholder, not persistent storage.

## Build

From this directory:

```bash
gradle assembleDebug --no-daemon
```

To configure the API host:

```bash
gradle assembleDebug --no-daemon -PAPI_BASE_URL=https://your-service.example
```

Do not embed provider API keys or secrets in the APK. Keep those on the server.
