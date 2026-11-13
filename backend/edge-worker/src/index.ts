export interface RateLimitBinding {
  limit(options: { key: string }): Promise<{ success: boolean }>;
}

export interface Env {
  APP_ALLOWED_ORIGINS?: string;
  OPENALEX_API_KEY?: string;
  SEMANTIC_SCHOLAR_API_KEY?: string;
  NCBI_API_KEY?: string;
  CROSSREF_MAILTO?: string;
  SEARCH_RATE_LIMITER: RateLimitBinding;
}

type AnyRecord = Record<string, any>;
type ProviderId = "openalex" | "crossref" | "semantic_scholar" | "europe_pmc" | "pubmed" | "arxiv";
type AccessStatus = "open" | "restricted" | "unknown";
type ProviderState = "success" | "empty" | "throttled" | "unavailable" | "not_requested";

interface SearchInput {
  query: string;
  yearFrom: number;
  yearTo: number;
  profile: string;
  providers: ProviderId[];
  author: string;
  journal: string;
  publisher: string;
  documentType: string;
  accessMode: string;
  minCitations: number | null;
  sort: string;
  page: number;
  pageSize: number;
}

interface NormalizedWork {
  id: string;
  title: string;
  authors: string;
  year: number | null;
  publicationDate: string;
  journal: string;
  publisher: string;
  citationCounts: Record<string, number>;
  doi: string;
  pdfUrl: string;
  landingUrl: string;
  abstractText: string;
  publicationType: string;
  sourceIds: string[];
  sources: string[];
  accessStatus: AccessStatus;
  accessLabel: string;
  peerReviewStatus: string;
  issn: string[];
  fieldOfStudy: string[];
  journalMetrics: {
    openAlexHIndex: number | null;
    openAlexTwoYearMeanCitedness: number | null;
    source: string;
    checkedAt: string;
  } | null;
  isRetracted: boolean | null;
}

interface ProviderStatus {
  provider: string;
  label: string;
  status: ProviderState;
  resultCount: number;
  message: string;
  elapsedMs: number;
}

const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
};

const PROVIDER_LABELS: Record<ProviderId, string> = {
  openalex: "OpenAlex",
  crossref: "Crossref",
  semantic_scholar: "Semantic Scholar",
  europe_pmc: "Europe PMC",
  pubmed: "PubMed",
  arxiv: "arXiv",
};

function json(data: unknown, status = 200, extra: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...JSON_HEADERS, ...extra },
  });
}

function corsHeaders(request: Request, env: Env): Record<string, string> {
  const origin = request.headers.get("Origin") ?? "";
  const allowed = (env.APP_ALLOWED_ORIGINS ?? "").split(",").map((item) => item.trim()).filter(Boolean);
  if (!origin) return {};
  if (allowed.includes("*") || allowed.includes(origin)) {
    return {
      "access-control-allow-origin": origin,
      "access-control-allow-methods": "GET, POST, OPTIONS",
      "access-control-allow-headers": "Content-Type, Authorization",
      "access-control-max-age": "86400",
      "vary": "Origin",
    };
  }
  return {};
}

function errorResponse(request: Request, env: Env, message: string, status: number): Response {
  const headers = { ...corsHeaders(request, env) };
  if (status === 429) headers["retry-after"] = "60";
  return json({ error: { message, status } }, status, headers);
}

function asRecord(value: unknown): AnyRecord {
  return value && typeof value === "object" && !Array.isArray(value) ? value as AnyRecord : {};
}

function asArray(value: unknown): any[] {
  return Array.isArray(value) ? value : [];
}

function cleanString(value: unknown, max = 500): string {
  return typeof value === "string" ? value.replace(/\s+/g, " ").trim().slice(0, max) : "";
}

function cleanYear(value: unknown): number | null {
  const year = typeof value === "number" ? Math.floor(value) : Number(value);
  return Number.isInteger(year) && year >= 1500 && year <= new Date().getUTCFullYear() + 1 ? year : null;
}

function normalizeDoi(value: unknown): string {
  return cleanString(value, 300).replace(/^https?:\/\/(dx\.)?doi\.org\//i, "").replace(/^doi:/i, "").toLowerCase();
}

function abstractFromOpenAlex(index: unknown): string {
  const inverted = asRecord(index);
  const words: Record<number, string> = {};
  for (const [word, positions] of Object.entries(inverted)) {
    for (const pos of asArray(positions)) {
      if (typeof pos === "number" && pos >= 0 && words[pos] === undefined) words[pos] = word;
    }
  }
  return Object.keys(words).map(Number).sort((a, b) => a - b).map((position) => words[position]).join(" ");
}

function stripMarkup(value: unknown): string {
  return cleanString(String(value ?? "").replace(/<[^>]*>/g, " ").replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
    .replace(/&#39;/g, "'").replace(/&quot;/g, '"'), 5000);
}

function baseWork(provider: ProviderId, input: Partial<NormalizedWork> & { title: string; sourceId?: string }): NormalizedWork {
  const doi = normalizeDoi(input.doi);
  const providerId = cleanString(input.sourceId, 500);
  return {
    id: doi ? "doi:" + doi : provider + ":" + (providerId || crypto.randomUUID()),
    title: cleanString(input.title, 1000) || "Untitled scholarly work",
    authors: cleanString(input.authors, 1000),
    year: input.year ?? null,
    publicationDate: cleanString(input.publicationDate, 40),
    journal: cleanString(input.journal, 500),
    publisher: cleanString(input.publisher, 300),
    citationCounts: input.citationCounts ?? {},
    doi,
    pdfUrl: cleanString(input.pdfUrl, 2000),
    landingUrl: cleanString(input.landingUrl, 2000),
    abstractText: cleanString(input.abstractText, 5000),
    publicationType: cleanString(input.publicationType, 120) || "unknown",
    sourceIds: providerId ? [provider + ":" + providerId] : [],
    sources: [provider],
    accessStatus: input.accessStatus ?? "unknown",
    accessLabel: input.accessStatus === "open" ? "Open full text" : input.accessStatus === "restricted" ? "No OA copy indicated" : "Access unknown",
    peerReviewStatus: cleanString(input.peerReviewStatus, 80) || "unknown",
    issn: input.issn ?? [],
    fieldOfStudy: input.fieldOfStudy ?? [],
    journalMetrics: input.journalMetrics ?? null,
    isRetracted: input.isRetracted ?? null,
  };
}

function providerErrorStatus(error: unknown): ProviderState {
  const message = error instanceof Error ? error.message : String(error);
  if (/429|rate.?limit|quota|thrott/i.test(message)) return "throttled";
  return "unavailable";
}

async function fetchJson(url: URL, headers: Record<string, string> = {}): Promise<AnyRecord> {
  const response = await fetch(url.toString(), {
    headers: { accept: "application/json", "user-agent": "ResearchMateAI/0.7 (+https://github.com/Kaveh-83/ResearchMate-AI)", ...headers },
    signal: AbortSignal.timeout(14000),
  });
  if (!response.ok) {
    const error = new Error("HTTP " + response.status + " from " + url.hostname);
    (error as AnyRecord).status = response.status;
    throw error;
  }
  return asRecord(await response.json());
}

async function searchOpenAlex(input: SearchInput, env: Env): Promise<{ works: NormalizedWork[]; total: number }> {
  const url = new URL("https://api.openalex.org/works");
  url.searchParams.set("search", input.query);
  url.searchParams.set("filter", "from_publication_date:" + input.yearFrom + "-01-01,to_publication_date:" + input.yearTo + "-12-31");
  url.searchParams.set("per-page", String(input.pageSize));
  url.searchParams.set("page", String(input.page));
  if (input.sort === "newest") url.searchParams.set("sort", "publication_date:desc");
  else if (input.sort === "citations") url.searchParams.set("sort", "cited_by_count:desc");
  else url.searchParams.set("sort", "relevance_score:desc");
  url.searchParams.set("select", "id,doi,title,publication_year,publication_date,authorships,primary_location,best_oa_location,open_access,cited_by_count,abstract_inverted_index,type,topics,primary_topic,locations,is_retracted");
  if (env.OPENALEX_API_KEY) url.searchParams.set("api_key", env.OPENALEX_API_KEY);
  const payload = await fetchJson(url);
  const works = asArray(payload.results).map((raw) => {
    const item = asRecord(raw);
    const primary = asRecord(item.primary_location);
    const source = asRecord(primary.source);
    const best = asRecord(item.best_oa_location);
    const oa = asRecord(item.open_access);
    const summary = asRecord(source.summary_stats);
    const authors = asArray(item.authorships).slice(0, 8).map((a) => cleanString(asRecord(asRecord(a).author).display_name)).filter(Boolean).join(", ");
    const topics = asArray(item.topics).slice(0, 4).map((t) => cleanString(asRecord(t).display_name)).filter(Boolean);
    const isOa = oa.is_oa === true || Boolean(cleanString(best.pdf_url));
    const doi = normalizeDoi(item.doi);
    return baseWork("openalex", {
      sourceId: cleanString(item.id),
      title: cleanString(item.title),
      authors,
      year: cleanYear(item.publication_year),
      publicationDate: cleanString(item.publication_date),
      journal: cleanString(source.display_name),
      publisher: cleanString(source.host_organization_name),
      citationCounts: { openalex: Number(item.cited_by_count) || 0 },
      doi,
      pdfUrl: cleanString(best.pdf_url),
      landingUrl: cleanString(best.landing_page_url || primary.landing_page_url || item.doi),
      abstractText: abstractFromOpenAlex(item.abstract_inverted_index),
      publicationType: cleanString(item.type),
      accessStatus: isOa ? "open" : oa.is_oa === false ? "restricted" : "unknown",
      peerReviewStatus: item.type === "article" || item.type === "review" ? "likely_peer_reviewed" : "unknown",
      issn: asArray(source.issn).map((v) => cleanString(v)).filter(Boolean),
      fieldOfStudy: topics,
      journalMetrics: Object.keys(summary).length ? {
        openAlexHIndex: Number.isFinite(Number(summary.h_index)) ? Number(summary.h_index) : null,
        openAlexTwoYearMeanCitedness: Number.isFinite(Number(summary["2yr_mean_citedness"])) ? Number(summary["2yr_mean_citedness"]) : null,
        source: "OpenAlex",
        checkedAt: new Date().toISOString(),
      } : null,
      isRetracted: typeof item.is_retracted === "boolean" ? item.is_retracted : null,
    });
  });
  return { works, total: Number(asRecord(payload.meta).count) || works.length };
}

async function searchCrossref(input: SearchInput, env: Env): Promise<{ works: NormalizedWork[]; total: number }> {
  const url = new URL("https://api.crossref.org/works");
  url.searchParams.set("query.bibliographic", input.query);
  if (input.author) url.searchParams.set("query.author", input.author);
  if (input.journal) url.searchParams.set("query.container-title", input.journal);
  const from = input.yearFrom + "-01-01";
  const to = input.yearTo + "-12-31";
  url.searchParams.set("filter", "from-pub-date:" + from + ",until-pub-date:" + to);
  url.searchParams.set("rows", String(input.pageSize));
  url.searchParams.set("offset", String((input.page - 1) * input.pageSize));
  if (input.sort === "newest") { url.searchParams.set("sort", "published"); url.searchParams.set("order", "desc"); }
  else if (input.sort === "citations") { url.searchParams.set("sort", "is-referenced-by-count"); url.searchParams.set("order", "desc"); }
  else { url.searchParams.set("sort", "score"); url.searchParams.set("order", "desc"); }
  if (env.CROSSREF_MAILTO) url.searchParams.set("mailto", env.CROSSREF_MAILTO);
  const payload = await fetchJson(url);
  const message = asRecord(payload.message);
  const works = asArray(message.items).map((raw) => {
    const item = asRecord(raw);
    const authors = asArray(item.author).slice(0, 8).map((a) => [cleanString(asRecord(a).given), cleanString(asRecord(a).family)].filter(Boolean).join(" ")).filter(Boolean).join(", ");
    const dateParts = asArray(asRecord(item.published || item["published-print"] || item["published-online"])["date-parts"]);
    const date = asArray(dateParts[0]);
    const year = cleanYear(date[0]);
    const licenses = asArray(item.license).map((l) => cleanString(asRecord(l).URL)).join(" ");
    const hasCc = /creativecommons\.org/i.test(licenses);
    const abstractText = stripMarkup(item.abstract);
    const firstIssn = cleanString(asArray(item.ISSN)[0]);
    return baseWork("crossref", {
      sourceId: cleanString(item.DOI),
      title: cleanString(asArray(item.title)[0]),
      authors,
      year,
      publicationDate: date.length ? date.join("-") : "",
      journal: cleanString(asArray(item["container-title"])[0]),
      publisher: cleanString(item.publisher),
      citationCounts: { crossref: Number(item["is-referenced-by-count"]) || 0 },
      doi: cleanString(item.DOI),
      landingUrl: cleanString(item.URL),
      abstractText,
      publicationType: cleanString(item.type),
      accessStatus: hasCc ? "open" : "unknown",
      peerReviewStatus: "unknown",
      issn: firstIssn ? [firstIssn] : [],
      isRetracted: item["update-to"] && asArray(item["update-to"]).some((u) => /retraction/i.test(cleanString(asRecord(u).type))) ? true : null,
    });
  });
  return { works, total: Number(message.totalResults) || works.length };
}

function profileFields(profile: string): string[] {
  const map: Record<string, string[]> = {
    materials: ["Engineering", "Materials Science", "Chemistry"],
    engineering: ["Engineering"],
    computer_science: ["Computer Science"],
    medicine: ["Medicine", "Biology"],
    life_sciences: ["Biology", "Medicine"],
    physics: ["Physics", "Mathematics"],
    chemistry: ["Chemistry"],
    environment: ["Environmental Science", "Geology"],
    agriculture: ["Agricultural and Food Sciences", "Biology"],
    social_sciences: ["Sociology", "Economics", "Psychology"],
    humanities: ["History", "Art", "Philosophy", "Linguistics"],
  };
  return map[profile] ?? [];
}

async function searchSemanticScholar(input: SearchInput, env: Env): Promise<{ works: NormalizedWork[]; total: number }> {
  const url = new URL("https://api.semanticscholar.org/graph/v1/paper/search");
  url.searchParams.set("query", input.query);
  url.searchParams.set("limit", String(Math.min(100, input.pageSize)));
  url.searchParams.set("offset", String((input.page - 1) * input.pageSize));
  url.searchParams.set("year", input.yearFrom + "-" + input.yearTo);
  const fields = profileFields(input.profile);
  if (fields.length) url.searchParams.set("fieldsOfStudy", fields.slice(0, 3).join(","));
  url.searchParams.set("fields", "title,abstract,year,authors,venue,citationCount,externalIds,isOpenAccess,openAccessPdf,publicationTypes,journal,fieldsOfStudy,url");
  const headers: Record<string, string> = {};
  if (env.SEMANTIC_SCHOLAR_API_KEY) headers["x-api-key"] = env.SEMANTIC_SCHOLAR_API_KEY;
  const payload = await fetchJson(url, headers);
  const works = asArray(payload.data).map((raw) => {
    const item = asRecord(raw);
    const ids = asRecord(item.externalIds);
    const oaPdf = asRecord(item.openAccessPdf);
    const journal = asRecord(item.journal);
    const paperId = cleanString(item.paperId);
    const isOa = item.isOpenAccess === true || Boolean(cleanString(oaPdf.url));
    return baseWork("semantic_scholar", {
      sourceId: paperId,
      title: cleanString(item.title),
      authors: asArray(item.authors).slice(0, 8).map((a) => cleanString(asRecord(a).name)).filter(Boolean).join(", "),
      year: cleanYear(item.year),
      journal: cleanString(item.venue || journal.name),
      publisher: "",
      citationCounts: { semantic_scholar: Number(item.citationCount) || 0 },
      doi: cleanString(ids.DOI),
      pdfUrl: cleanString(oaPdf.url),
      landingUrl: cleanString(item.url) || (paperId ? "https://www.semanticscholar.org/paper/" + paperId : ""),
      abstractText: cleanString(item.abstract, 5000),
      publicationType: asArray(item.publicationTypes).map((x) => cleanString(x)).filter(Boolean).join(", ") || "unknown",
      accessStatus: isOa ? "open" : item.isOpenAccess === false ? "restricted" : "unknown",
      peerReviewStatus: "unknown",
      fieldOfStudy: asArray(item.fieldsOfStudy).map((v) => cleanString(asRecord(v).category || v)).filter(Boolean),
    });
  });
  return { works, total: Number(payload.total) || works.length };
}

async function searchEuropePmc(input: SearchInput): Promise<{ works: NormalizedWork[]; total: number }> {
  const url = new URL("https://www.ebi.ac.uk/europepmc/webservices/rest/search");
  const query = input.query + " AND FIRST_PDATE:[" + input.yearFrom + " TO " + input.yearTo + "]";
  url.searchParams.set("query", query);
  url.searchParams.set("format", "json");
  url.searchParams.set("resultType", "core");
  url.searchParams.set("pageSize", String(Math.min(100, input.pageSize)));
  url.searchParams.set("page", String(input.page));
  const payload = await fetchJson(url);
  const resultList = asRecord(payload.resultList);
  const works = asArray(resultList.result).map((raw) => {
    const item = asRecord(raw);
    const fullText = asArray(item.fullTextUrlList?.fullTextUrl);
    const pdfLink = fullText.find((x) => /pdf/i.test(cleanString(asRecord(x).documentStyle))) || fullText[0];
    const pdfUrl = cleanString(asRecord(pdfLink).url);
    const pmcid = cleanString(item.pmcid);
    const isOa = item.isOpenAccess === "Y" || Boolean(pmcid && pdfUrl);
    const doi = asArray(item.idList?.doi)[0] || cleanString(item.doi);
    const authorString = cleanString(item.authorString);
    return baseWork("europe_pmc", {
      sourceId: cleanString(item.id || item.pmid || pmcid),
      title: cleanString(item.title),
      authors: authorString,
      year: cleanYear(item.pubYear),
      publicationDate: cleanString(item.firstPublicationDate),
      journal: cleanString(item.journalTitle),
      citationCounts: { europe_pmc: Number(item.citedByCount) || 0 },
      doi: cleanString(doi),
      pdfUrl: isOa ? pdfUrl : "",
      landingUrl: pmcid ? "https://europepmc.org/articles/" + pmcid : cleanString(item.pmid ? "https://pubmed.ncbi.nlm.nih.gov/" + item.pmid + "/" : item.doi ? "https://doi.org/" + doi : ""),
      abstractText: cleanString(item.abstractText, 5000),
      publicationType: asArray(item.pubTypeList?.pubType).map((x) => cleanString(x)).filter(Boolean).join(", ") || "journal-article",
      accessStatus: isOa ? "open" : item.isOpenAccess === "N" ? "restricted" : "unknown",
      peerReviewStatus: "unknown",
      issn: asArray(item.journalInfo?.journal?.issn).map((x) => cleanString(x)).filter(Boolean),
      fieldOfStudy: asArray(item.meshHeadingList?.meshHeading).slice(0, 5).map((x) => cleanString(asRecord(x).descriptorName)).filter(Boolean),
    });
  });
  return { works, total: Number(resultList.total) || works.length };
}

async function searchPubmed(input: SearchInput, env: Env): Promise<{ works: NormalizedWork[]; total: number }> {
  const base = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/";
  const term = "(" + input.query + ") AND (" + input.yearFrom + "/01/01[Date - Publication] : " + input.yearTo + "/12/31[Date - Publication])";
  const searchUrl = new URL(base + "esearch.fcgi");
  searchUrl.searchParams.set("db", "pubmed");
  searchUrl.searchParams.set("term", term);
  searchUrl.searchParams.set("retmode", "json");
  searchUrl.searchParams.set("retmax", String(Math.min(100, input.pageSize)));
  searchUrl.searchParams.set("retstart", String((input.page - 1) * input.pageSize));
  if (env.NCBI_API_KEY) searchUrl.searchParams.set("api_key", env.NCBI_API_KEY);
  const searchPayload = await fetchJson(searchUrl);
  const search = asRecord(searchPayload.esearchresult);
  const ids = asArray(search.idlist).map((x) => cleanString(x)).filter(Boolean);
  const total = Number(search.count) || 0;
  if (!ids.length) return { works: [], total };
  const summaryUrl = new URL(base + "esummary.fcgi");
  summaryUrl.searchParams.set("db", "pubmed");
  summaryUrl.searchParams.set("id", ids.join(","));
  summaryUrl.searchParams.set("retmode", "json");
  if (env.NCBI_API_KEY) summaryUrl.searchParams.set("api_key", env.NCBI_API_KEY);
  const summaryPayload = await fetchJson(summaryUrl);
  const result = asRecord(summaryPayload.result);
  const works = ids.map((id) => {
    const item = asRecord(result[id]);
    const articleIds = asArray(item.articleids);
    const doi = cleanString(articleIds.find((a) => cleanString(asRecord(a).idtype) === "doi") && asRecord(articleIds.find((a) => cleanString(asRecord(a).idtype) === "doi")).value);
    const authors = asArray(item.authors).slice(0, 8).map((a) => cleanString(asRecord(a).name)).filter(Boolean).join(", ");
    return baseWork("pubmed", {
      sourceId: id,
      title: stripMarkup(item.title),
      authors,
      year: cleanYear(String(item.pubdate || "").match(/\b\d{4}\b/)?.[0]),
      publicationDate: cleanString(item.pubdate),
      journal: cleanString(item.fulljournalname || item.source),
      citationCounts: {},
      doi,
      landingUrl: "https://pubmed.ncbi.nlm.nih.gov/" + id + "/",
      abstractText: "",
      publicationType: asArray(item.pubtype).map((x) => cleanString(x)).filter(Boolean).join(", ") || "unknown",
      accessStatus: item.pmc === "free" ? "open" : "unknown",
      peerReviewStatus: "unknown",
    });
  }).filter((w) => w.title);
  return { works, total };
}

function decodeXml(value: string): string {
  return value.replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"').replace(/&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCodePoint(parseInt(n, 16)))
    .replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
}

function xmlTag(block: string, tag: string): string {
  const match = block.match(new RegExp("<" + tag + "(?:\\s[^>]*)?>([\\s\\S]*?)</" + tag + ">"));
  return match ? decodeXml(match[1]) : "";
}

async function searchArxiv(input: SearchInput): Promise<{ works: NormalizedWork[]; total: number }> {
  const url = new URL("https://export.arxiv.org/api/query");
  url.searchParams.set("search_query", "all:" + input.query);
  url.searchParams.set("start", String((input.page - 1) * input.pageSize));
  url.searchParams.set("max_results", String(Math.min(50, input.pageSize)));
  url.searchParams.set("sortBy", input.sort === "newest" ? "submittedDate" : "relevance");
  url.searchParams.set("sortOrder", "descending");
  const response = await fetch(url.toString(), {
    headers: { accept: "application/atom+xml", "user-agent": "ResearchMateAI/0.7 contact: github.com/Kaveh-83/ResearchMate-AI" },
    signal: AbortSignal.timeout(14000),
  });
  if (!response.ok) throw new Error("HTTP " + response.status + " from arXiv");
  const xml = await response.text();
  const totalMatch = xml.match(/<opensearch:totalResults[^>]*>(\d+)<\/opensearch:totalResults>/);
  const entries = [...xml.matchAll(/<entry>([\s\S]*?)<\/entry>/g)].map((match) => match[1]);
  const works = entries.map((entry) => {
    const id = xmlTag(entry, "id");
    const arxivId = id.split("/abs/").pop()?.replace(/v\d+$/, "") || id;
    const title = xmlTag(entry, "title");
    const authors = [...entry.matchAll(/<author>\s*<name>([\s\S]*?)<\/name>\s*<\/author>/g)].slice(0, 8).map((m) => decodeXml(m[1])).join(", ");
    const published = xmlTag(entry, "published");
    const year = cleanYear(published.slice(0, 4));
    const summary = xmlTag(entry, "summary");
    const categories = [...entry.matchAll(/<category[^>]*term="([^"]+)"/g)].map((m) => m[1]).slice(0, 5);
    const pdf = [...entry.matchAll(/<link[^>]*title="pdf"[^>]*href="([^"]+)"/g)][0]?.[1] || "";
    const journalRef = xmlTag(entry, "arxiv:journal_ref");
    return baseWork("arxiv", {
      sourceId: arxivId,
      title,
      authors,
      year,
      publicationDate: published,
      journal: journalRef,
      citationCounts: {},
      doi: "",
      pdfUrl: pdf,
      landingUrl: id,
      abstractText: summary,
      publicationType: "preprint",
      accessStatus: "open",
      peerReviewStatus: journalRef ? "journal_reference_present_not_verified" : "preprint",
      fieldOfStudy: categories,
    });
  }).filter((w) => w.title);
  return { works, total: Number(totalMatch?.[1]) || works.length };
}

const PROFILE_PROVIDERS: Record<string, ProviderId[]> = {
  all: ["openalex", "crossref", "semantic_scholar", "europe_pmc", "pubmed", "arxiv"],
  materials: ["openalex", "crossref", "semantic_scholar", "arxiv"],
  engineering: ["openalex", "crossref", "semantic_scholar", "arxiv"],
  computer_science: ["openalex", "crossref", "semantic_scholar", "arxiv"],
  medicine: ["openalex", "crossref", "semantic_scholar", "europe_pmc", "pubmed"],
  life_sciences: ["openalex", "crossref", "semantic_scholar", "europe_pmc", "pubmed"],
  physics: ["openalex", "crossref", "semantic_scholar", "arxiv"],
  chemistry: ["openalex", "crossref", "semantic_scholar"],
  environment: ["openalex", "crossref", "semantic_scholar", "arxiv"],
  agriculture: ["openalex", "crossref", "semantic_scholar", "europe_pmc"],
  social_sciences: ["openalex", "crossref", "semantic_scholar"],
  humanities: ["openalex", "crossref", "semantic_scholar"],
};

function parseInput(raw: unknown): SearchInput {
  const body = asRecord(raw);
  const query = cleanString(body.query, 300);
  const now = new Date().getUTCFullYear();
  const yearFrom = Number(body.yearFrom ?? body.year_from ?? Math.max(1800, now - 5));
  const yearTo = Number(body.yearTo ?? body.year_to ?? now);
  const requested = asArray(body.providers).map((v) => cleanString(v)).filter((v): v is ProviderId =>
    ["openalex", "crossref", "semantic_scholar", "europe_pmc", "pubmed", "arxiv"].includes(v));
  const profile = cleanString(body.profile, 80) || "all";
  const providers = requested.length ? [...new Set(requested)] : (PROFILE_PROVIDERS[profile] ?? PROFILE_PROVIDERS.all);
  return {
    query,
    yearFrom: Number.isInteger(yearFrom) && yearFrom >= 1500 && yearFrom <= now + 1 ? yearFrom : Math.max(1800, now - 5),
    yearTo: Number.isInteger(yearTo) && yearTo >= 1500 && yearTo <= now + 1 ? yearTo : now,
    profile,
    providers,
    author: cleanString(body.author, 180),
    journal: cleanString(body.journal, 180),
    publisher: cleanString(body.publisher, 180),
    documentType: cleanString(body.documentType ?? body.document_type, 100).toLowerCase(),
    accessMode: cleanString(body.accessMode ?? body.access_mode, 40).toLowerCase() || "all",
    minCitations: body.minCitations === null || body.minCitations === undefined || body.minCitations === "" ? null : Math.max(0, Math.floor(Number(body.minCitations) || 0)),
    sort: ["relevance", "newest", "citations"].includes(cleanString(body.sort)) ? cleanString(body.sort) : "relevance",
    page: Math.max(1, Math.min(100, Math.floor(Number(body.page) || 1))),
    pageSize: Math.max(5, Math.min(50, Math.floor(Number(body.pageSize ?? body.page_size) || 20))),
  };
}

function mergeWorks(records: NormalizedWork[]): NormalizedWork[] {
  const merged = new Map<string, NormalizedWork>();
  for (const record of records) {
    const titleKey = record.title.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, " ").trim();
    const key = record.doi ? "doi:" + record.doi : titleKey + "|" + (record.year ?? "");
    const old = merged.get(key);
    if (!old) {
      merged.set(key, record);
      continue;
    }
    const citationCounts = { ...old.citationCounts, ...record.citationCounts };
    const accessStatus: AccessStatus = old.accessStatus === "open" || record.accessStatus === "open"
      ? "open"
      : old.accessStatus === "restricted" || record.accessStatus === "restricted" ? "restricted" : "unknown";
    merged.set(key, {
      ...old,
      id: old.doi ? "doi:" + old.doi : old.id,
      title: old.title.length >= record.title.length ? old.title : record.title,
      authors: old.authors.length >= record.authors.length ? old.authors : record.authors,
      year: old.year ?? record.year,
      publicationDate: old.publicationDate || record.publicationDate,
      journal: old.journal || record.journal,
      publisher: old.publisher || record.publisher,
      citationCounts,
      doi: old.doi || record.doi,
      pdfUrl: old.pdfUrl || record.pdfUrl,
      landingUrl: old.landingUrl || record.landingUrl,
      abstractText: old.abstractText.length >= record.abstractText.length ? old.abstractText : record.abstractText,
      publicationType: old.publicationType !== "unknown" ? old.publicationType : record.publicationType,
      sourceIds: [...new Set([...old.sourceIds, ...record.sourceIds])],
      sources: [...new Set([...old.sources, ...record.sources])],
      accessStatus,
      accessLabel: accessStatus === "open" ? "Open full text" : accessStatus === "restricted" ? "No OA copy indicated" : "Access unknown",
      issn: [...new Set([...old.issn, ...record.issn])],
      fieldOfStudy: [...new Set([...old.fieldOfStudy, ...record.fieldOfStudy])],
      journalMetrics: old.journalMetrics || record.journalMetrics,
      isRetracted: old.isRetracted === true || record.isRetracted === true ? true : old.isRetracted ?? record.isRetracted,
    });
  }
  return [...merged.values()];
}

function matchesFilters(work: NormalizedWork, input: SearchInput): boolean {
  const hay = (work.authors + " " + work.journal + " " + work.publisher + " " + work.title).toLowerCase();
  if (input.author && !work.authors.toLowerCase().includes(input.author.toLowerCase())) return false;
  if (input.journal && !work.journal.toLowerCase().includes(input.journal.toLowerCase())) return false;
  if (input.publisher && !work.publisher.toLowerCase().includes(input.publisher.toLowerCase())) return false;
  if (input.documentType && !work.publicationType.toLowerCase().includes(input.documentType)) return false;
  const citations = Math.max(0, ...Object.values(work.citationCounts));
  if (input.minCitations !== null && citations < input.minCitations) return false;
  if (input.accessMode === "open" && work.accessStatus !== "open") return false;
  if (input.accessMode === "restricted" && work.accessStatus === "open") return false;
  return Boolean(hay) || Boolean(work.doi);
}

async function runProvider(provider: ProviderId, input: SearchInput, env: Env): Promise<{ works: NormalizedWork[]; status: ProviderStatus }> {
  const start = Date.now();
  try {
    let result: { works: NormalizedWork[]; total: number };
    switch (provider) {
      case "openalex": result = await searchOpenAlex(input, env); break;
      case "crossref": result = await searchCrossref(input, env); break;
      case "semantic_scholar": result = await searchSemanticScholar(input, env); break;
      case "europe_pmc": result = await searchEuropePmc(input); break;
      case "pubmed": result = await searchPubmed(input, env); break;
      case "arxiv": result = await searchArxiv(input); break;
    }
    return {
      works: result.works,
      status: { provider, label: PROVIDER_LABELS[provider], status: result.works.length ? "success" : "empty", resultCount: result.total, message: "", elapsedMs: Date.now() - start },
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown provider error";
    return {
      works: [],
      status: { provider, label: PROVIDER_LABELS[provider], status: providerErrorStatus(error), resultCount: 0, message: message.slice(0, 220), elapsedMs: Date.now() - start },
    };
  }
}

async function searchV2(request: Request, env: Env): Promise<Response> {
  const clientIp = request.headers.get("CF-Connecting-IP") ?? "unknown";
  const limit = await env.SEARCH_RATE_LIMITER.limit({ key: clientIp + ":academic-search-v2" });
  if (!limit.success) return errorResponse(request, env, "Search request limit reached. Please wait one minute and try again.", 429);

  let body: unknown;
  try {
    const textBody = await request.text();
    if (textBody.length > 24000) return errorResponse(request, env, "Search request is too large.", 413);
    body = JSON.parse(textBody);
  } catch {
    return errorResponse(request, env, "Request body must be valid JSON.", 400);
  }

  const input = parseInput(body);
  if (input.query.length < 2 || input.query.length > 300) return errorResponse(request, env, "Query must contain 2–300 characters.", 400);
  if (input.yearFrom > input.yearTo) return errorResponse(request, env, "Start year must not exceed end year.", 400);

  const providerResults = await Promise.all(input.providers.map((provider) => runProvider(provider, input, env)));
  const statuses = providerResults.map((r) => r.status);
  const all = mergeWorks(providerResults.flatMap((r) => r.works)).filter((w) => matchesFilters(w, input));

  if (input.sort === "newest") all.sort((a, b) => (b.year ?? 0) - (a.year ?? 0));
  else if (input.sort === "citations") all.sort((a, b) => Math.max(0, ...Object.values(b.citationCounts)) - Math.max(0, ...Object.values(a.citationCounts)));
  else all.sort((a, b) => (b.accessStatus === "open" ? 1 : 0) - (a.accessStatus === "open" ? 1 : 0));

  const openCount = all.filter((w) => w.accessStatus === "open").length;
  const restrictedCount = all.filter((w) => w.accessStatus !== "open").length;
  const providerTotal = statuses.reduce((sum, s) => sum + s.resultCount, 0);

  return json({
    apiVersion: "v2",
    query: input.query,
    profile: input.profile,
    generatedAt: new Date().toISOString(),
    page: input.page,
    pageSize: input.pageSize,
    totalUnique: all.length,
    totalOpenFullText: openCount,
    totalNoVerifiedOpenCopy: restrictedCount,
    nextPage: input.page < 100 && statuses.some((s) => s.status === "success" && s.resultCount >= input.page * input.pageSize) ? input.page + 1 : null,
    providerTotal,
    providerStatus: statuses,
    appliedFilters: {
      yearFrom: input.yearFrom, yearTo: input.yearTo, profile: input.profile,
      providers: input.providers, author: input.author, journal: input.journal,
      publisher: input.publisher, documentType: input.documentType,
      accessMode: input.accessMode, minCitations: input.minCitations, sort: input.sort,
    },
    works: all,
  }, 200, corsHeaders(request, env));
}

async function searchLegacy(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const input = parseInput({
    query: url.searchParams.get("q"),
    year_from: url.searchParams.get("year_from"),
    year_to: url.searchParams.get("year_to"),
    pageSize: url.searchParams.get("limit") || 25,
    providers: ["openalex"],
    profile: "all",
    page: url.searchParams.get("page") || 1,
  });
  input.query = cleanString(url.searchParams.get("q"), 300);
  if (input.query.length < 2) return errorResponse(request, env, "q must contain at least 2 characters.", 400);
  const result = await searchOpenAlex(input, env);
  return json({ provider: "openalex", query: input.query, meta: { count: result.total }, next_cursor: null, results: result.works }, 200, corsHeaders(request, env));
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const cors = corsHeaders(request, env);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/v1/health") {
      return json({ ok: true, service: "researchmate-api", api_version: "v2" }, 200, cors);
    }
    if (request.method === "GET" && url.pathname === "/v1/literature/search") return searchLegacy(request, env);
    if (request.method === "POST" && url.pathname === "/v2/academic-search") return searchV2(request, env);
    return json({ error: { message: "Route not found.", status: 404 } }, 404, cors);
  },
};
