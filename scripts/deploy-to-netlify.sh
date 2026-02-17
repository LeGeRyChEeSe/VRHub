#!/bin/bash
# ================================================================================
# Deploy APK to Netlify (Sunshine-AIO-web)
# ================================================================================
# This script automates the deployment of Rookie On Quest APK to Netlify.
# It calculates checksums, updates version.json, and commits to the repository.
#
# Usage: ./deploy-to-netlify.sh <apk_path> <version> <sunshine_aio_web_path> [changelog]
#
# Example:
#   ./deploy-to-netlify.sh "./RookieOnQuest-v2.5.0.apk" "2.5.0" "../Sunshine-AIO-web" "Bug fixes"
# ================================================================================

set -euo pipefail

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Validate arguments
if [ $# -lt 3 ]; then
    log_error "Usage: $0 <apk_path> <version> <sunshine_aio_web_path> [changelog]"
    log_error "Example: $0 './RookieOnQuest-v2.5.0.apk' '2.5.0' '../Sunshine-AIO-web' 'Bug fixes'"
    exit 1
fi

APK_PATH="$1"
VERSION="$2"
SUNSHINE_WEB_PATH="$3"
CHANGELOG="${4:-Release $VERSION}"

# Validate APK exists
if [ ! -f "$APK_PATH" ]; then
    log_error "APK file not found: $APK_PATH"
    exit 1
fi

# Validate Sunshine-AIO-web directory exists
if [ ! -d "$SUNSHINE_WEB_PATH" ]; then
    log_error "Sunshine-AIO-web directory not found: $SUNSHINE_WEB_PATH"
    log_error "Please ensure Sunshine-AIO-web is cloned and the path is correct"
    exit 1
fi

# Resolve to absolute paths
APK_PATH="$(realpath "$APK_PATH")"
SUNSHINE_WEB_PATH="$(realpath "$SUNSHINE_WEB_PATH")"

log_info "Starting Netlify deployment..."
log_info "  APK: $APK_PATH"
log_info "  Version: $VERSION"
log_info "  Sunshine-AIO-web: $SUNSHINE_WEB_PATH"

# Calculate SHA-256 checksum
log_info "Calculating SHA-256 checksum..."
CHECKSUM=$(sha256sum "$APK_PATH" | awk '{print $1}')
log_info "  Checksum: $CHECKSUM"

# Determine APK filename (use version in filename)
APK_FILENAME="RookieOnQuest_${VERSION}.apk"
UPDATES_DIR="$SUNSHINE_WEB_PATH/public/updates/rookie"

# Create directory if it doesn't exist
log_info "Creating updates directory..."
mkdir -p "$UPDATES_DIR"

# Copy APK to updates directory
log_info "Copying APK to updates directory..."
cp "$APK_PATH" "$UPDATES_DIR/$APK_FILENAME"
log_info "  Copied: $UPDATES_DIR/$APK_FILENAME"

# Generate timestamp
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Create or update version.json
VERSION_JSON_PATH="$UPDATES_DIR/version.json"
log_info "Updating version.json..."

# Build the download URL - using the Netlify site URL pattern
# The actual domain will be configured via environment or default
DOWNLOAD_URL="/updates/rookie/$APK_FILENAME"

# Create version.json content
cat > "$VERSION_JSON_PATH" << EOF
{
    "version": "$VERSION",
    "changelog": "$CHANGELOG",
    "downloadUrl": "$DOWNLOAD_URL",
    "checksum": "$CHECKSUM",
    "timestamp": "$TIMESTAMP"
}
EOF

log_info "  Updated: $VERSION_JSON_PATH"
log_info "  Content:"
cat "$VERSION_JSON_PATH"

# Check if git is available and we're in a git repository
if [ -d "$SUNSHINE_WEB_PATH/.git" ]; then
    log_info "Git repository detected - ready for commit"

    # Check for changes
    cd "$SUNSHINE_WEB_PATH"
    if git diff --quiet && git diff --cached --quiet; then
        log_warn "No changes to commit"
    else
        log_info "Changes detected - commit and push required"
        log_info "  Files to commit:"
        git status --short
    fi
else
    log_warn "Not a git repository - skipping commit"
fi

log_info "Deployment preparation complete!"
log_info "  APK: $UPDATES_DIR/$APK_FILENAME"
log_info "  Version: $VERSION"
log_info "  Checksum: $CHECKSUM"
log_info ""
log_info "Next steps:"
log_info "  1. Review changes: cd $SUNSHINE_WEB_PATH && git status"
log_info "  2. Commit: git add -A && git commit -m 'Deploy RookieOnQuest v$VERSION'"
log_info "  3. Push: git push origin main"
log_info "  4. Netlify will auto-deploy after push"
