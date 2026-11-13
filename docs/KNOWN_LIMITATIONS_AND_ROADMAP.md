# ResearchMate AI — v0.8.0 status and roadmap

## Delivered in v0.8.0

- Persistent saved academic searches stored locally on the device.
- A saved search captures the exact query, date range, disciplinary profile, manual provider selection, author/journal/publisher filters, document type, access mode, minimum citations and sort order.
- Saved searches can be loaded back into the search form and rerun; the user explicitly starts the rerun after loading.
- Up to 20 saved searches are retained; duplicate query/profile pairs are replaced by the newest saved configuration.
- Saved searches remain on-device and do not require a user account or paid cloud database.
- Existing SQLite research library, projects, notes, evidence and exports are not migrated or deleted by this feature.
- Android release workflow now derives the GitHub Release tag and title from the Gradle versionName instead of hard-coding v0.7.0.

## Explicit limits of this build

- Saved searches are local to one installation; they are not synced across devices and are not automatically monitored.
- OpenAIRE Graph and DOAJ adapters are not yet wired in.
- Scopus, Web of Science, IEEE Xplore, ScienceDirect, SpringerLink and other subscription publisher APIs are not connected.
- Paid JCR Impact Factor and CiteScore values are not included. No journal quartiles are inferred.
- Citation graph traversal and reference/citation lists are not yet implemented.
- Retraction/correction monitoring is not yet implemented as a dedicated alert service.
- The system discovers metadata; it does not bypass subscriptions or download paywalled full text.
- Access labels describe returned metadata and are not legal entitlement determinations.
- Provider coverage can be limited by anonymous API rate limits; optional provider keys can increase applicable limits.
- Search queries are plain text; provider-specific Boolean syntax parity is not guaranteed.
- Citation counts and source-level metrics are snapshots and can differ between providers and update dates.
- No physical-device or Iranian-network test has yet been recorded for this release.
- PDF text extraction, OCR, table/figure extraction, semantic synthesis and AI model execution remain future work.

## Next priorities

### v0.8.1 — source coverage and citation intelligence

1. Validate and add OpenAIRE Graph and DOAJ adapters with source-level failure reporting.
2. Add exact-title/DOI/PMID/ORCID/ISSN lookup mode.
3. Add reference and citing-paper discovery with provider provenance.
4. Add a journal/ISSN explorer with verified metric name, source and data year.
5. Add retraction/correction data with trustworthy source labels.

### v0.9 — systematic review and document intelligence

1. Reproducible inclusion/exclusion workflow and PRISMA-style counts.
2. On-device PDF text extraction with page anchors and human review.
3. Evidence matrix and controlled extraction templates by domain.
4. Cross-paper synthesis with claim-to-source references.
5. Optional local AI connector; no mandatory paid API.

## Cost and research-integrity rules

- No paid source is enabled by default.
- No provider API keys are shipped in the APK or committed to source control.
- Do not bypass paywalls, publisher authentication, DRM or access restrictions.
- Citation counts stay attributed to the source and are not added together.
- Journal metrics must include metric name, provider/source and data year.
- A metadata-only paper is not equivalent to a reviewed full text.
- Missing or conflicting metadata remains explicit.
- Search result ranking is not proof of scientific quality.
