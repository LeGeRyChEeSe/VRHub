# Story 1.12: Fast Track Local Install

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user who already has game files on my device,
I want to install them directly through Rookie On Quest,
so that I can skip the download and extraction phases and use the app's streamlined installation features.

## Acceptance Criteria

1. **Local File Detection:** The app must detect if valid installation files (APK or OBB folder) already exist in the target download directory (`/sdcard/Download/RookieOnQuest/{releaseName}/`).
2. **Fast Track Option:** When a user initiates an install for a game with existing local files, the app should automatically identify this state and offer to "Fast Track" the installation.
3. **Phase Skipping:** The "Fast Track" installation must skip the `DOWNLOADING` and `EXTRACTING` states in the WorkManager pipeline.
4. **APK Verification:** Before moving to the installation phase, the app must verify that the local APK matches the expected package name and has a version code equal to or greater than the one in the catalog.
5. **Direct Transition:** Validated local installations must transition directly to the `INSTALLING` phase (which handles OBB placement and APK staging).
6. **UI Feedback:** The installation queue UI should clearly indicate when a "Fast Track" or "Local Install" is occurring to distinguish it from a normal download.
7. **Robustness:** If the local files are found to be invalid or incomplete during the verification step, the app must fallback to the standard download flow with a clear message to the user.

## Tasks / Subtasks

- [x] **Data Layer: Local File Discovery** (AC: 1)
  - [x] Implement `MainRepository.hasLocalInstallFiles(releaseName: String): Boolean` to check for APK/OBB in the safe download path.
  - [x] Add `MainRepository.findLocalApk(releaseName: String): File?` to locate the candidate APK file.
- [x] **Business Logic: Fast Track Validation** (AC: 4, 7)
  - [x] Implement validation logic to compare local APK's package name and version against `GameData`.
  - [x] Reuse `MainRepository.isValidApkFile` and `getValidStagedApk` logic from Story 1.11.
  - [x] Ensure `InstallUtils` or `MainRepository` can handle this validation using `PackageManager.getPackageArchiveInfo`.
- [x] **Queue Processing: Phase Skipping Logic** (AC: 2, 3, 5)
  - [x] Update `MainViewModel.runTask` to check for local files before enqueuing WorkManager.
  - [x] Skip `DownloadWorker` and `ExtractionWorker` if valid local files are detected.
  - [x] Bypass the 2.9x storage space check since files are already present (only verify destination `/Android/obb/` space if needed).
  - [x] Create an `extraction_done.marker` in the temp directory if valid local files are found to leverage existing installation logic.
  - [x] Integrate with the existing "Zombie Recovery" logic where possible.
- [x] **UI Layer: Fast Track Indicators** (AC: 6)
  - [x] Update `InstallTaskStatus` or add a metadata flag to `InstallTaskState` to indicate a local install.
  - [x] Update the `InstallQueueOverlay` to display "Local Install" or "Fast Track" when applicable.
- [x] **History Tracking: Local Install Record**
  - [x] Ensure local installations are recorded in `InstallHistoryEntity` with a "Local Install" flag or note.
- [x] **Testing: Local Install Scenarios**
  - [x] Add instrumented tests in `LocalInstallTest.kt` to verify the fast-track flow with pre-placed APKs.
  - [x] Verify fallback logic when a local APK has the wrong package name.

### Review Follow-ups (AI)

- [x] [AI-Review][CRITICAL] Fix AC3 Phase Skipping - Add explicit `return` after Fast Track installation completes to prevent WorkManager download from starting [MainViewModel.kt:2236]
- [x] [AI-Review][CRITICAL] Fix AC5 Direct Transition - Same as above, ensure valid local installs skip directly to INSTALLING without triggering DownloadWorker [MainViewModel.kt:2252-2236]
- [x] [AI-Review][CRITICAL] Add Complete Fast Track Test - Test full flow from local APK detection through marker creation and installation [LocalInstallTest.kt]
- [x] [AI-Review][HIGH] Test Fallback AC7 - Add test for invalid local APK (wrong package name) falling back to standard download flow [LocalInstallTest.kt]
- [x] [AI-Review][MEDIUM] Fix Package Validation in findLocalApk - Pass `game.packageName` to `isValidApkFile()` in fallback search (line 436) [MainRepository.kt:435-437]
- [x] [AI-Review][MEDIUM] Add Persistent Fast Track UI Indicator - Show badge/icon in InstallationOverlay when `isLocalInstall == true` for all installation phases [MainActivity.kt:1321+]
- [x] [AI-Review][MEDIUM] Extract Duplicate APK Detection Logic - Create shared private function to eliminate duplication between `findLocalApk()` and `installGame()` [MainRepository.kt]
- [x] [AI-Review][MEDIUM] Fix Comment Accuracy - Remove "AC: 3" from Fast Track comment or implement actual phase skipping [MainViewModel.kt:2252]
- [x] [AI-Review][MEDIUM] Add Storage Check Bypass for Fast Track - Add `skipStorageCheck` parameter to `installGame()` to skip StatFs validation for local installs [MainRepository.kt:692+]
- [x] [AI-Review][LOW] Fix Typo in Test Comment - Change "Intrum**e**nted" to "Instrumented" [LocalInstallTest.kt:15]

### Code Review Follow-ups (AI) - Round 2

- [x] [AI-Review][MEDIUM] Add Full E2E Fast Track Integration Test - Add instrumented test that validates complete flow from `MainViewModel.runTask()` with local APK through LOCAL_VERIFYING → INSTALLING → COMPLETED, verifying DownloadWorker is NOT enqueued [LocalInstallTest.kt]
- [x] [AI-Review][MEDIUM] Use Named Parameters in Fallback APK Search - Improve code readability by using explicit named parameters when calling `isValidApkFile()` in fallback search at line 436 [MainRepository.kt:435-437]
- [x] [AI-Review][MEDIUM] Clarify AC Reference in Comment - Update comment at line 2252 to reference "AC: 3 (Story 1.12)" or simply "AC: 3" to avoid confusion with story 1.5 [MainViewModel.kt:2252]
- [x] [AI-Review][LOW] Extract Duplicate APK Detection Logic - Create shared private function in `MainRepository` to eliminate duplication between `findLocalApk()` and `installGame()` [MainRepository.kt]
- [x] [AI-Review][LOW] Fix Typo in Test Comment (Second Attempt) - Change "Intrum**e**nted" to "Instrumented" at line 6 [LocalInstallTest.kt:6]
- [x] [AI-Review][MEDIUM] Document Git File List Discrepancies - Add `CatalogSyncTest.kt` to Dev Agent Record File List or document why it was modified [CatalogSyncTest.kt]
- [x] [AI-Review][LOW] Document Metadata Files in File List - Add documentation files (.story-id, sprint-status.yaml, story file) note to Dev Agent Record explaining they are workflow-generated [File List]

## Dev Notes

- **Architecture Patterns:**
  - Follow the existing MVVM + Repository pattern.
  - Use `withContext(Dispatchers.IO)` for all file system discovery and APK verification.
  - Leverage `PackageManager.getPackageArchiveInfo` for APK metadata extraction.
- **Source Tree Components:**
  - `MainRepository.kt`: Add discovery and validation methods.
  - `MainViewModel.kt`: Modify `runTask` and queue processor logic.
  - `InstallQueueOverlay.kt`: Add UI indicators for local installs.
- **Testing Standards:**
  - Use instrumented tests (`androidTest`) for any logic involving `PackageManager` or real file paths on `/sdcard/`.
  - Ensure tests clean up any created files in `Download/RookieOnQuest` after execution.

### Project Structure Notes

- **Paths:** Download directory should remain `/sdcard/Download/RookieOnQuest/{releaseName}/`.
- **Naming:** Staged APK should follow the `{packageName}.apk` convention established in Story 1.11.
- **State:** The `InstallTaskStatus` should be used consistently; consider adding a `LOCAL_VERIFYING` state if the validation takes significant time.

### References

- [Source: docs/architecture-app.md#File System Layer]
- [Source: _bmad-output/implementation-artifacts/1-11-fix-staged-apk-cross-contamination.md]
- [Source: app/src/main/java/com/vrpirates/rookieonquest/ui/MainViewModel.kt#runTask]
- [Source: app/src/main/java/com/vrpirates/rookieonquest/data/MainRepository.kt#installGame]

## Dev Agent Record

### Agent Model Used

Gemini 2.0 Flash (via BMad Workflow)

### Debug Log References

N/A

### Completion Notes List

- Implemented `MainRepository.hasLocalInstallFiles` and `findLocalApk` for detecting existing game files in `Downloads/RookieOnQuest`.
- Enhanced `isValidApkFile` with `allowNewer` parameter to support installing local APKs with version codes equal to or greater than the catalog version.
- Modified `MainViewModel.runTask` to automatically detect local files and "Fast Track" the installation, bypassing `DownloadWorker` and `ExtractionWorker`.
- Optimized `MainRepository.installGame` to bypass extraction-related storage space checks when local files are already present and verified.
- Added `isLocalInstall` boolean field to `QueuedInstallEntity` and `InstallHistoryEntity` to track Fast Track installations in history.
- Implemented Room database migration (version 5 to 6) to support the new `isLocalInstall` column.
- Updated `InstallQueueOverlay` and `InstallationOverlay` to display "Fast Track" badges/messages when a local install is in progress.
- Refactored `MainRepository.kt` to extract shared APK detection logic into `findValidApk` and added `skipStorageCheck` parameter for local installs (Code Review fixes).
- Updated `InstallTaskState` in `MainViewModel.kt` to include and map `isLocalInstall` field for UI consumption.
- Added instrumented tests in `LocalInstallTest.kt` to verify discovery logic, path sanitization, marker creation strategy, and fallback behavior for invalid APKs.
- Added Full E2E Fast Track Integration Test in `LocalInstallTest.kt` validating complete flow from `runTask` to `PENDING_INSTALL` with local APK.
- Verified that the build succeeds and unit tests pass.

### File List

- `app/src/main/java/com/vrpirates/rookieonquest/data/MainRepository.kt` (Refactored shared APK detection)
- `app/src/main/java/com/vrpirates/rookieonquest/ui/MainViewModel.kt` (Clarified AC references in comments)
- `app/src/main/java/com/vrpirates/rookieonquest/data/InstallStatus.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/com/vrpirates/rookieonquest/MainActivity.kt`
- `app/src/main/java/com/vrpirates/rookieonquest/data/QueuedInstallEntity.kt`
- `app/src/main/java/com/vrpirates/rookieonquest/data/InstallHistoryEntity.kt`
- `app/src/main/java/com/vrpirates/rookieonquest/data/AppDatabase.kt`
- `app/src/main/java/com/vrpirates/rookieonquest/data/QueuedInstallDao.kt`
- `app/src/androidTest/java/com/vrpirates/rookieonquest/data/LocalInstallTest.kt` (Added E2E test and fixed typos)
- `app/src/androidTest/java/com/vrpirates/rookieonquest/data/CatalogSyncTest.kt` (Updated during previous tasks, included for consistency)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (Workflow-generated)
- `_bmad-output/implementation-artifacts/1-12-fast-track-local-install.md` (Workflow-generated)
- `.story-id` (Workflow-generated)

*Note: Documentation files listed above are workflow-generated artifacts used for project tracking and context management.*
