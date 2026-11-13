# ResearchMate AI — v0.7.0 status and roadmap

## Delivered in v0.7.0 search implementation

- Multi-disciplinary profile-aware routing for general scholarly search and selected specialist sources.
- Provider adapters for OpenAlex, Crossref, Semantic Scholar, Europe PMC, PubMed and arXiv.
- Subscription/restricted bibliographic records are no longer excluded by an Open Access-only filter.
- Separate access labels for open full-text URLs, no OA copy indicated, and unknown access.
- Advanced search UI with year, author, journal, publisher, document type, minimum citations, access mode, sort, manual providers, and pagination.
- DOI-first deduplication, preserving provider identifiers and source provenance.
- Source-attributed citation counts; counts from different databases are never summed.
- Provider-level status and errors, so partial source outages do not fail the entire search.
- OpenAlex source H-index and two-year mean citedness displayed only when available in returned metadata.
- v0.6 local library, projects, PDF import, notes, evidence capture and exports retained.

## Explicit limits of this build

- OpenAIRE Graph and DOAJ adapters are not yet wired into v0.7; their API shape/quota should be validated in a separate integration change.
- Scopus, Web of Science, IEEE Xplore, ScienceDirect, SpringerLink and other subscription publisher APIs are not connected.
- Paid JCR Impact Factor and CiteScore values are not included. No journal quartiles are inferred.
- The system discovers metadata; it does not bypass subscriptions or download paywalled full text.
- Access labels describe the returned metadata and are not legal entitlement determinations.
- Provider coverage can be limited by anonymous API rate limits; optional provider keys can increase applicable limits.
- Search queries are plain text; provider-specific Boolean syntax parity is not guaranteed.
- Deep paging is app-level page/offset per provider, not a durable cursor for every upstream database.
- Citation counts and source-level metrics are snapshots and can differ between providers and update dates.
- No physical-device or Iranian-network test has yet been recorded for this release.
- PDF text extraction, OCR, table/figure extraction, semantic synthesis, and AI model execution remain future work.

## Next priorities

### v0.7.1 — coverage and reproducibility

1. Validate and add OpenAIRE Graph and DOAJ adapters.
2. Add exact-title/DOI/PMID/ORCID/ISSN lookup mode.
3. Add query history and reproducible search snapshots (query, profile, providers, filters, timestamps and status).
4. Improve provider-specific continuation tokens and query syntax support.
5. Add source filter facets and a journal/ISSN explorer.
6. Add explicit repository-version and license details where reliable data is available.

### v0.8 — journal and citation intelligence

1. Related-work graph for references and citing papers.
2. Journal directory with verified source/year for each metric.
3. Optional licensed integrations for JCR/CiteScore only when a valid entitlement exists.
4. Retraction/correction data with trustworthy source labels.
5. Saved queries and alerts.

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
