# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**VRHub** is a standalone Android application for Meta Quest VR headsets that allows users to browse, download, and install VR games from custom server configurations. The app is built with **Kotlin** and **Jetpack Compose** and serves as a neutral client — users configure their own server, the app does not host or provide any content.

**Branding:** "A Solstice Project"

**Epic/Story Workflow:** This project uses BMad methodology. Use `bmad-help` for navigation. Current active Epic: **Epic 1: Server Configuration System** (7 stories, see `_bmad-output/planning-artifacts/epics.md`).

## Architecture

### Server Configuration System (Epic 1)

The app replaces all VRPirates hardcoded values with a user-configurable KV pairs system:

- **JSON URL mode:** User provides a URL pointing to a JSON config file (`{"baseUri": "...", "password": "..."}`)
- **Manual KV mode:** User enters key-value pairs manually
- **TEST button:** Validates config before saving (connection test, JSON structure check)
- **Storage:** SharedPreferences or Room for persistence
- **FR-VH1 to FR-VH13** cover all configuration requirements

### Data Flow
```
UI (MainActivity/Compose)
  ↓
MainViewModel (state management, events)
  ↓
MainRepository (network, storage, installation logic)
  ↓
[Room Database, OkHttp, Retrofit, 7z extraction]
```

## Build & Development Commands

### Building the Project
```bash
# Clean the project
gradlew.bat clean
# or
make clean

# Build debug APK
gradlew.bat assembleDebug
# or
make build

# Build release APK (requires keystore.properties)
gradlew.bat assembleRelease
# or
make release
```

### Version Management
```bash
# Update version number and changelog (Windows)
make set-version V=x.x.x
```

### Installation
```bash
# Install APK to connected device via ADB
adb install -r app/build/outputs/apk/release/RookieOnQuest-vX.X.X.apk
# or
make install
```

### Secrets Configuration
**ROOKIE_UPDATE_SECRET** - Required for release builds to authenticate with the secure update gateway:

- **Local Development**: Add to `local.properties`:
  ```
  ROOKIE_UPDATE_SECRET=your_secret_here
  ```

- **CI/CD**: Set as GitHub repository secret (`ROOKIE_UPDATE_SECRET`)

- **Build Command**:
  ```bash
  # With Gradle property
  gradlew.bat assembleRelease -PROOKIE_UPDATE_SECRET=your_secret
  ```

**Build Command (release):**
```bash
gradlew.bat assembleRelease -PROOKIE_UPDATE_SECRET=xxx
```

### Release Workflow
```bash
# Full release process (Windows):
make set-version V=x.x.x   # Update version
make release               # Build APK
make commit               # Commit changes
make tag                  # Create git tag
make push                 # Push to GitHub
make gh-release           # Create GitHub release with APK
```

## Architecture

### Data Flow
```
UI (MainActivity/Compose)
  ↓
MainViewModel (state management, events)
  ↓
MainRepository (network, storage, installation logic)
  ↓
[Room Database, OkHttp, Retrofit, 7z extraction]
```

### Key Components

**MainActivity.kt** (1400+ lines)
- Single-activity Compose UI with all screens and overlays
- Permission flow, update dialogs, installation queue UI
- Uses `MainViewModel` for state and `MainEvent` for side effects

**MainViewModel.kt** (1100+ lines)
- Manages all application state via `StateFlow`
- Queue processing with coroutines (pause/resume/cancel/promote)
- Background metadata fetching with prioritization
- Permission checking, app updates, diagnostics export

**MainRepository.kt** (1000+ lines)
- **syncCatalog()**: Fetches catalog from user-configured server URL, parses game list, extracts thumbnails/icons
- **installGame()**: Multi-phase installation:
  1. Verify files with server (ground truth)
  2. Pre-flight storage check using `StatFs`
  3. Download with HTTP Range resumption
  4. Extract 7z archives with password from config
  5. Handle special install instructions via `install.txt` parsing
  6. Move OBB files to `/Android/obb/{packageName}/`
  7. Launch APK installer via `FileProvider`
- **verifyAndCleanupInstalls()**: Post-install cleanup of temp files
- Uses MD5 hashing for file/directory naming consistency
- Server URL and password come from user configuration (Stories 1.2-1.6)

**CatalogParser.kt**
- Parses semicolon-delimited game catalog format:
  `GameName;ReleaseName;PackageName;VersionCode;SizeBytes;Popularity`

### State Management Patterns

**Queue Management:**
- `_installQueue: MutableStateFlow<List<InstallTaskState>>`
- Queue processor runs as a coroutine job
- Only one task processes at a time (first in queue with `QUEUED` status)
- Task promotion moves a task to the front and pauses current active task
- Cancellation immediately kills the task job and cleans up temp files

**Metadata Fetching:**
- Priority-based system triggered by visible indices and search
- Runs in background loop (`startMetadataFetchLoop()`)
- Fetches sizes/descriptions/screenshots from mirror on-demand
- Uses `priorityUpdateChannel` for immediate refresh when UI state changes

**Permission Flow:**
- Three required permissions: `INSTALL_UNKNOWN_APPS`, `MANAGE_EXTERNAL_STORAGE`, `IGNORE_BATTERY_OPTIMIZATIONS`
- Sequential flow: request first permission → user grants → check again → request next
- Tracks state with `isPermissionFlowActive` to avoid infinite loops

### Special Installation Handling

**install.txt Parser:**
Games with non-standard data layouts (e.g., Quake3Quest) include an `install.txt` with `adb push` commands. The app parses these and executes the file moves to the correct locations on `/sdcard/`.

**OBB Handling:**
- Standard: Files in folder named `{packageName}` → moved to `/Android/obb/{packageName}/`
- Loose: `.obb` files at root → moved to `/Android/obb/{packageName}/`
- Both paths supported simultaneously

**7z Multi-part Archives:**
- Files like `game.7z.001`, `game.7z.002` are merged in sorted order
- Password from `PublicConfig` (Base64 decoded)
- Extraction preserves entire directory structure

### File Locations

- **Catalog cache:** `context.filesDir/VRP-GameList.txt`
- **Icons:** `context.filesDir/icons/`
- **Thumbnails:** `context.filesDir/thumbnails/`
- **Notes:** `context.filesDir/notes/` (game descriptions)
- **Temp install:** `context.cacheDir/install_temp/{md5hash}/`
- **Downloads:** `/sdcard/Download/RookieOnQuest/{safeReleaseName}/`
- **Logs:** `/sdcard/Download/RookieOnQuest/logs/`

### Database Schema

**Room Database** (`AppDatabase.kt`):
- Single table: `GameEntity`
- Fields: `gameName`, `releaseName` (primary key), `packageName`, `versionCode`, `sizeBytes`, `description`, `screenshotUrls`, `isFavorite`, `lastUpdated`, `popularity`

## Important Implementation Details

### Version Comparison
- Uses `versionCode` (Long) to compare catalog version vs installed version
- Supports API 28+ with fallback for older `versionCode.toLong()`

### Storage Space Checking
Pre-flight check before download/extraction:
- 7z archives require 2.9x space if keeping APK, 1.9x if not
- Non-archived requires 1.1x space
- Uses `StatFs` for real-time available space calculation

### Cancellation Safety
All long-running operations use:
```kotlin
currentCoroutineContext().ensureActive()
```
This ensures downloads/extractions can be interrupted cleanly when tasks are paused or cancelled.

### Update System
- Checks GitHub API for latest release on startup
- Compares semantic versions (strips "v" prefix)
- Downloads APK, installs via `FileProvider`
- Blocks back button during mandatory updates

## Coding Conventions

### Commit Message Format
Follow [Conventional Commits](https://www.conventionalcommits.org/):
- `feat:` - New features
- `fix:` - Bug fixes
- `docs:` - Documentation changes
- `style:` - UI/formatting changes (no logic)
- `refactor:` - Code restructuring
- `perf:` - Performance improvements
- `chore:` - Maintenance tasks

### Code Style
- Use `StateFlow` for reactive state
- Use `MutableSharedFlow` for one-time events
- Prefer `withContext(Dispatchers.IO)` for blocking I/O
- Use `suspend` functions for cancellable operations
- Log errors with `Log.e(TAG, "message", exception)`

## Testing

The project uses JUnit for unit tests and AndroidX Test for instrumented tests.
- Place unit tests in `app/src/test/`
- Place instrumented tests in `app/src/androidTest/`
- Use `./gradlew test` for unit tests
- Use `./gradlew connectedAndroidTest` for instrumented tests

## Key Dependencies

- **Jetpack Compose** - UI framework
- **Room** - Local database
- **Retrofit + OkHttp** - Network requests
- **Gson** - JSON parsing
- **Apache Commons Compress** - 7z extraction
- **Coil** - Image loading

## Critical Warnings

1. **Server configuration is user-provided** — app validates structure but not content; user bears full responsibility for configured server
2. **Never modify catalog parsing without testing against real server format** (structure may vary)
3. **Always use MD5 hash for directory names** to match server structure
4. **Storage space checks must account for extraction overhead**
5. **Queue processor must handle cancellation at every suspension point**
6. **APK version verification uses `PackageManager.getPackageArchiveInfo()`**
7. **File moves to `/Android/obb/` may fail silently on some Android versions**

## Common Debugging Scenarios

**Installation fails silently:**
- Check `install.info` marker in temp directory
- Verify `FileProvider` authority matches `${context.packageName}.fileprovider`
- Confirm OBB files reached `/Android/obb/{packageName}/`

**Extraction fails:**
- Verify password decoding from Base64
- Check 7z part files are sorted correctly before merge
- Ensure `extraction_done.marker` is created only after success

**Queue processor stops:**
- Check for uncaught exceptions in `runTask()`
- Verify `queueProcessorJob?.isActive` before starting new processor
- Ensure task status transitions are atomic

## Platform-Specific Notes

- **API Level:** Min SDK 29 (Android 10), Target SDK 34
- **Permissions:** `MANAGE_EXTERNAL_STORAGE` required for Quest sideloading
- **VR Optimization:** Wide screen support (800dp+) uses 3-column staggered grid
- **Background Downloads:** Requires battery optimization exemption for reliability
