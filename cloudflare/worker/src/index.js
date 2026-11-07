const JSON_HEADERS = { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" };

function reply(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
}

function authorized(request, env) {
  const expected = env.APP_API_TOKEN || "";
  const supplied = request.headers.get("authorization") || "";
  return Boolean(expected && supplied === `Bearer ${expected}`);
}

async function readJson(request, maxBytes = 65536) {
  const raw = await request.text();
  if (new TextEncoder().encode(raw).length > maxBytes) throw new Error("Request body too large");
  return JSON.parse(raw);
}

export default {
  async fetch(request, env) {
    if (request.method === "GET" && new URL(request.url).pathname === "/health") {
      return reply({ service: "ResearchMate API", status: "ok", storage: Boolean(env.DB) });
    }
    if (!authorized(request, env)) return reply({ error: "Unauthorized" }, 401);
    if (!env.DB) return reply({ error: "D1 binding DB is not configured" }, 503);

    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === "POST" && path === "/api/jobs") {
      let body;
      try { body = await readJson(request); }
      catch (error) { return reply({ error: error.message || "Invalid JSON" }, 400); }
      const query = String(body.query || "").trim();
      const yearFrom = Number(body.year_from);
      const yearTo = Number(body.year_to);
      const maxPapers = Math.min(Math.max(Number(body.max_papers || 8), 1), 15);
      if (!query || query.length > 1200) return reply({ error: "query is required (max 1200 chars)" }, 400);
      if (!Number.isInteger(yearFrom) || !Number.isInteger(yearTo) || yearFrom < 1900 || yearTo < yearFrom) {
        return reply({ error: "Invalid publication year range" }, 400);
      }
      const id = crypto.randomUUID();
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO research_jobs (id, query, year_from, year_to, max_papers, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'queued', ?, ?)"
      ).bind(id, query, yearFrom, yearTo, maxPapers, now, now).run();
      return reply({ id, status: "queued", message: "Job record created; worker execution is not yet connected." }, 202);
    }

    const match = path.match(/^\/api\/jobs\/([0-9a-f-]+)$/i);
    if (request.method === "GET" && match) {
      const row = await env.DB.prepare(
        "SELECT id, query, year_from, year_to, max_papers, status, result_json, error_message, created_at, updated_at FROM research_jobs WHERE id = ?"
      ).bind(match[1]).first();
      if (!row) return reply({ error: "Job not found" }, 404);
      let result = null;
      try { result = row.result_json ? JSON.parse(row.result_json) : null; } catch (_) {}
      return reply({ ...row, result });
    }

    const complete = path.match(/^\/api\/jobs\/([0-9a-f-]+)\/complete$/i);
    if (request.method === "POST" && complete) {
      let body;
      try { body = await readJson(request, 2_000_000); }
      catch (error) { return reply({ error: error.message || "Invalid JSON" }, 400); }
      const status = body.status;
      if (!["completed", "failed"].includes(status)) return reply({ error: "status must be completed or failed" }, 400);
      const resultJson = body.result == null ? null : JSON.stringify(body.result);
      if (resultJson && resultJson.length > 1_800_000) return reply({ error: "result payload too large; store large files in object storage" }, 413);
      const now = new Date().toISOString();
      const update = await env.DB.prepare(
        "UPDATE research_jobs SET status = ?, result_json = ?, error_message = ?, updated_at = ? WHERE id = ?"
      ).bind(status, resultJson, String(body.error || "").slice(0, 1200), now, complete[1]).run();
      if (!update.meta.changes) return reply({ error: "Job not found" }, 404);
      return reply({ id: complete[1], status });
    }

    return reply({ error: "Not found" }, 404);
  },
};
