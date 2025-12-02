#!/usr/bin/env bash
# tests/unit/test_skills_configurator.sh: Unit tests for Skills configuration

set -e

# Test framework setup
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BP_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
LIB="${BP_DIR}/lib/claude_configurator.sh"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Source the configurator library
source "${LIB}"

# Helper functions
print_test_header() {
    echo -e "\n${YELLOW}Running: $1${NC}"
}

assert_success() {
    local test_name=$1
    TESTS_RUN=$((TESTS_RUN + 1))
    TESTS_PASSED=$((TESTS_PASSED + 1))
    echo -e "${GREEN}✓ PASS${NC}: ${test_name}"
}

assert_failure() {
    local test_name=$1
    TESTS_RUN=$((TESTS_RUN + 1))
    TESTS_FAILED=$((TESTS_FAILED + 1))
    echo -e "${RED}✗ FAIL${NC}: ${test_name}"
}

# Create temporary test directory
TEST_DIR=$(mktemp -d)
trap "rm -rf ${TEST_DIR}" EXIT

# Test 1: Validate valid Skill structure
print_test_header "Test 1: Validate valid Skill structure"
mkdir -p "${TEST_DIR}/valid-skill"
cat > "${TEST_DIR}/valid-skill/SKILL.md" <<'EOF'
---
name: test-skill
description: A test skill for validation
---

# Test Skill

This is a test skill.
EOF

if validate_skill_structure "${TEST_DIR}/valid-skill" >/dev/null 2>&1; then
    assert_success "Should validate valid Skill structure"
else
    assert_failure "Should validate valid Skill structure"
fi

# Test 2: Reject Skill without SKILL.md
print_test_header "Test 2: Reject Skill without SKILL.md"
mkdir -p "${TEST_DIR}/invalid-skill"

if validate_skill_structure "${TEST_DIR}/invalid-skill" >/dev/null 2>&1; then
    assert_failure "Should reject Skill without SKILL.md"
else
    assert_success "Should reject Skill without SKILL.md"
fi

# Test 3: Reject SKILL.md without frontmatter
print_test_header "Test 3: Reject SKILL.md without frontmatter"
mkdir -p "${TEST_DIR}/no-frontmatter"
cat > "${TEST_DIR}/no-frontmatter/SKILL.md" <<'EOF'
# My Skill

This skill has no frontmatter.
EOF

if validate_skill_structure "${TEST_DIR}/no-frontmatter" >/dev/null 2>&1; then
    assert_failure "Should reject SKILL.md without frontmatter"
else
    assert_success "Should reject SKILL.md without frontmatter"
fi

# Test 4: Reject SKILL.md missing name field
print_test_header "Test 4: Reject SKILL.md missing name field"
mkdir -p "${TEST_DIR}/no-name"
cat > "${TEST_DIR}/no-name/SKILL.md" <<'EOF'
---
description: A skill without a name
---

# Unnamed Skill
EOF

if validate_skill_structure "${TEST_DIR}/no-name" >/dev/null 2>&1; then
    assert_failure "Should reject SKILL.md missing name field"
else
    assert_success "Should reject SKILL.md missing name field"
fi

# Test 5: Reject SKILL.md missing description field
print_test_header "Test 5: Reject SKILL.md missing description field"
mkdir -p "${TEST_DIR}/no-description"
cat > "${TEST_DIR}/no-description/SKILL.md" <<'EOF'
---
name: no-desc-skill
---

# Skill Without Description
EOF

if validate_skill_structure "${TEST_DIR}/no-description" >/dev/null 2>&1; then
    assert_failure "Should reject SKILL.md missing description field"
else
    assert_success "Should reject SKILL.md missing description field"
fi

# Test 6: List installed Skills
print_test_header "Test 6: List installed Skills"
mkdir -p "${TEST_DIR}/skills-dir/skill-a"
mkdir -p "${TEST_DIR}/skills-dir/skill-b"
cat > "${TEST_DIR}/skills-dir/skill-a/SKILL.md" <<'EOF'
---
name: skill-a
description: Skill A
---
EOF
cat > "${TEST_DIR}/skills-dir/skill-b/SKILL.md" <<'EOF'
---
name: skill-b
description: Skill B
---
EOF

output=$(list_installed_skills "${TEST_DIR}/skills-dir" 2>&1)
if echo "${output}" | grep -q "skill-a" && echo "${output}" | grep -q "skill-b" && echo "${output}" | grep -q "Total Skills: 2"; then
    assert_success "Should list installed Skills"
else
    assert_failure "Should list installed Skills"
fi

# Test 7: List empty Skills directory
print_test_header "Test 7: List empty Skills directory"
mkdir -p "${TEST_DIR}/empty-skills"

output=$(list_installed_skills "${TEST_DIR}/empty-skills" 2>&1)
if echo "${output}" | grep -q "No Skills found"; then
    assert_success "Should report no Skills in empty directory"
else
    assert_failure "Should report no Skills in empty directory"
fi

# Test 8: List non-existent Skills directory
print_test_header "Test 8: List non-existent Skills directory"
output=$(list_installed_skills "${TEST_DIR}/nonexistent" 2>&1)
if echo "${output}" | grep -q "No Skills directory found"; then
    assert_success "Should handle non-existent Skills directory"
else
    assert_failure "Should handle non-existent Skills directory"
fi

# Test 9: configure_skills with no config
print_test_header "Test 9: configure_skills with no config"
rm -rf "${TEST_DIR}/app1"
mkdir -p "${TEST_DIR}/app1"
rm -rf "${TEST_DIR}/cache1"
mkdir -p "${TEST_DIR}/cache1"

unset CLAUDE_CODE_CONFIG_FILE
if configure_skills "${TEST_DIR}/app1" "${TEST_DIR}/cache1" >/dev/null 2>&1; then
    if [ -d "${TEST_DIR}/app1/.claude/skills" ]; then
        assert_success "Should create Skills directory even without config"
    else
        assert_failure "Should create Skills directory even without config"
    fi
else
    assert_failure "configure_skills should succeed"
fi

# Test 10: configure_skills with bundled Skills
print_test_header "Test 10: configure_skills with bundled Skills"
rm -rf "${TEST_DIR}/app2"
mkdir -p "${TEST_DIR}/app2/.claude/skills/bundled-skill"
cat > "${TEST_DIR}/app2/.claude/skills/bundled-skill/SKILL.md" <<'EOF'
---
name: bundled-skill
description: A bundled skill
---
EOF

rm -rf "${TEST_DIR}/cache2"
mkdir -p "${TEST_DIR}/cache2"

unset CLAUDE_CODE_CONFIG_FILE
output=$(configure_skills "${TEST_DIR}/app2" "${TEST_DIR}/cache2" 2>&1)
if echo "${output}" | grep -q "Found 1 bundled Skill" && \
   echo "${output}" | grep -q "bundled-skill"; then
    assert_success "Should detect and validate bundled Skills"
else
    assert_failure "Should detect and validate bundled Skills"
fi

# Test 11: Validate Skill with supporting files
print_test_header "Test 11: Validate Skill with supporting files"
mkdir -p "${TEST_DIR}/skill-with-files/scripts"
mkdir -p "${TEST_DIR}/skill-with-files/templates"
cat > "${TEST_DIR}/skill-with-files/SKILL.md" <<'EOF'
---
name: complex-skill
description: A skill with supporting files
---

# Complex Skill

See [scripts/helper.sh](scripts/helper.sh) for utilities.
EOF
cat > "${TEST_DIR}/skill-with-files/scripts/helper.sh" <<'EOF'
#!/bin/bash
echo "Helper script"
EOF
cat > "${TEST_DIR}/skill-with-files/templates/template.txt" <<'EOF'
Template content
EOF

if validate_skill_structure "${TEST_DIR}/skill-with-files" >/dev/null 2>&1; then
    assert_success "Should validate Skill with supporting files"
else
    assert_failure "Should validate Skill with supporting files"
fi

# Print summary
echo ""
echo "======================================"
echo "Skills Configuration Test Summary"
echo "======================================"
echo "Total tests run: ${TESTS_RUN}"
echo -e "${GREEN}Passed: ${TESTS_PASSED}${NC}"
echo -e "${RED}Failed: ${TESTS_FAILED}${NC}"
echo "======================================"

if [ ${TESTS_FAILED} -eq 0 ]; then
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}Some tests failed!${NC}"
    exit 1
fi

