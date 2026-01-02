#!/usr/bin/env bash
# tests/unit/test_plugin_marketplace.sh: Unit tests for Plugin Marketplace configuration

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

# ============================================================================
# Test: parse_plugin_marketplaces
# ============================================================================

# Test 1: Parse valid marketplace configuration
print_test_header "Test 1: Parse valid marketplace configuration"
mkdir -p "${TEST_DIR}/app1"
cat > "${TEST_DIR}/app1/.claude-code-config.yml" <<'EOF'
claudeCode:
  enabled: true
  pluginMarketplaces:
    - name: my-marketplace
      source: https://github.com/org/plugins.git
      branch: main
      plugins:
        - plugin-one
        - plugin-two
    - name: other-marketplace
      source: git@github.com:company/tools.git
      plugins:
        - company-tool
EOF

output_file="${TEST_DIR}/app1/marketplaces.json"
if parse_plugin_marketplaces "${TEST_DIR}/app1/.claude-code-config.yml" "${output_file}"; then
    if [ -f "${output_file}" ]; then
        # Check that we got the expected marketplace data
        if grep -q '"my-marketplace"' "${output_file}" && \
           grep -q '"other-marketplace"' "${output_file}" && \
           grep -q '"plugin-one"' "${output_file}" && \
           grep -q '"plugin-two"' "${output_file}"; then
            assert_success "Should parse valid marketplace configuration"
        else
            assert_failure "Should parse valid marketplace configuration (missing expected data)"
        fi
    else
        assert_failure "Should parse valid marketplace configuration (output file not created)"
    fi
else
    assert_failure "Should parse valid marketplace configuration (function returned error)"
fi

# Test 2: Parse config without marketplaces section
print_test_header "Test 2: Parse config without marketplaces section"
mkdir -p "${TEST_DIR}/app2"
cat > "${TEST_DIR}/app2/.claude-code-config.yml" <<'EOF'
claudeCode:
  enabled: true
  mcpServers: []
EOF

output_file="${TEST_DIR}/app2/marketplaces.json"
# Function returns 1 when no marketplaces configured, but should still create empty array
parse_plugin_marketplaces "${TEST_DIR}/app2/.claude-code-config.yml" "${output_file}" || true
if [ -f "${output_file}" ] && grep -q '\[\]' "${output_file}"; then
    assert_success "Should return empty array when no marketplaces configured"
else
    assert_failure "Should return empty array when no marketplaces configured"
fi

# Test 3: Parse config with empty marketplaces array
print_test_header "Test 3: Parse config with empty marketplaces array"
mkdir -p "${TEST_DIR}/app3"
cat > "${TEST_DIR}/app3/.claude-code-config.yml" <<'EOF'
claudeCode:
  enabled: true
  pluginMarketplaces: []
EOF

output_file="${TEST_DIR}/app3/marketplaces.json"
# Note: Empty array still triggers the section detection, so function may return 0 or 1
parse_plugin_marketplaces "${TEST_DIR}/app3/.claude-code-config.yml" "${output_file}" || true
if [ -f "${output_file}" ] && grep -q '\[\]' "${output_file}"; then
    assert_success "Should handle empty marketplaces array"
else
    assert_failure "Should handle empty marketplaces array"
fi

# Test 4: Handle non-existent config file
print_test_header "Test 4: Handle non-existent config file"
output_file="${TEST_DIR}/nonexistent-output.json"
if ! parse_plugin_marketplaces "${TEST_DIR}/nonexistent.yml" "${output_file}"; then
    if [ -f "${output_file}" ] && grep -q '\[\]' "${output_file}"; then
        assert_success "Should handle non-existent config file gracefully"
    else
        assert_failure "Should handle non-existent config file gracefully"
    fi
else
    assert_failure "Should return error for non-existent config file"
fi

# ============================================================================
# Test: validate_marketplace
# ============================================================================

# Test 5: Validate marketplace with plugins/ structure
print_test_header "Test 5: Validate marketplace with plugins/ structure"
mkdir -p "${TEST_DIR}/marketplace1/plugins/my-plugin"
cat > "${TEST_DIR}/marketplace1/plugins/my-plugin/plugin.json" <<'EOF'
{"name": "my-plugin", "version": "1.0.0"}
EOF

output=$(validate_marketplace "${TEST_DIR}/marketplace1" 2>&1)
if echo "${output}" | grep -q "Found 1 plugin"; then
    assert_success "Should validate marketplace with plugins/ structure"
else
    assert_failure "Should validate marketplace with plugins/ structure"
fi

# Test 6: Validate marketplace with root-level plugin structure
print_test_header "Test 6: Validate marketplace with root-level plugin structure"
mkdir -p "${TEST_DIR}/marketplace2/another-plugin"
cat > "${TEST_DIR}/marketplace2/another-plugin/plugin.json" <<'EOF'
{"name": "another-plugin", "version": "1.0.0"}
EOF

output=$(validate_marketplace "${TEST_DIR}/marketplace2" 2>&1)
if echo "${output}" | grep -q "Found 1 plugin"; then
    assert_success "Should validate marketplace with root-level plugin structure"
else
    assert_failure "Should validate marketplace with root-level plugin structure"
fi

# Test 7: Validate empty marketplace (no plugins)
print_test_header "Test 7: Validate empty marketplace (no plugins)"
mkdir -p "${TEST_DIR}/marketplace3"

output=$(validate_marketplace "${TEST_DIR}/marketplace3" 2>&1)
# Should not fail, just warn
if validate_marketplace "${TEST_DIR}/marketplace3" >/dev/null 2>&1; then
    assert_success "Should not fail on empty marketplace"
else
    assert_failure "Should not fail on empty marketplace"
fi

# Test 8: Handle non-existent marketplace directory
print_test_header "Test 8: Handle non-existent marketplace directory"
if ! validate_marketplace "${TEST_DIR}/nonexistent-marketplace" >/dev/null 2>&1; then
    assert_success "Should fail for non-existent marketplace directory"
else
    assert_failure "Should fail for non-existent marketplace directory"
fi

# ============================================================================
# Test: find_plugin_in_marketplace
# ============================================================================

# Test 9: Find plugin in plugins/ subdirectory
print_test_header "Test 9: Find plugin in plugins/ subdirectory"
mkdir -p "${TEST_DIR}/find-test1/plugins/target-plugin"
cat > "${TEST_DIR}/find-test1/plugins/target-plugin/plugin.json" <<'EOF'
{"name": "target-plugin"}
EOF

result=$(find_plugin_in_marketplace "${TEST_DIR}/find-test1" "target-plugin")
if [ "${result}" = "${TEST_DIR}/find-test1/plugins/target-plugin" ]; then
    assert_success "Should find plugin in plugins/ subdirectory"
else
    assert_failure "Should find plugin in plugins/ subdirectory"
fi

# Test 10: Find plugin at root level
print_test_header "Test 10: Find plugin at root level"
mkdir -p "${TEST_DIR}/find-test2/root-plugin"
cat > "${TEST_DIR}/find-test2/root-plugin/plugin.json" <<'EOF'
{"name": "root-plugin"}
EOF

result=$(find_plugin_in_marketplace "${TEST_DIR}/find-test2" "root-plugin")
if [ "${result}" = "${TEST_DIR}/find-test2/root-plugin" ]; then
    assert_success "Should find plugin at root level"
else
    assert_failure "Should find plugin at root level"
fi

# Test 11: Return empty for non-existent plugin
print_test_header "Test 11: Return empty for non-existent plugin"
mkdir -p "${TEST_DIR}/find-test3"
result=$(find_plugin_in_marketplace "${TEST_DIR}/find-test3" "nonexistent-plugin" || true)
if [ -z "${result}" ]; then
    assert_success "Should return empty for non-existent plugin"
else
    assert_failure "Should return empty for non-existent plugin"
fi

# ============================================================================
# Test: install_plugin
# ============================================================================

# Test 12: Install plugin successfully
print_test_header "Test 12: Install plugin successfully"
mkdir -p "${TEST_DIR}/source-plugin"
cat > "${TEST_DIR}/source-plugin/plugin.json" <<'EOF'
{"name": "test-plugin", "version": "1.0.0"}
EOF
cat > "${TEST_DIR}/source-plugin/README.md" <<'EOF'
# Test Plugin
EOF
mkdir -p "${TEST_DIR}/target-plugins"

if install_plugin "${TEST_DIR}/source-plugin" "${TEST_DIR}/target-plugins" "test-plugin" >/dev/null 2>&1; then
    if [ -f "${TEST_DIR}/target-plugins/test-plugin/plugin.json" ] && \
       [ -f "${TEST_DIR}/target-plugins/test-plugin/README.md" ]; then
        assert_success "Should install plugin with all files"
    else
        assert_failure "Should install plugin with all files"
    fi
else
    assert_failure "Should install plugin successfully"
fi

# Test 13: Fail for non-existent source
print_test_header "Test 13: Fail for non-existent source"
install_result=0
install_plugin "${TEST_DIR}/nonexistent-source" "${TEST_DIR}/target2" "plugin" >/dev/null 2>&1 || install_result=$?
if [ ${install_result} -ne 0 ]; then
    assert_success "Should fail for non-existent source directory"
else
    assert_failure "Should fail for non-existent source directory"
fi

# ============================================================================
# Test: extract_plugin_skills
# ============================================================================

# Test 14: Extract skills from plugin
print_test_header "Test 14: Extract skills from plugin"
mkdir -p "${TEST_DIR}/plugin-with-skills/skills/my-skill"
cat > "${TEST_DIR}/plugin-with-skills/skills/my-skill/SKILL.md" <<'EOF'
---
name: my-skill
description: A skill from a plugin
---

# My Skill
EOF
mkdir -p "${TEST_DIR}/extracted-skills"

result=$(extract_plugin_skills "${TEST_DIR}/plugin-with-skills" "${TEST_DIR}/extracted-skills" "test-plugin")
if [ -f "${TEST_DIR}/extracted-skills/my-skill/SKILL.md" ]; then
    assert_success "Should extract skills from plugin"
else
    assert_failure "Should extract skills from plugin"
fi

# Test 15: Handle plugin without skills
print_test_header "Test 15: Handle plugin without skills"
mkdir -p "${TEST_DIR}/plugin-no-skills"
cat > "${TEST_DIR}/plugin-no-skills/plugin.json" <<'EOF'
{"name": "no-skills"}
EOF
mkdir -p "${TEST_DIR}/empty-skills-target"

result=$(extract_plugin_skills "${TEST_DIR}/plugin-no-skills" "${TEST_DIR}/empty-skills-target" "no-skills-plugin")
# Should return 0 and not create any skill directories
count=$(find "${TEST_DIR}/empty-skills-target" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | wc -l | tr -d ' ')
if [ "${count}" = "0" ]; then
    assert_success "Should handle plugin without skills"
else
    assert_failure "Should handle plugin without skills"
fi

# Test 16: Skip existing skills
print_test_header "Test 16: Skip existing skills"
mkdir -p "${TEST_DIR}/plugin-dupe-skill/skills/existing-skill"
cat > "${TEST_DIR}/plugin-dupe-skill/skills/existing-skill/SKILL.md" <<'EOF'
---
name: existing-skill
description: New version
---
EOF
mkdir -p "${TEST_DIR}/skills-with-existing/existing-skill"
cat > "${TEST_DIR}/skills-with-existing/existing-skill/SKILL.md" <<'EOF'
---
name: existing-skill
description: Original version
---
EOF

extract_plugin_skills "${TEST_DIR}/plugin-dupe-skill" "${TEST_DIR}/skills-with-existing" "dupe-plugin" >/dev/null 2>&1
# Check that original skill was preserved
if grep -q "Original version" "${TEST_DIR}/skills-with-existing/existing-skill/SKILL.md"; then
    assert_success "Should not overwrite existing skills"
else
    assert_failure "Should not overwrite existing skills"
fi

# ============================================================================
# Test: extract_all_plugin_skills
# ============================================================================

# Test 17: Extract skills from multiple plugins
print_test_header "Test 17: Extract skills from multiple plugins"
mkdir -p "${TEST_DIR}/multi-plugins/plugin-a/skills/skill-a"
cat > "${TEST_DIR}/multi-plugins/plugin-a/skills/skill-a/SKILL.md" <<'EOF'
---
name: skill-a
description: Skill from plugin A
---
EOF
mkdir -p "${TEST_DIR}/multi-plugins/plugin-b/skills/skill-b"
cat > "${TEST_DIR}/multi-plugins/plugin-b/skills/skill-b/SKILL.md" <<'EOF'
---
name: skill-b
description: Skill from plugin B
---
EOF
mkdir -p "${TEST_DIR}/multi-skills-target"

result=$(extract_all_plugin_skills "${TEST_DIR}/multi-plugins" "${TEST_DIR}/multi-skills-target")
if [ -f "${TEST_DIR}/multi-skills-target/skill-a/SKILL.md" ] && \
   [ -f "${TEST_DIR}/multi-skills-target/skill-b/SKILL.md" ]; then
    assert_success "Should extract skills from multiple plugins"
else
    assert_failure "Should extract skills from multiple plugins"
fi

# ============================================================================
# Test: configure_plugin_marketplaces (integration)
# ============================================================================

# Test 18: Configure with no config file
print_test_header "Test 18: Configure with no config file"
mkdir -p "${TEST_DIR}/no-config-app"
mkdir -p "${TEST_DIR}/no-config-deps/0"

output=$(configure_plugin_marketplaces "${TEST_DIR}/no-config-app" "${TEST_DIR}/no-config-deps" "0" 2>&1)
if echo "${output}" | grep -q "No configuration file found"; then
    assert_success "Should handle missing config file gracefully"
else
    assert_failure "Should handle missing config file gracefully"
fi

# Test 19: Configure with no marketplaces in config
print_test_header "Test 19: Configure with no marketplaces in config"
mkdir -p "${TEST_DIR}/empty-market-app"
cat > "${TEST_DIR}/empty-market-app/.claude-code-config.yml" <<'EOF'
claudeCode:
  enabled: true
  mcpServers: []
EOF
mkdir -p "${TEST_DIR}/empty-market-deps/0"

output=$(configure_plugin_marketplaces "${TEST_DIR}/empty-market-app" "${TEST_DIR}/empty-market-deps" "0" 2>&1)
if echo "${output}" | grep -q "No plugin marketplaces configured"; then
    assert_success "Should handle config without marketplaces"
else
    assert_failure "Should handle config without marketplaces"
fi

# Test 20: Parse marketplace config with default branch
print_test_header "Test 20: Parse marketplace config with default branch"
mkdir -p "${TEST_DIR}/default-branch-app"
cat > "${TEST_DIR}/default-branch-app/.claude-code-config.yml" <<'EOF'
claudeCode:
  enabled: true
  pluginMarketplaces:
    - name: test-market
      source: https://github.com/test/repo.git
      plugins:
        - test-plugin
EOF

output_file="${TEST_DIR}/default-branch-app/marketplaces.json"
if parse_plugin_marketplaces "${TEST_DIR}/default-branch-app/.claude-code-config.yml" "${output_file}"; then
    # Check that default branch is "main"
    if grep -q '"main"' "${output_file}"; then
        assert_success "Should use 'main' as default branch"
    else
        assert_failure "Should use 'main' as default branch"
    fi
else
    assert_failure "Should parse marketplace config with default branch"
fi

# Test 21: Parse marketplace config with mcpServers section (should not confuse them)
print_test_header "Test 21: Parse marketplace config followed by mcpServers section"
mkdir -p "${TEST_DIR}/mixed-config-app"
cat > "${TEST_DIR}/mixed-config-app/.claude-code-config.yml" <<'EOF'
claudeCode:
  enabled: true
  pluginMarketplaces:
    - name: my-marketplace
      source: https://github.com/org/plugins.git
      plugins:
        - plugin-one

  mcpServers:
    - name: github
      type: sse
      url: "https://example.com/sse"
EOF

output_file="${TEST_DIR}/mixed-config-app/marketplaces.json"
if parse_plugin_marketplaces "${TEST_DIR}/mixed-config-app/.claude-code-config.yml" "${output_file}"; then
    # Should only find 1 marketplace, not 2 (mcpServers should not be parsed as marketplace)
    marketplace_count=$(python3 -c "import json; data=json.load(open('${output_file}')); print(len(data))" 2>/dev/null || echo "0")
    if [ "${marketplace_count}" = "1" ]; then
        # Also verify the mcpServers name is NOT in the output
        if ! grep -q '"github"' "${output_file}"; then
            assert_success "Should not confuse mcpServers with plugin marketplaces"
        else
            assert_failure "Should not confuse mcpServers with plugin marketplaces (found github in output)"
        fi
    else
        assert_failure "Should find exactly 1 marketplace, found ${marketplace_count}"
    fi
else
    assert_failure "Should parse marketplace config with mcpServers section"
fi

# ============================================================================
# Print summary
# ============================================================================
echo ""
echo "======================================"
echo "Plugin Marketplace Test Summary"
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

