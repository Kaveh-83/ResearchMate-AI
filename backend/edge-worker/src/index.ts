export interface RateLimitBinding {
  limit(options: { key: string }): Promise<{ success: boolean }>;
}

export interface Env {
  APP_ALLOWED_ORIGINS?: string;
  OPENALEX_API_KEY?: string;
  SEARCH_RATE_LIMITER: RateLimitBinding;
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
  if (!origin) return {};
  if (allowed.includes("*") || allowed.includes(origin)) {
    return {
      "access-control-allow-origin": origin,
      "access-control-allow-methods": "GET, OPTIONS",
      "access-control-allow-headers": "Content-Type, Authorization",
      "vary": "Origin",
    };
  }
  return {};
}

function errorResponse(request: Request, env: Env, message: string, status: number, extra: Record<string, string> = {}): Response {
  const headers = { ...corsHeaders(request, env), ...extra };
  if (status === 429 && !headers["retry-after"]) headers["retry-after"] = "60";
  return json({ error: { message, status } }, status, headers);
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

  const clientIp = request.headers.get("CF-Connecting-IP") ?? "unknown";
  const { success } = await env.SEARCH_RATE_LIMITER.limit({
    key: `${clientIp}:literature-search`,
  });
  if (!success) {
    return errorResponse(request, env, "Search limit reached. Please wait one minute and try again.", 429);
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
  if (env.OPENALEX_API_KEY) {
    upstream.searchParams.set("api_key", env.OPENALEX_API_KEY);
  }

  const cursor = url.searchParams.get("cursor");
  if (cursor && cursor.length <= 2048) upstream.searchParams.set("cursor", cursor);

  let upstreamResponse: Response;
  try {
    upstreamResponse = await fetch(upstream.toString(), {
      method: "GET",
      headers: {
        "accept": "application/json",
        "user-agent": "ResearchMateAI/1.0 (scholarly literature discovery)",
      },
      signal: AbortSignal.timeout(20000),
    });
  } catch {
    return errorResponse(request, env, "OpenAlex is temporarily unreachable. Try again later.", 502);
  }

  if (!upstreamResponse.ok) {
    if (upstreamResponse.status === 429) {
      const retryAfter = upstreamResponse.headers.get("Retry-After") ?? "60";
      return errorResponse(
        request,
        env,
        env.OPENALEX_API_KEY
          ? "OpenAlex rate limit reached for the configured API key. Please retry after the indicated delay."
          : "OpenAlex anonymous rate limit reached. Configure the free OPENALEX_API_KEY secret in Cloudflare.",
        429,
        { "retry-after": retryAfter },
      );
    }
    if (upstreamResponse.status === 403) {
      return errorResponse(request, env, "OpenAlex rejected the API key. Check that it is activated and valid.", 502);
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
