#!/usr/bin/env bash
# tests/unit/test_mcp_configurator.sh: Unit tests for lib/mcp_configurator.sh

set -e

# Test framework setup
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BP_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MCP_LIB="${BP_DIR}/lib/mcp_configurator.sh"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Source the MCP configurator library
source "${MCP_LIB}"

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

# Test 1: Parse missing config file
print_test_header "Test 1: Parse missing config file"
unset CLAUDE_CODE_CONFIG_FILE
if parse_claude_code_config "${TEST_DIR}"; then
    assert_failure "Should fail when config file doesn't exist"
else
    assert_success "Should fail when config file doesn't exist"
fi

# Test 2: Parse existing config file
print_test_header "Test 2: Parse existing config file"
cat > "${TEST_DIR}/.claude-code-config.yml" <<'EOF'
claudeCode:
  enabled: true
  mcpServers:
    - name: filesystem
      type: stdio
      command: npx
EOF

if parse_claude_code_config "${TEST_DIR}"; then
    assert_success "Should find existing .claude-code-config.yml"
else
    assert_failure "Should find existing .claude-code-config.yml"
fi

# Test 3: Generate empty .claude.json when no config exists
print_test_header "Test 3: Generate empty .claude.json when no config exists"
rm -f "${TEST_DIR}/.claude-code-config.yml"
rm -f "${TEST_DIR}/.claude.json"
generate_claude_json "${TEST_DIR}"
if [ -f "${TEST_DIR}/.claude.json" ] && grep -q '"mcpServers": {}' "${TEST_DIR}/.claude.json"; then
    assert_success "Should create empty .claude.json when no config exists"
else
    assert_failure "Should create empty .claude.json when no config exists"
fi

# Test 4: Generate .claude.json from simple config
print_test_header "Test 4: Generate .claude.json from simple config"
cat > "${TEST_DIR}/.claude-code-config.yml" <<'EOF'
claudeCode:
  enabled: true
  mcpServers:
    - name: filesystem
      type: stdio
      command: npx
      args:
        - "-y"
        - "@modelcontextprotocol/server-filesystem"
      env:
        ALLOWED_DIRECTORIES: "/home/vcap/app,/tmp"
EOF

rm -f "${TEST_DIR}/.claude.json"
generate_claude_json "${TEST_DIR}"
if [ -f "${TEST_DIR}/.claude.json" ]; then
    if grep -q '"filesystem"' "${TEST_DIR}/.claude.json" && \
       grep -q '"type": "stdio"' "${TEST_DIR}/.claude.json" && \
       grep -q '"command": "npx"' "${TEST_DIR}/.claude.json"; then
        assert_success "Should generate .claude.json with filesystem server"
    else
        assert_failure "Should generate .claude.json with filesystem server"
    fi
else
    assert_failure "Should generate .claude.json file"
fi

# Test 5: Generate .claude.json with multiple servers
print_test_header "Test 5: Generate .claude.json with multiple servers"
cat > "${TEST_DIR}/.claude-code-config.yml" <<'EOF'
claudeCode:
  enabled: true
  mcpServers:
    - name: filesystem
      type: stdio
      command: npx
      args:
        - "-y"
        - "@modelcontextprotocol/server-filesystem"
      env:
        ALLOWED_DIRECTORIES: "/home/vcap/app"
    - name: github
      type: stdio
      command: npx
      args:
        - "-y"
        - "@modelcontextprotocol/server-github"
      env:
        GITHUB_PERSONAL_ACCESS_TOKEN: "ghp_test123"
EOF

rm -f "${TEST_DIR}/.claude.json"
generate_claude_json "${TEST_DIR}"
if [ -f "${TEST_DIR}/.claude.json" ]; then
    if grep -q '"filesystem"' "${TEST_DIR}/.claude.json" && \
       grep -q '"github"' "${TEST_DIR}/.claude.json"; then
        assert_success "Should generate .claude.json with multiple servers"
    else
        assert_failure "Should generate .claude.json with multiple servers"
    fi
else
    assert_failure "Should generate .claude.json file with multiple servers"
fi

# Test 6: Validate empty .claude.json
print_test_header "Test 6: Validate empty .claude.json"
cat > "${TEST_DIR}/.claude.json" <<'EOF'
{
  "mcpServers": {}
}
EOF

if validate_mcp_config "${TEST_DIR}" > /dev/null 2>&1; then
    assert_success "Should validate empty .claude.json"
else
    assert_failure "Should validate empty .claude.json"
fi

# Test 7: Validate .claude.json with servers
print_test_header "Test 7: Validate .claude.json with servers"
cat > "${TEST_DIR}/.claude.json" <<'EOF'
{
  "mcpServers": {
    "filesystem": {
      "type": "stdio",
      "command": "npx"
    }
  }
}
EOF

if validate_mcp_config "${TEST_DIR}" > /dev/null 2>&1; then
    assert_success "Should validate .claude.json with servers"
else
    assert_failure "Should validate .claude.json with servers"
fi

# Test 8: Validate missing .claude.json
print_test_header "Test 8: Validate missing .claude.json"
rm -f "${TEST_DIR}/.claude.json"
if validate_mcp_config "${TEST_DIR}" > /dev/null 2>&1; then
    assert_failure "Should fail when .claude.json doesn't exist"
else
    assert_success "Should fail when .claude.json doesn't exist"
fi

# Test 9: Validate malformed .claude.json
print_test_header "Test 9: Validate malformed .claude.json"
cat > "${TEST_DIR}/.claude.json" <<'EOF'
{
  "invalid": "structure"
}
EOF

if validate_mcp_config "${TEST_DIR}" > /dev/null 2>&1; then
    assert_failure "Should fail with malformed .claude.json"
else
    assert_success "Should fail with malformed .claude.json"
fi

# Test 10: Full configure_mcp_servers workflow
print_test_header "Test 10: Full configure_mcp_servers workflow"
rm -f "${TEST_DIR}/.claude.json"
cat > "${TEST_DIR}/.claude-code-config.yml" <<'EOF'
claudeCode:
  enabled: true
  mcpServers:
    - name: postgres
      type: stdio
      command: npx
      args:
        - "-y"
        - "@modelcontextprotocol/server-postgres"
      env:
        POSTGRES_CONNECTION_STRING: "postgresql://localhost/mydb"
EOF

if configure_mcp_servers "${TEST_DIR}" > /dev/null 2>&1; then
    if [ -f "${TEST_DIR}/.claude.json" ] && grep -q '"postgres"' "${TEST_DIR}/.claude.json"; then
        assert_success "Should complete full MCP configuration workflow"
    else
        assert_failure "Should complete full MCP configuration workflow"
    fi
else
    assert_failure "configure_mcp_servers should succeed"
fi

# Test 11: Parse manifest.yml (will fail in CF but test the function)
print_test_header "Test 11: Parse manifest.yml"
cat > "${TEST_DIR}/manifest.yml" <<'EOF'
---
applications:
- name: my-app
  claude-code-config:
    mcp-servers:
      - name: filesystem
        type: stdio
EOF

unset CLAUDE_CODE_MANIFEST_CONFIG
if parse_manifest_config "${TEST_DIR}"; then
    assert_success "Should detect claude-code-config in manifest.yml"
else
    assert_failure "Should detect claude-code-config in manifest.yml"
fi

# Test 12: Config file without mcpServers section
print_test_header "Test 12: Config file without mcpServers section"
cat > "${TEST_DIR}/.claude-code-config.yml" <<'EOF'
claudeCode:
  enabled: true
  version: "2.0.50"
EOF

rm -f "${TEST_DIR}/.claude.json"
OUTPUT_FILE="${TEST_DIR}/.claude.json"
if extract_mcp_servers "${TEST_DIR}/.claude-code-config.yml" "${OUTPUT_FILE}"; then
    assert_failure "Should fail when no mcpServers section exists"
else
    assert_success "Should fail when no mcpServers section exists"
fi

# Test 13: Remote MCP server with SSE transport
print_test_header "Test 13: Remote MCP server with SSE transport"
cat > "${TEST_DIR}/.claude-code-config.yml" <<'EOF'
claudeCode:
  enabled: true
  mcpServers:
    - name: remote-sse-server
      type: sse
      url: "https://mcp.example.com/api/sse"
      env:
        API_TOKEN: "test-token-123"
EOF

rm -f "${TEST_DIR}/.claude.json"
generate_claude_json "${TEST_DIR}"
if [ -f "${TEST_DIR}/.claude.json" ] && \
   grep -q '"remote-sse-server"' "${TEST_DIR}/.claude.json" && \
   grep -q '"type": "sse"' "${TEST_DIR}/.claude.json" && \
   grep -q '"url": "https://mcp.example.com/api/sse"' "${TEST_DIR}/.claude.json"; then
    assert_success "Should generate .claude.json with SSE remote server"
else
    assert_failure "Should generate .claude.json with SSE remote server"
fi

# Test 14: Remote MCP server with HTTP transport
print_test_header "Test 14: Remote MCP server with HTTP transport"
cat > "${TEST_DIR}/.claude-code-config.yml" <<'EOF'
claudeCode:
  enabled: true
  mcpServers:
    - name: remote-http-server
      type: http
      url: "https://api.example.com/mcp/endpoint"
      env:
        GATEWAY_TOKEN: "Bearer xyz789"
EOF

rm -f "${TEST_DIR}/.claude.json"
generate_claude_json "${TEST_DIR}"
if [ -f "${TEST_DIR}/.claude.json" ] && \
   grep -q '"remote-http-server"' "${TEST_DIR}/.claude.json" && \
   grep -q '"type": "http"' "${TEST_DIR}/.claude.json" && \
   grep -q '"url": "https://api.example.com/mcp/endpoint"' "${TEST_DIR}/.claude.json"; then
    assert_success "Should generate .claude.json with HTTP remote server"
else
    assert_failure "Should generate .claude.json with HTTP remote server"
fi

# Test 15: Mixed local and remote MCP servers
print_test_header "Test 15: Mixed local and remote MCP servers"
cat > "${TEST_DIR}/.claude-code-config.yml" <<'EOF'
claudeCode:
  enabled: true
  mcpServers:
    - name: filesystem
      type: stdio
      command: npx
      args:
        - "-y"
        - "@modelcontextprotocol/server-filesystem"
      env:
        ALLOWED_DIRECTORIES: "/tmp"
    - name: remote-data
      type: sse
      url: "https://data.example.com/mcp"
      env:
        DATA_KEY: "abc123"
    - name: llm-gateway
      type: http
      url: "https://llm.example.com/api"
      env:
        LLM_TOKEN: "xyz789"
EOF

rm -f "${TEST_DIR}/.claude.json"
generate_claude_json "${TEST_DIR}"
if [ -f "${TEST_DIR}/.claude.json" ] && \
   grep -q '"filesystem"' "${TEST_DIR}/.claude.json" && \
   grep -q '"remote-data"' "${TEST_DIR}/.claude.json" && \
   grep -q '"llm-gateway"' "${TEST_DIR}/.claude.json" && \
   grep -q '"type": "stdio"' "${TEST_DIR}/.claude.json" && \
   grep -q '"type": "sse"' "${TEST_DIR}/.claude.json" && \
   grep -q '"type": "http"' "${TEST_DIR}/.claude.json"; then
    assert_success "Should generate .claude.json with mixed local and remote servers"
else
    assert_failure "Should generate .claude.json with mixed local and remote servers"
fi

# Print summary
echo ""
echo "======================================"
echo "Test Summary"
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
