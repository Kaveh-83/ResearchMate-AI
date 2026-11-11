# ResearchMate AI — Known Limitations, Risk Controls, and Delivery Roadmap

**Status:** Product/engineering planning document for the native Android branch. This document distinguishes implemented capabilities from planned capabilities.

## 1. Current implementation boundary

The Android app currently provides an early Compose shell and an OpenAlex literature-search client. The central API worker is documented but has not yet been deployed. The app must not present full-text analysis, AI synthesis, research-gap detection, or report generation as available until those services are implemented and tested.

## 2. Known limitations and mitigations

| Area | Current or expected limitation | Product behavior / mitigation |
|---|---|---|
| Network reachability | API host or scientific sources may be blocked, slow, or intermittently unavailable in some regions | Provide connection diagnostics, timeouts, retry with backoff, and a replaceable API host; do not promise availability by country |
| Shared API capacity | A single public API can be abused or exhausted | Add per-device/app rate limits, request size limits, abuse monitoring, and server-side quotas before public distribution |
| OpenAlex coverage | Search metadata is not a complete index of every publisher or paper | Label source provenance; add further sources only when legal access and reliability are validated |
| Full-text access | Many papers are paywalled or expose only abstracts/metadata | Use open-access links or user-supplied files; label abstract-only/metadata-only evidence clearly |
| PDF extraction | Scanned PDFs, multi-column layouts, equations, tables, and figures can extract incorrectly | Record extraction quality; support OCR as a separate optional stage; preserve page references and allow manual correction |
| AI provider limits | Free model quotas, regional restrictions, rate limits, and model changes can interrupt work | Use provider adapters, explicit quotas, model-independent job records, and a no-AI fallback for discovery/organization |
| Scientific reliability | Models may misread units, conditions, methods, or results and may hallucinate citations | Require evidence-linked claims, page/section references, uncertainty labels, and a review stage; never fabricate missing values |
| Local storage | Large PDF collections consume device storage and may be removed by the OS if stored as cache | Store user documents in app-private persistent storage or user-selected SAF locations; expose storage management |
| Battery / memory | OCR and batch extraction can be expensive on mid-range devices | Run heavy extraction in controlled batches; allow pause/resume; prefer server processing where privacy and cost allow |
| Privacy | Sending a document to a remote model discloses its contents to that provider | Keep files local by default; obtain explicit per-task consent before uploading text or documents |
| Cost | “Free tier” services have quotas and can change their terms | Display usage/limits where known, set hard spending caps, and do not silently switch to paid services |
| Export quality | Complex equations, tables, RTL text, and citations can render differently across viewers | Validate PDF/DOCX/XLSX outputs and include a reproducibility/evidence bundle |

## 3. Reliability and evidence rules

- Every paper record stores source name, source URL, DOI when available, and retrieval timestamp.
- Each extracted claim should link to a paper and, where possible, a page or section.
- Missing, non-comparable, or unreadable values must remain explicitly missing; do not infer them silently.
- A record with only title/abstract metadata must never be described as a full-text review.
- Research gaps are candidate gaps with evidence and confidence, not proof of novelty.
- A job must support PARTIAL, NEEDS_INPUT, and FAILED states; partial output is preferable to fabricated completion.
- Long-running tasks must be resumable and cancellable. Progress is represented by verified stage counters, not invented percentages.

## 4. Architecture requirements before public sharing

1. Deploy the API once under operator control; ordinary users do not create provider accounts.
2. Keep provider secrets on the server, never in the APK or public repository.
3. Add rate limiting and request validation before distributing the app beyond a small test group.
4. Make API host configuration replaceable at build time and support a controlled fallback endpoint.
5. Keep local files and project data on-device by default.
6. Add clear privacy consent before remote document processing.
7. Test actual connectivity from Iranian mobile and fixed networks before claiming regional availability.
8. Add health checks and operational monitoring without logging document contents or API secrets.

## 5. Delivery roadmap

### Phase A — Build and installation foundation
- Resolve Android SDK / Gradle compatibility.
- Produce a debug APK through CI.
- Verify installation and launch on at least one physical Android device.
- Add basic UI smoke tests.

### Phase B — Native app experience
- Implement bottom navigation: Home, Research, Library, Reports, Settings.
- Implement the new research request form and protocol preview.
- Implement job status states and local project persistence.
- Implement PDF import using Android Storage Access Framework.

### Phase C — Shared API and scientific discovery
- Deploy the first API endpoint.
- Add health diagnostics and server-side rate limits.
- Connect OpenAlex search and source provenance.
- Add deduplication, filtering, pagination, and saved-paper records.

### Phase D — Full-text and evidence extraction
- Resolve legal OA full-text URLs.
- Import user PDFs locally.
- Extract text and page-level evidence.
- Add OCR only for scanned documents and expose extraction quality.

### Phase E — AI analysis and synthesis
- Add provider adapters and explicit model selection.
- Add consent before remote document processing.
- Extract structured methods/results with traceable evidence.
- Compare studies and generate candidate research gaps with confidence labels.

### Phase F — Reporting and release
- Generate PDF/DOCX reports and CSV/XLSX evidence matrices.
- Run regression, privacy, security, and low-memory tests.
- Test network behavior on Iranian carriers and fixed internet.
- Publish a signed release APK and release notes.

## 6. Release gates

The app is not ready for general distribution until:
- A real APK is built and installed successfully.
- The API is deployed and reachable from intended user networks.
- Public API abuse controls are active.
- Privacy and document-upload behavior is tested.
- Search results show provenance and access status.
- All unavailable capabilities are clearly labeled.