#!/usr/bin/env bash
# lib/mcp_configurator.sh: MCP server configuration parsing and .claude.json generation

# Parse MCP server configuration from .claude-code-config.yml
# Returns 0 if configuration found, 1 otherwise
parse_claude_code_config() {
    local build_dir=$1
    local config_file="${build_dir}/.claude-code-config.yml"

    if [ ! -f "${config_file}" ]; then
        return 1
    fi

    echo "       Found .claude-code-config.yml configuration file"

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
        python3 - "${config_file}" <<'PYTHON_SCRIPT' > "${output_file}"
import sys
import re
import json

if len(sys.argv) < 2:
    sys.exit(1)

config_file = sys.argv[1]

try:
    with open(config_file, 'r') as f:
        lines = f.readlines()
except:
    sys.exit(1)

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

    # Detect start of new server
    if in_mcp and re.match(r'-\s+name:', stripped):
        if current_server and current_server_name:
            mcp_servers[current_server_name] = current_server

        match = re.search(r'name:\s*(.+)', stripped)
        current_server_name = match.group(1).strip() if match else None
        current_server = {}
        in_args = False
        in_env = False
        continue

    # Parse server properties
    if current_server is not None:
        # Type
        if re.match(r'type:', stripped):
            match = re.search(r'type:\s*(.+)', stripped)
            if match:
                current_server['type'] = match.group(1).strip()

        # Command
        elif re.match(r'command:', stripped):
            match = re.search(r'command:\s*(.+)', stripped)
            if match:
                current_server['command'] = match.group(1).strip()

        # Args array
        elif re.match(r'args:', stripped):
            current_server['args'] = []
            in_args = True
            in_env = False
        elif in_args and re.match(r'-\s+"', stripped):
            match = re.search(r'-\s+"([^"]+)"', stripped)
            if match:
                current_server['args'].append(match.group(1))
        elif in_args and not re.match(r'-', stripped) and re.match(r'[a-zA-Z_]+:', stripped):
            in_args = False

        # Env object
        if re.match(r'env:', stripped):
            current_server['env'] = {}
            in_env = True
            in_args = False
        elif in_env and re.match(r'[A-Z_]+:', stripped):
            match = re.search(r'([A-Z_]+):\s*(.+)', stripped)
            if match:
                key = match.group(1).strip()
                value = match.group(2).strip().strip('"')
                current_server['env'][key] = value

    # Exit MCP section if we hit a non-indented line
    if in_mcp and not line.startswith((' ', '\t', '-')) and stripped and not re.match(r'(mcpServers|mcp-servers):', stripped):
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

# Generate .claude.json configuration file
generate_claude_json() {
    local build_dir=$1
    local output_file="${build_dir}/.claude.json"

    # Try to parse .claude-code-config.yml first
    if parse_claude_code_config "${build_dir}"; then
        if extract_mcp_servers "${CLAUDE_CODE_CONFIG_FILE}" "${output_file}"; then
            echo "       Created .claude.json from .claude-code-config.yml"
            return 0
        fi
    fi

    # Try to parse manifest.yml (won't work in CF but included for completeness)
    if parse_manifest_config "${build_dir}"; then
        if extract_mcp_servers "${CLAUDE_CODE_MANIFEST_CONFIG}" "${output_file}"; then
            echo "       Created .claude.json from manifest.yml"
            return 0
        fi
    fi

    # No configuration found - create empty .claude.json
    cat > "${output_file}" <<'EOF'
{
  "mcpServers": {}
}
EOF

    echo "       Created empty .claude.json (no MCP configuration found)"
    return 0
}

# Validate MCP server configuration
validate_mcp_config() {
    local build_dir=$1
    local config_file="${build_dir}/.claude.json"

    if [ ! -f "${config_file}" ]; then
        echo "       WARNING: .claude.json not found"
        return 1
    fi

    # Basic validation - check JSON structure
    if ! grep -q '"mcpServers"' "${config_file}"; then
        echo "       WARNING: .claude.json missing mcpServers section"
        return 1
    fi

    # Check if mcpServers has any servers configured
    local server_count=$(grep -c '"type"' "${config_file}" || echo "0")

    if [ "${server_count}" -eq 0 ]; then
        echo "       No MCP servers configured (using empty configuration)"
    else
        echo "       Validated .claude.json with ${server_count} MCP server(s)"
    fi

    return 0
}

# Main configuration function to be called from supply script
configure_mcp_servers() {
    local build_dir=$1

    echo "-----> Configuring MCP servers"

    # Generate .claude.json
    if ! generate_claude_json "${build_dir}"; then
        echo "       WARNING: Failed to generate .claude.json, using empty configuration"
    fi

    # Validate configuration
    validate_mcp_config "${build_dir}"

    return 0
}
