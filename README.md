# ResearchMate AI — Native Android

ResearchMate AI is being developed as a native Android application using Kotlin and Jetpack Compose.

## Project structure

- `android/` — Android application source code.
- `backend/edge-worker/` — Cloudflare Worker API for scholarly literature search.
- `docs/` — Architecture, UX, deployment, and roadmap documentation.
- `.github/workflows/` — Android APK build, Worker deployment, and API validation.

## Current capabilities

- Native Android shell with Home, Search, and Projects navigation.
- Literature search client connected to the ResearchMate Worker endpoint.
- OpenAlex-backed discovery of scholarly literature through the Worker.

## Current limitations

The following capabilities are planned and are not yet fully implemented: persistent local projects, PDF import and extraction, OCR, AI-assisted synthesis, research-gap analysis, and report export. The Projects screen is currently a placeholder.

## Build the Android app

The debug APK is built by GitHub Actions using the workflow **Android debug APK**. Run it manually from the Actions tab, or push a change under `android/` on the `main` branch.

The workflow injects the public Worker base URL at build time. No API secret is embedded in the APK.

## Deploy the Worker

The Worker source is in `backend/edge-worker/`. The **Deploy ResearchMate Worker** workflow deploys it to Cloudflare using the GitHub repository secrets:

- `CLOUDFLARE_API_TOKEN`
- `CLOUDFLARE_ACCOUNT_ID`

The OpenAlex API key must be configured as a Worker runtime Secret in Cloudflare, named `OPENALEX_API_KEY`. Do not commit it to this repository.

## Streamlit predecessor

The former Streamlit implementation is preserved on the Git branch `backup/streamlit-2026-11-12`. It is intentionally excluded from the active application source tree.
