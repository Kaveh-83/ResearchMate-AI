# ResearchMate Edge API (provisional)

This is a deliberately small Cloudflare Workers adapter. It is **not yet deployed** and Cloudflare is only a candidate host until access is tested from Iranian networks.

## Routes currently implemented

- `GET /v1/health`
- `GET /v1/literature/search?q=...&year_from=...&year_to=...&limit=...`

The literature route proxies OpenAlex and accepts an optional user-owned key in the `X-OpenAlex-Key` request header. The key is forwarded upstream as a Bearer token and is not written to storage. Do not add request-header logging.

## Local development

Requires Node.js 22+ and npm:

```bash
npm install
npm run typecheck
npm run dev
```

## Deploying a test instance

1. Create a free Cloudflare account and enable Workers.
2. From this directory, authenticate with Wrangler (`npx wrangler login`).
3. Run `npm run deploy`.
4. Copy the generated Worker URL into the Android app's API base URL setting once that setting is implemented.
5. Test the endpoint from at least two Iranian mobile/fixed-line networks before calling the service reachable in Iran.

No Cloudflare account credentials, API token, OpenAlex key, or user data belong in this repository.

## Important free-tier boundary

Workers Free is a lightweight request/response tier. The published limits include 100,000 requests/day and 10 ms CPU time per invocation. This adapter therefore does not parse PDFs, run scikit-learn, or execute large research jobs. Heavy extraction is intended to run on the Android device in the initial release.

Cloudflare's free tier and reachability from Iran are separate questions: a free account does not establish that the generated `workers.dev` URL will be reachable on every Iranian ISP. This must be measured, not assumed.
