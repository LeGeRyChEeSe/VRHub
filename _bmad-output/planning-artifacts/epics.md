---
stepsCompleted: ['step-01-validate-prerequisites', 'step-02-design-epics', 'step-03-create-stories', 'step-04-final-validation']
inputDocuments:
  - '_bmad-output/brainstorming/brainstorming-session-2026-04-27-000000.md'
  - '_bmad-output/planning-artifacts/prd.md'
---

# VRHub - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for VRHub (formerly Rookie On Quest), a Meta Quest VR application for browsing and installing VR games from custom server configurations.

## Requirements Inventory

### Functional Requirements

**Core Configuration (New for VRHub):**
- FR-VH1: Users can configure a custom server URL for catalog retrieval
- FR-VH2: Users can enter server configuration via JSON URL or manual key-value pairs
- FR-VH3: Users can add unlimited custom key-value pairs for server configuration
- FR-VH4: Users can test server configuration before saving
- FR-VH5: System validates that configured keys enable access to game catalog
- FR-VH6: System loads catalog automatically if valid configuration exists
- FR-VH7: System prompts for configuration if no valid configuration exists
- FR-VH8: Users can modify server configuration from Settings
- FR-VH9: System stores configuration locally between sessions
- FR-VH10: System displays clear error messages when configuration test fails

**Legal & Disclaimer:**
- FR-VH11: System displays legal disclaimer on first launch
- FR-VH12: System clearly separates app functionality from piracy responsibility
- FR-VH13: User bears full responsibility for configured server and its content

**Branding:**
- FR-VH14: Application is named "VRHub"
- FR-VH15: Application displays "A Solstice Project" branding

**Catalog & Download (from PRD):**
- FR1: Users can browse the VR game catalog with thumbnails and icons
- FR2: Users can search games by name
- FR3: Users can filter games by category/genre
- FR4: Users can sort games by name (alphabetical)
- FR5: Users can sort games by size when metadata is loaded
- FR6: Users can view game details including description, screenshots, size, version
- FR7: Users can mark games as favorites
- FR8: Users can view their favorites list
- FR11: Users can add games to download queue
- FR12: Users can choose "Download Only" mode
- FR13: Users can choose "Download & Install" mode
- FR14: Users can view current download queue with position and status
- FR15: Users can pause active downloads
- FR16: Users can resume paused downloads
- FR17: Users can cancel downloads at any time
- FR18: Users can promote queued downloads to front of queue
- FR21: System can download game files with HTTP range resumption
- FR22: System can extract password-protected 7z archives
- FR28: Users can view real-time download progress
- FR39: System can install APK files via Android FileProvider
- FR45: Users can browse cached catalog when offline
- FR48: System can detect network connectivity status
- FR49: System can display offline mode indicator
- FR56: Users can configure notification sound preferences
- FR57: Users can view app version

### NonFunctional Requirements

**Performance:**
- NFR-P1: UI must maintain 60fps during all operations
- NFR-P3: Animations must render at 60fps consistently

**Reliability:**
- NFR-R1: Download queue must persist across app crashes and reboots
- NFR-R4: HTTP range resumption must work with 0% data loss

**Usability:**
- NFR-U1: All touch targets must be minimum 48dp
- NFR-U3: Progress feedback must update continuously

**Maintainability:**
- NFR-M1: All coroutine operations must use `ensureActive()`
- NFR-M8: APK size must not exceed 50MB

### Additional Requirements (from Brainstorming)

**Architecture:**
- Remove VRP_BASE_URI hardcoded URL from codebase
- Replace with configurable KV pairs system
- Storage: SharedPreferences or Room for configuration persistence
- TEST button validates config before saving

**UX Flow:**
- App Launch → Check config exists → If yes: Load catalog | If no: Show config screen
- Config popup unified for first launch and settings access
- Blank canvas: no pre-filled fields, no suggested keys

**Legal:**
- Visible disclaimer on config screen
- App positioned as neutral tool, not piracy facilitator

## FR Coverage Map

| Requirement | Source | Epic |
|-------------|--------|------|
| FR-VH1 to FR-VH13 | Brainstorming | Epic 1: Server Configuration System |
| FR-VH14 to FR-VH15 | Brainstorming | Epic 2: Rebranding |
| FR1-FR8 | PRD | Epic 3: Catalog Browsing |
| FR11-FR28 | PRD | Epic 4: Download & Queue Management |
| FR39, FR45, FR48-FR49 | PRD | Epic 5: Installation & Offline |
| FR56-FR57 | PRD | Epic 6: Settings |

## Epic List

1. **Epic 1: Server Configuration System** - Custom URL/KV pairs config, TEST button, storage, validation
2. **Epic 2: Rebranding** - Rename to VRHub, Solstice branding
3. **Epic 3: Catalog Browsing** - Browse, search, filter, sort, favorites
4. **Epic 4: Download & Queue Management** - Queue, pause/resume, progress
5. **Epic 5: Installation & Offline** - APK install, offline mode
6. **Epic 6: Settings** - Preferences, about

---

## Epic 1: Server Configuration System

**Goal:** Replace VRPirates hardcoded URL with a user-configurable KV pairs system that supports both JSON URL and manual entry, with validation before saving.

### Story 1.1: Display Configuration Screen on First Launch

As a user,
I want to see a configuration screen when no server is configured,
So that I can set up my server URL before browsing games.

**Acceptance Criteria:**

**Given** the app is launched for the first time (no configuration exists)
**When** the app starts
**Then** the configuration screen is displayed
**And** the user cannot access the catalog until configuration is saved

**Given** the app is launched with existing valid configuration
**When** the app starts
**Then** the catalog loads automatically
**And** the configuration screen is NOT shown

**Given** the app is launched with no configuration
**When** the user views the configuration screen
**Then** the legal disclaimer is visible
**And** the user is informed they are responsible for the server they configure

### Story 1.2: Configure Server via JSON URL

As a user,
I want to enter a URL pointing to a JSON configuration file,
So that I can load my server settings automatically.

**Acceptance Criteria:**

**Given** the user is on the configuration screen
**When** the user selects "JSON URL" input mode
**Then** a text field appears for entering the JSON URL
**And** the field is empty (no pre-filled value)

**Given** the user has entered a JSON URL
**When** the user taps the TEST button
**Then** the system fetches the URL
**And** validates the JSON structure contains required keys (baseUri, password)
**And** displays success or specific error message

**Given** the user enters an invalid URL
**When** the user taps TEST
**Then** an error message displays "Invalid URL format"

**Given** the URL returns non-JSON content
**When** the user taps TEST
**Then** an error message displays "Server returned invalid JSON"

**Given** the URL returns JSON without required keys
**When** the user taps TEST
**Then** an error message displays "Configuration missing required keys"

### Story 1.3: Configure Server via Manual Key-Value Pairs

As a user,
I want to enter server configuration keys and values manually,
So that I can use servers that don't provide a JSON URL.

**Acceptance Criteria:**

**Given** the user is on the configuration screen
**When** the user selects "Manual Entry" input mode
**Then** a dynamic form appears with empty key and value fields
**And** an "Add Key" button is visible

**Given** the user is in manual entry mode
**When** the user enters a key-value pair
**And** taps "Add Key"
**Then** the pair is added to the configuration list
**And** new empty key-value fields appear

**Given** the user has added one or more key-value pairs
**When** the user taps the TEST button
**Then** the system validates the configuration
**And** displays success or specific error message

**Given** the configuration is valid
**When** the user taps TEST
**Then** "Configuration valid" message appears
**And** a SAVE button becomes enabled

### Story 1.4: Test Configuration Before Saving

As a user,
I want to test my configuration before saving it,
So that I know it works before navigating away.

**Acceptance Criteria:**

**Given** the user has entered configuration (URL or KV pairs)
**When** the user taps the TEST button
**Then** a loading indicator appears
**And** the system attempts to connect to the server using the configuration

**Given** the configuration test succeeds
**When** the test completes
**Then** a success message displays "Configuration valid"
**And** the SAVE button becomes enabled

**Given** the configuration test fails (connection error)
**When** the test completes
**Then** an error message displays "Connection failed: [specific error]"
**And** the SAVE button remains disabled

**Given** the configuration test fails (timeout > 10 seconds)
**When** the test completes
**Then** an error message displays "Connection timeout"
**And** the SAVE button remains disabled

### Story 1.5: Save and Persist Configuration

As a user,
I want my configuration to be saved and persisted,
So that I don't have to re-enter it every time I open the app.

**Acceptance Criteria:**

**Given** the user has a valid configuration (TEST passed)
**When** the user taps SAVE
**Then** the configuration is stored locally
**And** the app navigates to the catalog screen
**And** the catalog begins loading

**Given** the app is restarted
**When** the user opens the app
**Then** the saved configuration is loaded
**And** the catalog loads automatically

### Story 1.6: Modify Configuration from Settings

As a user,
I want to modify my server configuration from Settings,
So that I can switch to a different server if needed.

**Acceptance Criteria:**

**Given** the user is viewing the catalog with a valid configuration
**When** the user opens Settings
**Then** a "Server Configuration" option is visible

**Given** the user taps "Server Configuration" in Settings
**When** the configuration popup opens
**Then** the current saved configuration is displayed
**And** the user can modify it (change URL or KV pairs)
**And** the TEST button is available

**Given** the user modifies the configuration
**When** the user taps SAVE after successful TEST
**Then** the old configuration is replaced
**And** the catalog refreshes using the new configuration

### Story 1.7: Display Legal Disclaimer

As a user,
I want to see a clear legal disclaimer on the configuration screen,
So that I understand this app is a tool and I am responsible for how I use it.

**Acceptance Criteria:**

**Given** the user is on the configuration screen
**When** the screen loads
**Then** a disclaimer is displayed with text similar to:
"VRHub is a neutral tool for managing VR game installations. The app does not provide, host, or endorse any game content. You are solely responsible for the servers you configure and the content you access through them."

**Given** the disclaimer is displayed
**When** the user scrolls or views the screen
**Then** the disclaimer remains visible
**And** is not dismissible without completing or canceling configuration

---

## Epic 2: Rebranding

**Goal:** Rename the application from "Rookie On Quest" to "VRHub" and apply Solstice branding throughout.

### Story 2.1: Rename Application

As a user,
I want the app to be named "VRHub",
So that it is clearly distinguishable from the discontinued Rookie project.

**Acceptance Criteria:**

**Given** the app is installed on a Quest device
**When** the user views the app in the app launcher
**Then** the app name displays as "VRHub"
**And** NOT "Rookie On Quest"

**Given** the app is installed
**When** the user checks the app info
**Then** the package name reflects VRHub (e.g., com.vrhub.app)
**And** the app name is "VRHub"

### Story 2.2: Apply Solstice Branding

As a user,
I want to see "A Solstice Project" branding,
So that I know this is a Solstice Studio project.

**Acceptance Criteria:**

**Given** the user is using the app
**When** the Settings > About screen is viewed
**Then** "A Solstice Project" is displayed
**And** the branding reinforces the app's independence from VRPirates

---

## Epic 3: Catalog Browsing

**Goal:** Enable users to browse, search, filter, and sort the VR game catalog.

### Story 3.1: Browse Game Catalog

As a user,
I want to browse available games with thumbnails and icons,
So that I can discover games to install.

**Acceptance Criteria:**

**Given** the user has a valid server configuration
**When** the catalog loads
**Then** games are displayed in a grid layout
**And** each game shows thumbnail/icon
**And** game name is visible

### Story 3.2: Search Games by Name

As a user,
I want to search games by name,
So that I can quickly find specific games.

**Acceptance Criteria:**

**Given** the catalog is displayed
**When** the user enters text in the search field
**Then** games are filtered to show matches in real-time
**And** search is case-insensitive

### Story 3.3: Sort Games

As a user,
I want to sort games by name or size,
So that I can organize the catalog my way.

**Acceptance Criteria:**

**Given** the catalog is displayed
**When** the user selects sort option
**Then** games are re-ordered accordingly
**And** sort preference is remembered during the session

### Story 3.4: View Game Details

As a user,
I want to view game details including description, screenshots, and size,
So that I can make informed installation decisions.

**Acceptance Criteria:**

**Given** the catalog is displayed
**When** the user taps on a game
**Then** a detail screen opens
**And** shows description, screenshots, size, and version

### Story 3.5: Manage Favorites

As a user,
I want to mark games as favorites,
So that I can quickly access games I want to install later.

**Acceptance Criteria:**

**Given** the game detail is displayed
**When** the user taps the favorite button
**Then** the game is marked as favorite
**And** appears in the favorites list

---

## Epic 4: Download & Queue Management

**Goal:** Enable users to download games and manage the download queue with full control.

### Story 4.1: Add Game to Download Queue

As a user,
I want to add games to a download queue,
So that I can manage multiple downloads.

**Acceptance Criteria:**

**Given** the game detail is displayed
**When** the user taps "Download" or "Download & Install"
**Then** the game is added to the download queue
**And** download begins immediately if queue is empty

**Given** the user taps "Download Only"
**When** the download completes
**Then** files remain in download folder
**And** user can install manually later

**Given** the user taps "Download & Install"
**When** the download completes
**Then** installation begins automatically

### Story 4.2: View and Manage Queue

As a user,
I want to view my download queue with status and progress,
So that I can monitor downloads.

**Acceptance Criteria:**

**Given** downloads are in progress
**When** the user views the queue
**Then** each item shows position, status, and progress percentage
**And** user can pause, resume, or cancel any item

### Story 4.3: Pause and Resume Downloads

As a user,
I want to pause and resume downloads,
So that I can manage network usage.

**Acceptance Criteria:**

**Given** a download is in progress
**When** the user taps pause
**Then** the download pauses
**And** progress is saved

**Given** a download is paused
**When** the user taps resume
**Then** the download resumes from where it stopped

### Story 4.4: View Real-Time Progress

As a user,
I want to see real-time download progress,
So that I know how much is remaining.

**Acceptance Criteria:**

**Given** a download is in progress
**When** the user views the progress
**Then** percentage and bytes downloaded are displayed
**And** updates at least once per second

---

## Epic 5: Installation & Offline

**Goal:** Enable APK installation and offline catalog browsing.

### Story 5.1: Install APK Files

As a user,
I want the app to install downloaded APK files,
So that games are ready to play.

**Acceptance Criteria:**

**Given** a download has completed in "Download & Install" mode
**When** the APK is ready
**Then** the system installs it via FileProvider
**And** user sees success notification

### Story 5.2: Browse Catalog Offline

As a user,
I want to browse my cached catalog when offline,
So that I can see my games even without internet.

**Acceptance Criteria:**

**Given** the user has previously loaded the catalog
**When** internet is unavailable
**Then** cached catalog is displayed
**And** "Offline Mode" indicator is shown

---

## Epic 6: Settings

**Goal:** Provide user settings and app information.

### Story 6.1: Configure Notification Preferences

As a user,
I want to enable or disable notification sounds,
So that I can control audio alerts.

**Acceptance Criteria:**

**Given** the Settings screen
**When** the user toggles "Notification Sound"
**Then** the preference is saved
**And** applies to future installation completions

### Story 6.2: View App Version

As a user,
I want to view the app version,
So that I can check for updates or report issues.

**Acceptance Criteria:**

**Given** the Settings > About screen
**When** the user views it
**Then** app version is displayed
**And** "A Solstice Project" branding is visible
