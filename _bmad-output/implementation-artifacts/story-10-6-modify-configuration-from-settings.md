# Story 10.6: Modify Configuration from Settings

Status: ready-for-dev

## Story

As a user,
I want to modify my server configuration from Settings,
so that I can switch to a different server if needed.

## Acceptance Criteria

1. **Given** the user is viewing the catalog with a valid configuration
   **When** the user opens Settings
   **Then** a "Server Configuration" option is visible

2. **Given** the user taps "Server Configuration" in Settings
   **When** the configuration popup opens
   **Then** the current saved configuration is displayed
   **And** the user can modify it (change URL or KV pairs)
   **And** the TEST button is available

3. **Given** the user modifies the configuration
   **When** the user taps SAVE after successful TEST
   **Then** the old configuration is replaced
   **And** the catalog refreshes using the new configuration

## Tasks / Subtasks

- [ ] Task 1: Add Server Configuration option to Settings screen (AC: #1)
  - [ ] Subtask 1.1: Create or modify SettingsScreen composable
  - [ ] Subtask 1.2: Add "Server Configuration" menu item

- [ ] Task 2: Open config with existing values pre-filled (AC: #2)
  - [ ] Subtask 2.1: Load existing config when opening ConfigurationScreen from Settings
  - [ ] Subtask 2.2: Pre-fill JSON URL or KV pairs based on existing config type
  - [ ] Subtask 2.3: Show current values for editing

- [ ] Task 3: Refresh catalog after config change (AC: #3)
  - [ ] Subtask 3.1: Trigger catalog refresh after successful SAVE
  - [ ] Subtask 3.2: Show loading indicator during refresh

## Dev Notes

### Technical Context

**Prerequisite:** Stories 10.1-10.5 should be complete.

**This Story's Scope:** Adding Settings access to configuration. The ConfigurationScreen is reused but opened with existing values pre-filled for editing.

**Settings Screen:** If Settings screen doesn't exist yet, create a simple SettingsScreen with Server Configuration option. If it exists, add the option to it.

### File Locations (to CREATE)

- `app/src/main/java/com/vrpirates/rookieonquest/ui/SettingsScreen.kt` — if not exists

### File Locations (to MODIFY)

- `app/src/main/java/com/vrpirates/rookieonquest/ui/SettingsScreen.kt` — add Server Configuration option
- `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationScreen.kt` — add pre-fill mode
- `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationViewModel.kt` — add pre-fill logic

### Pre-fill Mode

- Add `isEditing: Boolean` parameter to ConfigurationScreen/ViewModel
- If editing, load current config and display values
- On SAVE, update existing config instead of creating new

### Architecture Pattern

- Follow existing Settings patterns if SettingsScreen exists
- Reuse ConfigurationScreen for both first-launch and settings editing
- Use same ViewModel but with different initial state

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List

- NEW: `app/src/main/java/com/vrpirates/rookieonquest/ui/SettingsScreen.kt` (if needed)
- UPDATE: `app/src/main/java/com/vrpirates/rookieonquest/ui/SettingsScreen.kt`
- UPDATE: `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationScreen.kt`
- UPDATE: `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationViewModel.kt`
