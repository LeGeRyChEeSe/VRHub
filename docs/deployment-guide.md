# Deployment Guide - Rookie On Quest

**Generated:** 2026-01-09
**CI/CD Platform:** GitHub Actions
**Distribution:** GitHub Releases

## Overview

Rookie On Quest uses **GitHub Actions** for automated builds and **GitHub Releases** for distribution. Release APKs are automatically built, signed, and published when a new version tag is pushed.

---

## CI/CD Pipeline

### GitHub Actions Workflow

**Location:** `.github/workflows/`

**Workflows:**
- **Build & Test** - Runs on every push/PR
- **Release** - Triggers on version tags (`vX.X.X`)

### Build Workflow (`.github/workflows/build.yml`)

**Triggers:**
- Push to `main` or `dev` branches
- Pull requests

**Steps:**
1. Checkout code
2. Set up JDK 17
3. Cache Gradle dependencies
4. Run `./gradlew assembleDebug`
5. Upload debug APK as artifact

### Release Workflow (`.github/workflows/release.yml`)

**Triggers:**
- Tag push matching pattern `v*` (e.g., `v2.4.0`)

**Steps:**
1. Checkout code at tag
2. Set up JDK 17
3. Decode signing key from secrets
4. Run `./gradlew assembleRelease`
5. Create GitHub Release
6. Upload signed APK to release

---

## Release Process

### 1. Update Version

Edit `app/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        versionCode = 16
        versionName = "2.5.0"
    }
}
```

**Or use Makefile:**
```bash
make set-version V=2.5.0
```

This also updates `CHANGELOG.md` with a new version section.

### 2. Update Changelog

Edit `CHANGELOG.md` to document changes:

```markdown
## [2.5.0] - 2026-01-09

### Added
- Dark mode support
- Offline browsing improvements

### Fixed
- Queue processing memory leak
```

### 3. Commit and Tag

```bash
git add .
git commit -m "chore: release v2.5.0"
git tag v2.5.0
git push origin main
git push origin v2.5.0
```

### 4. Automated Build

GitHub Actions detects the tag and:
- Builds release APK
- Signs with keystore from secrets
- Creates GitHub Release with changelog
- Uploads APK as release asset

### 5. Verify Release

Visit [Releases Page](https://github.com/LeGeRyChEeSe/rookie-on-quest/releases) to confirm:
- ✅ Release created with correct tag
- ✅ APK attached (`RookieOnQuest-vX.X.X.apk`)
- ✅ Changelog displayed in release notes

---

## Netlify Update Gateway Deployment

**Story:** 9.4 - Automated APK Deployment to Netlify

Since the repository moved to private status, APK distribution migrated to a custom Netlify-hosted gateway (Sunshine-AIO-web) for secure updates.

### Deployment Flow

The release workflow now automatically deploys to both GitHub Releases AND Netlify:

```
GitHub Actions Release Workflow
    │
    ├── Build Release APK
    ├── Create GitHub Release ← Standard distribution
    │
    └── Deploy to Netlify (NEW in Story 9.4)
          │
          ├── Download APK artifact
          ├── Calculate SHA-256 checksum
          ├── Clone Sunshine-AIO-web
          ├── Copy APK to public/updates/rookie/
          ├── Update version.json
          └── Push → Netlify Auto-Deploy
```

### Netlify Gateway Structure

**Repository:** `LeGeRyChEeSe/Sunshine-AIO-web`

| Path | Description |
|------|-------------|
| `public/updates/rookie/RookieOnQuest_{version}.apk` | APK file |
| `public/updates/rookie/version.json` | Version metadata |

### version.json Format

```json
{
    "version": "2.5.0",
    "changelog": "Release 2.5.0 - See GitHub release for details",
    "downloadUrl": "/updates/rookie/RookieOnQuest_2.5.0.apk",
    "checksum": "sha256-hash-of-apk",
    "timestamp": "2026-02-16T12:00:00Z"
}
```

### Required GitHub Secrets

| Secret Name | Description | Required For |
|-------------|-------------|--------------|
| `GH_PAT_SUNSHINE_AIO` | Personal Access Token with `repo` scope for Sunshine-AIO-web | Netlify deployment |
| `ROOKIE_UPDATE_SECRET` | HMAC signing key for update requests | Release builds |

### Creating GH_PAT_SUNSHINE_AIO Secret

To enable automatic deployment to Sunshine-AIO-web, create a Personal Access Token (PAT):

1. **Go to GitHub Settings:**
   - Visit https://github.com/settings/tokens
   - Click "Generate new token (classic)"

2. **Configure Token:**
   - **Note:** `RookieOnQuest Netlify Deployment`
   - **Expiration:** Select appropriate expiration (recommend 90 days or 1 year)
   - **Scopes:** Select `repo` (full control of private repositories)

3. **Copy the Token:**
   - Copy the generated token immediately (it won't be shown again)

4. **Add to Repository Secrets:**
   - Go to: https://github.com/LeGeRyChEeSe/rookie-on-quest/settings/secrets/actions
   - Click "New repository secret"
   - Name: `GH_PAT_SUNSHINE_AIO`
   - Value: Paste your token
   - Click "Add secret"

**Note:** The token needs write access to the Sunshine-AIO-web repository. Make sure the token's account has appropriate permissions.

### Local Deployment Script

For manual deployments, use the provided `deploy-to-netlify.sh` script:

```bash
# Navigate to scripts directory
cd scripts

# Make script executable
chmod +x deploy-to-netlify.sh

# Run deployment
./deploy-to-netlify.sh "<path-to-apk>" "<version>" "<path-to-Sunshine-AIO-web>" "[changelog]"

# Example:
./deploy-to-netlify.sh "../app/build/outputs/apk/release/RookieOnQuest-v2.5.0.apk" "2.5.0" "../Sunshine-AIO-web" "Bug fixes and improvements"
```

**Arguments:**
| Argument | Description | Required |
|----------|-------------|----------|
| `apk_path` | Path to the APK file | Yes |
| `version` | Version number (e.g., "2.5.0") | Yes |
| `sunshine_aio_web_path` | Path to cloned Sunshine-AIO-web repository | Yes |
| `changelog` | Release notes (optional, defaults to "Release {version}") | No |

**What the script does:**
1. Validates APK and directory paths exist
2. Calculates SHA-256 checksum
3. Copies APK to `public/updates/rookie/`
4. Creates/updates `version.json` with metadata
5. Shows git status for review

**Note:** The script prepares files for commit but does NOT automatically push. After running:
```bash
cd ../Sunshine-AIO-web
git add -A
git commit -m "Deploy RookieOnQuest v2.5.0"
git push origin main
# Netlify auto-deploys after push
```

### Manual Netlify Deployment

If CI/CD fails, deploy manually:

```bash
# 1. Clone Sunshine-AIO-web
git clone https://github.com/LeGeRyChEeSe/Sunshine-AIO-web.git
cd Sunshine-AIO-web

# 2. Copy APK
cp path/to/RookieOnQuest-v2.5.0.apk public/updates/rookie/RookieOnQuest_2.5.0.apk

# 3. Calculate checksum
sha256sum public/updates/rookie/RookieOnQuest_2.5.0.apk

# 4. Update version.json
# Edit public/updates/rookie/version.json with new version, checksum, timestamp

# 5. Commit and push
git add public/updates/rookie/
git commit -m "Deploy RookieOnQuest v2.5.0"
git push origin main

# Netlify auto-deploys after push
```

### Verify Netlify Deployment

1. Check Sunshine-AIO-web GitHub commit pushed successfully
2. Visit Netlify dashboard → Deploys → Verify "Deploy started" triggered
3. Wait for "Deploy published" status
4. Test update check from Quest app

### Netlify URL

The update gateway is accessible at: **https://sunshine-aio.com** (or configured custom domain)

### Retry Strategy for HTTPS Accessibility

After pushing to Sunshine-AIO-web, the workflow verifies APK accessibility via HTTPS using a retry mechanism:

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Max Retries | 6 | Sufficient for typical Netlify deploys (1-5 min) |
| Retry Delay | 30 seconds | Balances quick detection vs. not overwhelming Netlify |
| Total Wait Time | ~3 minutes | Covers most deployment scenarios |

**Retry Logic:**
1. **Attempt 1-6:** Check `https://www.sunshine-aio.com/updates/rookie/RookieOnQuest_{version}.apk`
2. **Success (HTTP 200):** Mark as verified, continue
3. **Failure:** Wait 30s, retry
4. **After 6 attempts:** Mark as "pending" (Netlify may still be deploying)

**Why this approach:**
- Netlify typically deploys in 1-5 minutes
- 6 retries × 30s = 3 minutes covers most deployments
- If still pending after 3 minutes, deployment is likely slow but will complete
- The workflow does NOT fail on pending - it's informational

**Customization:**
To adjust retry parameters, modify these lines in `release.yml`:
```yaml
MAX_RETRIES=6    # Increase for slower deployments
RETRY_DELAY=30   # Increase delay between checks
```

---

## GitHub Secrets Configuration

**Required Secrets (Repository Settings > Secrets and Variables > Actions):**

| Secret Name | Description | Example |
|-------------|-------------|---------|
| `KEYSTORE_FILE` | Base64-encoded keystore file | `base64 keystore.jks` |
| `KEYSTORE_PASSWORD` | Keystore password | `myStorePass123` |
| `KEY_ALIAS` | Signing key alias | `rookie_release_key` |
| `KEY_PASSWORD` | Key password | `myKeyPass456` |

### Encoding Keystore

```bash
# Encode keystore to base64
base64 -w 0 keystore.jks > keystore_base64.txt

# Copy contents and add to GitHub Secrets as KEYSTORE_FILE
```

---

## Manual Release (Fallback)

If CI/CD fails, create a release manually:

### 1. Build Release APK

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/RookieOnQuest-vX.X.X.apk`

### 2. Create GitHub Release

```bash
# Install GitHub CLI (gh)
gh release create v2.5.0 \
  --title "v2.5.0 - Feature Update" \
  --notes "See CHANGELOG.md for details" \
  app/build/outputs/apk/release/RookieOnQuest-v2.5.0.apk
```

**Or manually via GitHub Web UI:**
1. Go to Releases > Draft a new release
2. Choose tag `v2.5.0`
3. Upload APK
4. Paste changelog
5. Publish release

---

## Signing Configuration

### Release Signing

**File:** `app/build.gradle.kts`

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(...)
        }
    }
}
```

### Generating a New Keystore

```bash
keytool -genkeypair -v \
  -keystore rookie_keystore.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias rookie_release_key
```

**⚠️ Important:** Store keystore securely! Loss = inability to update app on users' devices.

---

## APK Optimization

### ProGuard/R8

**Enabled in Release:** Yes
**Configuration:** `proguard-rules.pro`

**Features:**
- Code shrinking (removes unused code)
- Obfuscation (renames classes/methods)
- Optimization (bytecode improvements)

**Keep Rules:**
```proguard
# Keep Retrofit interfaces
-keep interface com.vrpirates.rookieonquest.network.** { *; }

# Keep Room entities
-keep class com.vrpirates.rookieonquest.data.GameEntity { *; }
```

### APK Size Reduction

**Current Size:** ~8 MB (release APK)

**Strategies:**
- R8 shrinking enabled
- No unused resources included
- Vector drawables preferred over PNGs
- Dynamic asset downloading (icons, thumbnails)

---

## Distribution Channels

### Primary: GitHub Releases

**URL:** https://github.com/LeGeRyChEeSe/rookie-on-quest/releases

**Advantages:**
- Free hosting
- Version history
- Direct APK downloads
- Changelog integration

### Alternative: SideQuest (Future)

**Status:** Not yet listed

**Requirements for SideQuest listing:**
- Privacy policy URL
- Age rating
- Screenshot set (6 images)
- Application submission

---

## Update Mechanism

### In-App Update Check

**Trigger:** On app launch
**Primary API:** Netlify Function `https://sunshine-aio.com/.netlify/functions/check-update`
**Fallback:** GitHub API `/repos/LeGeRyChEeSe/rookie-on-quest/releases/latest`

**Flow:**
1. Fetch latest release info from Netlify secure gateway
2. Compare version with `BuildConfig.VERSION_NAME`
3. Verify HMAC signature for security
4. If newer version available:
   - Download APK from `https://sunshine-aio.com/updates/rookie/RookieOnQuest_{version}.apk`
   - Verify SHA-256 checksum
   - Display update dialog
   - Install via `FileProvider`

**Code:** See `MainViewModel.kt` → `checkForUpdates()` and `UpdateService.kt`

### Update Installation

APK downloaded to:
`/sdcard/Download/RookieOnQuest/updates/RookieOnQuest-vX.X.X.apk`

Installation triggered via Android's `PackageInstaller`.

---

## Rollback Strategy

### If Release Has Critical Bug

1. **Unpublish Release** (GitHub > Releases > Edit > Delete)
2. **Revert Tag:**
   ```bash
   git tag -d v2.5.0
   git push origin :refs/tags/v2.5.0
   ```
3. **Fix Bug and Re-release as Patch:**
   ```bash
   make set-version V=2.5.1
   git commit -m "fix: critical bug from v2.5.0"
   git tag v2.5.1
   git push origin main --tags
   ```

---

## Monitoring & Analytics

**Current Status:** No analytics integrated

**Potential Tools:**
- Google Analytics for Firebase (privacy concerns)
- Self-hosted Plausible Analytics
- GitHub download counts (public metric)

---

## Infrastructure Dependencies

### External Services

| Service | Purpose | Criticality | Owner |
|---------|---------|-------------|-------|
| GitHub Actions | CI/CD | High | GitHub |
| GitHub Releases | APK hosting | High | GitHub |
| Netlify (Sunshine-AIO) | Secure update gateway | **Critical** | VRPirates Team |
| VRPirates Servers | Game catalog/downloads | **Critical** | VRPirates Team |
| GitHub API | Update checks | Medium | GitHub |

**⚠️ App Non-Functional Without:** VRPirates server infrastructure

---

## Security Considerations

### APK Signing

- **Algorithm:** RSA 2048-bit
- **Validity:** 10,000 days
- **Storage:** GitHub Secrets (encrypted)

### Keystore Backup

**Recommendation:**
- Store keystore in secure location (encrypted cloud backup)
- Document recovery procedure
- Share with trusted team members

### Secrets Management

- Never commit `keystore.properties` to git
- Rotate secrets if compromised
- Use environment-specific secrets for staging vs. production

---

## Post-Release Checklist

- [ ] Verify release published on GitHub
- [ ] Test APK download from release page
- [ ] Install APK on Quest device and verify functionality
- [ ] Monitor GitHub Issues for user reports
- [ ] Update README.md badge if version format changed
- [ ] Announce release in community channels (optional)

---

## Troubleshooting

### Netlify Deployment Issues

#### Problem: Deployment fails with "Push failed"

**Symptoms:**
- GitHub Actions job fails at "Commit and Push to Sunshine-AIO-web"
- Error: "Push failed"

**Solutions:**
1. Check that `GH_PAT_SUNSHINE_AIO` secret is valid and has `repo` scope
2. Verify the token hasn't expired
3. Check that your GitHub account has push access to Sunshine-AIO-web repository
4. Check GitHub Actions logs for more details

#### Problem: APK not accessible after deployment

**Symptoms:**
- GitHub Actions shows success but APK doesn't download
- version.json shows old version

**Solutions:**
1. Check Netlify dashboard: https://app.netlify.com/sites/sunshine-aio/deploys
2. Wait 1-5 minutes for deployment to complete (Netlify shows "Deploy published")
3. Verify commit pushed to Sunshine-AIO-web: https://github.com/LeGeRyChEeSe/Sunshine-AIO-web/commits/main
4. Check that APK file exists in repository: `public/updates/rookie/`
5. Verify version.json has correct checksum

#### Problem: Update check fails from Quest app

**Symptoms:**
- App shows "Update check failed" error
- Can't connect to update server

**Solutions:**
1. Verify app is using correct endpoint: `https://sunshine-aio.com/.netlify/functions/check-update`
2. Check device clock is synchronized (out-of-sync clocks cause 403 errors)
3. Verify `ROOKIE_UPDATE_SECRET` matches between server and client
4. Test endpoint manually:
   ```bash
   curl -H "X-Rookie-Date: $(date -u +%Y-%m-%dT%H:%M:%SZ)" \
        -H "X-Rookie-Signature: <hmac-signature>" \
        https://sunshine-aio.com/.netlify/functions/check-update
   ```

#### Problem: version.json validation fails

**Symptoms:**
- GitHub Actions fails at "Setup deployment environment"
- Error: "version.json is not valid JSON" or "missing required fields"

**Solutions:**
1. Check that JSON syntax is correct (no trailing commas)
2. Verify all required fields: version, checksum, downloadUrl
3. Check workflow logs for exact error

### GitHub Actions Issues

#### Problem: Release build fails with "Secret not found"

**Symptoms:**
- Workflow fails at build step
- Error: "ROOKIE_UPDATE_SECRET" or other secret not found

**Solutions:**
1. Go to: https://github.com/LeGeRyChEeSe/rookie-on-quest/settings/secrets/actions
2. Verify all required secrets exist:
   - `ROOKIE_UPDATE_SECRET`
   - `GH_PAT_SUNSHINE_AIO`
   - `KEYSTORE_FILE`
   - `KEYSTORE_PASSWORD`
   - `KEY_ALIAS`
   - `KEY_PASSWORD`

#### Problem: APK not signed correctly

**Symptoms:**
- App won't install on Quest
- "Package parsing error" or "App not installed"

**Solutions:**
1. Verify keystore is correct
2. Check alias matches: `rookie_release_key`
3. Ensure passwords are correct
4. Verify signing happened in workflow logs

## Resources

- **GitHub Actions Docs:** https://docs.github.com/en/actions
- **Android App Signing:** https://developer.android.com/studio/publish/app-signing
- **ProGuard Manual:** https://www.guardsquare.com/manual/home
- **Netlify Docs:** https://docs.netlify.com/
