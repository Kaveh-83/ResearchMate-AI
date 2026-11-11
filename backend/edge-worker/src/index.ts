export interface Env {
  APP_ALLOWED_ORIGINS?: string;
}

const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
};

function json(data: unknown, status = 200, extra: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...JSON_HEADERS, ...extra },
  });
}

function corsHeaders(request: Request, env: Env): Record<string, string> {
  const origin = request.headers.get("Origin") ?? "";
  const allowed = (env.APP_ALLOWED_ORIGINS ?? "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  // Native Android clients generally send no Origin header. For browser-based
  // diagnostics, only explicitly configured origins are reflected.
  if (!origin) return {};
  if (allowed.includes("*") || allowed.includes(origin)) {
    return {
      "access-control-allow-origin": origin,
      "access-control-allow-methods": "GET, OPTIONS",
      "access-control-allow-headers": "Content-Type, Authorization, X-OpenAlex-Key",
      "vary": "Origin",
    };
  }
  return {};
}

function errorResponse(request: Request, env: Env, message: string, status: number): Response {
  return json({ error: { message, status } }, status, corsHeaders(request, env));
}

function validYear(value: string | null): number | null {
  if (!value || !/^\d{4}$/.test(value)) return null;
  const year = Number(value);
  return year >= 1800 && year <= new Date().getUTCFullYear() + 1 ? year : null;
}

async function searchLiterature(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const query = (url.searchParams.get("q") ?? "").trim();
  if (query.length < 2 || query.length > 300) {
    return errorResponse(request, env, "q must contain 2–300 characters.", 400);
  }

  const currentYear = new Date().getUTCFullYear();
  const yearFrom = validYear(url.searchParams.get("year_from")) ?? Math.max(1800, currentYear - 5);
  const yearTo = validYear(url.searchParams.get("year_to")) ?? currentYear;
  if (yearFrom > yearTo) {
    return errorResponse(request, env, "year_from must be less than or equal to year_to.", 400);
  }

  const requestedLimit = Number(url.searchParams.get("limit") ?? "20");
  const limit = Number.isFinite(requestedLimit)
    ? Math.min(50, Math.max(1, Math.floor(requestedLimit)))
    : 20;

  const upstream = new URL("https://api.openalex.org/works");
  upstream.searchParams.set("search", query);
  upstream.searchParams.set(
    "filter",
    `is_oa:true,type:article|review,from_publication_date:${yearFrom}-01-01,to_publication_date:${yearTo}-12-31`,
  );
  upstream.searchParams.set("per-page", String(limit));
  upstream.searchParams.set("sort", "relevance_score:desc");
  upstream.searchParams.set(
    "select",
    "id,doi,title,publication_year,publication_date,authorships,primary_location,best_oa_location,open_access,cited_by_count,abstract_inverted_index,type,has_content,content_urls",
  );

  const cursor = url.searchParams.get("cursor");
  if (cursor && cursor.length <= 2048) upstream.searchParams.set("cursor", cursor);

  // User-owned OpenAlex key is passed in a header, never in the app URL.
  const key = request.headers.get("X-OpenAlex-Key");
  const headers: Record<string, string> = {
    "accept": "application/json",
    "user-agent": "ResearchMateAI/1.0 (scholarly literature discovery)",
  };
  if (key && key.length <= 512) headers.authorization = `Bearer ${key}`;

  let upstreamResponse: Response;
  try {
    upstreamResponse = await fetch(upstream.toString(), {
      method: "GET",
      headers,
      signal: AbortSignal.timeout(20000),
    });
  } catch {
    return errorResponse(request, env, "OpenAlex is temporarily unreachable. Try again later.", 502);
  }

  if (!upstreamResponse.ok) {
    if (upstreamResponse.status === 429) {
      return errorResponse(request, env, "OpenAlex quota or rate limit reached. Wait or check your free API key quota.", 429);
    }
    if (upstreamResponse.status === 401 || upstreamResponse.status === 403) {
      return errorResponse(request, env, "OpenAlex rejected the API key. Check the key and its activation status.", 502);
    }
    return errorResponse(request, env, `OpenAlex returned HTTP ${upstreamResponse.status}.`, 502);
  }

  let payload: any;
  try {
    payload = await upstreamResponse.json();
  } catch {
    return errorResponse(request, env, "OpenAlex returned invalid JSON.", 502);
  }

  const results = Array.isArray(payload.results) ? payload.results : [];
  return json(
    {
      provider: "openalex",
      query,
      meta: payload.meta ?? {},
      next_cursor: payload.meta?.next_cursor ?? null,
      results,
    },
    200,
    corsHeaders(request, env),
  );
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const cors = corsHeaders(request, env);
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: cors });
    }

    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/v1/health") {
      return json({ ok: true, service: "researchmate-api", api_version: "v1" }, 200, cors);
    }
    if (request.method === "GET" && url.pathname === "/v1/literature/search") {
      return searchLiterature(request, env);
    }
    return json({ error: { message: "Route not found.", status: 404 } }, 404, cors);
  },
};
