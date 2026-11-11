# ResearchMate AI — Android + Online Platform Architecture

**Status:** Initial architecture decision (ADR-001)  
**Target:** Android app for users primarily in Iran; zero recurring budget; limited free-tier quotas are acceptable.  
**Source project:** Existing Streamlit application on `main`.  
**Working branch:** `feature/android-native-platform`

## 1. Product decision

ResearchMate AI will become a native Android application backed by a replaceable online service. It will not embed or depend on Streamlit, and it will not be a WebView wrapper.

The Android application is the primary user interface. Online research discovery and server-assisted workflows are exposed through a versioned HTTPS API. The application keeps user projects, downloaded papers, and generated files locally by default. Cloud synchronization is a later opt-in capability, not a prerequisite for the first release.

The product is **online-first with useful local/offline capabilities**, not fully offline:
- Online: scholarly search, metadata lookup, OA full-text retrieval where legally available, and optional AI-provider requests.
- Local: project management, saved papers, prompt generation, file inspection, cached results, and exports where supported.
- If the service is unreachable, the app must show a clear connection state and preserve local work.

## 2. Constraints and non-goals

- Recurring infrastructure/API spend must remain $0 for the initial release.
- Free-tier quotas, rate limits, cold starts, and provider outages are acceptable if surfaced clearly.
- No claim of guaranteed VPN-free reachability is made until tested from multiple Iranian networks.
- No paid-only service, private API key embedded in the APK, or single-provider dependency.
- No bypass of paywalls or access controls. Retrieve only openly available content and respect source licenses.
- No semantic-analysis claims for keyword/pattern extraction.
- The app must not upload user PDFs or project files to a third party without an explicit user action.

## 3. Proposed architecture

### Android client
- Kotlin + Jetpack Compose, single-activity, layered UI/data/domain boundaries.
- Room for local projects, saved literature, jobs, and cached metadata.
- DataStore for preferences and user-supplied provider settings.
- Android Storage Access Framework for importing/exporting documents.
- WorkManager for resumable background retrieval and queued jobs.
- PDF/text extraction on-device where practical; enforce file-size and memory limits.
- API client with timeouts, retry/backoff, cancellation, and visible error states.

### Online service
- A versioned API contract independent of any hosting provider.
- Initial lightweight edge/API adapter can be evaluated on a free serverless tier.
- Heavy PDF parsing and large document processing should remain on-device in the first release; do not assume a tiny edge CPU allowance can run Python/scikit-learn/PyMuPDF workloads.
- Provider adapters isolate scholarly sources and optional LLM providers.
- No user documents retained on the server by default.
- Any shared service credential stays in server-side secrets; never ship it in the APK.

### Research/data providers
- OpenAlex for discovery and bibliographic metadata; use a user's own free OpenAlex key when needed, stored securely and never committed.
- Europe PMC and verified open-access locations as alternative retrieval sources.
- Preserve DOI, source URL, retrieval date, license/status, and evidence-access level.
- Cache metadata and successful retrievals to reduce quota use and improve resilience.

### AI layer
- Existing prompt generation remains deterministic and can run locally.
- AI-assisted semantic synthesis is a separate provider adapter, not something the current repository already implements.
- First implementation should support user-configured provider credentials (BYOK) and a no-AI prompt/export fallback.
- Investigate free model/API availability and reachability from Iran before selecting a default provider. Do not promise unlimited free AI inference.
- The user explicitly chooses whether source text is sent to an AI provider.

## 4. Hosting strategy: decision is provisional

Cloudflare Workers Free is a candidate for a thin API/edge adapter and static metadata endpoints, not for heavy document processing. Current published limits include 100,000 requests/day and 10 ms CPU per invocation on the Free plan; D1 Free has a 500 MB per-database limit and 5 GB total storage. These limits make it unsuitable as a drop-in Python research server.

Before adopting any host, run a reachability test from at least two Iranian ISPs/mobile networks and test:
1. app/API TLS handshake and response latency;
2. OpenAlex and Europe PMC reachability;
3. upload/download of a small OA document;
4. app update/download access;
5. service uptime and free-tier sleep/quota behavior.

A host is not accepted merely because its dashboard is accessible outside Iran. If no free host is reliably reachable, the app must still retain local functions and allow an alternative API base URL to be configured. Do not build a VPN circumvention feature.

## 5. Existing capability migration map

| Existing capability | Android / service destination | Initial priority |
|---|---|---|
| OpenAlex search and filters | API provider adapter + Compose search screen | P0 |
| OA metadata, DOI, authors, license | Kotlin domain models + Room cache | P0 |
| OA PDF/XML retrieval | API adapter + SAF download/cache | P0 |
| PDF/text extraction | Android-side parser; structured extraction interface | P0 |
| Rule-based candidate signals | Kotlin domain module, clearly labeled heuristic | P1 |
| Prompt Studio | Kotlin local module, full feature parity | P0 |
| Evidence/corpus comparison | Local project and report model | P1 |
| CSV/JSON/BibTeX/XLSX/ZIP exports | Android export module | P1 |
| DOCX/PDF review exports | Android-compatible generators | P1 |
| AI semantic synthesis | Pluggable provider adapter; explicit consent | P2 |
| User accounts/cloud sync | Optional future service module | P2 |

## 6. API contract (first draft)

- `GET /v1/health` — service health and API version.
- `GET /v1/providers` — enabled providers and capability flags (never expose secrets).
- `GET /v1/literature/search` — query, years, OA-only flag, page/cursor, result limit.
- `GET /v1/literature/{work_id}` — normalized bibliographic record.
- `GET /v1/literature/{work_id}/content` — content availability metadata and legal download URL/stream.
- `POST /v1/jobs` — optional asynchronous job submission; return job ID.
- `GET /v1/jobs/{job_id}` — job state and result metadata.
- `POST /v1/ai/analyze` — optional user-authorized provider request; must not persist document text by default.

All endpoints must validate input, cap result sizes, support cancellation/timeouts, return structured errors, and avoid logging API keys or document text.

## 7. Delivery phases

### Phase 0 — architecture and repository baseline
- Preserve `main`; work only on `feature/android-native-platform`.
- Record current feature behavior and known limitations.
- Define Android module, API schema, and provider interfaces.
- Add CI checks before adding large features.

### Phase 1 — functional Android MVP
- Compose navigation and visual system.
- Local project/library database.
- Search and metadata screens using a replaceable API base URL.
- Prompt Studio parity.
- Import/export and local cache.
- Build a debug APK through GitHub Actions.

### Phase 2 — research workflows
- OA retrieval, text extraction, candidate evidence, comparison matrix.
- Reproducible exports and source/evidence traceability.
- Automated tests with mocked provider responses and sample documents.

### Phase 3 — optional AI and resilience
- Provider adapters, BYOK, consent UI, request budgets.
- Network diagnostics from Iran and fallback configuration.
- End-to-end tests on real devices and real networks.

## 8. Acceptance criteria

The first public test APK is not considered ready until:
- It installs and launches without Streamlit or a browser wrapper.
- Search, prompt generation, saved projects, and exports work in the app.
- No secret is embedded in the APK or public repository.
- A failed network request never destroys a user's local work.
- API/provider limitations are visible and accurately described.
- The APK is built by CI and its artifact is actually verified before a download link is shared.

## 9. Technical references

- Android architecture recommendations: https://developer.android.com/topic/architecture/recommendations
- Cloudflare Workers pricing and Free limits: https://developers.cloudflare.com/workers/platform/pricing/
- Cloudflare D1 limits: https://developers.cloudflare.com/d1/platform/limits/
- OpenAlex authentication and free API key: https://help.openalex.org/api/authentication/
- OpenAlex full-text availability and licensing: https://help.openalex.org/access/fulltext/
