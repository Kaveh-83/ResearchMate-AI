# ResearchMate AI v1.0.3 — Scientific Document Studio

## Architecture

The manuscript is authored in Markdown. Title, abstract and keywords are independent fields. Structured scientific assets (tables, charts, figures, equations and references) are stored separately so ordinary manuscript edits do not erase them.

The legacy schema 1.0 JSON package remains supported as an import/export interchange format. It is no longer the primary manuscript editing experience.

- PDF: offline HTML/CSS layout rendered in Android WebView and paginated by Android PrintManager. The system print dialog opens; select **Save as PDF** to save a file.
- DOCX: a separate WordprocessingML package writer. It creates editable paragraphs and headings, native Word tables, embedded PNG figure/chart images, equations as editable text, and references.
- Markdown: independent manuscript export.
- JSON: complete package backup/export and compatibility import.

No AI-generated code is executed. The scientific document rendering path disables JavaScript and network loading. Figure attachments are selected through Android's document picker and accepted only as content URIs.

## User workflow

1. Open **خانه → استودیوی سند علمی**.
2. Tap **کپی پرامپت پژوهش** and provide the topic, intended document type, language, date range and source constraints to the AI assistant.
3. The prompt requests four clearly labelled blocks: document title, abstract, keywords and manuscript Markdown. Copy each block into its matching field; paste only the manuscript section into the Markdown editor.
4. Use Markdown headings: # for main sections, ## for subsections, and ### for lower-level headings. Basic bold, italic, lists, blockquotes and Markdown tables are supported.
5. The draft is automatically saved locally on the device. It is not uploaded to the ResearchMate Worker.
6. If you have an older ResearchMate JSON package, open **ورود/ویرایش اجزای پیشرفته** and import the JSON file. The article text is moved into Markdown fields while tables, charts, figures, equations and references are preserved.
7. For each figure, select the image from the phone. Persisted content URIs are attached to the matching figure ID.
8. Review structural errors and warnings. Validation checks structure, not scientific truth.
9. Export PDF, Word, Markdown or a full JSON package backup.
10. For PDF, use the Android print dialog and select **Save as PDF**. Inspect the saved file before sharing or submitting it.

A sample research package and Markdown output are available in examples/steel_ml_mini_review.json and examples/steel_ml_mini_review.md.

## Structured package schema (legacy/interchange)

The top-level fields are:

- schema_version: currently 1.0.
- document: title, optional subtitle, language and citation-style label.
- manuscript: abstract, keywords and ordered sections. Each section has heading, level and Markdown content.
- tables: table title, headers, rows and source note.
- charts: bar or line charts with categories and one or more numeric series, axis labels and source.
- figures: unique IDs, captions, descriptions, source/licence notes and optional image_uri values inserted by the app after image selection.
- equations: display label, LaTeX source and plain-text alternative.
- references: full bibliographic entries, preferably objects with a text property.

The package is validated before export. Importing a package never executes embedded HTML, JavaScript or scripts.

## Supported manuscript Markdown

- Headings: #, ##, ###.
- Paragraphs and line breaks.
- Bold and italic inline emphasis.
- Ordered and unordered lists.
- Blockquotes.
- Markdown tables with a header row and separator row.
- Basic HTML escaping is applied before content is inserted into the local HTML document.

This is a focused Markdown subset, not a full CommonMark implementation. Inline images, footnotes, complex nested lists, raw HTML, citation plugins and automatic bibliography processing are not supported by the Markdown parser. Add scientific figures and chart data through structured assets.

## Tables

Each structured table has a title, a non-empty header array and rows with exactly the same number of cells as the headers. Units should be explicit. PDF uses HTML tables with repeating header groups where supported by WebView printing. DOCX uses native Word tables. Very wide tables and unusually long cells still require visual review.

Markdown tables in manuscript sections are converted to native HTML and Word tables.

## Charts

The chart type is bar or line; categories contains x-axis labels; each series contains a name and a numeric values array with one value per category. Include x_label, y_label (including units) and a real source. PDF uses SVG charts; DOCX embeds a generated PNG chart and also includes the underlying values as a native table. The app does not infer missing data or generate experimental values.

## Figures and images

Give each figure a unique ID such as fig1, a self-contained caption, a useful description and a source/licence statement. The user attaches the actual image in the app. image_uri is inserted locally by ResearchMate and must not be fabricated by the AI. Only content:// URIs are accepted for export. If no image is attached or the image cannot be read, the document displays a descriptive placeholder instead of pretending an image exists.

DOCX embeds supported images as PNG. The writer limits decoded image dimensions to reduce memory pressure and preserves the original aspect ratio. Very large or unusual provider-backed files should still be tested on the target device.

## Equations

The PDF renderer currently displays plain_text in a styled formula block and falls back to latex only when plain text is absent. DOCX writes an editable text representation. Advanced MathML/LaTeX typesetting, equation numbering fields and journal-specific equation layout are not yet implemented.

## References and scientific integrity

References are displayed as supplied. The app checks for blank entries but does not independently verify DOI resolution, author names, journal data or citation-style compliance. Check each reference against a trusted scholarly database or publisher page.

Passing validation means the package is structurally renderable. It does not prove that claims, measurements, images, DOIs, references or interpretations are scientifically correct.

## Validation and tests

JVM regression tests cover:

- Markdown conversion to separate manuscript sections.
- Preservation of structured assets when the Markdown manuscript changes.
- Rejection of invalid table row widths.
- Repair of common JSON wrappers/trailing commas without overwriting the original input.
- Content URI image attachment rules.
- A complete steelmaking/ML sample package rendered into HTML, including tables and SVG charts.
- Rejection of remote image URLs in structured figures.

The GitHub Actions workflow runs testDebugUnitTest before assembling the Android debug APK.

## Known limitations and required device QA

- Android PrintManager owns the PDF destination and print lifecycle. The user must select Save as PDF in the system dialog.
- Real-device testing is still required for print-service availability, content-URI image permissions, long-document pagination, RTL/LTR mixed paragraphs, wide tables, memory use and output appearance across Android versions.
- DOCX has a separate writer and embeds charts/figures, but advanced equations, automatic bibliography fields, headers/footers, journal-specific styles and complex multi-page table behavior need further validation in Microsoft Word and LibreOffice.
- The app has not yet been validated against every journal's production requirements. Inspect both PDF and DOCX before submission.
- It does not run Typst, Quarto, Python or generated code on the phone. No AI model is bundled and no inference API is called by the document renderer.

## Privacy and cost

The package is rendered locally on the device. Selected figure files are read through Android's document provider; the exporter does not upload images or the JSON package to the ResearchMate Worker. AI drafting occurs in the separate AI service chosen by the user and is subject to that service's data policy.
