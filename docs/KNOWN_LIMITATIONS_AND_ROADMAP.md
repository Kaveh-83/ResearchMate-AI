# ResearchMate AI — Known Limitations, Risk Controls, and Delivery Roadmap

**Status:** Current implementation audit for the native Android branch, updated 2026-11-12.

## 1. Current implementation boundary

The Android app is an early Kotlin + Jetpack Compose client. Version 0.2.0 builds successfully in GitHub Actions and includes a custom launcher icon, home/search/projects navigation, and an OpenAlex literature-search client.

The Cloudflare Worker named `researchmate-ai` is deployed. Its health endpoint returns HTTP 200. Its literature-search endpoint currently returns HTTP 429 because the production Worker reports that the `OPENALEX_API_KEY` secret is not configured/visible to the deployed Worker. Therefore live literature search is not yet operational.

The Android app is correctly configured with the public Worker URL. The current search failure is server-side provider configuration, not an Android build or URL configuration failure.

Full-text analysis, AI synthesis, research-gap detection, persistent projects, and report generation are not implemented in the native app.

## 2. Known limitations and mitigations

| Area | Current limitation | Mitigation |
|---|---|---|
| OpenAlex key | Production Worker does not see `OPENALEX_API_KEY`; search currently returns 429 | Add the secret to the exact `researchmate-ai` Worker production environment and redeploy |
| Network reachability | API host or scholarly sources may be blocked or slow in some regions | Test from intended mobile/fixed networks; do not promise availability by country |
| Shared API capacity | One public API can be abused or exhausted | Add per-device credentials, stronger quotas, monitoring, and abuse controls before public distribution |
| OpenAlex coverage | Search metadata is not a complete index of every publisher or paper | Preserve source provenance and add sources only after validation |
| Full-text access | Many papers expose only abstracts/metadata or are paywalled | Use legal OA links or user-supplied files; label evidence access clearly |
| PDF extraction | Scanned PDFs, equations, tables, and figures can extract incorrectly | Record extraction quality and add OCR as a separate stage |
| AI provider limits | Free model quotas and regional restrictions can interrupt work | Use provider adapters, explicit quotas, and no-AI fallbacks |
| Scientific reliability | Models can misread units, conditions, or results | Require evidence-linked claims and uncertainty labels |
| Local storage | Persistent Room database is not implemented | Add Room and explicit storage management |
| Privacy | Remote processing discloses document contents to the provider | Keep files local by default and obtain explicit consent before uploads |
| Export quality | RTL text, equations, tables, and citations need viewer testing | Validate PDF/DOCX/XLSX outputs on real devices |

## 3. Architecture requirements before public sharing

1. Keep provider secrets on the server, never in the APK or repository.
2. Add per-install abuse controls before broad public distribution.
3. Keep local files and project data on-device by default.
4. Obtain explicit consent before remote document processing.
5. Test actual connectivity from Iranian mobile and fixed networks before claiming regional availability.
6. Verify the live search endpoint after the OpenAlex secret is configured.

## 4. Delivery roadmap

### Phase A — Foundation (partially complete)
- Native Compose shell and CI APK build: complete.
- Custom app icon: complete.
- Worker deployment and health endpoint: complete.
- OpenAlex provider secret and live search: blocked on production Cloudflare secret configuration.
- Physical-device and target-network smoke tests: pending.

### Phase B — Native app experience
- Room database and persistent projects/library.
- PDF import using Android Storage Access Framework.
- Search pagination, saved-paper records, and source provenance.
- Prompt Studio parity and local exports.

### Phase C — Full-text and evidence extraction
- Legal OA full-text retrieval.
- Local PDF/text extraction and page-level evidence.
- OCR for scanned documents with extraction-quality reporting.

### Phase D — AI analysis and synthesis
- Provider adapters and explicit model selection.
- Consent before remote document processing.
- Evidence-linked methods/results extraction and candidate research gaps.

### Phase E — Reporting and release
- PDF/DOCX and evidence-matrix exports.
- Regression, privacy, and low-memory tests.
- Target-network testing.
- Signed release APK and release notes.

## 5. Release gates

The app is not ready for general distribution until:
- The APK is installed and launched on a physical device.
- The API is reachable from intended user networks.
- Literature search returns real records successfully.
- Public API abuse controls are adequate.
- Privacy and document-upload behavior is tested.
- Unavailable capabilities are clearly labeled.
