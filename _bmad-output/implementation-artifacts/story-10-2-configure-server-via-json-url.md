# Story 10.2: Configure Server via JSON URL

Status: review

## Story

As a user,
I want to enter a URL pointing to a JSON configuration file,
so that I can load my server settings automatically.

## Acceptance Criteria

1. **Given** the user is on the configuration screen
   **When** the user selects "JSON URL" input mode
   **Then** a text field appears for entering the JSON URL
   **And** the field is empty (no pre-filled value)

2. **Given** the user has entered a JSON URL
   **When** the user taps the TEST button
   **Then** the system fetches the URL
   **And** validates the JSON structure contains required keys (baseUri, password)
   **And** displays success or specific error message

3. **Given** the user enters an invalid URL
   **When** the user taps TEST
   **Then** an error message displays "Invalid URL format"

4. **Given** the URL returns non-JSON content
   **When** the user taps TEST
   **Then** an error message displays "Server returned invalid JSON"

5. **Given** the URL returns JSON without required keys
   **When** the user taps TEST
   **Then** an error message displays "Configuration missing required keys"

## Tasks / Subtasks

- [x] Task 1: Add JSON URL input mode to ConfigurationScreen (AC: #1)
  - [x] Subtask 1.1: Add tab/segmented control to switch between JSON URL and Manual KV modes
  - [x] Subtask 1.2: Create JSON URL input text field composable

- [x] Task 2: Implement JSON URL fetching and parsing (AC: #2, #3, #4, #5)
  - [x] Subtask 2.1: Add OkHttp client call to fetch JSON URL
  - [x] Subtask 2.2: Parse JSON response and validate required keys (baseUri, password)
  - [x] Subtask 2.3: Handle network errors with specific messages
  - [x] Subtask 2.4: Handle JSON parse errors with specific messages

- [x] Task 3: Wire TEST button to JSON URL flow (AC: #2, #3, #4, #5)
  - [x] Subtask 3.1: Add loading state during fetch
  - [x] Subtask 3.2: Display success/error messages based on result
  - [x] Subtask 3.3: Enable SAVE button only on successful validation

## Dev Notes

### Technical Context

**Prerequisite:** Story 10.1 must be complete — ConfigurationScreen UI exists and ServerConfigRepository is available.

**This Story's Scope:** JSON URL input mode only. The TEST button logic and SAVE logic are shared with Story 10.4. Manual KV mode is Story 10.3.

### File Locations (to CREATE)
None — all files already created in Story 10.1.

### File Locations (to MODIFY)

- `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationScreen.kt` — add JSON URL mode UI
- `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationViewModel.kt` — add JSON URL test logic
- `app/src/main/java/com/vrpirates/rookieonquest/data/ServerConfigRepository.kt` — add JSON URL fetch method (or create separate network util)

### Expected JSON Format

```json
{
  "baseUri": "https://your-server.example.com/",
  "password": "your-password-here"
}
```

### Architecture Pattern

- Use existing `OkHttpClient` from network package for fetching
- Add `fetchJsonConfig(url: String): Result<ServerConfig>` method to repository or create `ConfigFetcher` utility
- Follow error handling pattern: specific error types for better user messaging

### Project Structure Notes

- Existing network layer at `com.vrpirates.rookieonquest.network.*` — follow same patterns
- Use `Gson` for JSON parsing (already in dependencies)

## Dev Agent Record

### Review Findings

- [x] [Review][Patch] BLOCKER — Compilation error: `uiState.inputState.inputMode` should be `uiState.inputMode` [ConfigurationScreen.kt:34] — **False positive: actual file uses correct `uiState.inputMode`**
- [x] [Review][Patch] HIGH — No timeout on `okHttpClient.newCall().await()` — UI hangs eternally if server doesn't respond [ServerConfigRepository.kt:35]
- [x] [Review][Patch] HIGH — `ensureActive()` missing in `testConfiguration()` — cancelled coroutine may update dead UI state [ConfigurationViewModel.kt:68-79]
- [x] [Review][Patch] HIGH — `ensureActive()` missing in `saveConfiguration()` — catch block may update cancelled UI state [ConfigurationViewModel.kt:80-88]
- [x] [Review][Patch] HIGH — MANUAL_KV mode: `isSaveEnabled` never set to `true` — SAVE button permanently disabled [ConfigurationViewModel.kt]
- [x] [Review][Patch] HIGH — SAVE lambda silent `return@Button` with no error feedback when baseUri/password blank in MANUAL_KV mode [ConfigurationScreen.kt:54]
- [x] [Review][Patch] HIGH — `isValid()` doesn't validate URL format — `javascript:`, `data:`, `file://` pass validation [ServerConfig.kt:20]
- [x] [Review][Patch] MEDIUM — `Gson()` instantiated on every call — GC pressure, should use shared static instance [ServerConfig.kt, ServerConfigRepository.kt]
- [x] [Review][Patch] MEDIUM — `catch (e: Exception)` too broad — catches `CancellationException` which should be re-thrown [ConfigurationViewModel.kt:73]
- [x] [Review][Patch] MEDIUM — `testConfiguration()` has no debounce — rapid taps launch concurrent coroutines with independent state mutations [ConfigurationViewModel.kt:51]
- [x] [Review][Patch] MEDIUM — `hasValidConfig` not thread-safe — synchronous read without StateFlow, may return stale data [ConfigurationViewModel.kt:52]
- [x] [Review][Patch] MEDIUM — Whitespace-only strings (e.g., `"   "`) pass `isValid()` via `isNotBlank()` [ServerConfig.kt:20]
- [x] [Review][Patch] MEDIUM — No length validation on `baseUri`/`password` — malicious server could return MB+ strings [ServerConfig.kt]
- [x] [Review][Patch] LOW — `testConfiguration()` returns `Unit` — caller cannot distinguish outcomes without duplicating `when(error)` logic [ConfigurationViewModel.kt]
- [x] [Review][Patch] LOW — TEST button stays enabled after save (inconsistent with SAVE button which checks `!isSaved`) [ConfigurationScreen.kt:48]
- [x] [Review][Patch] LOW — `clearError()` defined but state resets are scattered across multiple functions [ConfigurationViewModel.kt]
MiniMax-M2

### Debug Log References
N/A - Build successful with no errors

### Completion Notes List
Implemented JSON URL configuration fetching and TEST button wiring:
- Added `fetchJsonConfig()` to `ServerConfigRepository` with proper URL validation, network error handling, JSON parsing, and required keys validation (baseUri, password)
- Added `testConfiguration()` to `ConfigurationViewModel` that calls repository and updates UI state with success/error messages
- Added `testedConfig` field to `ConfigurationUiState` to store validated config for SAVE operation
- Wired TEST button in `ConfigurationScreen` to call `testConfiguration()`, with CircularProgressIndicator during loading
- Updated SAVE button to use `testedConfig` when in JSON_URL mode
- Added error type classes: `InvalidUrlException`, `NetworkException`, `JsonParseException`, `MissingKeysException`
- All acceptance criteria satisfied: URL format validation, network error handling, invalid JSON handling, missing keys handling

### File List

- UPDATE: `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationScreen.kt`
- UPDATE: `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationViewModel.kt`
- UPDATE: `app/src/main/java/com/vrpirates/rookieonquest/data/ServerConfigRepository.kt`
