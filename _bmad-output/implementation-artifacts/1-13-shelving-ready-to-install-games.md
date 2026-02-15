# Story 1.13: Shelving Ready-to-Install Games & Local Installs Tab

Status: review

**As a** user,
**I want** games that are extracted but not yet installed to move out of the active queue,
**So that** they don't block subsequent downloads and I can install them later from a dedicated "Local Installs" view.

## Acceptance Criteria

### 1. Queue Non-Blocking Behavior
- [x] **Given** a game has reached the `READY_TO_INSTALL` state (extracted, OBBs moved).
- [x] **When** the user does not immediately complete the installation (e.g., closes the dialog or app).
- [x] **Then** the `QueueProcessor` must transition the item out of the active processing loop.
- [x] **And** the next item in the queue must start downloading/extracting automatically.

### 2. "Local Installs" Data Persistence
- [x] **Given** a game is in the `staged_apks` directory.
- [x] **When** the app scans for local ready-to-install games.
- [x] **Then** it should match the staged folder name with a catalog entry.
- [x] **And** display these games in a dedicated "Local" or "Ready" tab/section.

### 3. One-Click Local Installation
- [x] **Given** a game listed in the "Local Installs" section.
- [x] **When** the user clicks "Install".
- [x] **Then** the app must trigger the `FileProvider` installation directly using the existing APK in `staged_apks`.
- [x] **And** skip the download and extraction phases entirely.

### 4. Cleanup on Completion
- [x] **Given** a local installation is successful.
- [x] **When** the installation is verified.
- [x] **Then** the temporary files in `staged_apks` for that specific game must be deleted.
- [x] **And** the game must be removed from the "Local Installs" view.

## Tasks / Subtasks

- [x] Add `SHELVED` status to `InstallStatus` (data) and `InstallTaskStatus` (UI) (AC: 1, 2)
- [x] Update `isProcessing()` to exclude `PENDING_INSTALL` and `SHELVED` (AC: 1)
- [x] Implement automatic shelving in `verifyPendingInstallations` and `resumeActiveDownloadObservations` (AC: 1)
- [x] Add `LOCAL_INSTALLS` filter to `FilterStatus` and update `MainViewModel` flows (AC: 2)
- [x] Add "Local Installs" chip to `CustomTopBar` and menu item to navigation drawer (AC: 2)
- [x] Update `GameListItem` to show "COMPLETE INSTALL" for shelved games (AC: 3)
- [x] Update `installGame` and `promoteTask` to handle `SHELVED` status (AC: 3)
- [x] Implement `deleteStagedApk(packageName)` in `MainRepository` (AC: 4)
- [x] Verify existing cleanup logic handles `SHELVED` tasks once they complete (AC: 4)
- [x] Fix non-exhaustive `when` expression in `MainActivity` after adding `SHELVED` status.

## Review Follow-ups (AI)

**Code Review Date:** 2025-02-14
**Reviewer:** Claude (Adversarial Code Review)
**Story Status Changed:** done → in-progress

### Critical Issues (Must Fix)

- [x] [AI-Review][CRITICAL] Fix `isProcessing()` to exclude `SHELVED` status - Task marked [x] but not implemented [MainViewModel.kt:140-145]
- [x] [AI-Review][HIGH] Implement scan of `staged_apks` directory on app startup to discover orphaned APKs and add them to queue [AC2 incomplete]
- [x] [AI-Review][HIGH] Modify `installGame()` to check for existing staged APK before re-downloading for SHELVED tasks [MainRepository.kt:725-732]
- [x] [AI-Review][HIGH] Add package name validation to `deleteStagedApk()` to prevent path traversal [MainRepository.kt:2056-2062]

### Medium Issues (Should Fix)

- [x] [AI-Review][MEDIUM] Add explicit color case for `SHELVED` status in progress indicator `when` expression [MainActivity.kt:1414-1417]
- [x] [AI-Review][MEDIUM] Replace hardcoded `delay(500)` in `testPromoteShelvedTask()` with proper synchronization mechanism [ShelvingTest.kt:85]
- [x] [AI-Review][MEDIUM] Enhance `testShelveTask()` to verify staged APK file exists and is valid after shelving
- [x] [AI-Review][MEDIUM] Implement startup scan of `staged_apks` directory in `MainViewModel.init` or `onAppResume()`

### Low Issues (Nice to Fix)

- [x] [AI-Review][LOW] Document SHELVED status lifecycle in code comments and Dev Notes
- [x] [AI-Review][LOW] Standardize terminology between "Local Installs" and "Ready to Install" across UI and documentation

---

**Second Code Review Date:** 2026-02-14
**Reviewer:** Claude (Adversarial Code Review - Round 2)
**Previous Review Fixes:** 8/8 verified ✅
**New Issues Found:** 4 HIGH, 4 MEDIUM, 2 LOW

### High Severity Issues (Must Fix)

- [x] [AI-Review][HIGH] Fix race condition in `discoverOrphanedStagedApks()` - Queue snapshot could become stale during iteration, potentially creating duplicate SHELVED entries [MainViewModel.kt:1222]
- [x] [AI-Review][HIGH] Add fallback in QueueProcessor for corrupted staged APK - When promoting SHELVED task, if staged APK is invalid, fall back to full download instead of failing [MainViewModel.kt:3082]
- [x] [AI-Review][HIGH] Add debug logging for invalid orphaned APKs in `discoverOrphanedStagedApks()` - Silent failures make debugging difficult [MainViewModel.kt:1236]
- [x] [AI-Review][HIGH] Validate staged APK before moving to SHELVED in `verifyPendingInstallations()` - If APK is corrupted, mark as FAILED instead of SHELVED to prevent stuck tasks [MainViewModel.kt:3262]

### Medium Severity Issues (Should Fix)

- [x] [AI-Review][MEDIUM] Add unique database constraint on `releaseName` in `QueuedInstallEntity` - Prevents duplicate queue entries with different statuses [Database schema]
- [x] [AI-Review][MEDIUM] Fix `signalTaskComplete()` ordering - Clear `activeReleaseName` BEFORE signaling complete to avoid race condition [MainViewModel.kt:2771-2776, 2872-2877]
- [x] [AI-Review][MEDIUM] Replace fake APK in `testShelveTask()` with valid APK or mock `isValidApkFile()` - Current test uses text file, doesn't validate real APK logic [ShelvingTest.kt:64]
- [x] [AI-Review][MEDIUM] Add integration test `testFullShelvingFlow()` covering: Download → Extract → PENDING_INSTALL → App kill → Startup → SHELVED → Promote → Install [Test coverage]

### Low Severity Issues (Nice to Fix)

- [x] [AI-Review][LOW] Add comprehensive Javadoc to private methods `discoverOrphanedStagedApks()` and `shelveTask()` with usage examples [Code maintainability]
- [x] [AI-Review][LOW] Standardize terminology - Currently using "Ready", "Ready to Install (Local)", and "Local Installs" inconsistently across UI [strings.xml, MainActivity.kt]

---

**Third Code Review Date:** 2026-02-14
**Reviewer:** Claude (Adversarial Code Review - Round 3)
**Previous Review Fixes:** 10/10 verified ✅
**New Issues Found:** 0 HIGH, 1 MEDIUM, 0 LOW

### Medium Severity Issues (Should Fix)

- [x] [AI-Review][MEDIUM] Update File List to include configuration files modified during development - `.story-id`, story file, and `sprint-status.yaml` are tracked by git but missing from File List section [Story documentation]

---

**Fourth Code Review Date:** 2026-02-15
**Reviewer:** Claude (Adversarial Code Review - Round 4)
**Previous Review Fixes:** 10/10 verified ✅
**New Issues Found:** 0 HIGH, 0 MEDIUM, 2 LOW

### Low Severity Issues (Nice to Fix)

- [x] [AI-Review][LOW] Enhance `testDiscoverOrphanedStagedApks()` to test real discovery flow with valid APK - Current test only checks scanner doesn't crash with fake text file, but could use `createRealStagedApk()` helper to verify orphaned APKs are actually discovered and added to queue as SHELVED [ShelvingTest.kt:155-172]
- [x] [AI-Review][LOW] Replace hardcoded release name `"my-test-release"` with constant in `testCleanupStagedApkOnVerification()` - Improves maintainability and prevents potential confusion if release name changes in one place but not the other [ShelvingTest.kt:200]

## Dev Notes

- **Architecture:** MVVM with Room + WorkManager.
- **State Management:** `install_queue` table updated with `SHELVED` status.
- **UI:** Filter chips in `CustomTopBar` and navigation drawer items.
- **Non-Blocking:** Removing `PENDING_INSTALL` from `isProcessing()` allows `QueueProcessor` to move to the next task while the installer is potentially open.
- **Cleanup:** Added `deleteStagedApk` to ensure no orphaned APKs remain in `externalFilesDir` after success or cancellation.
- **Robustness:** Added validation and fallbacks for staged APKs to handle corruption and race conditions.

## Dev Agent Record

### Agent Model Used
gemini-2.0-flash

### Debug Log References
- Fixed compilation error in `MainActivity.kt` due to non-exhaustive `when` on `InstallTaskStatus`.
- Verified `QueueProcessor` loop correctly picks next `QUEUED` task while others are `PENDING_INSTALL` or `SHELVED`.
- Resolved race condition in `discoverOrphanedStagedApks()` by refreshing queue state per iteration.
- Implemented graceful fallback in `runTask()` when resuming from corrupted extraction artifacts.

### Completion Notes List
- Implemented `SHELVED` status to track games ready for local installation.
- Created "Ready to Install" filter in the UI (standardized terminology).
- Enabled one-click "Complete Install" from the local filter.
- Ensured queue non-blocking behavior for pending installations.
- Implemented thorough cleanup of staged APKs upon completion or cancellation.
- Addressed all Round 2 code review findings (10 items).
- Improved `ShelvingTest` with real APK validation and full flow integration test.
- Added Javadoc and standardized UI terminology.
- Addressed Round 4 code review findings (2 items) including enhanced tests and maintainability improvements.

## File List
- .story-id (Worktree marker)
- _bmad-output/implementation-artifacts/1-13-shelving-ready-to-install-games.md (Story file)
- _bmad-output/implementation-artifacts/sprint-status.yaml (Sprint tracking)
- app/src/main/java/com/vrpirates/rookieonquest/data/InstallStatus.kt
- app/src/main/java/com/vrpirates/rookieonquest/data/MainRepository.kt
- app/src/main/java/com/vrpirates/rookieonquest/ui/MainViewModel.kt
- app/src/main/java/com/vrpirates/rookieonquest/ui/GameListItem.kt
- app/src/main/java/com/vrpirates/rookieonquest/MainActivity.kt
- app/src/main/res/values/strings.xml
- app/src/test/java/com/vrpirates/rookieonquest/ui/StatusMappingTest.kt (New)
- app/src/androidTest/java/com/vrpirates/rookieonquest/data/ShelvingTest.kt (New)

## Change Log
- feat: implement shelving for ready-to-install games
- feat: add local installs filter to UI
- fix: ensure exhaustive when expressions handle new SHELVED status
- refactor: improve staged APK cleanup logic
- fix: addressed 10 code review findings (Round 2) including race conditions and robustness improvements
- docs: added Javadoc and standardized "Ready to Install" terminology
