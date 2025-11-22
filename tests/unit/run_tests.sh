#!/usr/bin/env bash
# tests/unit/run_tests.sh: Main test runner for unit tests

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}═══════════════════════════════════════${NC}"
echo -e "${BLUE}Claude Code Buildpack - Unit Tests${NC}"
echo -e "${BLUE}═══════════════════════════════════════${NC}"

# Track overall results
TOTAL_FAILURES=0

# Find and run all test files
for test_file in "${SCRIPT_DIR}"/test_*.sh; do
    if [ -f "${test_file}" ]; then
        echo -e "\n${YELLOW}Running $(basename "${test_file}")${NC}"
        echo "───────────────────────────────────────"

        if bash "${test_file}"; then
            echo -e "${GREEN}✓ Test suite passed${NC}"
        else
            echo -e "${RED}✗ Test suite failed${NC}"
            TOTAL_FAILURES=$((TOTAL_FAILURES + 1))
        fi
    fi
done

# Print final summary
echo -e "\n${BLUE}═══════════════════════════════════════${NC}"
echo -e "${BLUE}Final Summary${NC}"
echo -e "${BLUE}═══════════════════════════════════════${NC}"

if [ ${TOTAL_FAILURES} -eq 0 ]; then
    echo -e "${GREEN}✓ All test suites passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ ${TOTAL_FAILURES} test suite(s) failed${NC}"
    exit 1
fi
