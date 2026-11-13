# ResearchMate AI v0.9.0 — Product & Engineering Plan

## Product goal

Make literature search practical for real research workflows: users can select a complete search result set, curate it in batches, and move the selected papers into a durable library or export them without losing source provenance.

This is a design specification, not a claim that these features are already implemented.

## P0 — Select all discovered papers

### Required user experience

- Add a selection toolbar above search results.
- Provide three explicit actions:
  1. Select visible results — only the currently displayed access tab/filter.
  2. Select all loaded results — all result records fetched into the current search session, across access tabs and pages already loaded.
  3. Select all search results — retrieve the complete result set represented by the search, not merely the current page.
- Show a persistent count, e.g. "38 selected of 124 discovered".
- Provide clear selection and invert selection.
- Keep selected IDs stable when the user changes the access tab or scrolls.
- If a new query or filter set is submitted, clear the prior selection after confirmation or clearly show that it belongs to the prior query.

### Critical correctness requirement

The current API uses page-based retrieval across several providers and merges/deduplicates records. A page-level count is not proof that every unique result has been loaded. "Select all search results" must not silently select only the current page.

Before shipping full-result selection:
- define a stable search-session identifier or a reproducible search request snapshot;
- fetch all pages in a controlled, sequential manner, respecting provider rate limits and the API's maximum page boundary;
- deduplicate by DOI, then PMID/arXiv ID, then normalized title/year;
- report partial completion if a provider is throttled or a page fails;
- show the exact number of selected and unselected records;
- offer "select all successfully retrieved" separately from "retry missing pages".
- never represent provider-reported total counts as fully selectable records until the actual record IDs have been retrieved.

### Bulk actions

For selected records:
- Save all to the local library, with duplicate detection and a progress/result summary.
- Export selected references to BibTeX, RIS, CSV and a structured JSON file.
- Add selected papers to a project or reading queue.
- Send selected records to Prompt Studio in bounded batches; never silently truncate a large selection.
- Compare a user-chosen subset (default 2–5) rather than trying to compare hundreds at once.
- Remove selected records from the current selection without deleting library data.

## P1 — Research workflow improvements

1. Reading queue with statuses: To read, Reading, Read, Excluded.
2. Tags and collections by project, topic, method and material/system.
3. Duplicate warnings when saving a paper already in the library.
4. One-tap export of the selected records and their source metadata.
5. Search history with reproducible query snapshots and provider status.
6. Citation links: discover references and citing papers where the provider API supplies them; display provider and retrieval date.
7. Article quality checklist: retraction/correction flags, DOI validity, abstract availability, publication type, and metadata completeness. Do not turn these into an opaque quality score.

## P2 — High-value future capabilities

- Systematic-review screening with Include / Exclude / Maybe and reason labels.
- Evidence extraction matrix with claim, value, unit, method, page/section, and source.
- Alerts for corrections/retractions using trustworthy sources.
- Citation graph and seminal-vs-recent paper exploration.
- Journal explorer with only verifiable metrics, each carrying source and data year.
- Cross-paper synthesis that links every extracted claim back to a paper and, when available, a page/section anchor.

## UX layout proposal

Search screen:
- Header: query name + result count + provider status.
- Selection bar: Select visible | Select loaded | Select all results | Clear.
- Selection summary: selected count and retrieval completeness.
- Bulk action sheet: Save to library | Export | Add to project | Send to Prompt Studio | Compare subset.
- Partial-failure banner with retry option and per-provider status.

## Acceptance criteria

- Selecting visible records never changes records outside the visible set.
- Select loaded includes every unique record currently fetched, regardless of access tab.
- Select all results retrieves every available page within the declared search limit or explicitly reports the incomplete portion.
- No silent partial selection.
- DOI/identifier duplicates are not saved twice.
- Bulk operations show success, skipped duplicates and failures separately.
- Large Prompt Studio transfers are chunked and preserve paper IDs/source URLs.
- Existing library, projects, evidence, notes, saved searches and exports remain compatible.
- Tests cover empty results, 1 result, >1 page, duplicate DOI, throttled provider, failed page, access-tab changes and interrupted retrieval.

## Recommended v0.9.0 delivery order

1. Selection toolbar and select-visible/select-loaded.
2. Safe complete-result retrieval and select-all-results.
3. Bulk save with duplicate handling.
4. RIS/BibTeX/CSV export of selected items.
5. Add to project/reading queue.
6. Citation exploration and systematic-review features in later releases.

## Non-negotiable constraints

- Free scholarly providers remain the default.
- No API keys or secrets inside the Android APK or public repository.
- No paywall circumvention or unauthorized full-text downloading.
- Show provenance and limitations for citations and access labels.
- Preserve user library data during upgrades.
- Do not claim a feature is shipped until the Android build and release artifact have been verified.
