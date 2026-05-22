# Story 1.2: Intégration du Consentement dans le MainRepository

Status: backlog

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

**User Story Statement:**
As a VRHub user,
I want my consent preference to be enforced at the repository level before any stats are collected,
so that no anonymous data is ever sent without my explicit permission.

**Business Value:**
This story bridges the DataStore persistence layer (STORY-1.1) with the actual stats collection workflow. Without consent enforcement in `MainRepository`, the entire privacy-first design of Epic 1 collapses — stats could be collected regardless of user choice. This story ensures that every path leading to `StatsCollector.collectStats()` first validates active consent, making the repository the single gatekeeper for all data exfiltration.

## Acceptance Criteria

### AC #1: Consent Gate in maybeCollectStats
- [ ] `MainRepository.maybeCollectStats()` reads `consentPreferences.consentEnabled` via its Flow before any collection logic
- [ ] When `consentEnabled = false`, the method logs `"maybeCollectStats: skipped — no consent"` and returns immediately without calling `statsCollector.collectStats()`
- [ ] When `consentEnabled = true`, the method proceeds with package querying, catalog matching, tier resolution, and stats submission

### AC #2: Consent Gate in resolveUserTier
- [ ] `MainRepository.resolveUserTier()` is only invoked from within a consent-checked code path (i.e., after `maybeCollectStats` confirms consent)
- [ ] No direct calls to `resolveUserTier()` exist outside of the stats collection flow

### AC #3: Integration with Catalog Sync
- [ ] `syncCatalog()` calls `maybeCollectStats()` at the end of its successful execution path (after catalog insertion completes)
- [ ] If consent is disabled, `syncCatalog` still completes successfully — consent only gates stats, not catalog operations
- [ ] The `maybeCollectStats()` call in `syncCatalog()` does NOT throw exceptions that would abort the sync

### AC #4: Integration with Favorite Toggle
- [ ] `toggleFavorite()` calls `maybeCollectStats()` after updating the favorite status in Room
- [ ] Both `isFavorite = true` and `isFavorite = false` trigger a potential stats re-collection
- [ ] The call is wrapped in try/catch so that stats failures never break the toggle operation

### AC #5: StatsCollector Integration
- [ ] `MainRepository` instantiates `StatsCollector` with both `NetworkModule.statsApiService` and `consentPreferences` (already done at line 72)
- [ ] `statsCollector.collectStats()` is called with the correct parameters: `(email = null, games = Map<String, Boolean>, tier = String)`
- [ ] The `games` map contains only packages that exist in both the installed packages list AND the catalog (intersection)

### AC #6: Tier Resolution Fallback
- [ ] When `resolveUserTier()` fails (network error or server returns non-200), it defaults to `"standard"` tier instead of crashing
- [ ] The fallback is logged as a warning, not an error
- [ ] Stats collection continues with the default tier when resolution fails

### AC #7: Thread Safety
- [ ] All consent reads and stats operations in `MainRepository` run on `Dispatchers.IO` via `withContext(Dispatchers.IO)`
- [ ] No blocking calls on the main thread during consent checking or stats collection

## Tasks / Subtasks

- [ ] Task 1: Verify and harden `maybeCollectStats()` consent gate (AC: #1, #3, #4)
  - [ ] Subtask 1.1: Confirm `consentPreferences.consentEnabled.first()` is called at the start of `maybeCollectStats()`
  - [ ] Subtask 1.2: Verify early return with log when consent is false
  - [ ] Subtask 1.3: Ensure `syncCatalog()` calls `maybeCollectStats()` only after successful catalog insertion (already done at line 320)
  - [ ] Subtask 1.4: Ensure `toggleFavorite()` calls `maybeCollectStats()` after DB update (already done at line 107)

- [ ] Task 2: Harden tier resolution fallback (AC: #6)
  - [ ] Subtask 2.1: Verify `resolveUserTier()` returns `"standard"` on any exception or non-successful response
  - [ ] Subtask 2.2: Add warning log for failed tier resolution

- [ ] Task 3: Validate StatsCollector integration (AC: #5)
  - [ ] Subtask 3.1: Confirm `statsCollector.collectStats(null, games, tier)` is called with correct arguments
  - [ ] Subtask 3.2: Verify the `games` map only contains catalog-matched packages

- [ ] Task 4: Add unit tests for MainRepository consent integration (AC: #1, #6)
  - [ ] Subtask 4.1: Test `maybeCollectStats()` skips collection when consent is false
  - [ ] Subtask 4.2: Test `maybeCollectStats()` proceeds when consent is true and games exist
  - [ ] Subtask 4.3: Test `resolveUserTier()` returns `"standard"` on network failure

## Dev Notes

### Existing Code Analysis

**Already Implemented (do NOT redo):**
- `MainRepository` already has `consentPreferences = ConsentPreferences(context)` at line 71
- `MainRepository` already has `statsCollector = StatsCollector(NetworkModule.statsApiService, consentPreferences)` at line 72
- `maybeCollectStats()` method exists with consent check (lines 446-473) — needs validation and hardening
- `syncCatalog()` calls `maybeCollectStats()` at the end (line 320)
- `toggleFavorite()` calls `maybeCollectStats()` after DB update (line 107)
- `resolveUserTier()` exists but may need fallback improvement

**Existing Patterns to Preserve:**
- Consent is read via `consentPreferences.consentEnabled.first()` (Flow terminal operator in suspend context)
- Stats collection uses `statsCollector.collectStats(email, gamesMap, tier)` with `email = null` for MVP
- Tier resolution calls `NetworkModule.statsApiService.getUserTier("anonymous")`
- All repository operations use `withContext(Dispatchers.IO)`

### Project Structure Notes

- MainRepository: `app/src/main/java/com/vrhub/data/MainRepository.kt`
- StatsCollector: `app/src/main/java/com/vrhub/data/StatsCollector.kt`
- ConsentPreferences: `app/src/main/java/com/vrhub/data/ConsentPreferences.kt` (from STORY-1.1)
- NetworkModule: `app/src/main/java/com/vrhub/network/NetworkModule.kt`
- Tests should go in: `app/src/test/java/com/vrhub/data/MainRepositoryTest.kt`

### References

- [Source: PRD.md — FR7, FR8, FR9, FR10, FR12: Stats collection requirements]
- [Source: PRD.md — NFR5, NFR7: Consent isolation and zero identification]
- [Source: MainRepository.kt line 446-473: Current maybeCollectStats() implementation]
- [Source: MainRepository.kt line 104-107: toggleFavorite() integration point]
- [Source: MainRepository.kt line 320: syncCatalog() integration point]
- [Source: StatsCollector.kt — collectStats() enforces its own consent check as defense-in-depth]

## Dev Agent Record

### Agent Model Used

_(to be filled by dev agent)_

### Debug Log References

_(to be filled by dev agent)_

### Completion Notes List

_(to be filled by dev agent)_

### File List

- `app/src/main/java/com/vrhub/data/MainRepository.kt` — UPDATE (harden consent gate, improve tier fallback)
- `app/src/test/java/com/vrhub/data/MainRepositoryTest.kt` — CREATE or UPDATE (consent integration tests)
