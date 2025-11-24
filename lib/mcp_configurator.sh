#!/usr/bin/env bash
# lib/mcp_configurator.sh: Claude Code configuration management
# Handles both MCP server configuration and Claude settings generation

# Parse Claude Code configuration settings from .claude-code-config.yml
# Extracts logLevel, version, model, etc.
parse_config_settings() {
    local config_file=$1

    if [ ! -f "${config_file}" ]; then
        return 1
    fi

    # Parse logLevel setting
    local log_level=$(grep -E "^\s*logLevel:" "${config_file}" | sed -E 's/^\s*logLevel:\s*(.+)\s*$/\1/' | tr -d '"' | tr -d "'")
    if [ -n "${log_level}" ]; then
        export CLAUDE_CODE_LOG_LEVEL="${log_level}"
        echo "       Setting log level: ${log_level}"
    fi

    # Parse version setting
    local version=$(grep -E "^\s*version:" "${config_file}" | sed -E 's/^\s*version:\s*(.+)\s*$/\1/' | tr -d '"' | tr -d "'")
    if [ -n "${version}" ]; then
        export CLAUDE_CODE_VERSION="${version}"
        echo "       Setting Claude Code version: ${version}"
    fi

    # Parse model setting
    local model=$(grep -E "^\s*model:" "${config_file}" | sed -E 's/^\s*model:\s*(.+)\s*$/\1/' | tr -d '"' | tr -d "'")
    if [ -n "${model}" ]; then
        export CLAUDE_CODE_MODEL="${model}"
        echo "       Setting Claude Code model: ${model}"
    fi

    return 0
}

# Parse MCP server configuration from .claude-code-config.yml
# Returns 0 if configuration found, 1 otherwise
parse_claude_code_config() {
    local build_dir=$1
    local config_file="${build_dir}/.claude-code-config.yml"

    if [ ! -f "${config_file}" ]; then
        return 1
    fi

    echo "       Found .claude-code-config.yml configuration file"

    # Parse configuration settings (logLevel, version, model)
    parse_config_settings "${config_file}"

    # Export config file location for later parsing
    export CLAUDE_CODE_CONFIG_FILE="${config_file}"
    return 0
}

# Parse MCP server configuration from manifest.yml
# Note: Cloud Foundry doesn't make manifest.yml available during staging
# This function is here for reference but won't work in actual CF environment
# Users should use .claude-code-config.yml instead
parse_manifest_config() {
    local build_dir=$1
    local manifest_file="${build_dir}/manifest.yml"

    if [ ! -f "${manifest_file}" ]; then
        return 1
    fi

    # Check if manifest has claude-code-config section
    if grep -q "claude-code-config:" "${manifest_file}"; then
        echo "       Found claude-code-config in manifest.yml"
        export CLAUDE_CODE_MANIFEST_CONFIG="${manifest_file}"
        return 0
    fi

    return 1
}

# Extract MCP servers from YAML configuration file
# Simplified YAML parser for the specific structure we expect
extract_mcp_servers() {
    local config_file=$1
    local output_file=$2

    if [ ! -f "${config_file}" ]; then
        echo "       No configuration file found: ${config_file}"
        return 1
    fi

    # Parse YAML and convert to JSON
    # This is a simplified parser that handles the specific structure in DESIGN.md

    # Check if file has mcpServers or mcp-servers section
    if ! grep -qE "(mcpServers|mcp-servers):" "${config_file}"; then
        echo "       No MCP server configuration found in ${config_file}"
        return 1
    fi

    # Use Python for YAML parsing (available in Cloud Foundry stacks)
    if command -v python3 > /dev/null 2>&1; then
        # Run Python parser - stdout goes to file, stderr goes to console
        python3 - "${config_file}" > "${output_file}" <<'PYTHON_SCRIPT'
import sys
import re
import json

if len(sys.argv) < 2:
    sys.exit(1)

config_file = sys.argv[1]

try:
    with open(config_file, 'r') as f:
        lines = f.readlines()
except Exception as e:
    print(json.dumps({'mcpServers': {}}))
    sys.exit(0)

mcp_servers = {}
in_mcp = False
current_server = None
current_server_name = None
in_args = False
in_env = False

for line in lines:
    stripped = line.strip()

    # Detect start of mcpServers or mcp-servers section
    if re.match(r'(mcpServers|mcp-servers):', stripped):
        in_mcp = True
        continue

    # Detect start of new server (with or without inline name)
    if in_mcp and re.match(r'-\s+name:', stripped):
        # Save previous server
        if current_server and current_server_name:
            mcp_servers[current_server_name] = current_server

        # Start new server
        match = re.search(r'name:\s*(.+)', stripped)
        current_server_name = match.group(1).strip() if match else None
        current_server = {}
        in_args = False
        in_env = False
        continue

    # Parse server properties (only when we have a current server)
    if current_server is not None:
        # Type (stdio, sse, http)
        if re.match(r'^\s*type:', line):
            match = re.search(r'type:\s*(.+)', stripped)
            if match:
                current_server['type'] = match.group(1).strip()

        # URL (for remote servers: sse, http)
        elif re.match(r'^\s*url:', line):
            match = re.search(r'url:\s*(.+)', stripped)
            if match:
                url = match.group(1).strip().strip('"')
                current_server['url'] = url

        # Command (for local servers: stdio)
        elif re.match(r'^\s*command:', line):
            match = re.search(r'command:\s*(.+)', stripped)
            if match:
                current_server['command'] = match.group(1).strip()

        # Args array
        elif re.match(r'^\s*args:', line):
            current_server['args'] = []
            in_args = True
            in_env = False

        elif in_args:
            if re.match(r'^\s+-\s+"', line):
                match = re.search(r'-\s+"([^"]+)"', stripped)
                if match:
                    current_server['args'].append(match.group(1))
            elif re.match(r'^\s*[a-zA-Z_]+:', line):
                # Exit args section
                in_args = False

        # Env object
        if re.match(r'^\s*env:', line) and not in_args:
            current_server['env'] = {}
            in_env = True
            in_args = False

        elif in_env and re.match(r'^\s+[A-Z_]+:', line):
            match = re.search(r'([A-Z_]+):\s*(.+)', stripped)
            if match:
                key = match.group(1).strip()
                value = match.group(2).strip().strip('"').strip("'")
                # Handle environment variable substitution syntax
                current_server['env'][key] = value

    # Detect end of MCP section
    # Exit if we hit a non-indented, non-empty line that's not a comment and not a dash
    if in_mcp and line and not line.startswith((' ', '\t', '-', '#')) and stripped:
        in_mcp = False

# Add last server
if current_server and current_server_name:
    mcp_servers[current_server_name] = current_server

# Output JSON
output = {'mcpServers': mcp_servers}
print(json.dumps(output, indent=2))
PYTHON_SCRIPT

    else
        # Fallback: create empty config if Python not available
        cat > "${output_file}" <<'EOF'
{
  "mcpServers": {}
}
EOF
        echo "       WARNING: Python3 not available for YAML parsing, using empty config"
        return 1
    fi

    # Validate generated JSON
    if [ -f "${output_file}" ]; then
        # Basic validation - check if file has proper structure
        if grep -q '"mcpServers"' "${output_file}"; then
            echo "       Generated .claude.json with MCP server configuration"
            return 0
        fi
    fi

    echo "       Failed to generate valid .claude.json"
    return 1
}

# Generate .claude/mcp.json configuration file
# Note: Claude Code uses .claude/mcp.json for MCP server configuration,
# while .claude.json is used for internal CLI state
generate_claude_json() {
    local build_dir=$1
    local claude_dir="${build_dir}/.claude"
    local output_file="${claude_dir}/mcp.json"
    
    # Create .claude directory if it doesn't exist
    mkdir -p "${claude_dir}"

    # Try to parse .claude-code-config.yml first
    if parse_claude_code_config "${build_dir}"; then
        if extract_mcp_servers "${CLAUDE_CODE_CONFIG_FILE}" "${output_file}"; then
            echo "       Created .claude/mcp.json from .claude-code-config.yml"
            return 0
        fi
    fi

    # Try to parse manifest.yml (won't work in CF but included for completeness)
    if parse_manifest_config "${build_dir}"; then
        if extract_mcp_servers "${CLAUDE_CODE_MANIFEST_CONFIG}" "${output_file}"; then
            echo "       Created .claude/mcp.json from manifest.yml"
            return 0
        fi
    fi

    # No configuration found - create empty .claude/mcp.json
    cat > "${output_file}" <<'EOF'
{
  "mcpServers": {}
}
EOF

    echo "       Created empty .claude/mcp.json (no MCP configuration found)"
    return 0
}

# Generate .claude/settings.json from configuration
# This creates Claude Code settings like alwaysThinkingEnabled
generate_claude_settings_json() {
    local build_dir=$1
    local settings_dir="${build_dir}/.claude"
    local settings_file="${settings_dir}/settings.json"
    
    # Create .claude directory if it doesn't exist
    mkdir -p "${settings_dir}"
    
    echo "-----> Generating Claude settings configuration"
    
    # Check if config file specifies settings
    if [ -n "${CLAUDE_CODE_CONFIG_FILE}" ] && [ -f "${CLAUDE_CODE_CONFIG_FILE}" ]; then
        # Check if settings section exists
        if grep -q "^\s*settings:" "${CLAUDE_CODE_CONFIG_FILE}"; then
            # Parse alwaysThinkingEnabled setting
            local always_thinking=$(grep -A 5 "^\s*settings:" "${CLAUDE_CODE_CONFIG_FILE}" | \
                                   grep "alwaysThinkingEnabled:" | \
                                   awk -F": " '{print $2}' | \
                                   tr -d ' "' | \
                                   head -1)
            
            if [ -n "${always_thinking}" ]; then
                echo "       Configuring alwaysThinkingEnabled: ${always_thinking}"
                cat > "${settings_file}" <<EOF
{
  "alwaysThinkingEnabled": ${always_thinking}
}
EOF
                echo "       Created ${settings_file}"
                return 0
            fi
        fi
    fi
    
    # If no settings specified, create default with alwaysThinkingEnabled: true
    # This improves multi-step operation performance in Cloud Foundry
    echo "       Using default settings with extended thinking enabled"
    cat > "${settings_file}" <<'EOF'
{
  "alwaysThinkingEnabled": true
}
EOF
    
    echo "       Created ${settings_file} with default configuration"
    return 0
}

# Validate MCP server configuration
validate_mcp_config() {
    local build_dir=$1
    local config_file="${build_dir}/.claude/mcp.json"

    if [ ! -f "${config_file}" ]; then
        echo "       WARNING: .claude/mcp.json not found"
        return 1
    fi

    # Basic validation - check JSON structure
    if ! grep -q '"mcpServers"' "${config_file}"; then
        echo "       WARNING: .claude/mcp.json missing mcpServers section"
        return 1
    fi

    # Check if mcpServers has any servers configured
    # Note: grep -c outputs "0" even when no matches are found, so we don't need || echo "0"
    local server_count=$(grep -c '"type"' "${config_file}" 2>/dev/null || true)

    if [ "${server_count}" -eq 0 ] 2>/dev/null; then
        echo "       No MCP servers configured (using empty configuration)"
    else
        echo "       Validated .claude/mcp.json with ${server_count} MCP server(s)"
    fi

    return 0
}

# Generate MCP server configuration (.claude/mcp.json)
configure_mcp_servers() {
    local build_dir=$1

    echo "-----> Configuring MCP servers"

    # Generate .claude/mcp.json
    if ! generate_claude_json "${build_dir}"; then
        echo "       WARNING: Failed to generate .claude/mcp.json, using empty configuration"
    fi

    # Validate configuration
    validate_mcp_config "${build_dir}"

    return 0
}

# Generate Claude settings configuration (.claude/settings.json)
configure_claude_settings() {
    local build_dir=$1

    echo "-----> Configuring Claude settings"

    # Generate .claude/settings.json
    generate_claude_settings_json "${build_dir}"

    return 0
}

# Main configuration function - configures both MCP servers and Claude settings
configure_claude_code() {
    local build_dir=$1

    # Configure MCP servers (.claude.json)
    configure_mcp_servers "${build_dir}"

    # Configure Claude settings (.claude/settings.json)
    configure_claude_settings "${build_dir}"

    return 0
}
