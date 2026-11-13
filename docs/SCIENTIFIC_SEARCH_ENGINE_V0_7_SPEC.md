# ResearchMate AI — v0.7 Scientific Search Engine Specification

**Status:** Approved product direction / implementation specification  
**Target release:** v0.7.x  
**Prepared:** 2026-11-13  
**Primary goal:** Turn ResearchMate's current single-provider, open-access-only lookup into a trustworthy, cross-disciplinary scholarly discovery engine.

## 1. Product definition

ResearchMate Search is a multi-provider scholarly metasearch system. A user submits one research question and receives a unified, deduplicated result set from general scholarly indexes and subject-specific databases. Search must work across disciplines; it must not assume that every user is in materials science.

The system must distinguish three different things:

1. **Discovery:** bibliographic metadata for a work exists in a scholarly index.
2. **Access:** a legal full-text location is available, or the publisher requires a subscription/payment.
3. **Impact/standing:** article-level citation indicators and journal-level metrics. These are different measures and must never be presented as interchangeable.

A search result is not proof that the full text was read or reviewed.

## 2. Search experience

### 2.1 Search modes

- **Quick search:** query terms, relevance ranking, optional year range.
- **Advanced search:** fielded clauses for title, abstract, author, DOI, journal/venue, ISSN, publisher, affiliation, and keywords.
- **Exact phrase and Boolean mode:** quoted phrases, AND/OR/NOT, parentheses where supported by the selected providers. The UI must show when a provider cannot honor a given operator.
- **Identifier lookup:** DOI, PMID, PMCID, arXiv ID, ORCID, ISSN, or exact title.
- **Subject-focused search:** select a discipline profile to add relevant vocabularies, provider routing, publication types, journal/source facets, and discipline-specific filters.
- **Query expansion:** suggest synonyms, acronyms, spelling variants, controlled vocabulary terms, and English equivalents for Persian queries. The user can inspect, edit, or disable expansion. The original query must always be retained.

### 2.2 Discipline profiles

The initial profile catalog should cover:

- Materials science, metallurgy, manufacturing, and chemical engineering
- Mechanical, electrical, civil, and other engineering
- Computer science, AI, and information technology
- Physics, mathematics, and astronomy
- Chemistry and chemical sciences
- Medicine, nursing, and public health
- Biology, biotechnology, and life sciences
- Environment, climate, and earth sciences
- Agriculture and food science
- Economics, business, and management
- Psychology, education, and social sciences
- Humanities, law, and arts
- Multidisciplinary / no profile

A profile is a routing/configuration layer, not a hard inclusion filter. Users must be able to search outside the selected profile and inspect all providers. Profiles should be editable and versioned.

Each profile should define:
- preferred and supplementary providers;
- domain vocabulary and query-expansion terms;
- relevant publication types and study designs;
- known subject categories and journal/source catalog references;
- profile-specific filters and result labels.

### 2.3 Advanced filters

Implement filters progressively, with unsupported filters disabled or clearly marked per provider:

- publication date: from/to, exact year, and date type (online, issue, accepted where available);
- publication type: journal article, review, systematic review, meta-analysis, conference paper, preprint, book/chapter, thesis, report, dataset, clinical trial, editorial, standard;
- field / subject category;
- journal / venue / ISSN / publisher;
- author, ORCID, institution, country, funder;
- language;
- peer-review status where the source can verify it;
- full-text status: open full text, repository copy, metadata/abstract only, subscription/purchase indicated, unknown;
- citation-count range and ranking/percentile where supported;
- minimum abstract availability;
- retracted, corrected, expression of concern, or other post-publication status where available;
- sort by relevance, newest, oldest, citation count, journal metric, or source-specific relevance.

Never infer peer review, journal quartile, full-text availability, or retraction status from the title alone.

## 3. Provider strategy

Use replaceable server-side provider adapters behind the Cloudflare Worker. No API secret is embedded in the Android APK. Provider errors must be isolated: one failing source must not cancel successful results from the others.

### 3.1 Cross-disciplinary core providers

| Provider | Main value | Initial use |
|---|---|---|
| OpenAlex | Broad scholarly graph: works, authors, institutions, sources, topics, citations, OA locations | Core cross-disciplinary search, source/journal lookup, citation and OA metadata |
| Crossref | DOI registration metadata, publisher deposits, journal/ISSN lookup, licenses, abstracts, post-publication updates | Broad publisher and journal metadata, identifier resolution |
| Semantic Scholar | Scholarly graph, citation/reference links, fields of study, venue and paper-level citation information | Related papers, references/citations, field-aware ranking signals |
| OpenAIRE Graph | Publications and links to research outputs, organizations, projects, and OA information | Supplementary coverage and graph enrichment |

### 3.2 Subject-specific providers

| Area | Providers to route to | Notes |
|---|---|---|
| Medicine / life sciences | PubMed / NCBI E-utilities, PubMed Central, Europe PMC | Biomedical query syntax, MeSH where available, publication types, clinical-trial metadata, PMC/Europe PMC full-text links |
| Physics / mathematics / computer science | arXiv | Preprints and subject categories; always label preprints separately from peer-reviewed publications |
| Multidisciplinary OA journals | DOAJ | OA journal and deposited article metadata; not a substitute for subscription-journal coverage |
| Computer science / engineering | Semantic Scholar plus broad indexes; optional ACM/IEEE publisher adapters | Publisher-specific APIs require separate terms/credentials where applicable |
| Other disciplines | OpenAlex, Crossref, OpenAIRE, Semantic Scholar plus future specialist adapters | Routing must be driven by the selected profile and source capabilities, not a fixed materials-only list |

### 3.3 Optional/credentialed integrations

Keep adapters for the following as optional future integrations, only after verifying current terms, API access, quotas, and any user-provided institutional/API entitlement:

- Elsevier Scopus / ScienceDirect
- Clarivate Web of Science / Journal Citation Reports
- IEEE Xplore
- Springer Nature
- CORE and other repository aggregators
- Additional discipline repositories and publisher platforms

Do not scrape Google Scholar or publisher websites as a substitute for documented APIs. Do not imply that a university's website login automatically authorizes app-level API use. No paid provider may be activated by default or incur charges silently.

### 3.4 Current OpenAlex key requirement

OpenAlex is still the first provider, but production-scale use should use a server-side free API key. OpenAlex documents a free key and a daily free budget; heavier usage can be usage-priced. The Worker must enforce a strict free-budget policy and must not opt into paid usage without explicit administrator configuration. If the free budget or provider quota is exhausted, show a provider-level pause/fallback rather than silently incur charges.

### 3.5 API behavior and resilience

- Use documented official APIs and respect each provider's terms, rate limits, and usage policies.
- Crossref requests should identify the service using a polite-pool `mailto` parameter or agent header, and use caching / backoff.
- Respect arXiv's request interval and paging rules.
- NCBI / Europe PMC calls should use their supported search and retrieval APIs.
- All provider calls need timeout, bounded concurrency, retry/backoff for transient errors, circuit breaking, and provider-specific rate limits.
- Apply caching to repeated queries and identifier lookups. Cache TTL must reflect data volatility and provider policy.
- Return partial results when some providers fail. Include a per-provider status: success, no results, throttled, unavailable, disabled, or credential required.
- Keep independent provider cursors/page tokens. A merged result set must not pretend one shared cursor is valid for every source.
- Show result coverage: which sources were queried, which responded, how many records each contributed, and which failed/skipped.
- Keep server-side secrets in Cloudflare Worker secrets only. Never log secrets or send them to the app.

## 4. Open-access and subscription result separation

Search all available bibliographic records, not only open-access records.

Every record must have an explicit access label:

1. **Open full text — publisher:** full text/PDF at a publisher-hosted OA location.
2. **Open full text — repository:** legal repository copy or accepted manuscript.
3. **Open access status indicated, link unverified:** metadata indicates OA but the location cannot currently be verified.
4. **Subscription / purchase indicated:** metadata is discoverable, but no legal free full-text location was found.
5. **Metadata / abstract only:** record is discoverable, full-text status is unknown or unavailable.
6. **Unknown:** not enough metadata to make a safe determination.

UI organization:
- Primary **Open full-text** results tab.
- Separate **Subscription / restricted** results tab.
- Optional **All results** tab.
- Visible filters and counts for both categories.
- Each paywalled/restricted record provides DOI, publisher landing page, abstract/metadata if licensed for display, and any legitimate institutional/library access route where supplied by a provider.
- Show legal repository alternatives before labeling a work as subscription-only.
- Allow the user to add a library PDF they already have access to, subject to local import behavior.

Never bypass paywalls, authentication, DRM, access controls, or publisher restrictions. Do not download or expose subscription full text without a lawful entitlement. A publisher link or institutional access hint is discovery, not proof that the app can retrieve the PDF.

The Unpaywall query-search endpoint was retired on 2026-09-18; do not build the new search around that retired endpoint. Use OpenAlex for broad search and OA locations, and use a DOI-level resolver only when it is still supported and appropriate.

## 5. Article-level impact and journal-level standing

These are separate UI sections and data models.

### 5.1 Article-level indicators

- Citation count from each provider, displayed separately (for example, OpenAlex citations and Semantic Scholar citations).
- Provider name and last-checked date/time beside every count.
- Citation velocity or citations-per-year only when the date and citation data support a defensible calculation.
- Field/year-normalized citation percentile only when a provider supplies it or ResearchMate can compute it from a defined, sufficiently large comparison cohort.
- Influential citation information and citation contexts only when a source supplies them.
- References and papers citing this work, where the source API supports them.
- Retraction/correction notices and post-publication updates when authoritative metadata is available.

Do not add citation counts from multiple providers together. Coverage and deduplication differ, and the resulting number would be misleading. Citation count is not proof of quality.

### 5.2 Journal-level indicators

Show journal metrics separately from metrics for the individual article:

- SJR and SJR quartile (with source/year);
- Journal H-index (with source);
- Journal Impact Factor only when a legitimate JCR data entitlement/API is available;
- CiteScore only when a legitimate Scopus/Elsevier data entitlement/API is available;
- journal subject categories, publisher, country, ISSN/eISSN, and indexing claims with source/date.

Do not label SJR, JIF, CiteScore, H-index, or quartile as an "article rank". Do not infer Q1–Q4 from a journal name or an outdated cached record. If a metric is unavailable, show "not verified" rather than estimating it. Verify licensing and redistribution terms before ingesting or redistributing commercial journal-metric datasets.

### 5.3 Article ranking within search results

Default ranking should be transparent and configurable:
- text relevance and match quality;
- match to selected field/profile;
- recency, with an adjustable recency boost;
- citation evidence, normalized by field and publication year where supported;
- source completeness and metadata quality;
- user-selected inclusion/exclusion preferences.

Do not use raw citation count or journal quartile as the sole quality score. Provide a "Why this result?" explanation showing which factors influenced ordering. Keep relevance sort available even when metrics are missing.

## 6. Deduplication and canonical record model

Deduplicate in this order:
1. normalized DOI;
2. PMID / PMCID / arXiv ID and other trusted identifiers;
3. provider work IDs linked through a verified DOI or identifier map;
4. normalized title + first author + publication year as a conservative fallback.

Keep all provider IDs and source-specific metadata on the canonical record. Preserve conflicting values as source-labelled values; do not silently choose one publisher name, title, date, or citation count when sources disagree.

Recommended canonical fields:
- stable local ID and canonical identifier;
- title, subtitle, abstract, keywords;
- authors and affiliations;
- publication date/year and date precision;
- document type and peer-review/preprint labels;
- journal/venue, publisher, ISSN/eISSN, volume/issue/pages;
- DOI and provider IDs;
- provider list and source URLs;
- OA status, license, legal full-text locations, publisher landing page;
- citation counts keyed by provider and checked timestamp;
- journal metrics keyed by metric/source/year;
- retraction/correction status;
- field categories and subject-profile matches;
- record provenance, fetched timestamp, and missing/conflict flags.

## 7. Result card and detail screen

Each result card should show:
- title and authors;
- year, journal/venue, publisher;
- work type and preprint/peer-review label;
- access badge;
- DOI and publisher/source links;
- source-provider chips;
- citation counts with provider names;
- journal metrics only when verified (metric + year + source);
- short abstract snippet and matched query terms;
- save, compare, open source, and export actions.

The detail screen should include:
- complete available metadata;
- access alternatives and license information;
- journal profile with ISSN and metrics;
- separate article citation panel;
- references / citing works;
- source provenance and last-updated timestamps;
- corrections/retractions warnings;
- related articles and search-within-this-journal action.

## 8. ResearchMate-added recommendations

The following improvements are recommended beyond the requested filters:

1. **Related-work graph:** explore references, citing papers, and papers that cite the same references.
2. **Citation context:** show whether a citing paper supports, extends, or contrasts with the source when the provider explicitly classifies citation intent. Do not invent this classification.
3. **Seminal vs recent toggle:** discover foundational papers separately from the newest work.
4. **Journal explorer:** search journals by subject, ISSN, publisher, indexing source, OA status, and verified metric/year.
5. **Retraction and correction alerts:** make post-publication updates visible and offer saved-paper monitoring when a reliable source supports it.
6. **Saved searches and alerts:** let users save a query/profile/filter set and optionally receive update notifications.
7. **Reproducible search history:** save query, expanded terms, provider list, filters, timestamps, counts, and errors so a search can be repeated in a thesis or systematic review.
8. **Systematic-review mode:** export search strategies and screening records; later add inclusion/exclusion decisions and PRISMA-style counts. Do not advertise full PRISMA compliance until the complete workflow is validated.
9. **Bulk export:** RIS, BibTeX, CSV, JSON, and later EndNote-compatible formats, with provider provenance preserved.
10. **Search quality controls:** query spell-check, acronym expansion, duplicate detection, empty-result suggestions, and "search again in this journal/author/subject" actions.
11. **Trust labels:** mark metadata-only, abstract-only, full-text-read, and user-verified records distinctly. A result must never be described as "analyzed" solely because metadata was retrieved.
12. **Persian-first query assistance:** optional Persian-to-English search-term expansion and bilingual query history, while preserving the original user wording.

## 9. Backend/API contract

Introduce a versioned search request (prefer POST for complex filter payloads) while preserving the current v1 route until the Android migration is complete.

Proposed request concepts:
- query text and query clauses;
- selected discipline profile;
- selected/disabled providers;
- date range and document types;
- journal, publisher, ISSN, author, affiliation, and identifier filters;
- language, access mode, peer-review/preprint preference;
- citation and journal metric constraints;
- page size, independent provider cursors, and sort mode;
- query-expansion options.

Proposed response:
- request ID, canonical query, applied/unsupported filters;
- canonical deduplicated records;
- provider status/count/latency/error information;
- access-category totals;
- per-provider citation and journal metrics;
- independent continuation tokens;
- search timestamp and provenance.

Keep provider adapter interfaces independent of Android UI. Kotlin should parse a stable canonical schema rather than provider-specific payloads. Add tests for normalization, deduplication, provider failures, partial success, access classification, metric provenance, filter support, and cursor handling.

## 10. Delivery plan

### v0.7.0 — Search foundation
- Provider adapter framework and stable unified result schema.
- OpenAlex no longer filters out subscription records.
- Crossref and Semantic Scholar integration.
- OpenAIRE integration if operationally stable.
- Multi-provider merging and DOI/identifier-based deduplication.
- Separate Open full-text / Subscription-restricted / All tabs.
- Provider status and per-source citation counts.
- Core filters: date, type, author, journal, publisher, DOI, access status, citation range.
- Search history, pagination, and query reproducibility.
- Android result cards/detail screen update.

### v0.7.1 — Discipline-specialist coverage
- Discipline profiles and subject-specific routing.
- PubMed/PMC and Europe PMC for biomedical searches.
- arXiv for relevant preprint-heavy fields.
- DOAJ for OA journals/articles.
- Journal/ISSN explorer.
- Query expansion, field categories, language and peer-review/preprint distinctions.

### v0.7.2 — Journal and citation intelligence
- Journal metric catalog with verified source/year and clear licensing.
- Citation/reference graph and related-work navigation.
- Field/year-normalized indicators only where defensible.
- Retraction/correction notices and saved-search alerts where reliable sources exist.

### Later releases
- Optional integrations with licensed publisher indexes/APIs.
- Systematic-review workflow and PRISMA-style screening/export.
- Advanced relevance learning based on explicit user feedback.
- More subject-specific databases based on coverage audits and verified API access.

## 11. Acceptance criteria

The feature is not complete until all the following are verified:

- A normal search can return both OA and subscription-metadata records.
- Open full-text records and restricted/metadata-only records are visibly separated.
- A search queries multiple enabled providers and reports each provider's result/error state.
- Results are deduplicated without losing provider provenance.
- Citation counts are source-labelled, never summed across providers.
- Journal metrics display metric name, source, and year; unavailable/licensed metrics are not fabricated.
- Subject profiles change provider routing and suggested filters without hiding the global search option.
- Unsupported filters are identified rather than silently ignored.
- No paid API or usage-based service can charge by default.
- All external content has visible source links and retrieval dates.
- Existing v0.6 local library, projects, evidence, and exports continue to work.
- APK build and Worker typecheck/deployment checks pass; at least one live query per provider is smoke-tested.
- Search is verified on a physical Android device and at least one Iranian network before declaring regional availability.

## 12. Official provider references

- OpenAlex API reference: https://help.openalex.org/api/
- OpenAlex authentication and free API key: https://help.openalex.org/api/authentication/
- OpenAlex usage/pricing announcement: https://blog.openalex.org/openalex-api-new-features-and-usage-based-pricing/
- Crossref REST API: https://www.crossref.org/documentation/retrieve-metadata/rest-api/
- Crossref authentication and rate limits: https://www.crossref.org/documentation/retrieve-metadata/rest-api/access-and-authentication/
- Semantic Scholar Academic Graph API: https://api.semanticscholar.org/api-docs/graphs
- OpenAIRE Graph API: https://graph.openaire.eu/docs/apis/graph-api-v4/quickstart/
- NCBI APIs / E-utilities: https://www.ncbi.nlm.nih.gov/home/develop/api/
- Europe PMC REST API: https://dev.europepmc.org/RestfulWebService
- arXiv API manual: https://info.arxiv.org/help/api/user-manual.html
- DOAJ metadata help: https://doaj.org/docs/faq/
- Unpaywall API status: https://unpaywall.org/api
- SCImago Journal Rankings: https://www.scimagojr.com/journalrank.php
- Clarivate Web of Science Journals API: https://developer.clarivate.com/apis/wos-journal

