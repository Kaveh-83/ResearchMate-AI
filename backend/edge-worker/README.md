# ResearchMate Edge API

A lightweight Cloudflare Worker API for ResearchMate AI. It is not deployed yet. Cloudflare remains the first deployment candidate, and reachability from Iranian networks must be tested before public release.

## Routes

- `GET /v1/health`
- `GET /v1/literature/search?q=...&year_from=...&year_to=...&limit=...`

The search endpoint queries OpenAlex and returns open-access journal articles and reviews. It returns bibliographic metadata and OA PDF links where available; it does not claim that every result has a full text.

## Security and usage controls

- Search requests are throttled to approximately 15 requests per minute per source IP, per Cloudflare location.
- Cloudflare's Worker Rate Limiting binding is approximate and location-local, not a strict global quota or authentication system.
- No API keys or provider secrets are embedded in the APK or repository.
- Query length is bounded, result count is capped at 50, and upstream requests time out after 20 seconds.
- CORS is closed by default for browser origins. Native Android requests without an Origin header are supported.
- Do not log authorization headers, personal data, or uploaded paper contents.

Because this is an anonymous public endpoint, the IP throttle is a first abuse barrier, not a complete anti-abuse solution. Before scaling beyond a small pilot, add anonymous installation credentials with server-side issuance/rotation and stronger per-install quotas, plus monitoring and a documented privacy policy. IP-based throttling can affect multiple users sharing a mobile carrier NAT.

## Local development

Requires Node.js 22+ and npm:

```bash
npm install
npm run typecheck
npm run dev
```

## Deploy through Cloudflare dashboard (no command line required)

1. Sign in to Cloudflare and open **Workers & Pages**.
2. Create a Worker and choose **Connect to Git**.
3. Connect the GitHub repository `Kaveh-83/ResearchMate-AI`.
4. Select branch `feature/android-native-platform`.
5. Set the project root directory to `backend/edge-worker`.
6. Set the deploy command to `npx wrangler deploy`; no build command is required.
7. Deploy, then copy the generated `workers.dev` URL.

The Worker config already includes the search rate-limit binding. Do not add secrets or API keys to GitHub files.

## After deployment

Check:

- `https://YOUR-WORKER.workers.dev/v1/health`
- `https://YOUR-WORKER.workers.dev/v1/literature/search?q=high%20entropy%20alloy&year_from=2021&year_to=2026&limit=5`

Then test the two endpoints from at least one Iranian mobile network and one fixed-line network. A successful deployment does not prove reachability from Iran.

## Free-tier boundary

Cloudflare's current Workers Free limits include 100,000 requests per day and 10 ms CPU time per invocation. These are platform limits, not a promise of unlimited use. This Worker only proxies lightweight scholarly metadata; PDF parsing, OCR, large research jobs, and AI synthesis are outside its scope.
