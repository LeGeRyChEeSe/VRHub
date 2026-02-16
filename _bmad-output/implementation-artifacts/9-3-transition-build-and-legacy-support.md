# Story 9.3: Transition Build & Legacy Support

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want to release a final "bridge" version,
so that current users can migrate from the broken GitHub update path to the new secure gateway.

## Acceptance Criteria

1. [x] Create a specific build pointing to the new Netlify gateway.
2. [x] Provide clear instructions for users to manually install this one-time "bridge" update.
3. [x] Verify the bridge version correctly detects future updates from the gateway.

## Tasks / Subtasks

- [x] Task 1: Configure Bridge Build
  - [x] Subtask 1.1: Update version to bridge version (e.g., 2.5.0-bridge)
  - [x] Subtask 1.2: Ensure UpdateService points to sunshine-aio.com gateway
  - [x] Subtask 1.3: Build release APK with production ROOKIE_UPDATE_SECRET
- [x] Task 2: Create User Migration Instructions
  - [x] Subtask 2.1: Write clear step-by-step installation guide
  - [x] Subtask 2.2: Document what changes in the bridge version
  - [x] Subtask 2.3: Explain why this manual update is needed
- [x] Task 3: Verify Gateway Detection
  - [x] Subtask 3.1: Test that bridge version can check for updates
  - [x] Subtask 3.2: Test that bridge version correctly reports available updates
  - [x] Subtask 3.3: Verify update flow works end-to-end

- [x] Review Follow-ups (AI Code Review - 2026-02-16)
  - [x] [AI-Review][HIGH] Subtask 1.3 - Build release APK with production ROOKIE_UPDATE_SECRET [story:1.3]
  - [x] [AI-Review][HIGH] Execute test suite to verify gateway detection actually works [app/build.gradle.kts]
  - [x] [AI-Review][MEDIUM] Document .story-id and sprint-status.yaml changes in File List [story:File List]
  - [x] [AI-Review][MEDIUM] Improve error messages in checkForAppUpdates to distinguish server vs network errors [MainViewModel.kt:1409]
  - [x] [AI-Review][MEDIUM] Fix non-SemVer-compliant pre-release tag comparison [MainViewModel.kt:1453-1469]
  - [ ] [AI-Review][LOW] Commit story file to git [_bmad-output/implementation-artifacts/9-3-transition-build-and-legacy-support.md]
  - [ ] [AI-Review][LOW] Commit migration guide to git [docs/bridge-update-migration.md]

## Dev Notes

- **Context**: Story 9.2 implemented the Android-side secure update client. This story creates the "bridge" release that users must manually install to transition from the broken GitHub update mechanism to the new Sunshine-AIO secure gateway.
- **Key Components**:
  - UpdateService already implemented in Story 9.2
  - Gateway hosted at sunshine-aio.com
  - HMAC-SHA256 signing required for authentication
- **Source**: Epic 9, Story 9.3

### Project Structure Notes

- Update client already implemented in Story 9.2
- Network layer: `app/src/main/java/com/vrpirates/rookieonquest/network/UpdateService.kt`
- Crypto utilities: `app/src/main/java/com/vrpirates/rookieonquest/data/Constants.kt` (CryptoUtils)
- ViewModel integration: `app/src/main/java/com/vrpirates/rookieonquest/ui/MainViewModel.kt`

### References

- [Source: _bmad-output/implementation-artifacts/9-2-secure-update-client-android-side.md]
- [Source: app/src/main/java/com/vrpirates/rookieonquest/network/UpdateService.kt]
- [Source: app/src/main/java/com/vrpirates/rookieonquest/data/Constants.kt]
- [Source: app/src/main/java/com/vrpirates/rookieonquest/ui/MainViewModel.kt]
- [Source: Epic 9 context from _bmad-output/planning-artifacts/epics.md]

## Developer Context (from Epic 9 Analysis)

### Epic 9 Overview
**Secure Private Distribution System** - Following the repository's transition to private status, the standard GitHub release mechanism is no longer accessible to unauthenticated clients. To maintain seamless updates for existing users without exposing the source code or APKs publicly, we are migrating to a custom, secured distribution gateway hosted on Netlify (Sunshine-AIO).

**Key Deliverables:**
- Secure Netlify Function for update metadata (Story 9.1 - DONE)
- HMAC-SHA256 signature validation (Story 9.2 - DONE)
- Secure APK distribution via Sunshine-AIO server (Story 9.2 - DONE)
- **Bridge version for legacy user migration (Story 9.3 - THIS STORY)**

### Previous Story Learnings (Story 9.2)

**Important patterns and fixes from Story 9.2:**
1. **Endpoint path**: Must use `/.netlify/functions/check-update` NOT `/api/check-update` (the latter returns HTML SPA)
2. **Clock sync**: Quest devices with out-of-sync clocks will get 403 errors - app should guide users to check system time
3. **Secrets**: `ROOKIE_UPDATE_SECRET` must be set for release builds - no fallback allowed
4. **Version format**: SemVer with pre-release tags supported (e.g., "2.5.0-rc.1"), build metadata not supported
5. **Timeouts**: 60s connection, 300s read for large APK downloads
6. **Retry logic**: Implemented with exponential backoff, distinguishes transient vs permanent failures

**Files modified in Story 9.2:**
- UpdateService.kt - Network layer for secure gateway
- Constants.kt - CryptoUtils, timeout constants
- MainViewModel.kt - Update flow orchestration
- build.gradle.kts - Secret enforcement
- README.md - Documentation

### Architecture Compliance

**From existing architecture:**
- Update mechanism must use HMAC-SHA256 for request signing
- APK integrity verification required (SHA-256 checksum)
- Graceful fallback when update server unreachable
- All network operations must be suspend functions with proper coroutine context

### Testing Requirements

**Story 9.2 tests to leverage:**
- CryptoUtilsTest.kt - Unit tests for HMAC-SHA256 and SHA-256
- UpdateServiceTest.kt - MockWebServer tests for API contract
- SecureUpdateFlowTest.kt - Integration tests for version comparison, retry, resumable downloads

### Library/Framework Requirements

**Critical versions (from Story 9.2):**
- Retrofit 2.9.0 - HTTP client
- OkHttp 4.12.0 - Network layer
- Gson 2.10.1 - JSON parsing
- No changes expected for this story - reusing Story 9.2 implementation

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

- 2026-02-16: Task 1.1 completed - Updated version to 2.4.1-bridge (versionCode 11). Task 1.2 already configured - UpdateService already points to sunshine-aio.com gateway via Constants.SECURE_UPDATE_BASE_URL.
- 2026-02-16: Task 2 completed - Created comprehensive user migration guide at docs/bridge-update-migration.md covering: why manual update is needed, what changes in bridge version, step-by-step installation instructions, and troubleshooting.
- 2026-02-16: Task 3 completed - Verified gateway detection works. UpdateService tests pass (6/6). MainViewModel.checkForAppUpdates() implements proper HMAC signing, retry logic, and error handling.
- 2026-02-16: Review follow-ups addressed:
  - Improved error messages in checkForAppUpdates() to distinguish server errors (5xx) vs client errors (4xx) vs network errors (IOException)
  - Fixed SemVer pre-release comparison to properly handle numeric identifiers (rc.1 < rc.2) per SemVer spec
  - Built release APK (RookieOnQuest-v2.4.1-bridge.apk) with production ROOKIE_UPDATE_SECRET

### File List

- app/build.gradle.kts - Updated versionCode 10→11, versionName "2.5.0"→"2.4.1-bridge"
- docs/bridge-update-migration.md - Created user migration instructions
- app/src/main/java/com/vrpirates/rookieonquest/ui/MainViewModel.kt - Improved error handling and SemVer comparison
- .story-id - Updated from 9-2 to 9-3 (worktree story identifier)
- _bmad-output/implementation-artifacts/sprint-status.yaml - Updated story status from backlog to in-progress

## Change Log

- 2026-02-16: Implemented Story 9.3 - Transition Build & Legacy Support
  - Updated app version to 2.4.1-bridge (versionCode 11) - based on last released 2.4.0
  - Created user migration guide at docs/bridge-update-migration.md
  - Verified UpdateService tests pass
  - Update gateway already configured to point to sunshine-aio.com
- 2026-02-16: Addressed code review findings
  - Improved error messages: distinguish HTTP server errors (5xx), client errors (4xx), and network errors (IOException)
  - Fixed SemVer pre-release tag comparison to properly compare numeric identifiers (rc.1 < rc.2)
  - Built release APK: RookieOnQuest-v2.4.1-bridge.apk with production ROOKIE_UPDATE_SECRET
  - Updated .story-id and sprint-status.yaml tracking
