# ResearchMate AI v1.0 — Scientific Document Studio

## What this feature does

The Scientific Document Studio converts a validated JSON research package into an offline, multi-page PDF on Android. The package is intended to be authored by an external AI assistant, while ResearchMate remains responsible for validating its structure and laying out the document.

The app never executes scripts or arbitrary code returned by an AI model.

## User workflow

1. Open **خانه → استودیوی ساخت سند علمی**.
2. Tap **کپی پرامپت استاندارد برای هوش مصنوعی**.
3. Paste the prompt into the AI assistant. Add the research topic, intended output type, language, target audience, source range and real source material.
4. Ask the model to return only one valid JSON object using schema version 1.0. Remove any Markdown code-fence labels if the model adds them.
5. Replace the example JSON inside ResearchMate with the AI response.
6. Run **اعتبارسنجی**. Resolve all structural errors. Review the warnings, especially missing sources, units or figure permissions.
7. For each item in the figures array, select the corresponding image from the phone. Images are matched in order to figure IDs that do not yet have an attachment. Supported formats are PNG, JPEG and WebP.
8. Create the PDF. Open the saved file and inspect page breaks, figure captions, chart values, units, references and image quality before sharing or submitting it.

## Package structure

The top-level fields are:

- schema_version: currently 1.0.
- document: title, optional subtitle, language and citation-style label.
- manuscript: abstract, keywords and ordered sections.
- tables: tables with headers, rows and a source note.
- charts: bar or line charts with categories and one or more numeric series.
- figures: captions, descriptions, source/licence notes and optional image_uri values added by the app after image selection.
- equations: a display label, LaTeX text and a plain-text alternative.
- references: full bibliographic entries, preferably as objects with a text property.

A complete starter package is available in the in-app editor. The in-app copy-prompt action contains the complete field contract and scientific-integrity instructions.

## Section-by-section authoring notes

### Manuscript
Use one sections object per article section. heading is the visible heading, level is 1, 2 or 3, and content is the complete section text. The Android renderer supports paragraphs, line breaks, basic list markers and Markdown bold marker removal. It is not a full Markdown or LaTeX typesetter.

### Tables
Each table has a title, a non-empty header array and rows with exactly the same number of cells as the headers. Units should be explicit. The renderer draws table borders and wraps cell text. Very wide tables may still need to be simplified and reviewed on a phone-sized PDF renderer.

### Charts
type is bar or line; categories contains the x-axis labels; each series contains a name and a values array with one numeric value per category. Include x_label, y_label (including units) and source. The renderer draws vector axes, grid lines, bars/lines, category labels and a legend. It does not infer missing data or generate experimental values.

### Figures and images
Give each figure a unique ID such as fig1, a self-contained caption, a useful description and a source/licence statement. The user attaches the actual image in the app. image_uri is inserted locally by ResearchMate and should not be fabricated by the AI. If no image is attached, the PDF displays a descriptive placeholder instead of pretending an image exists.

### Equations
The renderer currently prints plain_text in a highlighted formula block and falls back to latex only when plain text is absent. Advanced mathematical typesetting is not yet implemented.

### References
Each reference is displayed as supplied. The app checks that entries are not blank, but it does not independently verify DOI resolution, author names, journal data or citation-style compliance. These must be checked against a trusted scholarly database or publisher page.

## Validation performed

The app checks JSON parsing, schema version, document title, at least one manuscript section, table row widths, chart type, chart category/value lengths and numeric chart values. It warns when abstracts, keywords, chart sources, figure captions, figure sources, figure attachments or references are missing.

Passing validation means the package is structurally renderable. It does not prove that claims, measurements, images, DOIs, references or interpretations are scientifically correct.

## Output support and current limitations

- PDF: offline A4-like paginated PDF, title/subtitle, hierarchical section headings, paragraphs, basic lists, tables, vector bar/line charts, locally attached figures, formula text blocks and a references section.
- DOCX: basic editable text/Markdown export. Charts, figures, complex tables, advanced equations, automatic bibliography fields and journal-specific templates are not yet at PDF parity.
- Image attachments use content URIs selected through Android's document picker. Access can depend on the source provider and file availability.
- The renderer is not a substitute for journal production software. Before submission, inspect the PDF in a full PDF reader and follow the target journal's author instructions.
- It does not run Typst, Quarto, Python or generated code on the phone. Advanced LaTeX typesetting, complex vector illustrations, automatic reference verification and true journal templates are future work.
- No AI model is bundled and no inference API is called by this document renderer.

## Privacy and cost

The package is rendered locally on the device. Selected figure files are read through Android's document provider; the exporter does not upload the images or JSON package to the ResearchMate Worker. AI drafting occurs in the separate AI service chosen by the user and is subject to that service's data policy.
