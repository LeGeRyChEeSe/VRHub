# Story 10.7: Display Legal Disclaimer

Status: ready-for-dev

## Story

As a user,
I want to see a clear legal disclaimer on the configuration screen,
so that I understand this app is a tool and I am responsible for how I use it.

## Acceptance Criteria

1. **Given** the user is on the configuration screen
   **When** the screen loads
   **Then** a disclaimer is displayed with text similar to:
   "VRHub is a neutral tool for managing VR game installations. The app does not provide, host, or endorse any game content. You are solely responsible for the servers you configure and the content you access through them."

2. **Given** the disclaimer is displayed
   **When** the user scrolls or views the screen
   **Then** the disclaimer remains visible
   **And** is not dismissible without completing or canceling configuration

## Tasks / Subtasks

- [ ] Task 1: Add legal disclaimer to ConfigurationScreen (AC: #1, #2)
  - [ ] Subtask 1.1: Create disclaimer text composable with appropriate styling
  - [ ] Subtask 1.2: Place disclaimer at top of configuration screen
  - [ ] Subtask 1.3: Style disclaimer to be visually distinct but not intrusive

## Dev Notes

### Technical Context

**Prerequisite:** Story 10.1 should be complete — ConfigurationScreen exists.

**This Story's Scope:** Adding the legal disclaimer text to the ConfigurationScreen. This is a UI-only story to ensure legal compliance and user awareness.

### Disclaimer Text

> "VRHub is a neutral tool for managing VR game installations. The app does not provide, host, or endorse any game content. You are solely responsible for the servers you configure and the content you access through them."

### File Locations (to MODIFY)

- `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationScreen.kt` — add disclaimer composable

### UI Styling Suggestions

- Use `AlertDialog` or `Card` with muted background
- Smaller font size (caption/body2)
- Gray or muted color (not alarming)
- Icon (info/warning) optional
- Non-dismissible — no close button or X

### Architecture Pattern

- Create `DisclaimerCard` composable function
- Call from ConfigurationScreen before other content
- Reuse across first-launch and Settings editing modes

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List

- UPDATE: `app/src/main/java/com/vrpirates/rookieonquest/ui/ConfigurationScreen.kt`
