# ResearchMate AI — v0.6.0 status and roadmap

## Delivered in v0.6.0

- Dark-first Material 3 design and RTL navigation.
- SQLite storage for projects, library papers, notes, tags, and evidence records.
- Automatic one-time migration of existing v0.5 projects from SharedPreferences.
- OpenAlex-backed literature discovery (currently limited to open-access articles/reviews).
- Article-to-prompt flow and bulk save of selected search results.
- PDF import using Storage Access Framework and first-page preview using Android PdfRenderer.
- Paper detail records with notes, tags, and manually entered evidence anchors.
- Comparison of up to four saved records.
- Preliminary keyword frequency signals to help formulate questions from the local corpus.
- JSON backup/restore plus CSV, BibTeX, and Markdown exports.

## Explicitly not delivered yet

- Multi-provider discovery, publisher/discipline-specific routing, advanced filters, and subscription-metadata discovery.
- Search pagination and alternative scholarly-provider failover.
- Unified result schema, DOI-based deduplication, per-provider coverage reporting, and query reproducibility.
- Verified journal-metric catalog and citation/reference graph.
- PDF full-text extraction, OCR, automatic table/figure extraction, and automatic evidence extraction.
- Automatic semantic synthesis and verified research-gap detection.
- In-app LLM inference, provider adapters for AI, local Ollama connection, or cloud AI jobs.
- Cloud synchronization, user accounts, sharing, and conflict resolution.
- Full report editor with version history and PDF/DOCX publishing.
- Physical-device validation on Iranian networks.

## Next priorities

### v0.7 — scientific search engine (new top priority)

The full product and implementation specification is maintained in [SCIENTIFIC_SEARCH_ENGINE_V0_7_SPEC.md](SCIENTIFIC_SEARCH_ENGINE_V0_7_SPEC.md).

1. Replace the single-provider/open-access-only search with a server-side provider-adapter architecture.
2. Integrate broad scholarly sources: OpenAlex, Crossref, Semantic Scholar, and OpenAIRE, subject to live API smoke tests and quotas.
3. Search subscription/restricted bibliographic records as well as open-access records; separate full-text OA, repository copy, subscription/purchase indicated, metadata-only, and unknown access states.
4. Add profile-aware routing for disciplines, including biomedical (PubMed/PMC and Europe PMC), preprint-heavy science/CS (arXiv), and OA-journal discovery (DOAJ).
5. Add advanced filters, independent provider pagination, query history, coverage/error reporting, and DOI/identifier-based deduplication.
6. Display source-labelled citation counts and verified journal metrics with source/year provenance. Never sum citation counts from different providers or infer journal quartiles.
7. Add journal/ISSN discovery, related-work navigation, and a clear explanation of result ranking.
8. Preserve a strict no-surprise-cost policy: no paid service is enabled by default, provider secrets stay server-side, and provider quotas/errors are visible.
9. Maintain current local library/projects and test the new schema with migration-safe changes.

The v0.7 feature must not be called complete until provider-level live tests, Android build checks, Worker checks, and physical-device/network verification have passed.

### v0.8 — document intelligence

1. On-device PDF text extraction with size/page limits and source-page anchors.
2. Extracted-text review and human correction before any synthesis.
3. Evidence matrix with structured field templates by domain.
4. Automatic extraction must preserve page/section provenance and distinguish extracted facts from interpretation.

### v0.9 — reproducible analysis and optional AI

1. Reproducible screening protocol with inclusion/exclusion decisions and explicit uncertain records.
2. Cross-paper synthesis with claim-to-source references.
3. Contradiction and missing-variable analysis.
4. Idea Lab improvements using confirmed extracted evidence, not keyword frequency alone.
5. A provider-neutral AI interface with explicit per-task consent.
6. Optional local-model connector; no mandatory paid API.
7. Usage budgets, timeouts, graceful fallbacks, and privacy controls.
8. Full report editor and PDF/DOCX/CSV/BibTeX/RIS exports.

## Data safety notes

- The app migrates existing v0.5 projects into SQLite on first access. The original SharedPreferences payload is left untouched.
- JSON backup includes project, paper, note, tag, and evidence data.
- Backup does not include PDF bytes. It stores the URI string only; access on another phone is not guaranteed.
- A restore merges/replaces records by their IDs and preserves unrelated records.
- No user PDF is transmitted to the Worker in v0.6.0.

## Scientific integrity notes

- A word-frequency signal is not a research gap.
- Citation count is not a journal quartile, impact factor, or quality score.
- Article-level citations and journal-level metrics are different measures.
- Citation counts from separate providers must remain separately attributed; they must not be added together.
- A metadata-only paper is not equivalent to a reviewed full text.
- Search-result ranking is not proof of scientific quality.
- Missing values remain missing; they must not be inferred from titles or abstracts.
