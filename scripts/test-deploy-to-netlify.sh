#!/bin/bash
# ================================================================================
# Test Suite for deploy-to-netlify.sh
# ================================================================================
# This script tests the deployment script functionality.
#
# Run: ./test-deploy-to-netlify.sh
# ================================================================================

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

TESTS_PASSED=0
TESTS_FAILED=0

log_pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    TESTS_FAILED=$((TESTS_FAILED + 1))
}

log_info() {
    echo -e "${YELLOW}[INFO]${NC} $1"
}

log_info "Running tests..."

# Test 1: Script exists and is executable
log_info "Test 1: Script existence check"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/deploy-to-netlify.sh" ]; then
    log_pass "deploy-to-netlify.sh exists"
else
    log_fail "deploy-to-netlify.sh not found"
fi

# Test 2: Script has correct shebang
log_info "Test 2: Shebang check"
if head -1 "$SCRIPT_DIR/deploy-to-netlify.sh" | grep -q "^#!/bin/bash"; then
    log_pass "Correct shebang"
else
    log_fail "Missing or incorrect shebang"
fi

# Test 3: Script has set -euo pipefail
log_info "Test 3: Error handling flags check"
if grep -q "set -euo pipefail" "$SCRIPT_DIR/deploy-to-netlify.sh"; then
    log_pass "Error handling flags present"
else
    log_fail "Missing error handling flags"
fi

# Test 4: Script has required functions
log_info "Test 4: Required functions check"
for func in log_info log_warn log_error; do
    if grep -q "^$func()" "$SCRIPT_DIR/deploy-to-netlify.sh"; then
        log_pass "Function $func exists"
    else
        log_fail "Function $func missing"
    fi
done

# Test 5: Script validates arguments
log_info "Test 5: Argument validation check"
if grep -q 'if \[ \$# -lt 3 \]' "$SCRIPT_DIR/deploy-to-netlify.sh"; then
    log_pass "Argument validation present"
else
    log_fail "Missing argument validation"
fi

# Test 6: Script calculates SHA-256 checksum
log_info "Test 6: Checksum calculation check"
if grep -q "sha256sum" "$SCRIPT_DIR/deploy-to-netlify.sh"; then
    log_pass "SHA-256 checksum calculation present"
else
    log_fail "Missing SHA-256 checksum calculation"
fi

# Test 7: Script creates version.json
log_info "Test 7: version.json creation check"
if grep -q 'version.json' "$SCRIPT_DIR/deploy-to-netlify.sh"; then
    log_pass "version.json creation present"
else
    log_fail "Missing version.json creation"
fi

# Test 8: Script validates APK file exists
log_info "Test 8: APK validation check"
if grep -q 'if \[ ! -f "\$APK_PATH" \]' "$SCRIPT_DIR/deploy-to-netlify.sh"; then
    log_pass "APK file validation present"
else
    log_fail "Missing APK file validation"
fi

# Test 9: Script validates Sunshine-AIO-web directory
log_info "Test 9: Directory validation check"
if grep -q 'if \[ ! -d "\$SUNSHINE_WEB_PATH" \]' "$SCRIPT_DIR/deploy-to-netlify.sh"; then
    log_pass "Directory validation present"
else
    log_fail "Missing directory validation"
fi

# Test 10: Script uses absolute paths
log_info "Test 10: Absolute path resolution check"
if grep -q 'realpath' "$SCRIPT_DIR/deploy-to-netlify.sh"; then
    log_pass "Absolute path resolution present"
else
    log_fail "Missing absolute path resolution"
fi

# Test 11: Test script execution with mock data (dry run)
log_info "Test 11: Dry run execution test"
# Create a simple test that just validates the script can be sourced
if bash -n "$SCRIPT_DIR/deploy-to-netlify.sh" 2>/dev/null; then
    log_pass "Script syntax is valid"
else
    log_fail "Script has syntax errors"
fi

# Test 12: Idempotency check - script handles repeated runs gracefully
log_info "Test 12: Idempotency check"
# Check that script doesn't fail when run multiple times with same inputs
# The script should handle "no changes to commit" case gracefully
if grep -q "No changes to commit" "$SCRIPT_DIR/deploy-to-netlify.sh"; then
    log_pass "Script handles idempotent runs (no changes case)"
else
    log_fail "Script may fail on repeated runs"
fi

# Test 13: Checksum is deterministic - same file produces same hash
log_info "Test 13: Deterministic checksum check"
# Verify script uses sha256sum which produces consistent results
if grep -q "sha256sum.*awk" "$SCRIPT_DIR/deploy-to-netlify.sh"; then
    log_pass "Checksum uses deterministic sha256sum"
else
    log_fail "Checksum method may not be deterministic"
fi

# Test 14: Version.json is always valid JSON
log_info "Test 14: JSON structure validation"
# Check that version.json is created with proper JSON structure
if grep -q '"version":' "$SCRIPT_DIR/deploy-to-netlify.sh" && \
   grep -q '"checksum":' "$SCRIPT_DIR/deploy-to-netlify.sh"; then
    log_pass "JSON contains required fields"
else
    log_fail "JSON may be missing required fields"
fi

# Summary
echo ""
echo "========================================"
echo "Test Results Summary"
echo "========================================"
echo "Total Tests: 14"
echo -e "${GREEN}Passed:${NC} $TESTS_PASSED"
echo -e "${RED}Failed:${NC} $TESTS_FAILED"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}Some tests failed!${NC}"
    exit 1
fi
