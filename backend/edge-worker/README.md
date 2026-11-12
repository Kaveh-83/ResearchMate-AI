# ResearchMate Edge API

A lightweight Cloudflare Worker API for the native ResearchMate AI app.

## Deployment status

- Worker: `researchmate-ai`
- Public base URL: `https://researchmate-ai.kavehjafari710.workers.dev`
- Deployment: managed by the GitHub Actions workflow `.github/workflows/deploy-worker.yml`
- Health endpoint: verified with HTTP 200.
- Literature search: currently returns HTTP 429 because the production Worker does not yet expose the required `OPENALEX_API_KEY` secret. Add that secret to the `researchmate-ai` Worker before enabling live search.

The Streamlit app is maintained separately. Android builds receive the API base URL through the Gradle property `API_BASE_URL`; ordinary app users do not need a Cloudflare account or a server URL.

## Routes

- `GET /v1/health`
- `GET /v1/literature/search?q=...&year_from=...&year_to=...&limit=...`

The search endpoint queries OpenAlex and filters for open-access journal articles and reviews. It returns bibliographic metadata and open-access PDF links where available. It does not claim every result has a downloadable full text.

## Required Cloudflare secret

The Worker reads the OpenAlex key from a server-side secret named exactly:

`OPENALEX_API_KEY`

To add or verify it:

1. Open the [Cloudflare dashboard](https://dash.cloudflare.com/).
2. Go to **Workers & Pages** and select the Worker named **researchmate-ai**.
3. Open **Settings** → **Variables and Secrets**.
4. Add a **Secret** named `OPENALEX_API_KEY` and paste the OpenAlex API key as its value.
5. Save/deploy the Worker, then test the search route below.

Make sure the secret belongs to this exact Worker and the production environment. A secret stored on a different Worker or under a different name is not available to this deployment.

Do not put the key in the APK, source code, README, or any committed file. The Cloudflare deployment credentials used by GitHub Actions are separate from this OpenAlex provider secret.

## Security and usage controls

- Search requests are throttled to approximately 15 requests per minute per source IP, per Cloudflare location.
- Cloudflare's Worker Rate Limiting binding is approximate and location-local, not a strict global quota or authentication system.
- Provider secrets are read only from Cloudflare Worker secrets; no API keys are embedded in the APK or repository.
- Query length is bounded, result count is capped at 50, and upstream requests time out after 20 seconds.
- CORS is closed by default for browser origins. Native Android requests without an Origin header are supported.
- Do not log authorization headers, personal data, or uploaded paper contents.

The IP throttle is a first abuse barrier, not a complete anti-abuse system. Before scaling beyond a small pilot, add anonymous installation credentials with server-side issuance/rotation and stronger per-install quotas, monitoring, and a documented privacy policy. IP-based throttling can affect multiple users sharing a mobile carrier NAT.

## Local development

Requires Node.js 22+ and npm:

```bash
npm install
npm run typecheck
npm run dev
```

## Deployment and verification

A push to `feature/android-native-platform` that changes `backend/edge-worker/**` or the deployment workflow triggers GitHub Actions. The workflow uses the repository secrets:

- `CLOUDFLARE_API_TOKEN`
- `CLOUDFLARE_ACCOUNT_ID`

Check the deployed service:

- [Health](https://researchmate-ai.kavehjafari710.workers.dev/v1/health)
- [Sample literature search](https://researchmate-ai.kavehjafari710.workers.dev/v1/literature/search?q=high%20entropy%20alloy&year_from=2021&year_to=2026&limit=5)

After adding `OPENALEX_API_KEY`, test both endpoints from at least one Iranian mobile network and one fixed-line network. A successful deployment does not prove regional reachability.

## Free-tier boundary

Cloudflare Workers Free has platform quotas that may change. They are not a promise of unlimited use. This Worker proxies lightweight scholarly metadata; PDF parsing, OCR, large research jobs, and AI synthesis are outside its current scope.
