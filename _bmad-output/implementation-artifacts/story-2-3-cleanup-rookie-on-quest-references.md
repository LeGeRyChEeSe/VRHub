# Story 2.3: Cleanup Rookie On Quest References

Status: done

## Story

As a user,
I want the app to display "VRHub" everywhere and use VRHub paths,
So that there is no confusion with the old "Rookie On Quest" identity.

## Acceptance Criteria

1. **Given** the user views permission dialogs
   **When** the permission text is displayed
   **Then** the app name is "VRHub" (not "Rookie On Quest")

2. **Given** the user views Settings > About
   **When** the screen loads
   **Then** no "Rookie On Quest" text appears

3. **Given** the app saves files to Downloads
   **When** files are saved
   **Then** the path is `/sdcard/Download/VRHub/` (not `RookieOnQuest`)

4. **Given** the app exports logs
   **When** the user exports diagnostics
   **Then** the log path displayed references "VRHub" folder

5. **Given** the app writes wake lock tags
   **When** extraction runs
   **Then** the wake lock tag is "VRHub:Extraction" (not "RookieOnQuest:Extraction")

## Tasks / Subtasks

- [x] Task 1: Update permission strings in strings.xml
  - [x] Subtask 1.1: `perm_install_desc` → replace "Rookie On Quest" with "VRHub"
  - [x] Subtask 1.2: `perm_battery_desc` → replace "Rookie On Quest" with "VRHub"
  - [x] Subtask 1.3: `perm_overlay_subtitle` → replace "Rookie On Quest" with "VRHub"

- [x] Task 2: Update download folder path constant
  - [x] Subtask 2.1: `DOWNLOADS_ROOT_DIR_NAME` = "RookieOnQuest" → "VRHub" in Constants.kt
  - [x] Subtask 2.2: Update all user-facing messages referencing Download/RookieOnQuest (MainActivity.kt, MainViewModel.kt)
  - [x] Subtask 2.3: Update WAKE_LOCK_TAG in WakeLockManager.kt to "VRHub:Extraction"

- [x] Task 3: Update code identifiers (Theme, RookieOnQuestTheme → VRHubTheme)
  - [x] Subtask 3.1: `Theme.RookieOnQuest` → `Theme.VRHub` in themes.xml and AndroidManifest.xml
  - [x] Subtask 3.2: `RookieOnQuestTheme` → `VRHubTheme` in Theme.kt
  - [x] Subtask 3.3: Update all imports in MainActivity.kt and test files

## Dev Notes

### Technical Context

**Files to MODIFY:**

| File | Changes |
|------|---------|
| `app/src/main/res/values/strings.xml` | 3 permission strings (lines 22, 24, 45) |
| `app/src/main/java/com/vrhub/data/Constants.kt` | `DOWNLOADS_ROOT_DIR_NAME` (line 385) |
| `app/src/main/java/com/vrhub/MainActivity.kt` | "Download/RookieOnQuest" messages (lines 1733, 1816) |
| `app/src/main/java/com/vrhub/ui/MainViewModel.kt` | "Download/RookieOnQuest" log message (line 3578) |
| `app/src/main/java/com/vrhub/data/WakeLockManager.kt` | `WAKE_LOCK_TAG` (line 38) |
| `app/src/main/res/values/themes.xml` | `Theme.RookieOnQuest` → `Theme.VRHub` (line 3) |
| `app/src/main/AndroidManifest.xml` | `Theme.RookieOnQuest` → `Theme.VRHub` (lines 30, 46) |
| `app/src/main/java/com/vrhub/ui/theme/Theme.kt` | `RookieOnQuestTheme` → `VRHubTheme` (line 80) |
| `app/src/main/java/com/vrhub/MainActivity.kt` | Import and usage of `RookieOnQuestTheme` (lines 76, 85) |
| `app/src/androidTest/java/com/vrpirates/rookieonquest/ui/QueueUITest.kt` | Test file imports and usage |

**Strings to UPDATE:**
```
strings.xml:
- "Allow Rookie On Quest to install game files" → "Allow VRHub to install game files"
- "Allow Rookie On Quest to run in the background" → "Allow VRHub to run in the background"
- "Rookie On Quest needs some permissions" → "VRHub needs some permissions"

Constants.kt:
- DOWNLOADS_ROOT_DIR_NAME = "RookieOnQuest" → "VRHub"

WakeLockManager.kt:
- WAKE_LOCK_TAG = "RookieOnQuest:Extraction" → "VRHub:Extraction"
```

### Code Identifier Renames (internal only):

```
themes.xml, AndroidManifest.xml:
- Theme.RookieOnQuest → Theme.VRHub

Theme.kt, MainActivity.kt, QueueUITest.kt:
- RookieOnQuestTheme → VRHubTheme
```

### Breaking Change Warning

**DOWNLOADS_ROOT_DIR_NAME change is a breaking change for existing users:**
- Old path: `/sdcard/Download/RookieOnQuest/`
- New path: `/sdcard/Download/VRHub/`
- Users who have already downloaded games will need to manually move their files OR the app should detect both paths

**Mitigation options (choose one):**
1. **Hard cutover** — New path only. Existing downloads in old path are orphaned. Simple but breaks existing installs.
2. **Soft cutover** — App checks both paths. New downloads go to VRHub. Old path still accessible for reading. More complex but user-friendly.

### Branding Requirements

- App display name: VRHub
- Tagline: "A Solstice Project" (already implemented in story 2-2)
- No mention of VRPirates in any user-facing text
- No mention of "Rookie On Quest" in any user-facing text

## Review Findings

- [x] [Review][Patch] Additional permission strings still say "Rookie" [strings.xml:49,55,77] — fixed
- [x] [Review][Patch] Constants.kt comment still references old path [Constants.kt:383] — fixed
- [x] [Review][Patch] MainRepository.kt comment still references old path [MainRepository.kt:2139] — fixed
- [x] [Review][Patch] Export header hardcodes "ROOKIE ON QUEST" [MainRepository.kt:2186] — fixed
- [x] [Review][Patch] UI title hardcodes "ROOKIE ON QUEST" [MainActivity.kt:1098] — fixed
- [x] [Review][Patch] Clipboard label uses "Rookie Logs" not "VRHub Logs" [MainActivity.kt:234] — fixed

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

**Implemented changes:**

1. **strings.xml** - Updated 3 permission strings:
   - `perm_install_desc`: "Rookie On Quest" → "VRHub"
   - `perm_battery_desc`: "Rookie On Quest" → "VRHub"
   - `perm_overlay_subtitle`: "Rookie On Quest" → "VRHub"

2. **Constants.kt** - Changed `DOWNLOADS_ROOT_DIR_NAME` from "RookieOnQuest" to "VRHub"

3. **WakeLockManager.kt** - Changed `WAKE_LOCK_TAG` from "RookieOnQuest:Extraction" to "VRHub:Extraction"

4. **themes.xml** - Renamed style from `Theme.RookieOnQuest` to `Theme.VRHub`

5. **AndroidManifest.xml** - Updated theme references from `Theme.RookieOnQuest` to `Theme.VRHub` (2 locations)

6. **Theme.kt** - Renamed composable function from `RookieOnQuestTheme` to `VRHubTheme`

7. **MainActivity.kt** - Updated import and usage from `RookieOnQuestTheme` to `VRHubTheme`, updated 2 user-facing messages

8. **MainViewModel.kt** - Updated log message path

9. **QueueUITest.kt** (Android instrumented test) - Updated import and all usages from `RookieOnQuestTheme` to `VRHubTheme`

**Build verification:** `compileDebugKotlin` passed successfully.

### File List

- `app/src/main/res/values/strings.xml` - Modified (3 string changes)
- `app/src/main/java/com/vrhub/data/Constants.kt` - Modified (`DOWNLOADS_ROOT_DIR_NAME` constant)
- `app/src/main/java/com/vrhub/data/WakeLockManager.kt` - Modified (`WAKE_LOCK_TAG` constant)
- `app/src/main/res/values/themes.xml` - Modified (theme name)
- `app/src/main/AndroidManifest.xml` - Modified (2 theme references)
- `app/src/main/java/com/vrhub/ui/theme/Theme.kt` - Modified (composable function name)
- `app/src/main/java/com/vrhub/MainActivity.kt` - Modified (import, theme usage, 2 messages)
- `app/src/main/java/com/vrhub/ui/MainViewModel.kt` - Modified (1 log message)
- `app/src/androidTest/java/com/vrpirates/rookieonquest/ui/QueueUITest.kt` - Modified (import and usages)

### Change Log

- 2026-04-28: Addressed story 2.3 review findings - All "Rookie On Quest" / "RookieOnQuest" references replaced with "VRHub" / "VRHub" across permission strings, download paths, wake lock tags, and theme identifiers.

