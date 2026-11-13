# ResearchMate AI — Android UX specification

**Target:** Native Android research workspace, Persian-first, RTL.  
**Current release:** 0.6.0.  
**Visual direction:** Dark-first scientific workspace; charcoal/navy surfaces, restrained violet accent, teal secondary accent, warm amber for insight markers. No forced white theme.

## Navigation

Bottom navigation:
- Home — workspace overview and quick actions.
- Search — open-access literature discovery.
- Library — saved papers, imported PDFs, notes, tags, and evidence.
- Compare — side-by-side comparison of up to four records.
- Tools — backup, restore, and export.

Secondary screens:
- Projects — saved research projects and prompt workflows.
- Paper detail — bibliographic metadata, notes, tags, evidence.
- PDF preview — first-page in-app preview and external full-document handoff.
- Idea Lab — preliminary corpus signals and evidence-seeking prompt generation.
- Prompt Studio — preserved scientific writing and reviewer prompt workflow.

## Dark theme tokens

- Background: `#0B0D13`
- Base surface: `#11151F`
- Elevated surface: `#1D2533`
- Highest surface: `#293142`
- Primary violet: `#B9A5FF`
- Secondary teal: `#8BD6CD`
- Tertiary amber: `#FFC27A`
- Main text: `#E7E7F0`
- Secondary text: `#B7BDCC`
- Outline: `#424B5D`

## Product principles

1. Local-first for projects, paper records, notes, tags, and evidence.
2. Online search is optional and must show service/rate-limit failures clearly.
3. Never claim a paper was read in full when only metadata or abstract is available.
4. Never represent frequency heuristics as AI or confirmed research gaps.
5. Missing values are displayed as missing, not inferred.
6. PDF URI access is persisted through Android document permissions; PDF bytes are not uploaded.
7. Preserve Persian RTL while rendering DOI, URLs, and chemical formulas as LTR where appropriate.
8. Avoid fake progress indicators and unverified analysis scores.

## Current feature behavior

- Library search covers local title, author, venue, DOI, abstract, note, and tags.
- Evidence is manually entered with variable/value/unit/page/excerpt fields.
- Comparison uses available metadata, notes, and manually registered evidence.
- Idea Lab lists infrequent terms found in saved titles, abstracts, and notes, and creates a prompt that asks a separate model to verify any candidate gap.
- Export formats: JSON backup, CSV, BibTeX, Markdown.
- PDF preview renders page one; the system PDF app can open the full document.

## Planned

- Full-text extraction and OCR on device.
- Automatic evidence candidates with human review.
- Reproducible search protocols and screening.
- Evidence-linked synthesis and contradiction review.
- Optional AI/local-model integration with explicit consent.
- Full report editor and DOCX/PDF exports.
