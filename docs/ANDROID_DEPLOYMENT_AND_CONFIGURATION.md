# Android app configuration and deployment boundary

## User-facing behavior

Ordinary users install the APK and use the shared ResearchMate service. They do not create a Cloudflare account, paste a Worker URL, or provide an OpenAlex key.

The API endpoint is compiled into each app build. It is intentionally not editable in the ordinary search screen.

## Build-time configuration

The endpoint is supplied by the build operator:

```bash
gradle assembleDebug --no-daemon -PAPI_BASE_URL=https://researchmate-ai.kavehjafari710.workers.dev
```

If `API_BASE_URL` is omitted, the app remains installable and local UI remains usable, but online literature search displays a not-configured message.

Do not put provider secrets in `API_BASE_URL`. This value is a public service address, not a credential. OpenAlex/provider credentials belong on the server side.

## Current deployment verification

- Worker name: `researchmate-ai`
- Base URL: `https://researchmate-ai.kavehjafari710.workers.dev`
- `GET /v1/health`: verified HTTP 200.
- `GET /v1/literature/search`: currently HTTP 429 because the deployed Worker reports that `OPENALEX_API_KEY` is not configured in its production environment.

To fix the current search outage, open Cloudflare → Workers & Pages → `researchmate-ai` → Settings → Variables and Secrets. Add a Secret named exactly `OPENALEX_API_KEY`, save it, and redeploy the Worker. Confirm it is attached to this Worker and the production environment, not a Pages project, a different Worker, or a local `wrangler.toml` variable.

The GitHub repository secrets `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` only authorize GitHub Actions to deploy the Worker. They do not configure the OpenAlex provider key.

## Hosting independence

The Android client calls the stable ResearchMate API contract, not Cloudflare-specific APIs. The host can be replaced without changing screens or research logic, provided the replacement implements the same routes and response schema.

## Privacy defaults

- Imported PDFs and local project data remain on-device by default.
- Search terms are sent to the ResearchMate API when the user starts an online search.
- Do not upload full paper text or personal documents without a separate explicit consent step.
- Never log API keys, authorization headers, or uploaded document contents.

## Current implementation boundary

This is an early native client. A successful debug build and a healthy Worker do not imply that literature search, full-text analysis, AI synthesis, research-gap detection, or report generation are all available.
