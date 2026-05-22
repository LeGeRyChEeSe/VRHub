# Story 1.1: Mise en place de Jetpack Preferences DataStore et du schéma de consentement

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

**User Story Statement:**
As a VRHub user,
I want my consent preferences to be reliably stored using Jetpack Preferences DataStore,
so that my privacy choices persist across app restarts and the system is ready for the stats collection workflow.

**Business Value:**
Establishes the foundational persistence layer for the entire Stats Collection & Privacy Subsystem. Without reliable consent storage, no stats collection can ethically occur. This story creates the `ConsentPreferences` wrapper around DataStore that will be used by every subsequent story in Epic 1.

## Acceptance Criteria

### AC #1: DataStore Schema
- [ ] `ConsentPreferences` class encapsulates a `Preferences DataStore` with key `consent_enabled` (boolean, default `false`)
- [ ] `ConsentPreferences` class encapsulates key `has_seen_consent_dialog` (boolean, default `false`)
- [ ] Both preferences are accessible via reactive `Flow<Boolean>` streams
- [ ] Both preferences support synchronous write via `suspend fun setXxx(value: Boolean)`

### AC #2: Interface for DI
- [ ] `ConsentPreferencesInterface` abstract the storage layer with `consentEnabled: Flow<Boolean>` and `hasSeenConsentDialog: Flow<Boolean>`
- [ ] `ConsentPreferences` implements `ConsentPreferencesInterface`
- [ ] `setConsentEnabled(enabled: Boolean)` and `setHasSeenConsentDialog(shown: Boolean)` suspend functions exist

### AC #3: Testability
- [ ] `FakeConsentPreferences` implements `ConsentPreferencesInterface` for unit testing
- [ ] `ConsentPreferencesTest` exercises the real DataStore key behavior (default false, true after set, false after toggle back)

### AC #4: Naming Convention
- [ ] Preference key names use snake_case: `consent_enabled`, `has_seen_consent_dialog`
- [ ] DataStore name is `vrhub_settings`

### AC #5: Thread Safety
- [ ] All DataStore operations are dispatched to `Dispatchers.IO` internally
- [ ] The public API exposes Flows that are already conflated for preference reads

## Tasks / Subtasks

- [ ] Task 1: Audit and extend `ConsentPreferences.kt` to add `has_seen_consent_dialog` key (AC: #1, #4)
  - [ ] Subtask 1.1: Add `HAS_SEEN_CONSENT_DIALOG` booleanPreferencesKey
  - [ ] Subtask 1.2: Add `hasSeenConsentDialog: Flow<Boolean>` property
  - [ ] Subtask 1.3: Add `setHasSeenConsentDialog(shown: Boolean)` suspend function
  - [ ] Subtask 1.4: Verify `consent_enabled` key default is `false`
- [ ] Task 2: Update `ConsentPreferencesInterface.kt` with new interface members (AC: #2)
  - [ ] Subtask 2.1: Add `hasSeenConsentDialog: Flow<Boolean>` to interface
  - [ ] Subtask 2.2: Add `setHasSeenConsentDialog(shown: Boolean)` to interface
- [ ] Task 3: Update `FakeConsentPreferences.kt` to implement new interface members (AC: #3)
  - [ ] Subtask 3.1: Add `hasSeenConsentDialog` MutableStateFlow
  - [ ] Subtask 3.2: Implement `setHasSeenConsentDialog`
- [ ] Task 4: Enhance `ConsentPreferencesTest.kt` with comprehensive tests (AC: #3)
  - [ ] Subtask 4.1: Test `has_seen_consent_dialog` key default is false
  - [ ] Subtask 4.2: Test `setHasSeenConsentDialog(true)` makes flow emit true
  - [ ] Subtask 4.3: Test `setHasSeenConsentDialog(false)` after true reverts to false
  - [ ] Subtask 4.4: Test `consent_enabled` default remains false (regression)
  - [ ] Subtask 4.5: Test `setConsentEnabled(true/false` toggle sequence

## Dev Notes

### Existing Code Analysis

**Already Implemented (do NOT redo):**
- `app/src/main/java/com/vrhub/data/ConsentPreferences.kt` — Already has `consent_enabled` key, `consentEnabled` Flow, and `setConsentEnabled()`. Extension needed only for `has_seen_consent_dialog`.
- `app/src/main/java/com/vrhub/data/ConsentPreferencesInterface.kt` — Already has `consentEnabled: Flow<Boolean>` and `setConsentEnabled()`. Extension needed for `hasSeenConsentDialog`.
- `app/src/main/java/com/vrhub/data/FakeConsentPreferences.kt` — Already implements `ConsentPreferencesInterface`. Needs extension for new members.
- `app/src/main/java/com/vrhub/data/ConsentPreferencesTest.kt` — Partial tests exist, but testing only the key name, not the full class behavior. Needs comprehensive tests.

**Existing Patterns to Preserve:**
- DataStore is created via ` preferencesDataStore(name = "vrhub_settings")` extension on `Context`
- Preference keys use `booleanPreferencesKey("consent_enabled")` pattern
- Flow is mapped via `dataStore.data.map { preferences -> preferences[KEY] ?: false }`
- All writes go through `dataStore.edit { preferences -> preferences[KEY] = value }`

### Project Structure Notes

- DataStore extension defined in `ConsentPreferences.kt` via `val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "vrhub_settings")`
- All data layer classes are in `app/src/main/java/com/vrhub/data/`
- Test classes are in `app/src/test/java/com/vrhub/data/`
- No existing `has_seen_consent_dialog` key exists — this is net NEW functionality

### References

- [Source: PRD.md — FR13, FR14, FR15: DataStore persistence requirements]
- [Source: PRD.md — NFR4: Start-up speed < 50ms for consent checking]
- [Source: StatsCollector.kt — Already reads `consentPreferences.consentEnabled` via interface]
- [Source: MainRepository.kt line 71 — Instantiates `ConsentPreferences(context)` directly]
- [Source: ConsentDialog.kt — Will need to check `hasSeenConsentDialog` before showing]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-20250514

### Debug Log References

51f99d2 feat(stats): implement ConsentPreferences DataStore for story 1.1

### Completion Notes List

- STORY-1.1 establishes the DataStore foundation for consent preferences
- Two keys are required: `consent_enabled` and `has_seen_consent_dialog`
- The `ConsentPreferences` class and interface need extension (not replacement)
- All DataStore operations must use `Dispatchers.IO` for thread safety

### File List

- `app/src/main/java/com/vrhub/data/ConsentPreferences.kt` — UPDATE (add has_seen_consent_dialog)
- `app/src/main/java/com/vrhub/data/ConsentPreferencesInterface.kt` — UPDATE (add hasSeenConsentDialog interface members)
- `app/src/main/java/com/vrhub/data/FakeConsentPreferences.kt` — UPDATE (implement new interface members)
- `app/src/test/java/com/vrhub/data/ConsentPreferencesTest.kt` — UPDATE (comprehensive tests)