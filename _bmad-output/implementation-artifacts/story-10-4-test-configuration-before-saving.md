# Story 10.4: Test Configuration Before Saving

Status: ready-for-dev

## Story

As a user,
I want to test my configuration before saving it,
so that I know it works before navigating away.

## Acceptance Criteria

1. **Given** the user has entered configuration (URL or KV pairs)
   **When** the user taps the TEST button
   **Then** a loading indicator appears
   **And** the system attempts to connect to the server using the configuration

2. **Given** the configuration test succeeds
   **When** the test completes
   **Then** a success message displays "Configuration valid"
   **And** the SAVE button becomes enabled

3. **Given** the configuration test fails (connection error)
   **When** the test completes
   **Then** an error message displays "Connection failed: [specific error]"
   **And** the SAVE button remains disabled

4. **Given** the configuration test fails (timeout > 10 seconds)
   **When** the test completes
   **Then** an error message displays "Connection timeout"
   **And** the SAVE button remains disabled

## Tasks / Subtasks

- [ ] Task 1: Implement test configuration logic (AC: #1, #2, #3, #4)
  - [ ] Subtask 1.1: Create testConnection(config: ServerConfig): TestResult suspend function
  - [ ] Subtask 1.2: Implement connection test with 10-second timeout
  - [ ] Subtask 1.3: Handle connection errors with specific messages
  - [ ] Subtask 1.4: Handle timeout errors specifically

- [ ] Task 2: Implement UI loading and feedback states (AC: #1, #2, #3, #4)
  - [ ] Subtask 2.1: Add isTesting state to ConfigurationViewModel
  - [ ] Subtask 2.2: Show loading indicator during test
  - [ ] Subtask 2.3: Display success/error messages
  - [ ] Subtask 2.4: Enable/disable SAVE button based on test result

## Dev Notes

### Technical Context

**Prerequisite:** Stories 10.1, 10.2, and 10.3 should be complete or in progress.

**This Story's Scope:** The TEST button logic that validates configuration works before saving. This is the central validation point shared by both JSON URL and Manual KV input modes.

**Test Strategy:** The test should attempt to fetch a known file from the configured server to verify the configuration is valid and accessible.

### File Locations (to CREATE)
None — extend existing files.

### File Locations (to MODIFY)

- `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationViewModel.kt` — add testConnection logic
- `app/src/main/java/com/vrpirates/rookieonquest/data/ServerConfigRepository.kt` — add test method

### TestResult Sealed Class

```kotlin
sealed class TestResult {
    object Success : TestResult()
    data class ConnectionError(val message: String) : TestResult()
    data class Timeout(val seconds: Int) : TestResult()
    data class InvalidConfig(val message: String) : TestResult()
}
```

### Timeout Handling

- Use `withTimeoutOrNull` from kotlinx.coroutines.timeout
- 10-second timeout as specified in AC
- Display specific timeout error message

### Architecture Pattern

- Test runs as suspend function in viewModelScope
- Loading state prevents duplicate test requests
- Results cached until config changes

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List

- UPDATE: `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationViewModel.kt`
- UPDATE: `app/src/main/java/com/vrpirates/rookieonquest/data/ServerConfigRepository.kt`
