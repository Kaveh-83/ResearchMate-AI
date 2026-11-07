# ResearchMate AI 1.1 (experimental)

A research workflow for automatic scholarly discovery, legal open-access full-text retrieval, structured per-paper evidence extraction, cautious cross-paper synthesis, and discipline-specific outputs.

## What it does

1. Searches OpenAlex by topic and publication years with `open_access.is_oa:true` and `type:article|review`; closed-access records are excluded from discovery. This remains an exploratory single-query search, not a complete systematic search.
2. Keeps articles whose full text cannot be obtained; marks them as abstract-only or metadata-only instead of silently dropping them.
3. Attempts legal open-access retrieval from OpenAlex cached GROBID TEI/PDF content (when an optional free OpenAlex key is supplied), verified OpenAlex OA locations, the optional Unpaywall DOI resolver (requires the user's email), and Europe PMC full-text XML for PMCID records.
4. Extracts PDF/XML/HTML text, page references, and tables when available. Download candidates are restricted to locations OpenAlex marks as open access; a closed-access primary location is never used as a fallback.
5. Produces rule-based evidence extraction by default. With a valid Gemini API key **and explicit consent**, it can analyze each paper and synthesize evidence across papers. Model-proposed quotations are checked against the retrieved source text, and unsupported quotations are discarded. A model-generated review still requires human verification.
6. Creates separate outputs: a review-article draft (DOCX/PDF), editable A1 scientific poster (PPTX/PDF), evidence/comparison workbook (XLSX), exploratory relationship map (PNG), BibTeX, methods/audit report (PDF), and ZIP package. If Persian PDF dependencies are missing, the app skips those PDFs rather than producing broken square-glyph output and includes an output-warning file.

## Important scientific limitations

- This is an experimental research assistant, not an autonomous scientific authority or a publication-ready systematic review.
- OpenAlex search is not exhaustive. Search strategies, inclusion criteria, and screening should be refined for formal reviews.
- Full text is only downloaded from legal open-access sources. No paywall or access-control bypass is attempted.
- The rule-only mode is an extraction aid, not deep reasoning. Model-generated claims must be checked against cited source passages; quotation matching is a safeguard, not proof that every interpretation is correct.
- PDF tables/figures may not be perfectly extracted. Scanned PDFs may have no selectable text.
- The Gemini free tier has model/rate/region availability limits, and Google may process submitted content under its free-tier data practices. OpenAlex has a free API key option; cached full-text downloads count against the daily API allowance. User consent is required before paper text is sent. Never expose API keys in source code or commit history.
- Streamlit Community Cloud session storage is temporary; project persistence, authentication, and long-term file storage are not implemented in this version.

## Deploy on Streamlit Community Cloud

1. Push the repository to GitHub.
2. In Streamlit Community Cloud, deploy `app.py` from branch `main`.
3. The app installs packages from `requirements.txt`.
4. For deeper LLM analysis, create a Gemini API key in Google AI Studio. Add it to Streamlit Community Cloud → App → Settings → Secrets as `GEMINI_API_KEY = "your-key"`, or enter it in the sidebar at runtime. Then explicitly enable the consent checkbox. Never commit the key or paste it into `app.py`.
5. An optional OpenAlex API key can be stored as `OPENALEX_API_KEY = "your-key"` in the same Secrets panel. Basic OpenAlex search does not require a key; the key increases the API allowance and enables access to OpenAlex's cached full-text endpoints when available.
6. For additional OA DOI resolution, optionally store your own email as `UNPAYWALL_EMAIL = "you@example.org"` in Secrets or enter it in the app. Unpaywall requires an email on its API requests. The app uses returned locations only when Unpaywall explicitly confirms `is_oa: true`.

## Local run

```bash
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\\Scripts\\activate
pip install -r requirements.txt
streamlit run app.py
```

## Regression tests

Run `python engine_mock_test.py` to test the fallback pipeline, mocked Gemini pipeline, OpenAlex Open Access filtering, exclusion of closed-access PDF locations, and source-verification of model quotations. Run `python multi_agent_smoke_test.py` to test the provider registry, fallback behavior, and source-bound agent prompts. Run `python smoke_test.py` to exercise the output package generators.

## Outputs

- `ResearchMate_Review_Article.docx` and `.pdf`
- `ResearchMate_Scientific_Poster_A1.pptx` and `ResearchMate_Scientific_Poster_A1.pdf`
- `ResearchMate_Evidence_Matrix.xlsx`
- `ResearchMate_Evidence_Map.png`
- `ResearchMate_References.bib`
- `ResearchMate_Methodology_Audit.pdf`
- A ZIP package of all successfully generated outputs, plus an optional output warning text file


## Experimental cloud backend scaffold (not deployed)

A minimal Cloudflare Worker + D1 job ledger is available under `cloudflare/worker/`. It is intentionally not wired into the Streamlit execution path yet.

To provision it in your own Cloudflare account:
1. Install Node.js and Wrangler, then authenticate with `npx wrangler login`.
2. From `cloudflare/worker`, create the D1 database: `npx wrangler d1 create researchmate-db`.
3. Replace the placeholder `database_id` in `wrangler.toml` with the ID returned by Cloudflare.
4. Apply the schema: `npx wrangler d1 execute researchmate-db --file=schema.sql`.
5. Set a long random API token: `npx wrangler secret put APP_API_TOKEN`.
6. Deploy: `npx wrangler deploy`.

The Worker exposes authenticated job-create, job-status, and job-completion endpoints. The health endpoint is public and returns no user data. The API token must be stored as a server-side Streamlit Secret and must never be embedded in browser JavaScript or committed to GitHub.

Important: this is a job-record API scaffold only. It does not yet launch GitHub Actions, call model providers, or process PDFs asynchronously. Do not treat a successful Worker deployment as a complete cloud research pipeline.
