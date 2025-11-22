#!/usr/bin/env bash
# tests/unit/test_detect.sh: Unit tests for bin/detect script

set -e

# Test framework setup
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BP_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
DETECT_SCRIPT="${BP_DIR}/bin/detect"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Helper functions
print_test_header() {
    echo -e "\n${YELLOW}Running: $1${NC}"
}

assert_exit_code() {
    local expected=$1
    local actual=$2
    local test_name=$3

    TESTS_RUN=$((TESTS_RUN + 1))

    if [ "${expected}" -eq "${actual}" ]; then
        echo -e "${GREEN}✓ PASS${NC}: ${test_name}"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗ FAIL${NC}: ${test_name}"
        echo "  Expected exit code: ${expected}, Got: ${actual}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

# Create temporary test directory
TEST_DIR=$(mktemp -d)
trap "rm -rf ${TEST_DIR}" EXIT

# Test 1: Detect with .claude-code-config.yml
print_test_header "Test 1: Detection with .claude-code-config.yml file"
touch "${TEST_DIR}/.claude-code-config.yml"
EXIT_CODE=0
"${DETECT_SCRIPT}" "${TEST_DIR}" > /dev/null 2>&1 || EXIT_CODE=$?
assert_exit_code 0 ${EXIT_CODE} "Should detect when .claude-code-config.yml exists"
rm -f "${TEST_DIR}/.claude-code-config.yml"

# Test 2: Detect with environment variable
print_test_header "Test 2: Detection with CLAUDE_CODE_ENABLED=true"
export CLAUDE_CODE_ENABLED=true
EXIT_CODE=0
"${DETECT_SCRIPT}" "${TEST_DIR}" > /dev/null 2>&1 || EXIT_CODE=$?
assert_exit_code 0 ${EXIT_CODE} "Should detect when CLAUDE_CODE_ENABLED=true"
unset CLAUDE_CODE_ENABLED

# Test 3: Detect with manifest.yml
print_test_header "Test 3: Detection with manifest.yml containing claude-code-enabled"
cat > "${TEST_DIR}/manifest.yml" <<EOF
---
applications:
- name: test-app
  claude-code-enabled: true
EOF
EXIT_CODE=0
"${DETECT_SCRIPT}" "${TEST_DIR}" > /dev/null 2>&1 || EXIT_CODE=$?
assert_exit_code 0 ${EXIT_CODE} "Should detect when manifest.yml has claude-code-enabled: true"
rm -f "${TEST_DIR}/manifest.yml"

# Test 4: No detection when none of the conditions are met
print_test_header "Test 4: No detection when conditions not met"
EXIT_CODE=0
"${DETECT_SCRIPT}" "${TEST_DIR}" > /dev/null 2>&1 || EXIT_CODE=$?
assert_exit_code 1 ${EXIT_CODE} "Should not detect when no conditions are met"

# Test 5: Output format validation
print_test_header "Test 5: Output format validation"
export CLAUDE_CODE_ENABLED=true
OUTPUT=$("${DETECT_SCRIPT}" "${TEST_DIR}" 2>&1)
if [[ "${OUTPUT}" =~ "Claude Code CLI" ]]; then
    TESTS_RUN=$((TESTS_RUN + 1))
    TESTS_PASSED=$((TESTS_PASSED + 1))
    echo -e "${GREEN}✓ PASS${NC}: Output contains buildpack name"
else
    TESTS_RUN=$((TESTS_RUN + 1))
    TESTS_FAILED=$((TESTS_FAILED + 1))
    echo -e "${RED}✗ FAIL${NC}: Output should contain buildpack name"
fi
unset CLAUDE_CODE_ENABLED

# Print summary
echo -e "\n${YELLOW}═══════════════════════════════════════${NC}"
echo -e "${YELLOW}Test Summary${NC}"
echo -e "${YELLOW}═══════════════════════════════════════${NC}"
echo -e "Tests Run:    ${TESTS_RUN}"
echo -e "${GREEN}Tests Passed: ${TESTS_PASSED}${NC}"

if [ ${TESTS_FAILED} -gt 0 ]; then
    echo -e "${RED}Tests Failed: ${TESTS_FAILED}${NC}"
    exit 1
else
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
fi
