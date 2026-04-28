# Story 11.1: Update README for VRHub Rebrand

## Story ID
- **Story ID:** 11.1
- **Epic:** Epic 11 - Cleanup et documentation
- **Story Key:** `11-1-update-readme-for-vrhub-rebrand`

---

## User Story

As a developer,
I want the README to reflect the VRHub identity and current project state,
So that contributors and users understand this is a live project under the Solstice brand.

---

## Background

Since Epic 2 (VRHub Rebrand), the project has undergone major changes:
- Package renamed from `com.vrpirates.rookieonquest` to `com.vrhub`
- All user-facing "ROOKIE/Rookie" references replaced with "VRHUB"
- APK output renamed from `RookieOnQuest-v*.apk` to `VRHub-v*.apk`
- "A Solstice Project" branding applied throughout the app
- Database and prefs renamed (`rookie_database` → `vrhub_database`, `rookie_prefs` → `vrhub_prefs`)
- Log/history exports renamed (`rookie_logs` → `vrhub_logs`, `rookie_history` → `vrhub_history`)

**Current README Problems:**
- Opens with "🛑 PROJECT CEASED / END OF LIFE" header
- References "Rookie On Quest" throughout
- Badges show "STATUS-ARCHIVED" instead of active status
- Contains VRPirates shutdown narrative that's no longer relevant
- Installation instructions still reference `RookieOnQuest-v*.apk` filenames

---

## Acceptance Criteria

### AC-1: Title and Branding Update
```
Given the README is opened
When the reader views the header
Then they see "VRHub" as the project name
And "A Solstice Project" tagline
And no mention of "Rookie On Quest"
```

### AC-2: Remove Archive Status
```
Given the README is opened
When the reader views the badges
Then they see "STATUS-ACTIVE" or similar active indicator
And no "ARCHIVED" badge
And the shutdown narrative is removed
```

### AC-3: Technical References Updated
```
Given the README installation section is read
When the reader follows build instructions
Then they see "VRHub-v*.apk" as the APK output name
And "com.vrhub" package references where appropriate
And "vrhub_prefs", "vrhub_database" references if mentioned
```

### AC-4: Screenshots Section
```
Given the README contains screenshots
When the reader views them
Then old UI screenshots are removed or replaced
And any remaining screenshots show VRHub branding
Or a note indicates screenshots are outdated
```

### AC-5: Consistent VRHub Branding
```
Given the README is read in full
When the reader searches for "rookie" (case-insensitive)
Then no matches are found in user-facing content
And only internal API references remain (ROOKIE_UPDATE_SECRET, X-Rookie-*)
```

---

## Implementation Notes

### Files to Modify
- `README.md` - Main documentation

### References to Keep (Internal API Contracts)
These should NOT be changed as they are API contracts with the update server:
- `ROOKIE_UPDATE_SECRET` - Build config property name
- `X-Rookie-Signature` - HTTP header for update API
- `X-Rookie-Date` - HTTP header for update API

### References to Update
- Title "Rookie On Quest" → "VRHub"
- Badge "STATUS-ARCHIVED" → remove or change to active
- All narrative about VRPirates shutdown
- APK filename references
- Package name references where visible

### Structure Recommendation

**Recommended README Structure:**
1. Project title and tagline
2. Badges (version, build status - NOT archived)
3. Brief project description
4. Key Features (if applicable)
5. Build & Development Commands
6. Contributing guidelines

**Remove:**
- "PROJECT CEASED / END OF LIFE" header
- "The End of the Journey" section about VRPirates shutdown
- Legacy status indicators

---

## Success Criteria

- `grep -i "rookie on quest" README.md` returns no matches
- `grep -i "archived" README.md` returns no matches
- Project title displays "VRHub"
- Build instructions reference correct APK filename pattern `VRHub-v*.apk`
- No screenshots showing old ROOKIE branding in the title/drawer

---

## Metadata

- **Status:** ready-for-dev
- **Created:** 2026-04-28
- **Last Updated:** 2026-04-28