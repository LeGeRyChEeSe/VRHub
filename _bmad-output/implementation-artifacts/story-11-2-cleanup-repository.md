# Story 11.2: Cleanup Repository

## Story ID
- **Story ID:** 11.2
- **Epic:** Epic 11 - Cleanup et documentation
- **Story Key:** `11-2-cleanup-repository`

---

## User Story

As a developer,
I want a clean, well-organized repository with no dead code or outdated files,
So that the project is maintainable and new contributors can understand it quickly.

---

## Background

Since the VRHub rebrand (Epic 2) and the Epic 10 server configuration system, the repository contains:
- Old VRPirates-related files and comments
- Deprecated workflows or scripts
- Outdated documentation
- Code that's no longer reachable
- Stale screenshots

A thorough cleanup will ensure long-term maintainability.

---

## Acceptance Criteria

### AC-1: Remove Dead Code
```
Given the codebase is reviewed
When dead code paths are identified
Then they are removed or marked as tech-debt
```

**Specific Areas to Review:**
- Any VRPirates-specific code no longer reachable after rebrand
- Commented-out blocks that reference old package names
- TODO comments that are resolved

### AC-2: Update Documentation Files
```
Given the docs folder is reviewed
When outdated documentation is found
Then it is updated or removed
```

**Files to Review:**
- `docs/architecture-infra.md` - Check for outdated references
- `ACTION_PLAN.md` - Review if still relevant
- `CHANGELOG.md` - Ensure entries are accurate
- Any `.md` files referencing VRPirates shutdown as current state

### AC-3: Clean Up Screenshots
```
Given screenshots exist in the repository
When old UI screenshots are found
Then they are removed or clearly labeled as outdated
```

**Screenshots to Review:**
- `vrhub_screen*.png` files at root - keep if showing current UI, remove if showing old branding
- Any screenshots in `docs/` folder
- Any screenshots in README that show old UI

### AC-4: Remove Stale Scripts
```
Given the scripts/ folder is reviewed
When scripts reference VRPirates or are no longer used
Then they are removed or documented as deprecated
```

### AC-5: Verify Build Works
```
Given cleanup changes are made
When the project is built
Then assembleDebug succeeds without errors
```

---

## Implementation Checklist

### Phase 1: Code Review
- [ ] Search for `vrpirates` (case-insensitive) in all files - ensure no user-facing references remain
- [ ] Search for `rookie` references outside of internal API contracts (ROOKIE_UPDATE_SECRET, X-Rookie-*)
- [ ] Review MainActivity.kt for any remaining old comments or debug code
- [ ] Check Constants.kt for any outdated comments

### Phase 2: Documentation
- [ ] Review `ACTION_PLAN.md` - archive or update
- [ ] Review `docs/architecture-infra.md` - update or remove if redundant with CLAUDE.md
- [ ] Check CHANGELOG.md accuracy
- [ ] Verify CLAUDE.md reflects current project state (VRHub branding)

### Phase 3: Assets
- [ ] Remove old screenshots at repo root if showing outdated UI
- [ ] Keep screenshots if they show current VRHub UI
- [ ] Update README screenshot references

### Phase 4: Scripts
- [ ] Check `scripts/` folder for VRPirates references
- [ ] Remove deprecated scripts
- [ ] Ensure active scripts have correct package references

### Phase 5: Validation
- [ ] Run `./gradlew assembleDebug` - must succeed
- [ ] Verify all tests pass
- [ ] Check git status for unexpected file deletions

---

## Files to Potentially Modify or Remove

| File | Action | Reason |
|------|--------|--------|
| `ACTION_PLAN.md` | Review/Update | Contains outdated VRPirates shutdown info |
| `vrhub_screen*.png` | Keep or Remove | Keep only if showing current UI |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Removed (already done) | Part of rebrand |
| Old screenshots at root | Remove | If showing old ROOKIE branding |

## Files That Should Be Kept

| File | Reason |
|------|--------|
| `CLAUDE.md` | Current project documentation |
| `CHANGELOG.md` | Historical record (review for accuracy) |
| `README.md` | Will be updated in Story 11.1 |
| `docs/architecture-infra.md` | Keep if accurate |

---

## Success Criteria

- `./gradlew assembleDebug` succeeds
- No user-facing `vrpirates` or `Rookie On Quest` references in code
- Only internal API contracts retain `ROOKIE` prefix (ROOKIE_UPDATE_SECRET, X-Rookie-*)
- Screenshots at repo root are either removed or show current VRHub UI
- Documentation files are accurate and up-to-date

---

## Metadata

- **Status:** backlog
- **Created:** 2026-04-28
- **Last Updated:** 2026-04-28
- **Blocked by:** 11-1-update-readme-for-vrhub-rebrand (can be done in parallel but README update should happen first)