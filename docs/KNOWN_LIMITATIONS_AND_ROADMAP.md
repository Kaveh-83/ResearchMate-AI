# ResearchMate AI — v0.6.0 status and roadmap

## Delivered in v0.6.0

- Dark-first Material 3 design and RTL navigation.
- SQLite storage for projects, library papers, notes, tags, and evidence records.
- Automatic one-time migration of existing v0.5 projects from SharedPreferences.
- OpenAlex-backed open-access discovery retained.
- Article-to-prompt flow and bulk save of selected search results.
- PDF import using Storage Access Framework and first-page preview using Android PdfRenderer.
- Paper detail records with notes, tags, and manually entered evidence anchors.
- Comparison of up to four saved records.
- Preliminary keyword frequency signals to help formulate questions from the local corpus.
- JSON backup/restore plus CSV, BibTeX, and Markdown exports.

## Explicitly not delivered yet

- PDF full-text extraction, OCR, automatic table/figure extraction, and automatic evidence extraction.
- Automatic semantic synthesis and verified research-gap detection.
- In-app LLM inference, provider adapters, local Ollama connection, or cloud AI jobs.
- Cloud synchronization, user accounts, sharing, and conflict resolution.
- Full report editor with version history and PDF/DOCX publishing.
- Search pagination and alternative scholarly-provider failover.
- Physical-device validation on Iranian networks.

## Next priorities

### v0.7 — document intelligence
1. On-device PDF text extraction with size/page limits and source-page anchors.
2. Extracted text review and human correction before any synthesis.
3. Evidence matrix with structured field templates by domain.
4. OA source/license metadata and alternate source adapters.

### v0.8 — reproducible analysis
1. Cross-paper synthesis with claim-to-source references.
2. Contradiction and missing-variable analysis.
3. Idea Lab improvements using confirmed extracted evidence.
4. Search protocols, inclusion/exclusion criteria, and reproducibility logs.

### v0.9 — export and optional AI
1. Full report editor and PDF/DOCX/CSV/BibTeX exports.
2. A provider-neutral AI interface with explicit per-task consent.
3. Optional local-model connector; no mandatory paid API.
4. Usage budgets, timeouts, graceful fallbacks, and privacy controls.

## Data safety notes

- The app migrates existing v0.5 projects into SQLite on first access. The original SharedPreferences payload is left untouched.
- JSON backup includes project, paper, note, tag, and evidence data.
- Backup does not include PDF bytes. It stores the URI string only; access on another phone is not guaranteed.
- A restore merges/replaces records by their IDs and preserves unrelated records.
- No user PDF is transmitted to the Worker in v0.6.0.

## Scientific integrity notes

- A word-frequency signal is not a research gap.
- Citation count is not a journal quartile, impact factor, or quality score.
- A metadata-only paper is not equivalent to a reviewed full text.
- Missing values remain missing; they must not be inferred from titles or abstracts.
