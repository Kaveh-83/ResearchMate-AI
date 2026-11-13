# ResearchMate Edge API — v0.7

Cloudflare Worker API for the native ResearchMate AI app. Version 0.7 adds a provider-based academic search endpoint.

## Production service

- Worker: `researchmate-ai`
- Public base URL: `https://researchmate-ai.kavehjafari710.workers.dev`
- Health: `GET /v1/health`
- Legacy compatibility: `GET /v1/literature/search` (OpenAlex metadata)
- New multi-provider search: `POST /v2/academic-search`

## Providers

| Provider ID | API | Purpose |
|---|---|---|
| `openalex` | OpenAlex Works API | Broad multidisciplinary metadata, citations, OA locations, source indicators |
| `crossref` | Crossref REST API | DOI registration and publisher-deposited metadata |
| `semantic_scholar` | Semantic Scholar Graph API | Scholarly graph, fields of study, citations, OA PDF metadata |
| `europe_pmc` | Europe PMC REST API | Biomedical/life-science search, abstracts, MeSH and full-text links |
| `pubmed` | NCBI E-utilities | PubMed records and publication metadata |
| `arxiv` | arXiv Atom API | Preprint discovery for arXiv subject categories |

The app can ask for automatic provider routing by discipline or provide a manual provider list. A provider failure is isolated and returned in `providerStatus`; successful sources still return results.

## Request example

```http
POST /v2/academic-search
Content-Type: application/json
```

```json
{
  "query": "high entropy alloy coatings",
  "yearFrom": 2020,
  "yearTo": 2026,
  "profile": "materials",
  "providers": [],
  "author": "",
  "journal": "",
  "publisher": "",
  "documentType": "",
  "accessMode": "all",
  "minCitations": null,
  "sort": "relevance",
  "page": 1,
  "pageSize": 20
}
```

Set `providers` to an array of provider IDs to override profile routing. An empty array activates routing for `profile`.

## Response behavior

The `works` array is normalized across sources and deduplicated primarily by DOI. Provider identifiers and source names are retained.

- `citationCounts` is keyed by provider. Values are not summed across sources.
- `accessStatus` is `open`, `restricted`, or `unknown`.
- `open` means that a source returned a full-text/open-access URL or an explicit OA indicator.
- `restricted` means a source explicitly reports no OA status/availability (not proof that every version is paywalled).
- `unknown` means the provider metadata is insufficient to establish access.
- `journalMetrics` currently reports OpenAlex source H-index and two-year mean citedness when those values are included in the work's source metadata. It does not report JCR Impact Factor, CiteScore or Q-rankings.
- `providerStatus` includes provider ID, state, count, latency and a bounded error message.
- `nextPage` is an app-level next-page hint. Each provider currently derives page/offset independently from the same page number; providers with cursor-only deep paging may require separate continuation work.

## Filters

The request supports:
- date range;
- subject profile;
- explicit provider list;
- author, journal, publisher;
- document type;
- access mode;
- minimum citation count;
- relevance/newest/citation sort;
- page and page size.

Some filters are applied as post-retrieval filters so their semantics are consistent across providers. Provider-native search syntax is not identical; this version does not promise full Boolean query parity for every source.

## Required bindings and optional provider secrets

Required Cloudflare bindings:
- `SEARCH_RATE_LIMITER` — rate-limits each client IP.

Optional secrets:
- `OPENALEX_API_KEY` — OpenAlex free key; allows a larger daily budget and cursor paging under OpenAlex terms.
- `SEMANTIC_SCHOLAR_API_KEY` — Semantic Scholar key for higher or more reliable provider quota where granted.
- `NCBI_API_KEY` — NCBI E-utilities key (default rate ceiling is lower without a key).
- `CROSSREF_MAILTO` — contact email used for Crossref polite-pool requests. Configure as a non-secret Worker variable if desired.

All provider keys stay on Cloudflare and are never included in Android. The current adapters can return results without most keys, but public anonymous limits vary and may throttle searches.

## Cost and legal boundaries

- No commercial index is activated by default.
- Do not add a pay-per-use provider without an explicit owner decision and spending controls.
- Subscription article metadata can be discoverable even if full text is not available.
- The Worker never attempts to bypass publisher paywalls, credentials, DRM or access restrictions.
- The Worker does not download subscription PDFs.
- A DOI/publisher link is not proof of app entitlement.
- Do not infer Q-rank, Impact Factor or CiteScore from a journal name.

## Security and reliability

- Query body is capped, query length bounded, provider result count capped, and requests have timeouts.
- Calls to providers run independently. One provider's timeout or HTTP 429 does not cancel all other providers.
- No raw provider keys are logged or returned.
- IP-based throttling is approximate and location-local; it is not a global authentication or quota system.
- Search should be tested across networks before being advertised as available in every country.
- Search responses use `Cache-Control: no-store` until a compliant cache policy is implemented.

## Local development

Requires Node.js 22+ and npm:

```bash
npm install
npm run typecheck
npm run dev
```

## Deployment

The workflow `.github/workflows/deploy-worker.yml` deploys the Worker after a change on `main` to `backend/edge-worker/**`. It uses repository secrets:
- `CLOUDFLARE_API_TOKEN`
- `CLOUDFLARE_ACCOUNT_ID`

After deployment, smoke-test:
- `GET /v1/health`
- `POST /v2/academic-search` with a general topic and a biomedical topic.

## Current limitations

- OpenAIRE Graph and DOAJ adapters are not part of the v0.7 first build; they are planned after API response shape and quota testing.
- Subscription metadata coverage depends on the metadata source and its publisher deposits. This is not a complete copy of Scopus/Web of Science.
- Journal metric coverage is limited to available OpenAlex source summary data.
- Crossref abstract metadata may contain publisher-supplied content; preserve provider attribution.
- arXiv feed parsing is deliberately limited to the metadata fields currently exposed by its Atom API.
- DOI/title deduplication uses a conservative fallback; false merges remain possible for common titles.
