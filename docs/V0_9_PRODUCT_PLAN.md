# ResearchMate AI v0.9.0 — Release scope and product notes

## Release intent

Move ResearchMate from a multi-source paper finder toward a practical literature-workflow tool while preserving the project's constraints:
- Android-first and usable on an ordinary phone.
- No mandatory paid API key; connected scholarly providers remain the existing free-source set.
- Local library/project data remain on-device.
- No paywall bypass and no unsupported claims about full-text access.
- File creation must be real and verifiable, not just a prompt asking for a file.

## Implemented in v0.9.0 source

### 1. Bulk selection in academic search
- Select visible records in the current access-status tab.
- Select all records already loaded in the current search session, across tabs.
- Retrieve subsequent pages sequentially and select records actually returned.
- Pagination reuses a snapshot of the original search request so edits to the search form do not silently alter the in-progress result set.
- Sequential retrieval uses a short pause and is bounded to 30 additional pages per action.
- If retrieval stops early, the UI reports the number actually retrieved and selected rather than claiming the provider's reported total has been collected.
- Selected references can be saved to the local library, sent to Prompt Studio, or exported as RIS.

### 2. Scientific document output
- Prompt Studio's instructions now require a real PDF as the primary deliverable and request an editable DOCX copy when supported.
- The app provides an offline document handoff: paste the final AI-generated text into the deliverable field, then save PDF or DOCX to a location chosen on the device.
- PDF output uses Android's bidirectional text layout for Persian/RTL text, A4-like page dimensions, margins, title, and page numbering.
- DOCX output is a minimal WordprocessingML package with RTL paragraph settings and basic heading/list handling.
- No AI inference or text is sent to a paid service by the local file exporter.

### 3. Existing capabilities retained
- Multi-provider search, saved search profiles, provider status and access labels.
- Local paper library, projects, notes, evidence records, comparison view, and library exports.
- Existing backup/restore workflow.

## Important limitations

- The prompt itself cannot force an external AI chat service to attach files if that service lacks file-generation capability. The app therefore offers a second, reliable local export path after the user pastes the final text.
- The app does not yet perform automatic full-text scientific extraction from every PDF.
- Bulk retrieval is bounded to 30 additional pages per action. Provider totals are not treated as evidence that all records were retrieved.
- Selection is based on IDs returned by the current search session. Cross-provider duplicate resolution still depends on the backend's current identifier/deduplication logic.
- The DOCX exporter prioritizes robust basic text, RTL direction, and headings; complex tables, equations, embedded figures, reference-manager fields, and journal-specific page styles require later refinement.
- The PDF exporter is a lightweight device-side formatter, not a full journal typesetting engine. Complex tables, figures, and equation layout should be checked before submission.
- No paid Scopus, Web of Science, IEEE, Springer, or citation-context service is enabled.
- A DOI, citation count, or journal metric is metadata, not proof of research quality.

## Benchmark lessons adopted

- Elicit: structured screening/extraction/report workflow and PDF/Word exports.
- ResearchRabbit: collections, notes, and citation-led discovery.
- SciSpace/Scite: paper understanding and citation-context analysis.
- ResearchMate adopts the feasible parts first: reliable batch selection, export, source provenance, and an explicit file-delivery workflow. Citation graphs and evidence-grounded full-text synthesis remain future work because they require dependable reference/citation/full-text data.

## Next recommended increment (v0.10)

1. Reading queue states: To read / Reading / Read / Excluded.
2. User-defined collections and tags linked to projects.
3. Duplicate detection by DOI, PMID/arXiv identifier, then normalized title/year before saving.
4. Systematic-review screening: Include / Exclude / Maybe with a reason and audit trail.
5. Evidence matrix with claim, value, unit, method, page/section, and exact source.
6. Citation network only after a provider-backed reference/citation endpoint is implemented.
7. Improved DOCX/PDF layout for tables, figures, equation blocks, and bibliography styles.

## Acceptance checks

- The app's visible version matches Gradle versionName.
- Select visible does not select hidden access tabs.
- Select loaded selects all IDs fetched in the active search session.
- Retrieve all pages uses the original request snapshot and reports partial results honestly.
- RIS export contains the selected records only.
- PDF and DOCX files are created locally from pasted final text and are not represented as AI-generated content unless an external model actually produced the text.
- Existing library, projects, notes, and evidence remain compatible.
