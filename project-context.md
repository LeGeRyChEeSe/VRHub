# Project Context: Rookie On Quest & Sunshine AIO

## Technical Stack
- **Rookie On Quest (Android)**: Kotlin, Jetpack Compose, Retrofit, Room, WorkManager.
- **Sunshine AIO (Web)**: React, Netlify (Functions, Hosting), ESM, Node.js.

## Security Standards
- **Update Signatures**: Requests to `/api/check-update` MUST be signed with HMAC-SHA256.
- **Timestamp Validation**: Request timestamps (`X-Rookie-Date`) MUST be within ±5 minutes of server time.
- **MIME Protection**: Always use `X-Content-Type-Options: nosniff`.
- **CORS**: Explicit allowlist including `rookie.vrpirates.org`, `sunshine-aio.netlify.app`, and local dev ports. Allow `.netlify.app` subdomains for previews.

## Code Style & Patterns
- **Netlify Functions**: Use ESM (`export const handler = ...`). Use `node:` prefixes for built-in modules.
- **Error Handling**: Standardized JSON error responses `{ error: 'message' }`.
- **Logging**: Use structured JSON logging with request IDs for production observability.
- **Path Resolution**: Use environment-aware path resolution in Netlify Functions (handle both local and serverless execution environments).

## Infrastructure & Deployment
- **APK Hosting**: Non-indexed directory `/public/updates/rookie/`.
- **CI/CD**: Build targets and version metadata are managed via GitHub Actions (Epic 8).
- **Environment Variables**:
  - `ROOKIE_UPDATE_SECRET`: Shared HMAC key.
  - `CACHE_TTL_SECONDS`: Configurable TTL for update metadata (default 60s).
