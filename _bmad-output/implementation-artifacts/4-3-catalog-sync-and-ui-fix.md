# Story 4.3: Catalog Sync and UI Fix

Status: in-progress

## Story

As a Rookie On Quest user,
I want the catalog to synchronize automatically when needed and have a polished interface,
so that I can access the latest VR games without manual intervention and without UI glitches.

## Acceptance Criteria

1. **Automatic Initial Sync:** The app must trigger a catalog synchronization automatically on first launch or if the database is empty (FR9, FR50).
2. **Robust Update Check:** The GitHub update check must have a timeout (5s) to prevent the app from hanging on slow networks (FR60).
3. **UI Layout Integrity:** The TopBar title must not push the settings icon off-screen on narrow devices (NFR-U1, FR1).
4. **Diagnostic Transparency:** The synchronization process must log detailed information for troubleshooting (NFR-M3).

## Tasks / Subtasks

- [x] Fix TopBar title clipping (AC: 3)
  - [x] Apply weight(1f) and Ellipsis to title Text in MainActivity.kt
- [x] Implement background initialization loop improvements (AC: 1, 2)
  - [x] Add 5s timeout to app update check in MainViewModel.kt
  - [x] Trigger refreshData() if database is empty on startup
- [x] Enhance Catalog Sync Diagnostics (AC: 4)
  - [x] Add detailed logging to MainRepository.syncCatalog()
- [ ] Verify fix on device (AC: 1, 2, 3)
  - [ ] Confirm catalog loads on clean install
  - [ ] Confirm settings icon is visible
  - [ ] Confirm no hang on startup without network

## Dev Notes

- **Architecture:** MVVM with Room + WorkManager.
- **Components:** `MainViewModel.kt`, `MainActivity.kt`, `MainRepository.kt`.
- **Patterns:** Coroutines with `withTimeoutOrNull` for robust networking.

### Project Structure Notes

- Modifications isolated to `com.vrpirates.rookieonquest.ui` and `com.vrpirates.rookieonquest.data`.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Epic 4]
- [Source: app/src/main/java/com/vrpirates/rookieonquest/data/Constants.kt]

## Dev Agent Record

### Agent Model Used

Gemini 2.0 Flash (Thought)

### Debug Log References

### Completion Notes List

### File List

- `app/src/main/java/com/vrpirates/rookieonquest/MainActivity.kt`
- `app/src/main/java/com/vrpirates/rookieonquest/ui/MainViewModel.kt`
- `app/src/main/java/com/vrpirates/rookieonquest/data/MainRepository.kt`
