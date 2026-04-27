# Story 10.3: Configure Server via Manual Key-Value Pairs

Status: review

## Story

As a user,
I want to enter server configuration keys and values manually,
so that I can use servers that don't provide a JSON URL.

## Acceptance Criteria

1. **Given** the user is on the configuration screen
   **When** the user selects "Manual Entry" input mode
   **Then** a dynamic form appears with empty key and value fields
   **And** an "Add Key" button is visible

2. **Given** the user is in manual entry mode
   **When** the user enters a key-value pair
   **And** taps "Add Key"
   **Then** the pair is added to the configuration list
   **And** new empty key-value fields appear

3. **Given** the user has added one or more key-value pairs
   **When** the user taps the TEST button
   **Then** the system validates the configuration
   **And** displays success or specific error message

4. **Given** the configuration is valid
   **When** the user taps TEST
   **Then** "Configuration valid" message appears
   **And** a SAVE button becomes enabled

## Tasks / Subtasks

- [x] Task 1: Add Manual KV input mode to ConfigurationScreen (AC: #1, #2)
  - [x] Subtask 1.1: Create dynamic key-value pair form composable
  - [x] Subtask 1.2: Add "Add Key" button to add new pairs
  - [x] Subtask 1.3: Display list of added key-value pairs with delete option

- [x] Task 2: Implement state management for manual KV pairs (AC: #2)
  - [x] Subtask 2.1: Track list of key-value pairs in ConfigurationViewModel
  - [x] Subtask 2.2: Add remove pair functionality
  - [x] Subtask 2.3: Handle empty key/value validation

- [x] Task 3: Wire TEST button to manual KV validation (AC: #3, #4)
  - [x] Subtask 3.1: Validate at least baseUri and password keys exist
  - [x] Subtask 3.2: Enable SAVE on successful validation

## Dev Notes

### Technical Context

**Prerequisite:** Story 10.1 must be complete — ConfigurationScreen UI exists and ServerConfigRepository is available.

**This Story's Scope:** Manual KV input mode. At minimum, user must provide `baseUri` and `password` keys for the configuration to be valid. Other keys may be added for extensibility (FR-VH3: "Users can add unlimited custom key-value pairs").

**Relationship to Story 10.2:** JSON URL mode and Manual KV mode are alternative input paths. The TEST button logic is shared.

### File Locations (to MODIFY)

- `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationScreen.kt` — add Manual KV mode UI
- `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationViewModel.kt` — add KV pair state management
- `app/src/main/java/com/vrpirates/rookieonquest/data/ServerConfig.kt` — may need to support flexible KV storage

### Required Keys

Minimum required for valid configuration:
- `baseUri` — server base URL for catalog downloads
- `password` — password for 7z archives

### Architecture Pattern

- Store KV pairs as `Map<String, String>` in configuration state
- Convert to `ServerConfig` when TEST passes
- Follow same validation flow as JSON URL mode

### UI Component Ideas

- Card-based display for each key-value pair
- TextField for key (hint: "Key name")
- TextField for value (hint: "Value", obscure text for password)
- IconButton to delete pair
- OutlinedButton "Add Key" at bottom
- Fresh empty fields appear after adding

## Dev Agent Record

### Agent Model Used
MiniMax-M2

### Debug Log References
N/A - Build successful with no errors

### Completion Notes List
Implemented Manual KV configuration mode:
- Added `validateManualConfig()` to `ConfigurationViewModel` — validates baseUri/password locally without network call
- Extended `testConfiguration()` to handle both `JSON_URL` and `MANUAL_KV` modes via `when` expression
- Added `IllegalArgumentException` handling in `testConfiguration()` error mapping
- Updated SAVE button to use `testedConfig` for both modes (removed duplicate config building in lambda)
- Updated TEST button enabled condition to check KV pairs in `MANUAL_KV` mode
- Case-insensitive key matching for `baseUri` and `password` (ignoreCase on `.equals()`)
- Extra keys extracted and passed to `ServerConfig`
- All acceptance criteria satisfied for AC #1-4

### File List

- UPDATE: `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationScreen.kt`
- UPDATE: `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationViewModel.kt`
- UPDATE: `app/src/main/java/com/vrpirates/rookieonquest/data/ServerConfig.kt` (was already updated in story 10.2)

### Change Log

- "Implemented Manual KV configuration mode — validateManualConfig(), testConfiguration() handles both modes, SAVE uses testedConfig for both modes (Date: 2026-04-27)"
- "Addressed code review findings — 8 patches applied (Date: 2026-04-27)"
- "Addressed re-review findings — 8 more patches applied (Date: 2026-04-27)"

### Review Findings

- [x] [Review][Patch] MEDIUM — password not trimmed before storage, but baseUri is trimmed — inconsistency [ConfigurationViewModel.kt:243]
- [x] [Review][Patch] MEDIUM — setInputMode() clears testedConfig but setJsonUrl() doesn't [ConfigurationViewModel.kt:91-101]
- [x] [Review][Patch] MEDIUM — saveJob race condition: saveJob?.cancel() before capture, state used after cancel [ConfigurationViewModel.kt:251-254] — **NOT APPLICABLE: config is explicit parameter, no state capture issue**
- [x] [Review][Patch] MEDIUM — TEST button enabled for MANUAL_KV without baseUri/password keys [ConfigurationScreen.kt:121-124]
- [x] [Review][Patch] MEDIUM — currentState captured BEFORE testJob?.cancel(), stale state risk [ConfigurationViewModel.kt:150-152]
- [x] [Review][Patch] LOW — SAVE button if/else branches identical (dead code) [ConfigurationScreen.kt:136-143]
- [x] [Review][Patch] LOW — setJsonUrl() enables SAVE for "http://" without domain [ConfigurationViewModel.kt:91-101]
- [x] [Review][Patch] LOW — Delete button hidden when only 1 pair, no reset option [ConfigurationScreen.kt:249]
- [x] [Review][Patch] BLOCKER — ensureActive() after network call (ineffective if cancelled during I/O) [ConfigurationViewModel.kt:57, 70]
- [x] [Review][Patch] HIGH — testedConfig stale after updateKeyValuePair() / setJsonUrl() — now clears testedConfig on any modification [ConfigurationViewModel.kt:122]
- [x] [Review][Patch] MEDIUM — Password whitespace bypass: raw password checked with isBlank(), then trimmed used. Space-only password passes isBlank() as false but becomes blank after trim [ConfigurationViewModel.kt:237]
- [x] [Review][Patch] MEDIUM — isSaved flag never reset after modification [ConfigurationViewModel.kt:113]
- [x] [Review][Patch] MEDIUM — baseUri.isBlank() / password.isBlank() return identical error message [ConfigurationViewModel.kt:225-229]
- [x] [Review][Patch] MEDIUM — Duplicate key collision: associate() silently keeps last value [ConfigurationViewModel.kt:250-257]

### Status

Status: review
