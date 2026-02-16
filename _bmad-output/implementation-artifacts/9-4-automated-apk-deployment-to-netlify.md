# Story 9.4: Automated APK Deployment to Netlify

Status: backlog

## Story

As a developer,
I want the release workflow to automatically deploy new APK versions to the Netlify update gateway,
so that users receive updates without manual file copying.

## Acceptance Criteria

1. [ ] Modify `release.yml` workflow to include Netlify deployment step after successful build.
2. [ ] Add step to download/release APK artifact from the build job.
3. [ ] Add step to calculate SHA-256 checksum of the APK.
4. [ ] Add step to clone `Sunshine-AIO-web` repository (or use local worktree).
5. [ ] Add step to copy new APK to `Sunshine-AIO-web/public/updates/rookie/`.
6. [ ] Add step to update `Sunshine-AIO-web/public/updates/rookie/version.json` with new version, changelog, and checksum.
7. [ ] Add step to commit and push changes to `Sunshine-AIO-web` main branch.
8. [ ] Verify Netlify auto-deploy triggers after push.
9. [ ] Add documentation for the deployment process.

## Tasks / Subtasks

- [ ] Task 1: Analyze Current Workflow
  - [ ] Review `release.yml` to identify where to add Netlify deployment step
  - [ ] Verify APK artifact is available after build
  - [ ] Check if Sunshine-AIO-web is accessible as worktree or needs to be cloned
- [ ] Task 2: Implement Deployment Script
  - [ ] Create a shell script to handle APK copy, checksum calculation, and version.json update
  - [ ] Script should be reusable and idempotent
- [ ] Task 3: Add GitHub Actions Step
  - [ ] Add new job or step in release.yml for Netlify deployment
  - [ ] Configure GitHub token with permission to push to Sunshine-AIO-web
  - [ ] Add error handling and rollback capability
- [ ] Task 4: Testing & Validation
  - [ ] Test deployment with a test release (e.g., 2.5.1-rc.1)
  - [ ] Verify APK is accessible at Netlify URL
  - [ ] Verify version.json is correct
  - [ ] Verify update check works from app

## Technical Notes

### File Locations (in Sunshine-AIO-web)
- APK: `public/updates/rookie/RookieOnQuest_{version}.apk`
- Version metadata: `public/updates/rookie/version.json`

### version.json Format
```json
{
  "version": "2.5.0",
  "changelog": "...",
  "downloadUrl": "/updates/rookie/RookieOnQuest_2.5.0.apk",
  "checksum": "sha256-hash-here"
}
```

### GitHub Actions Requirements
- Need `contents: write` permission to access APK artifact
- Need GitHub token with push access to `Sunshine-AIO-web` repository
- Alternatively, use deploy key or GitHub App token

### Workflow Integration
The deployment should happen AFTER:
1. APK is successfully built
2. APK is signed
3. GitHub Release is created

The deployment should trigger Netlify automatically via the push to `Sunshine-AIO-web/main`.

## Dependencies

- Story 9.1: Netlify Update Gateway (Server-side) - must be complete
- Story 9.2: Secure Update Client (Android-side) - must be complete

## Notes

- The APK filename format is `RookieOnQuest_{versionName}.apk` (e.g., `RookieOnQuest_2.5.0.apk`)
- The version in version.json should match the GitHub release version
- Changelog should be extracted from CHANGELOG.md for the release version
