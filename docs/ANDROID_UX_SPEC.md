# ResearchMate AI — Android UX and Screen Specification

**Product direction:** Native Android research workspace, Persian-first with English technical metadata support.  
**Visual direction:** Calm scientific workspace; high information density without looking like a spreadsheet. Material 3 components, readable typography, restrained accent color, clear loading/error/empty states.

## Navigation

Primary bottom navigation:
- **خانه:** recent projects, start a new research task, recent activity.
- **پژوهش:** create and track research requests.
- **کتابخانه:** saved papers, imported PDFs, collections.
- **گزارش‌ها:** completed and draft reports.
- **تنظیمات:** network/API configuration, language, privacy, export preferences.

The first prototype currently has a simplified top-level home/search/projects navigation. This document defines the target navigation; it is not a claim that all screens are already implemented.

## Main screens

### 1. Home
- Primary action: New research request.
- Continue recent project.
- Recent jobs and their state (draft, running, needs input, completed, partial, failed).
- Quick access to search papers and prompt studio.

### 2. New research request
Fields:
- Research topic/question (required).
- Research domain (optional).
- Publication year range.
- Document type and language.
- Preferred sources.
- Open-access-only toggle.
- Desired output: evidence map, comparative review, research gap report, or full review.
- Output language.
- Constraints and exclusions.

Before execution, show a protocol preview: query terms, source list, date range, inclusion/exclusion rules, and expected limitations. User can edit or confirm.

### 3. Job progress
- Named stage timeline, not fake percentage progress.
- Counts: discovered, screened, included, full texts retrieved, extracted.
- Pause, resume, cancel.
- Warnings and requests for user input.
- Tap a stage to inspect completed artifacts and errors.

### 4. Literature results
- Search query and filters.
- Result cards: title, authors, year, venue, DOI, citation count, OA status.
- Sort/filter and deduplication labels.
- Save to project, open DOI/source, download OA file where licensed.
- Distinguish metadata-only records from full-text records.

### 5. Paper detail
Tabs:
- Overview and bibliographic data.
- Abstract.
- Extracted methods/materials/results.
- Tables and figures (only if extraction succeeded).
- Notes and evidence references.
- Source and license/access information.

### 6. Evidence matrix
Rows are papers; columns are comparable variables such as composition, process, microstructure, test conditions, measured properties, and limitations. Non-comparable or missing values must be explicitly labeled.

### 7. Synthesis and research gaps
- Themes and cross-paper comparisons.
- Contradictions and confidence notes.
- Candidate research gaps with supporting paper references.
- Feasibility and relevance assessment.
- User can mark a candidate as accepted, rejected, or needing more evidence.

### 8. Report editor and export
- Report outline with sections.
- Citations linked to source records.
- Export to PDF/DOCX and evidence tables to CSV/XLSX.
- Version history and reproducibility bundle.

### 9. Settings and privacy
- API endpoint configuration.
- Optional user-owned provider keys, stored securely and never committed.
- Network diagnostics.
- Clear consent before sending document text to an AI provider.
- Local storage and cache management.
- Language and export settings.

## UX principles

1. Show what the system is doing and which source supports the result.
2. Keep original files local by default.
3. Do not hide missing data or failed extraction.
4. Never label keyword heuristics as AI understanding.
5. Preserve work when connectivity fails.
6. Use Persian explanations with original English technical terms where helpful.
7. Make every long-running task resumable and cancellable.
8. Keep the initial workflow simple; reveal advanced search settings only when needed.

## Visual tokens (initial proposal)

- Layout: 8 dp spacing grid; 16–20 dp screen margins.
- Cards: 14–20 dp corner radius.
- Typography: Material 3 scale; title, body, metadata hierarchy.
- Status: distinct labels for queued/running/needs-input/partial/completed/failed.
- Colors: Material dynamic/system palette with one restrained scientific accent; status colors reserved for meaningful state only.
- RTL: Persian UI must be tested for mixed RTL/LTR text, DOI, URLs, chemical formulae, and numerical values.

## Design and implementation sequence

1. Validate navigation and task flow with low-fidelity wireframes.
2. Define components and tokens.
3. Implement home, request creation, progress, search, and library.
4. Connect each screen to repository/API state.
5. Add empty/loading/error/partial states.
6. Validate RTL and accessibility on real Android devices.
7. Add advanced evidence/report screens as their engines become available.
