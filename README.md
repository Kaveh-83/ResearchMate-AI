# ResearchMate AI — Native Android Research Workspace

ResearchMate AI is a Persian-first native Android research workspace built with Kotlin and Jetpack Compose. Version **0.7.0** focuses on turning the literature search screen into a multi-provider academic discovery engine.

## v0.7.0 capabilities

- Multi-disciplinary search profiles for materials science, engineering, computer science, medicine, life sciences, physics, chemistry, environment, agriculture, social sciences, and humanities.
- Server-side provider adapters for OpenAlex, Crossref, Semantic Scholar, Europe PMC, PubMed, and arXiv.
- Automatic source routing by discipline, or manual provider selection.
- Discovery of both open-access and subscription/restricted bibliographic records. The search no longer filters every result to Open Access only.
- Separate result views for records with a verified open full-text URL and records without a verified open copy.
- Advanced filters for publication year, author, journal, publisher, document type, minimum citations, access status, and sort order.
- DOI-first deduplication across sources with provider IDs and provenance retained.
- Citation counts attributed to each provider; counts are never added across sources.
- OpenAlex journal/source H-index and 2-year mean-citedness displayed only when returned by the source metadata.
- Provider-by-provider result counts, latency and error states; a failing source does not cancel the other sources.
- Multi-page discovery, multi-select saving, and handoff to the Scientific Writing Prompt Studio.
- Local SQLite research library, projects, notes, tags, manually captured evidence, PDF import/preview and local exports retained from v0.6.

## Current provider coverage

| Provider | Main coverage in v0.7 |
|---|---|
| OpenAlex | Broad multi-disciplinary works, sources, OA status, source-labelled citations, available journal/source summary indicators |
| Crossref | DOI and publisher-deposited scholarly metadata, publication dates, journal, publisher, references/citation metadata and license records |
| Semantic Scholar | Scholarly graph search, fields of study, paper citations, OA PDF metadata and paper details |
| Europe PMC | Biomedical/life-science literature, abstracts, MeSH terms, full-text links and citations |
| PubMed | Biomedical indexed records, publication types and identifiers via NCBI E-utilities |
| arXiv | Physics, mathematics, computer science and related preprints; preprints are labelled separately |

Provider coverage and API quotas vary. A provider status panel is included in the app so results are not presented as a complete search of every database when one is unavailable.

## Search quality and scientific integrity

- Open full-text, no verified open copy, and unknown access are not treated as interchangeable states.
- A record without an OA URL is not automatically proven to be paywalled.
- The app links to DOI/publisher or repository landing pages; it does not bypass paywalls, sign-in walls, DRM or subscription controls.
- Citation counts remain source-labelled and must not be added together.
- Journal-level metrics are separate from article-level citations. The app does not invent Q1–Q4, JIF or CiteScore values.
- Current OpenAlex source indicators may include H-index and two-year mean citedness; their source is labelled. Commercial Journal Citation Reports / Scopus indicators are not included unless a valid licensed data entitlement is configured.
- Search relevance is a discovery aid, not a peer-review or quality verdict.
- Metadata-only results are not described as full-text reviewed or scientifically analyzed.

## Privacy and cost model

- Research projects, library records, notes, and evidence stay in the app's local SQLite database.
- Search queries and search filters are sent to the ResearchMate Cloudflare Worker, which queries the selected scholarly APIs.
- Imported PDFs are not uploaded by the search service.
- No provider secret is embedded in the Android APK or committed source code.
- OpenAlex, Crossref, Europe PMC, PubMed and arXiv have documented public API access subject to their own quotas and policies. Optional API keys can be configured server-side to increase applicable limits.
- Semantic Scholar may apply anonymous request quotas. Its optional API key is a Cloudflare Worker secret.
- No paid publisher service or paid journal metric is enabled by default. Optional commercial publisher integrations require a separate entitlement and explicit configuration.

## Not included yet

- OpenAIRE Graph and DOAJ adapters; planned after a response-shape and live-stability validation.
- Scopus, Web of Science, IEEE Xplore, ScienceDirect, SpringerLink and other subscription publisher APIs.
- Paid Journal Impact Factor / CiteScore data. No commercial journal quartile or ranking values are fabricated.
- Complete journal explorer with ISSN-level filtering and a licensed metric history.
- Citation context classification, citing/reference graph UI, saved-search alerts, retraction monitoring and systematic-review screening workflow.
- Full-text PDF extraction, OCR, table/figure extraction, automatic evidence extraction and semantic synthesis.

## v0.6 local workspace retained

- Local SQLite database for papers, projects, notes, tags and evidence.
- PDF import through Android's document picker with first-page preview through Android PdfRenderer.
- Manual evidence capture with variable, value, unit, page and excerpt.
- Comparison of up to four saved papers.
- Preliminary keyword-frequency signals for idea exploration. These are heuristics, not semantic gap detection.
- JSON backup/restore, CSV, BibTeX and Markdown exports.
- No user PDF is sent to the literature Worker.

## Repository layout

- `android/` — native Android app.
- `backend/edge-worker/` — Cloudflare Worker API.
- `docs/` — architecture, UX, deployment and release notes.
- `.github/workflows/` — Android APK build and Worker deployment workflows.

## Build

The **Android debug APK** GitHub Actions workflow builds the debug APK and injects the public Worker base URL through `API_BASE_URL`.

To build locally, use Android Studio or run Gradle from the `android/` directory with Android SDK 36 and Java 17 installed:

```bash
gradle assembleDebug -PAPI_BASE_URL=https://researchmate-ai.kavehjafari710.workers.dev
```

## Worker deployment

The Worker source is in `backend/edge-worker/`. GitHub Actions uses repository secrets `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`.

Optional provider secrets belong only in Cloudflare Worker settings:
- `OPENALEX_API_KEY`
- `SEMANTIC_SCHOLAR_API_KEY`
- `NCBI_API_KEY`
- `CROSSREF_MAILTO` (a contact email, not a secret)

Never place these values in the APK, source code, or a public issue.

## Previous Streamlit application

The Streamlit predecessor is retained on `backup/streamlit-2026-11-12` and is not part of the active native Android build.
