# ResearchMate AI 1.0 (experimental)

A research workflow for automatic scholarly discovery, legal open-access full-text retrieval, structured per-paper evidence extraction, cautious cross-paper synthesis, and discipline-specific outputs.

## What it does

1. Searches OpenAlex by topic and publication years (experimental single-query discovery; not a complete systematic search).
2. Keeps articles whose full text cannot be obtained; marks them as abstract-only or metadata-only instead of silently dropping them.
3. Attempts legal open-access retrieval from OpenAlex cached GROBID TEI/PDF content (when an optional free OpenAlex key is supplied), `best_oa_location` / location PDF links, and Europe PMC full-text XML for PMCID records.
4. Extracts PDF text, page references, and tables when available.
5. Produces rule-based evidence extraction by default. With a Gemini API key **and explicit consent**, it can analyze each paper and synthesize evidence across papers. The key is entered at runtime and is not stored in the repository.
6. Creates separate outputs: a review-article draft (DOCX/PDF), editable A1 scientific poster (PPTX/PDF), evidence/comparison workbook (XLSX), exploratory relationship map (PNG), BibTeX, methods/audit report (PDF), and ZIP package. If Persian PDF dependencies are missing, the app skips those PDFs rather than producing broken square-glyph output and includes an output-warning file.

## Important scientific limitations

- This is an experimental research assistant, not an autonomous scientific authority or a publication-ready systematic review.
- OpenAlex search is not exhaustive. Search strategies, inclusion criteria, and screening should be refined for formal reviews.
- Full text is only downloaded from legal open-access sources. No paywall or access-control bypass is attempted.
- The rule-only mode is an extraction aid, not deep reasoning. Model-generated claims must be checked against cited source passages.
- PDF tables/figures may not be perfectly extracted. Scanned PDFs may have no selectable text.
- The Gemini free tier has model/rate/region availability limits, and Google may process submitted content under its free-tier data practices. OpenAlex has a free API key option; cached full-text downloads count against the daily API allowance. User consent is required before paper text is sent. Never expose API keys in source code or commit history.
- Streamlit Community Cloud session storage is temporary; project persistence, authentication, and long-term file storage are not implemented in this version.

## Deploy on Streamlit Community Cloud

1. Push the repository to GitHub.
2. In Streamlit Community Cloud, deploy `app.py` from branch `main`.
3. The app installs packages from `requirements.txt`.
4. For deeper LLM analysis, create a Gemini API key in Google AI Studio and enter it in the app at runtime. For improved full-text coverage, an optional free OpenAlex API key unlocks its cached PDF/TEI endpoint. Do not commit it or paste it into `app.py`.

## Local run

```bash
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\\Scripts\\activate
pip install -r requirements.txt
streamlit run app.py
```

## Outputs

- `ResearchMate_Review_Article.docx` and `.pdf`
- `ResearchMate_Scientific_Poster_A1.pptx` and `ResearchMate_Scientific_Poster_A1.pdf`
- `ResearchMate_Evidence_Matrix.xlsx`
- `ResearchMate_Evidence_Map.png`
- `ResearchMate_References.bib`
- `ResearchMate_Methodology_Audit.pdf`
- A ZIP package of all successfully generated outputs, plus an optional output warning text file
