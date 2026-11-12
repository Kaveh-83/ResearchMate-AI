# Android application

This directory contains the native Kotlin + Jetpack Compose Android client. It does not load the Streamlit website.

## Version 0.4.0

- Application ID: \`ai.researchmate.mobile\`
- Version: \`0.4.0\` (versionCode 4)
- Minimum Android: API 26 (Android 8.0)
- Compile/target SDK: API 36
- UI: Jetpack Compose + Material 3, Persian-first RTL
- API base URL is compiled into the CI APK: \`https://researchmate-ai.kavehjafari710.workers.dev\`

The CI artifact is a debug APK for testing, not a signed Play Store release.

## Implemented in 0.4.0

- Native home, search, prompt studio, and projects navigation.
- OpenAlex literature search through the existing Cloudflare Worker.
- Per-paper citation count and approximate citations-per-year band.
- Paper document type (research article/review when supplied by OpenAlex).
- Abstract reconstruction from OpenAlex inverted-index metadata when available.
- Per-paper action to prefill the detailed prompt studio with bibliographic metadata and abstract.
- Detailed Persian-first prompt generator covering search protocol, inclusion/exclusion criteria, paper-level extraction, evidence comparison, candidate research gaps, citation integrity, and a separate critical-review pass.
- One-tap copy of the generated prompt.
- Build-time API base URL configuration.

## Important interpretation notes

The displayed citation band is a rough citation-activity indicator derived from the OpenAlex citation count divided by paper age. It is not a normalized field percentile, a measure of methodological quality, or a journal quartile. Q1–Q4 and impact factor must be verified separately from a trusted journal-metrics source and are not inferred by this app.

The abstract is reconstructed from OpenAlex metadata. It is not a retrieved or parsed PDF. A prompt generated from a search result is a task instruction, not evidence that the paper has already been fully read or analyzed.

## Not implemented yet

- Room database and persistent local projects/library.
- PDF import/storage and verified full-text retrieval.
- PDF extraction/OCR and page-level evidence locators.
- Actual AI-provider execution, full-text synthesis, and automated research-gap analysis.
- Formal journal quartile lookup.
- Report export (PDF/DOCX).
- Signed release APK and physical-device QA.

## Build

\`\`\`bash
gradle assembleDebug --no-daemon -PAPI_BASE_URL=https://researchmate-ai.kavehjafari710.workers.dev
\`\`\`

Do not embed provider API keys or secrets in the APK. Keep those on the server.
