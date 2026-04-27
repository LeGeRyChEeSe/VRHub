# Story 10.5: Save and Persist Configuration

Status: ready-for-dev

## Story

As a user,
I want my configuration to be saved and persisted,
so that I don't have to re-enter it every time I open the app.

## Acceptance Criteria

1. **Given** the user has a valid configuration (TEST passed)
   **When** the user taps SAVE
   **Then** the configuration is stored locally
   **And** the app navigates to the catalog screen
   **And** the catalog begins loading

2. **Given** the app is restarted
   **When** the user opens the app
   **Then** the saved configuration is loaded
   **And** the catalog loads automatically

## Tasks / Subtasks

- [ ] Task 1: Implement save configuration functionality (AC: #1)
  - [ ] Subtask 1.1: Add saveConfiguration(config: ServerConfig) to ServerConfigRepository
  - [ ] Subtask 1.2: Implement navigation to catalog screen after save
  - [ ] Subtask 1.3: Trigger catalog load after navigation

- [ ] Task 2: Implement configuration loading on app start (AC: #2)
  - [ ] Subtask 2.1: Add loadConfiguration(): ServerConfig? to ServerConfigRepository
  - [ ] Subtask 2.2: Check for existing valid config in MainActivity startup
  - [ ] Subtask 2.3: Route to catalog with auto-load if config exists

## Dev Notes

### Technical Context

**Prerequisite:** Stories 10.1-10.4 should be complete or in progress.

**This Story's Scope:** The persistence layer and app startup routing. After SAVE, config is stored in SharedPreferences. On app launch, config is loaded and if valid, catalog loads automatically.

**Relationship to Epic 1 Stories:** This story completes the "bootstrap + TEST + SAVE" flow started in Stories 10.1-10.4.

### File Locations (to MODIFY)

- `app/src/main/java/com/vrpirates/rookieonquest/data/ServerConfigRepository.kt` — add save/load methods
- `app/src/main/java/com/vrpirates/rookieonquest/MainActivity.kt` — add startup routing logic
- `app/src/main/java/com/vrpirates/rookieonquest/MainViewModel.kt` — may need method to trigger catalog load

### Storage Strategy

- Use SharedPreferences for simple KV storage
- Store as JSON string for ServerConfig object
- Key: `server_config` (or similar)

### Startup Flow

```
App Start
  → Check ServerConfigRepository.hasValidConfig()
  → If YES: Load config → Start catalog sync → Show catalog
  → If NO: Show ConfigurationScreen
```

### Navigation

- After SAVE, navigate from ConfigurationScreen to MainScreen (catalog)
- May need Compose Navigation or simple conditional composable rendering

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List

- UPDATE: `app/src/main/java/com/vrpirates/rookieonquest/data/ServerConfigRepository.kt`
- UPDATE: `app/src/main/java/com/vrpirates/rookieonquest/MainActivity.kt`
- UPDATE: `app/src/main/java/com/vrpirates/rookieonquest/MainViewModel.kt`
