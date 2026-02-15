# Story 9.2: Secure Update Client (Android-side)

Status: done

## Story

As a user,
I want my app to check for updates via the new secure gateway,
so that I can stay up to date even though the GitHub repo is private.

## Acceptance Criteria

1. [x] Refactor `GitHubService.kt` to `UpdateService.kt` pointing to `sunshine-aio.com`.
2. [x] Implement request signing logic in the app to authenticate with the gateway (HMAC-SHA256).
3. [x] Implement SHA-256 checksum verification for downloaded APKs.
4. [x] Maintain graceful fallback if the update server is unreachable or if the clock is out of sync.

## Tasks / Subtasks

- [x] Task 1: Refactor Network Layer
  - [x] Rename GitHubService to UpdateService
  - [x] Update endpoint to `api/check-update`
  - [x] Add signature and date headers
- [x] Task 2: Implement Security Logic
  - [x] Add HMAC-SHA256 to CryptoUtils
  - [x] Move update secret to BuildConfig
  - [x] Add SHA-256 file verification to CryptoUtils
- [x] Task 3: Update UI and ViewModel
  - [x] Implement request signing in MainViewModel.checkForAppUpdates
  - [x] Implement checksum verification in MainViewModel.downloadAndInstallUpdate
  - [x] Update MainActivity and UpdateOverlay to use new UpdateInfo model
- [x] Task 4: Error Handling
  - [x] Detect and report 403 Forbidden (clock skew) errors
- [x] Task 5: Testing & Validation
  - [x] Add CryptoUtils unit tests
  - [x] Add UpdateService unit tests (MockWebServer)

### Review Follow-ups (AI)

- [x] [AI-Review][HIGH] Implement resumable downloads for update APKs with HTTP Range header support and local progress persistence [MainViewModel.kt:1428-1448]
- [x] [AI-Review][HIGH] Add actionable 403 clock skew recovery guidance with step-by-step instructions (Settings → Time → Toggle automatic time) [MainViewModel.kt:1392-1394]
- [x] [AI-Review][HIGH] Implement automatic retry logic (exponential backoff) when update server is unreachable or times out [MainViewModel.kt:1373-1403]
- [x] [AI-Review][HIGH] Add download timeout configuration for update APK downloads separate from VRP API timeout [MainViewModel.kt:1429]
- [x] [AI-Review][MEDIUM] Add UpdateServiceTest.kt to story File List documentation
- [x] [AI-Review][MEDIUM] Add failure case tests to UpdateServiceTest.kt: 403 Forbidden, network timeout, invalid JSON, missing fields [UpdateServiceTest.kt]
- [x] [AI-Review][MEDIUM] Add user-visible error message when update check fails due to network unreachability (currently silent) [MainViewModel.kt:1373-1403]
- [x] [AI-Review][MEDIUM] Add progress reporting during SHA-256 checksum verification for large APKs [MainViewModel.kt:1451]
- [x] [AI-Review][LOW] Add KDoc documentation to UpdateService.kt explaining API contract, header format, and expected response structure [UpdateService.kt]
- [x] [AI-Review][LOW] Standardize error message formats throughout update flow (consistent capitalization, punctuation) [MainViewModel.kt:1394,1469]
- [x] [AI-Review][HIGH] Remove BuildConfig fallback secret "DEVELOPMENT_SECRET" and fail build if ROOKIE_UPDATE_SECRET not provided in release mode [build.gradle.kts:73]
- [x] [AI-Review][HIGH] Document GitHubService.kt deletion in File List (currently shows as modified in git but not tracked) [Deleted file]
- [x] [AI-Review][MEDIUM] Add null-check for updateInfo.checksum before verification to prevent null==null bypass [MainViewModel.kt:1501]
- [x] [AI-Review][MEDIUM] Add network timeout test to UpdateServiceTest.kt to verify timeout configuration behavior [UpdateServiceTest.kt]
- [x] [AI-Review][MEDIUM] Add IOException/network failure test to UpdateServiceTest.kt for complete failure case coverage [UpdateServiceTest.kt]
- [x] [AI-Review][LOW] Standardize error message prefix format: use "Update check failed:" consistently or remove all colons [MainViewModel.kt:1398,1416]
- [x] [AI-Review][LOW] Restore version name validation in build.gradle.kts or update comment to reflect that validation is intentionally relaxed (currently accepts invalid version names silently, contradiciting comment) [build.gradle.kts:67-71]
- [x] [AI-Review][LOW] Add warning log for debug builds when ROOKIE_UPDATE_SECRET is empty to prevent developer confusion about update check failures [build.gradle.kts:74-81]
- [x] [AI-Review][LOW] Consider relaxing version name validation regex to accept versions like "2.5.0-rc" (without trailing number) - document exact SemVer format requirements in project README [build.gradle.kts:69]
- [x] [AI-Review][LOW] Update UpdateService.kt KDoc to document all possible exceptions: HttpException (any code), IOException, JsonParseException [UpdateService.kt:30-37]

## Dev Notes

- **Security**: Request signing prevents unauthorized access to the update metadata. Checksum verification ensures APK integrity.
- **Clock Sync**: Since signatures depend on timestamps, Quest devices with out-of-sync clocks will receive a 403 error. The app now explicitly suggests checking the system clock in this case.
- **Privacy**: The update secret is injected at build time, keeping it out of the source code.

### Project Structure Notes

- Network services consolidated in `com.vrpirates.rookieonquest.network`.
- Crypto utilities centralized in `CryptoUtils` object within `Constants.kt`.
- Secure Update API base URL: `https://sunshine-aio.com/`.

### References

- [Source: app/src/main/java/com/vrpirates/rookieonquest/network/UpdateService.kt]
- [Source: app/src/main/java/com/vrpirates/rookieonquest/data/Constants.kt]
- [Source: app/src/main/java/com/vrpirates/rookieonquest/ui/MainViewModel.kt]
- [Source: app/src/main/java/com/vrpirates/rookieonquest/MainActivity.kt]
- [Source: app/build.gradle.kts]

### Change Log
- Initial implementation of Secure Update client.
- Addressed code review findings - 18 items resolved (Date: 2026-02-15)
- Final adversarial review completed - All 20 items resolved (Date: 2026-02-15)

## Dev Agent Record

### Agent Model Used

Gemini 2.0 Flash

### Debug Log References

### Completion Notes List

- Successfully migrated update check from GitHub to Sunshine-AIO secure gateway.
- Implemented HMAC-SHA256 request signing for authentication, using explicit UTF-8 encoding for cross-platform consistency.
- Added SHA-256 integrity verification for downloaded update APKs with real-time progress reporting.
- Improved error feedback for system clock desynchronization (403 Forbidden) with actionable recovery instructions.
- Implemented resumable downloads for update APKs using HTTP Range headers.
- Added automatic retry logic with exponential backoff for update checks.
- Configured specialized, longer timeouts for large update APK downloads.
- Standardized and improved user-facing error messages for network issues with "Update check failed:" prefix.
- Added comprehensive unit tests for CryptoUtils (including progress-aware SHA-256) and UpdateService (including failure cases like 403, timeout, and network failure).
- Added full KDoc documentation to UpdateService API contract, including explicit exception documentation.
- Enforced `ROOKIE_UPDATE_SECRET` requirement for release builds in `build.gradle.kts` (removed unsafe fallback).
- Improved version name validation and documented SemVer requirements in README.md.
- Added null-check for update checksum to prevent bypass.

### File List
- `app/src/main/java/com/vrpirates/rookieonquest/network/UpdateService.kt`
- `app/src/main/java/com/vrpirates/rookieonquest/network/GitHubService.kt` (Deleted)
- `app/src/main/java/com/vrpirates/rookieonquest/data/Constants.kt`
- `app/src/main/java/com/vrpirates/rookieonquest/ui/MainViewModel.kt`
- `app/src/main/java/com/vrpirates/rookieonquest/MainActivity.kt`
- `app/build.gradle.kts`
- `app/src/test/java/com/vrpirates/rookieonquest/data/CryptoUtilsTest.kt`
- `app/src/test/java/com/vrpirates/rookieonquest/network/UpdateServiceTest.kt`
- `README.md`
