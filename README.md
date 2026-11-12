# ResearchMate AI — Native Android Workspace

ResearchMate AI is a Persian-first native Android research workspace built with Kotlin and Jetpack Compose. Version **0.6.0** introduces a dark-first interface and a local research library.

## v0.6.0 capabilities

- Dark-first Material 3 interface with Persian RTL layout.
- OpenAlex-backed open-access literature search through the ResearchMate Cloudflare Worker.
- Multi-select article handoff to the Scientific Writing Prompt Studio.
- Local SQLite database for papers, research projects, notes, tags, and evidence records.
- One-time migration of v0.5 project records from SharedPreferences to SQLite.
- PDF import through Android's document picker, persisted URI access, and first-page preview through Android PdfRenderer. Full-document reading can be handed to an installed PDF app.
- Manual evidence capture with variable, value, unit, page, and excerpt.
- Comparison view for up to four saved papers.
- Preliminary keyword-frequency signals for idea exploration. These are heuristics, not semantic gap detection.
- Local export to JSON backup, CSV, BibTeX, and Markdown.
- Existing prompt studio retained, including structured writing prompts, evaluation rubric, and bounded revision instructions.

## Privacy and cost model

- Research projects, library records, notes, and evidence stay in the app's local SQLite database.
- Literature search sends the search query and year range to the ResearchMate Worker and OpenAlex.
- Importing a PDF stores a document URI grant; the PDF bytes are not uploaded by this app.
- JSON backup includes metadata, notes, and the stored PDF URI, but does **not** package the PDF file itself. The original PDF must remain available at its source location.
- No AI inference API is called by v0.6.0. Prompt generation is local. The Idea Lab's frequency signals are local heuristics and must not be presented as confirmed research gaps.
- No API secret is embedded in the Android APK.

## Known limits in v0.6.0

- PDF preview renders the first page only; text extraction, OCR, and table extraction are not yet implemented.
- Evidence extraction is manual. The app does not yet infer results or methods from PDFs.
- The comparison view compares registered metadata, notes, and manually captured evidence; missing values remain explicit.
- AI semantic synthesis, model execution, provider adapters, and optional local-model connectivity are not active.
- Cloud sync, multi-device accounts, and collaborative projects are not active.
- Search currently relies on the configured OpenAlex-backed Worker and its rate limits.
- The app has not yet been verified on a physical device across Iranian mobile networks.

## Repository layout

- `android/` — native Android app.
- `backend/edge-worker/` — Cloudflare Worker API.
- `docs/` — architecture, UX, deployment, and release notes.
- `.github/workflows/` — Android APK build and Worker deployment workflows.

## Build

The **Android debug APK** GitHub Actions workflow builds the debug APK. It injects the public Worker base URL through `API_BASE_URL`; no provider key is included in the APK.

To build locally, use Android Studio or run Gradle from the `android/` directory with Android SDK 36 and Java 17 installed:

```bash
gradle assembleDebug -PAPI_BASE_URL=https://researchmate-ai.kavehjafari710.workers.dev
```

## Worker deployment

The Worker source is in `backend/edge-worker/`. The deployment workflow uses repository secrets:

- `CLOUDFLARE_API_TOKEN`
- `CLOUDFLARE_ACCOUNT_ID`

The OpenAlex key is an optional Worker runtime secret named `OPENALEX_API_KEY`. Never commit it to the repository.

## Previous Streamlit application

The Streamlit predecessor is retained on `backup/streamlit-2026-11-12` and is not part of the active native Android build.
