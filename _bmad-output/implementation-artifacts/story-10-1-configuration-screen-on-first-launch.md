# Story 10.1: Configuration Screen on First Launch

Status: review

## Story

As a user,
I want to see a configuration screen when no server is configured,
so that I can set up my server URL before browsing games.

## Acceptance Criteria

1. **Given** the app is launched for the first time (no configuration exists)
   **When** the app starts
   **Then** the configuration screen is displayed
   **And** the user cannot access the catalog until configuration is saved

2. **Given** the app is launched with existing valid configuration
   **When** the app starts
   **Then** the catalog loads automatically
   **And** the configuration screen is NOT shown

3. **Given** the app is launched with no configuration
   **When** the user views the configuration screen
   **Then** the legal disclaimer is visible
   **And** the user is informed they are responsible for the server they configure

## Tasks / Subtasks

- [x] Task 1: Implement server configuration storage (AC: #1, #2)
  - [x] Subtask 1.1: Create `ServerConfig` data class with `baseUri` and `password` fields
  - [x] Subtask 1.2: Add SharedPreferences storage for server configuration
  - [x] Subtask 1.3: Create `ServerConfigRepository` to manage config persistence
  - [x] Subtask 1.4: Add validation method to check if config exists and is valid

- [x] Task 2: Implement configuration screen UI (AC: #1, #3)
  - [x] Subtask 2.1: Create `ConfigurationScreen` composable with legal disclaimer
  - [x] Subtask 2.2: Add JSON URL input mode with text field
  - [x] Subtask 2.3: Add Manual KV pair input mode with dynamic form
  - [x] Subtask 2.4: Add TEST and SAVE buttons
  - [x] Subtask 2.5: Display legal disclaimer text

- [x] Task 3: Implement app startup flow (AC: #1, #2)
  - [x] Subtask 3.1: Modify `MainActivity` to check for existing valid config on start
  - [x] Subtask 3.2: Route to configuration screen if no valid config exists
  - [x] Subtask 3.3: Route to catalog screen if valid config exists

- [x] Task 4: Add ConfigurationViewModel (AC: all)
  - [x] Subtask 4.1: Create `ConfigurationViewModel` with state management
  - [x] Subtask 4.2: Implement `testConfiguration()` suspend function
  - [x] Subtask 4.3: Implement `saveConfiguration()` function
  - [x] Subtask 4.4: Handle loading, error, and success states

## Dev Notes

### Technical Context

**Epic 1 Overview:** This story begins the Server Configuration System — replacing all hardcoded server values with a user-configurable KV pairs system. Stories 10.2-10.6 will extend this to support JSON URL mode, manual KV mode, TEST button, and Settings access.

**This Story's Scope:** Only the bootstrap logic — detecting missing config and showing the configuration screen. JSON URL parsing, manual KV entry, and TEST functionality are handled in Stories 1.2-1.4.

### File Locations (to CREATE)

- `app/src/main/java/com/vrpirates/rookieonquest/data/ServerConfig.kt` — data class
- `app/src/main/java/com/vrpirates/rookieonquest/data/ServerConfigRepository.kt` — SharedPreferences wrapper
- `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationScreen.kt` — UI composable
- `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationViewModel.kt` — state management

### File Locations (to MODIFY)

- `app/src/main/java/com/vrpirates/rookieonquest/MainActivity.kt` — add startup config check
- `app/src/main/java/com/vrpirates/rookieonquest/MainViewModel.kt` — may need state for config exists check
- `app/src/main/java/com/vrpirates/rookieonquest/ui/theme/Theme.kt` — if custom colors needed for config screen

### Legal Disclaimer Text

From Epic 1 Story 1.7 (for reference):
> "VRHub is a neutral tool for managing VR game installations. The app does not provide, host, or endorse any game content. You are solely responsible for the servers you configure and the content you access through them."

### Architecture Pattern

Follow existing MVVM pattern:
- `ConfigurationViewModel` manages UI state via `StateFlow`
- `ServerConfigRepository` handles persistence via SharedPreferences
- UI in Compose with Material 3 components
- Use existing `RookieOnQuestTheme` for consistent styling

### Project Structure Notes

- Package: `com.vrpirates.rookieonquest`
- Existing data layer at `data/` — follow same structure
- Existing UI layer at `ui/` — follow same structure
- ViewModel scoped to configuration screen only (no shared state with MainViewModel yet)

### Key Dependencies

- Jetpack Compose for UI
- ViewModel + StateFlow for state management
- SharedPreferences for simple key-value storage (no Room needed for config)

### Testing Standards

- Unit tests for `ServerConfigRepository` config save/load logic
- Unit tests for `ConfigurationViewModel` state transitions
- UI screenshot test for configuration screen rendering with disclaimer

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

- Created ServerConfig data class with baseUri, password, and extraKeys fields
- Created ServerConfigRepository with SharedPreferences storage and hasValidConfig() check
- Created ConfigurationViewModel with state management for UI
- Created ConfigurationScreen with legal disclaimer, tabbed input mode (JSON URL / Manual KV), and action buttons
- Created MainScreenWrapper in MainActivity that checks hasValidConfig() and routes accordingly
- Build successful with only minor warning (unused variable)

### File List

- NEW: `app/src/main/java/com/vrpirates/rookieonquest/data/ServerConfig.kt`
- NEW: `app/src/main/java/com/vrpirates/rookieonquest/data/ServerConfigRepository.kt`
- NEW: `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationScreen.kt`
- NEW: `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationViewModel.kt`
- UPDATE: `app/src/main/java/com/vrpirates/rookieonquest/MainActivity.kt`

## Change Log

- 2026-04-27: Initial implementation — server config storage, configuration screen UI, startup routing

## Review Findings

- [x] [Review][Defer] **Placeholder password invalide le système de validation** — deferred: bridge intentionnel, password validé en 10.4 (TEST button). `"pending_validation"` est un placeholder acceptable pour 10.1.

- [x] [Review][Defer] **SAVE sans validation en mode JSON URL** — deferred: pas de validation requise dans l'AC de 10.1. Validation viendra avec 10.4.

- [x] [Review][Patch] **Aucune validation d'URL JSON** [`ConfigurationViewModel.kt:79-88`] — ajout validation `http://`/`https://` dans `setJsonUrl()`.

- [x] [Review][Patch] **isSaveEnabled non réinitialisé après SAVE** [`ConfigurationViewModel.kt:135-152`] — `isSaveEnabled = false` ajouté dans les blocs success et catch.

- [x] [Review][Patch] **Suppression concurrent index dans kvPairs** [`ConfigurationViewModel.kt:116-121`] — déjà safe (utilise `toMutableList()`). Aucun changement nécessaire.

- [x] [Review][Patch] **Clé/valeur vide en mode KV peut bypasser validation** [`ConfigurationScreen.kt:143-150`] — ajout guard `if (baseUri.isBlank() || password.isBlank()) return@Button` dans le build config MANUAL_KV.

- [x] [Review][Defer] **TEST button non implémenté** [`ConfigurationScreen.kt:121`] — deferred, TEST button est explicitement scope story 10.4, pas 10.1
