# ResearchMate AI 1.0.3 — Release Candidate

## Scientific Document Studio

- Replaced the JSON-first manuscript editing experience with a Markdown-first editor.
- Separated title, abstract, keywords and manuscript body.
- Added local draft autosave, including a final save when leaving the editor.
- Kept schema 1.0 JSON as a compatibility import/export format for structured scientific assets.
- Added JSON file import and structured package export.
- Added standalone Markdown export.
- Changed PDF generation to an offline HTML/CSS WebView pipeline using Android PrintManager and the system Save as PDF flow.
- Added HTML table rendering for Markdown tables and structured tables.
- Added SVG bar/line charts, content-URI figures, equations and references to the HTML PDF path.
- Added a separate DOCX writer with editable paragraphs, native Word tables, embedded PNG chart/figure images, equations as editable text and references.
- Restricted WebView JavaScript and network loading; structured image references must use content URIs.
- Prevented overlapping PDF print jobs and extended WebView retention while a print job is active.

## Regression coverage

The Android CI workflow now runs JVM regression tests before building the debug APK. Tests cover Markdown section conversion, preservation of structured assets, invalid table rejection, JSON repair, image URI rules, a real steelmaking/ML review fixture, HTML table/chart rendering and HTML injection escaping.

## Sample research input/output

- examples/steel_ml_mini_review.json
- examples/steel_ml_mini_review.md

The sample is a focused narrative review based on published studies in BOF, EAF, continuous casting and rolling. Its BOF chart uses published test-set R² values from the cited 2025 study; it is not a new industrial experiment or a claim of model performance at another plant.

## Important limitations before production release

- CI build and JVM tests do not verify the Android print dialog or actual PDF output on a physical phone.
- PDF pagination, print-service availability, RTL/LTR mixed text, image-provider permissions and long/wide tables need real-device QA.
- DOCX generation compiles, but final rendering still needs inspection in Microsoft Word and LibreOffice.
- LaTeX is displayed as editable text rather than fully typeset mathematics.
- JSON export preserves image URI references, not image bytes. Save PDF/DOCX or retain original image files when archiving figures.
- The existing JSON import/export format remains available; this release candidate does not remove it.

This is a release candidate on a feature branch. Do not merge to main or publish as a production release until device-level PDF and DOCX QA is completed.
