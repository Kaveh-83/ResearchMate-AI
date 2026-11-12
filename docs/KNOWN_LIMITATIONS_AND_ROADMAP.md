# ResearchMate AI — Known Limitations, Risk Controls, and Delivery Roadmap

**Status:** Native Android implementation audit, updated 2026-11-12 (v0.4.0).

## 1. Current implementation boundary

The native Android client is an early Kotlin + Jetpack Compose application. Version 0.4.0 adds a Persian-first prompt studio and richer OpenAlex paper cards. The current working workflow is:

1. Search OpenAlex through the central Worker.
2. Inspect paper metadata, citation count, approximate citation activity, paper type, DOI, and abstract when available.
3. Open the prompt studio directly from a selected paper with its metadata prefilled.
4. Generate and copy a detailed research work order for a separate AI/research workflow.

This is prompt preparation and literature discovery. It does **not** mean that the app has already downloaded or read the full paper, executed an AI model, synthesized evidence, or verified research gaps.

The Cloudflare Worker is deployed, but the live literature provider must be rechecked independently. A successful Android build does not prove that a real literature query works. Rate limits and provider credentials remain server-side concerns.

## 2. Implemented features in v0.4.0

| Area | Status | Notes |
|---|---|---|
| Native Compose shell | Implemented | Home, search, prompt studio, projects navigation |
| OpenAlex discovery | Implemented in client | Uses the existing HTTPS Worker |
| Citation metadata | Implemented | Shows count supplied by OpenAlex |
| Approximate citation band | Implemented | Citations divided by paper age; not field-normalized |
| Paper type | Implemented | Uses provider document type when available |
| Abstract display | Implemented when available | Reconstructed from OpenAlex inverted index |
| Per-paper prompt | Implemented | Prefills title, metadata, DOI and abstract |
| Detailed prompt studio | Implemented | Local deterministic prompt generation + clipboard |
| Room database/projects | Not implemented | Projects screen remains a status placeholder |
| Full-text/PDF analysis | Not implemented | An abstract is not a substitute for full text |
| AI synthesis/gap analysis | Not implemented | Prompt is generated locally but not executed by an AI service |
| Journal quartile lookup | Not implemented | Never infer Q1–Q4 from citation count |
| Reports/export | Not implemented | Requires document and citation rendering modules |

## 3. Known limitations and mitigations

| Area | Current limitation | Mitigation |
|---|---|---|
| OpenAlex key/quota | Provider requests may be rate-limited | Check production Worker secret and quota; preserve clear error messages |
| Network reachability | API host or scholarly sources may be blocked or slow in some regions | Test from intended mobile/fixed networks; do not promise availability by country |
| Shared API capacity | One public API can be abused or exhausted | Add per-device credentials, stronger quotas, monitoring, and abuse controls before public distribution |
| OpenAlex coverage | Search metadata is not a complete index of every publisher or paper | Preserve source provenance and add sources only after validation |
| Citation comparisons | Raw citations favor older papers and high-citation fields | Display count and age-adjusted rough band with a clear warning; add field/year normalization before any ranking |
| Journal level | OpenAlex search response does not establish current JCR/SJR quartile | Integrate a verifiable journal-metrics provider or leave Q1–Q4 unknown |
| Full-text access | Many papers expose only abstracts/metadata or are paywalled | Use legal OA links or user-supplied files; label evidence access clearly |
| PDF extraction | Scanned PDFs, equations, tables, and figures can extract incorrectly | Record extraction quality and add OCR as a separate stage |
| AI provider limits | Free model quotas and regional restrictions can interrupt work | Use provider adapters, explicit quotas, and no-AI fallbacks |
| Scientific reliability | Models can misread units, conditions, or results | Require evidence-linked claims and uncertainty labels |
| Local storage | Persistent Room database is not implemented | Add Room and explicit storage management |
| Privacy | Remote processing discloses document contents to the provider | Keep files local by default and obtain explicit consent before uploads |
| Export quality | RTL text, equations, tables, and citations need viewer testing | Validate PDF/DOCX outputs on real devices |

## 4. Delivery roadmap

### Phase A — Discovery + prompt foundation
- Native Compose shell and CI APK build: complete.
- OpenAlex paper search and metadata cards: implemented.
- Per-paper citation count and approximate citations-per-year band: implemented.
- Per-paper prompt prefill: implemented.
- Detailed local prompt studio and copy action: implemented.
- Formal quality metrics and journal quartile verification: pending.

### Phase B — Local research workspace
- Room database for projects, saved papers, tags, notes, and prompt history.
- Search pagination and saved-paper records.
- PDF import using Android Storage Access Framework.
- Local file storage and reproducible project snapshots.

### Phase C — Full-text and evidence extraction
- Legal OA full-text retrieval and license/access checks.
- Local PDF/text extraction and page-level evidence.
- OCR for scanned documents with extraction-quality reporting.
- Structured extraction of methods, materials, test conditions, numerical results, and limitations.

### Phase D — AI analysis and synthesis
- Provider adapters and explicit model selection.
- Consent before remote document processing.
- Evidence-linked per-paper analysis, cross-paper comparison, and candidate gap assessment.
- A no-AI export path and transparent partial-result states.

### Phase E — Reporting and release
- PDF/DOCX and evidence-matrix exports.
- Verified citation formatting and reference cross-check.
- Regression, privacy, and low-memory tests.
- Target-network testing.
- Signed release APK and release notes.

## 5. Release gates

The app is not ready for broad public distribution until:
- The APK is installed and launched on a physical device.
- The API is reachable from intended user networks.
- Literature search returns real records successfully.
- Public API abuse controls are adequate.
- Privacy and document-upload behavior is tested.
- Unavailable capabilities are clearly labeled.
