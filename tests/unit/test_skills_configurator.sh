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

# Test 1: Validate HTTPS git URL
print_test_header "Test 1: Validate HTTPS git URL"
if validate_git_url "https://github.com/example/repo.git" >/dev/null 2>&1; then
    assert_success "Should accept https:// URLs"
else
    assert_failure "Should accept https:// URLs"
fi

# Test 2: Reject file:// URL
print_test_header "Test 2: Reject file:// URL"
if validate_git_url "file:///local/path" >/dev/null 2>&1; then
    assert_failure "Should reject file:// URLs"
else
    assert_success "Should reject file:// URLs"
fi

# Test 3: Reject git:// URL
print_test_header "Test 3: Reject git:// URL"
if validate_git_url "git://github.com/example/repo.git" >/dev/null 2>&1; then
    assert_failure "Should reject git:// URLs"
else
    assert_success "Should reject git:// URLs"
fi

# Test 4: Reject http:// URL
print_test_header "Test 4: Reject http:// URL"
if validate_git_url "http://github.com/example/repo.git" >/dev/null 2>&1; then
    assert_failure "Should reject http:// URLs (must be https)"
else
    assert_success "Should reject http:// URLs (must be https)"
fi

# Test 5: Parse empty Skills config
print_test_header "Test 5: Parse empty Skills config"
cat > "${TEST_DIR}/config.yml" <<'EOF'
claudeCode:
  enabled: true
  mcpServers: []
EOF

if parse_skills_config "${TEST_DIR}/config.yml" "${TEST_DIR}/skills.json" >/dev/null 2>&1; then
    assert_failure "Should return error when no skills section exists"
else
    assert_success "Should return error when no skills section exists"
fi

# Test 6: Parse config with single git Skill
print_test_header "Test 6: Parse config with single git Skill"
cat > "${TEST_DIR}/config.yml" <<'EOF'
claudeCode:
  enabled: true
  skills:
    - name: test-skill
      git:
        url: https://github.com/example/skills.git
        ref: main
        path: skills/
EOF

if parse_skills_config "${TEST_DIR}/config.yml" "${TEST_DIR}/skills.json" >/dev/null 2>&1; then
    if [ -f "${TEST_DIR}/skills.json" ] && \
       grep -q '"name": "test-skill"' "${TEST_DIR}/skills.json" && \
       grep -q '"url": "https://github.com/example/skills.git"' "${TEST_DIR}/skills.json"; then
        assert_success "Should parse single git Skill"
    else
        assert_failure "Should parse single git Skill"
    fi
else
    assert_failure "parse_skills_config should succeed"
fi

# Test 7: Parse config with multiple git Skills
print_test_header "Test 7: Parse config with multiple git Skills"
cat > "${TEST_DIR}/config.yml" <<'EOF'
claudeCode:
  enabled: true
  skills:
    - name: skill-one
      git:
        url: https://github.com/org/skill-one.git
        ref: v1.0.0
    - name: skill-two
      git:
        url: https://github.com/org/skill-two.git
        ref: main
        path: my-skill/
EOF

if parse_skills_config "${TEST_DIR}/config.yml" "${TEST_DIR}/skills.json" >/dev/null 2>&1; then
    if [ -f "${TEST_DIR}/skills.json" ] && \
       grep -q '"skill-one"' "${TEST_DIR}/skills.json" && \
       grep -q '"skill-two"' "${TEST_DIR}/skills.json"; then
        assert_success "Should parse multiple git Skills"
    else
        assert_failure "Should parse multiple git Skills"
    fi
else
    assert_failure "parse_skills_config should succeed"
fi

# Test 8: Validate valid Skill structure
print_test_header "Test 8: Validate valid Skill structure"
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

# Test 9: Reject Skill without SKILL.md
print_test_header "Test 9: Reject Skill without SKILL.md"
mkdir -p "${TEST_DIR}/invalid-skill"

if validate_skill_structure "${TEST_DIR}/invalid-skill" >/dev/null 2>&1; then
    assert_failure "Should reject Skill without SKILL.md"
else
    assert_success "Should reject Skill without SKILL.md"
fi

# Test 10: Reject SKILL.md without frontmatter
print_test_header "Test 10: Reject SKILL.md without frontmatter"
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

# Test 11: Reject SKILL.md missing name field
print_test_header "Test 11: Reject SKILL.md missing name field"
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

# Test 12: Reject SKILL.md missing description field
print_test_header "Test 12: Reject SKILL.md missing description field"
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

# Test 13: List installed Skills
print_test_header "Test 13: List installed Skills"
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

# Test 14: List empty Skills directory
print_test_header "Test 14: List empty Skills directory"
mkdir -p "${TEST_DIR}/empty-skills"

output=$(list_installed_skills "${TEST_DIR}/empty-skills" 2>&1)
if echo "${output}" | grep -q "No Skills found"; then
    assert_success "Should report no Skills in empty directory"
else
    assert_failure "Should report no Skills in empty directory"
fi

# Test 15: List non-existent Skills directory
print_test_header "Test 15: List non-existent Skills directory"
output=$(list_installed_skills "${TEST_DIR}/nonexistent" 2>&1)
if echo "${output}" | grep -q "No Skills directory found"; then
    assert_success "Should handle non-existent Skills directory"
else
    assert_failure "Should handle non-existent Skills directory"
fi

# Test 16: configure_skills with no config
print_test_header "Test 16: configure_skills with no config"
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

# Test 17: configure_skills with bundled Skills
print_test_header "Test 17: configure_skills with bundled Skills"
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

# Test 18: Parse Skills with optional ref field
print_test_header "Test 18: Parse Skills with optional ref field"
cat > "${TEST_DIR}/config-no-ref.yml" <<'EOF'
claudeCode:
  enabled: true
  skills:
    - name: skill-no-ref
      git:
        url: https://github.com/example/skill.git
EOF

if parse_skills_config "${TEST_DIR}/config-no-ref.yml" "${TEST_DIR}/skills-no-ref.json" >/dev/null 2>&1; then
    if [ -f "${TEST_DIR}/skills-no-ref.json" ] && \
       grep -q '"name": "skill-no-ref"' "${TEST_DIR}/skills-no-ref.json" && \
       grep -q '"url": "https://github.com/example/skill.git"' "${TEST_DIR}/skills-no-ref.json"; then
        assert_success "Should parse Skill without ref field"
    else
        assert_failure "Should parse Skill without ref field"
    fi
else
    assert_failure "parse_skills_config should succeed"
fi

# Test 19: Parse Skills with optional path field
print_test_header "Test 19: Parse Skills with optional path field"
cat > "${TEST_DIR}/config-no-path.yml" <<'EOF'
claudeCode:
  enabled: true
  skills:
    - name: skill-no-path
      git:
        url: https://github.com/example/skill.git
        ref: main
EOF

if parse_skills_config "${TEST_DIR}/config-no-path.yml" "${TEST_DIR}/skills-no-path.json" >/dev/null 2>&1; then
    if [ -f "${TEST_DIR}/skills-no-path.json" ] && \
       grep -q '"name": "skill-no-path"' "${TEST_DIR}/skills-no-path.json"; then
        assert_success "Should parse Skill without path field"
    else
        assert_failure "Should parse Skill without path field"
    fi
else
    assert_failure "parse_skills_config should succeed"
fi

# Test 20: Validate Skill with supporting files
print_test_header "Test 20: Validate Skill with supporting files"
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

