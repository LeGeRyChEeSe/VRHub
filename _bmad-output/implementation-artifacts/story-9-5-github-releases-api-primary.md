# Story 9.5: GitHub Releases API Primary with Netlify Fallback

Status: backlog

## Story

As a user,
I want my app to check for updates using the GitHub Releases API as the primary mechanism,
so that updates continue to work even when the repository is transferred to a GitHub organization.

## Background

**Problem:** Story 9.2 implemented update checks exclusively via the Netlify Function (sunshine-aio.com). If the repository is transferred to a GitHub organization, the GitHub URL will change, but more critically — the update infrastructure should not depend on a separate external service for the primary update path.

**Current State (Story 9.2):**
- `GitHubService.kt` was deleted and replaced by `UpdateService.kt`
- Update check → `.netlify/functions/check-update` (Netlify only)
- APK download URL → served from Netlify-hosted APK
- No GitHub API involvement

**Required Change:**
- GitHub Releases API (`api.github.com/repos/{owner}/{repo}/releases/latest`) as **primary**
- Netlify Function as **fallback** (for when GitHub API fails)
- APK attached as GitHub Release Asset for direct download via GitHub
- Both mechanisms return compatible `UpdateInfo` structure

## Acceptance Criteria

1. [ ] App checks GitHub Releases API first for latest version
2. [ ] Falls back to Netlify Function if GitHub API fails or returns error
3. [ ] Both mechanisms return compatible `UpdateInfo` structure (version, changelog, downloadUrl, checksum, timestamp)
4. [ ] GitHub API check uses proper `User-Agent` header to avoid rate limiting (e.g., `VRHub/1.0`)
5. [ ] Fallback logic ONLY triggers on 404 (GitHub URL no longer exists) — NOT on rate limits (403) or temporary errors (5xx, IOException)
6. [ ] Rate limits and temporary errors trigger retry with backoff on GitHub API, not fallback
6. [ ] `UpdateInfo.downloadUrl` can point to either GitHub Assets or Netlify-hosted APK
7. [ ] APK is attached as a GitHub Release Asset for direct download
8. [ ] Owner/repo config stored in `PublicConfig` or `Constants` (not hardcoded)

## Technical Analysis

### Current Implementation Files (Story 9.2)

| File | Changes Needed |
|------|----------------|
| `UpdateService.kt` | Add `GitHubUpdateService` interface/class, keep Netlify as fallback |
| `MainViewModel.kt` | Update `checkForAppUpdates()` to try GitHub first, fallback to Netlify |
| `Constants.kt` | Add `GITHUB_RELEASES_API_URL` pattern, `FALLBACK_NETLIFY_URL` |
| `PublicConfig` | Add `githubOwner`, `githubRepo` fields |
| Build config | Ensure GitHub release asset upload configured in CI |

### GitHub Releases API Response Structure

```
GET /repos/{owner}/{repo}/releases/latest
Accept: application/vnd.github.v3+json
User-Agent: VRHub/1.0

Response:
{
  "tag_name": "v2.5.0",
  "name": "VRHub v2.5.0",
  "body": "## Changelog\n- Fix X\n- Add Y",
  "html_url": "https://github.com/{owner}/{repo}/releases/tag/v2.5.0",
  "assets": [
    {
      "name": "VRHub_2.5.0.apk",
      "browser_download_url": "https://github.com/.../VRHub_2.5.0.apk",
      "size": 57000000
    }
  ]
}
```

### UpdateInfo Compatibility

Both GitHub API and Netlify must return compatible `UpdateInfo`:

| Field | GitHub Source | Netlify Source |
|-------|--------------|---------------|
| `version` | `tag_name` (strip "v" prefix) | `version` |
| `changelog` | `body` (markdown) | `changelog` |
| `downloadUrl` | `assets[0].browser_download_url` | `downloadUrl` |
| `checksum` | Must compute/store SHA256 separately | `checksum` |
| `timestamp` | Use `published_at` or compute | `timestamp` |

### Error Handling Strategy

```
try GitHub API
  ├─ 200 → parse UpdateInfo, return
  ├─ 403 → rate limit → WAIT, retry with backoff (DO NOT fall back to Netlify)
  ├─ 404 → repo URL no longer exists → Netlify fallback (repo was transferred/deleted)
  └─ 5xx → server error → WAIT, retry with backoff (DO NOT fall back to Netlify)
      └─ IOException → network error → WAIT, retry with backoff (DO NOT fall back to Netlify)
          └─ Netlify fallback succeeds → use Netlify result
          └─ Netlify also fails → show error with failure details
```

**Key principle:** Netlify fallback is ONLY for permanent GitHub URL failures (404 - repo transferred/deleted). Rate limits and temporary errors trigger wait/retry on GitHub, not fallback.

### File Locations

- **GitHub API base:** `https://api.github.com`
- **Release endpoint:** `/repos/{owner}/{repo}/releases/latest`
- **APK stored as:** GitHub Release Asset (direct download URL)
- **Netlify fallback:** `.netlify/functions/check-update` (existing)

### Key Implementation Notes from Story 9.2

1. **HMAC-SHA256 signing** was added in Story 9.2 for Netlify — can be reused for Netlify fallback
2. **Checksum verification** is already implemented — needed for both paths
3. **Version comparison** (`isVersionNewer()`) already works — no changes needed
4. **Retry logic** (exponential backoff) already exists in `checkForAppUpdates()` — extend for dual-source
5. **Download and install** flow unchanged — works with any `downloadUrl`

## Tasks / Subtasks

- [ ] **Task 1: GitHub API Service**
  - [ ] Create `GitHubReleaseService` interface in `network/`
  - [ ] Implement `GitHubReleaseService` using Retrofit with GitHub API
  - [ ] Add proper `User-Agent` header to avoid rate limiting
  - [ ] Parse GitHub API response to `UpdateInfo` structure
  - [ ] Handle rate limit (403) with `X-RateLimit-Remaining` header check

- [ ] **Task 2: Dual-Source Update Checker**
  - [ ] Update `checkForAppUpdates()` to try GitHub first
  - [ ] Implement fallback logic: if GitHub fails, try Netlify
  - [ ] Collect error details from both sources for better UX
  - [ ] Ensure only one success is needed to proceed

- [ ] **Task 3: Configuration**
  - [ ] Add `githubOwner` and `githubRepo` to `PublicConfig` or server config
  - [ ] These come from user-configured server JSON (like `baseUri`)
  - [ ] Default values can be empty string (GitHub check skipped if not configured)

- [ ] **Task 4: CI/CD Update**
  - [ ] Configure GitHub Actions to upload APK as Release Asset
  - [ ] Ensure release workflow creates GitHub Release with asset
  - [ ] Verify `browser_download_url` is properly set

- [ ] **Task 5: Testing**
  - [ ] Add unit tests for `GitHubReleaseService`
  - [ ] Add integration test for dual-source fallback flow
  - [ ] Test 403 rate limit handling
  - [ ] Test both sources returning same version (no duplicate dialogs)

## Dev Notes

- **Why GitHub Primary:** GitHub Releases is always available if repo exists, no separate service dependency
- **Netlify Fallback Scope:** ONLY for permanent URL failures (404 - repo transferred/deleted). Rate limits (403) and temporary errors (5xx, IOException) get retry with backoff on GitHub, NOT fallback
- **User-Agent matters:** GitHub API rate limits without proper User-Agent (60 req/hour unauthenticated)
- **APK Checksum:** Must store SHA256 somewhere (release notes? separate file?) for GitHub path verification
- **Asset Naming:** APK should be named consistently: `VRHub_{version}.apk`

## References

- [GitHub REST API - Latest Release](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)
- [GitHub Upload Release Assets](https://docs.github.com/en/repositories/releasing-projects-on-github/attaching-files-to-releases)
- [Story 9.2 implementation](./story-9-2-secure-update-client-android-side.md) - current Netlify-only implementation
