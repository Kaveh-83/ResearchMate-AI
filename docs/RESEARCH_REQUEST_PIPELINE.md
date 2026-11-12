# ResearchMate AI — Research Request Pipeline

**Status:** Product workflow specification  
**Applies to:** Native Android client and versioned online API

## 1. Example user request

"Find recent papers on corrosion-resistant high-entropy alloy coatings, compare the evidence, and identify research gaps."

## 2. End-to-end request lifecycle

1. **Intake and clarification**
   - Capture topic, research question, publication window, language, output type, and constraints.
   - Ask only for missing information that changes the search or report materially.
   - Show the user a reviewable request summary before a costly/long run.

2. **Research protocol generation**
   - Expand the question into concepts, synonyms, spelling variants, and sub-questions.
   - Create inclusion/exclusion rules (years, document types, language, OA preference).
   - Preserve the exact search strings and filters for reproducibility.

3. **Literature discovery**
   - Search OpenAlex and additional providers through replaceable provider adapters.
   - Store provider ID, DOI, title, authors, year, venue, citations, OA status, and source URL.
   - Keep search date, query, and provider metadata.

4. **Deduplication and screening**
   - Deduplicate by normalized DOI, then provider ID/title-author-year fallback.
   - Screen title/abstract against the stated criteria.
   - Label decisions as included, excluded, or uncertain, with a reason.
   - Do not silently discard uncertain records.

5. **Full-text acquisition**
   - Attempt only legally available OA locations or user-provided files.
   - Record the actual access route, license/status when available, and retrieval time.
   - If only metadata/abstract is available, mark the record as partial evidence.

6. **Document extraction**
   - Extract text, headings, references, tables, and figures when technically possible.
   - Keep page/section locations where available.
   - Record extraction warnings; never imply a table/figure was read if it was not extracted.

7. **Evidence extraction**
   - Create structured fields per paper: material/composition, processing, microstructure, test conditions, methods, quantitative outcomes, limitations, and conclusions.
   - Attach each extracted claim to a paper and page/section/snippet when possible.
   - Separate source statements from system interpretation.

8. **Cross-paper synthesis**
   - Compare studies only when methods and conditions are sufficiently comparable.
   - Flag incompatible test conditions and missing data.
   - Summarize consensus, disagreement, trends, and evidence strength.

9. **Research-gap assessment**
   - Derive candidate gaps from explicit limitations, contradictions, untested variables, narrow sample ranges, and missing validation.
   - Score each candidate by evidence coverage, novelty confidence, feasibility, and relevance.
   - Describe these as candidate gaps, not proof that no prior work exists.

10. **Critical quality review**
    - Check that claims have citations, numerical comparisons use compatible units/conditions, and conclusions do not exceed evidence.
    - Flag missing full texts, extraction failures, small evidence bases, and provider coverage limitations.
    - Allow a user to inspect and correct the included paper set before final export.

11. **Report and export**
    - Produce an executive summary, method, evidence table, synthesis, gaps, limitations, future work, and references.
    - Export PDF/DOCX and structured CSV/XLSX/JSON/BibTeX where supported.
    - Preserve a project snapshot: original request, search protocol, included papers, evidence, warnings, and report version.

## 3. Job state machine

`DRAFT → READY → DISCOVERING → SCREENING → RETRIEVING → EXTRACTING → SYNTHESIZING → REVIEWING → COMPLETED`

Any stage may transition to:
- `PAUSED`: user paused or app/network interrupted; resume from last checkpoint.
- `NEEDS_INPUT`: required information or user decision is missing.
- `PARTIAL`: the run completed with explicit provider/full-text/extraction limitations.
- `FAILED`: unrecoverable error; preserve all completed artifacts and show the cause.

The first Android release should implement this state machine incrementally. It must not present the whole lifecycle as complete until each corresponding engine exists and passes tests.

## 4. User-visible progress

Show:
- current stage and completed stages;
- number of records found, screened, included, and full texts retrieved;
- source/provider names and any rate-limit status;
- warnings and decisions that require user input;
- cancel/pause/resume controls for long-running jobs.

Do not fabricate a percentage when the system cannot estimate remaining work. Use named stages and counts instead.

## 5. Evidence and privacy rules

- Keep imported PDFs and project files on-device by default.
- Never send a document to an AI provider unless the user explicitly chooses that provider and confirms the transfer.
- Never store provider keys in source control, logs, or public APK constants.
- Never bypass paywalls, login walls, or access controls.
- Keep provenance: DOI/URL, provider, retrieval date, license/access status, page/section locator, and extraction confidence.
- Label heuristic screening separately from semantic model judgments.
- If a source is unavailable, report the limitation rather than inventing a citation or result.

## 6. First implementation slice

The current implementation slice is intentionally narrower:
- Android native search screen;
- user-configurable HTTPS API base URL;
- edge API route that queries OpenAlex;
- results list showing title, authors, year, venue, citation count, DOI, and OA PDF URL.

This is **literature discovery only**. Screening, full-text retrieval, extraction, synthesis, gap assessment, and report generation remain later development phases.
