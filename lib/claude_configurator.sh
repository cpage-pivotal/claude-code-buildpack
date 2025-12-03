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
    local log_level=$(grep -E "^[[:space:]]*logLevel:" "${config_file}" | sed -E 's/^[[:space:]]*logLevel:[[:space:]]*(.+)[[:space:]]*$/\1/' | tr -d '"' | tr -d "'")
    if [ -n "${log_level}" ]; then
        export CLAUDE_CODE_LOG_LEVEL="${log_level}"
        echo "       Setting log level: ${log_level}"
    fi

    # Parse version setting
    local version=$(grep -E "^[[:space:]]*version:" "${config_file}" | sed -E 's/^[[:space:]]*version:[[:space:]]*(.+)[[:space:]]*$/\1/' | tr -d '"' | tr -d "'")
    if [ -n "${version}" ]; then
        export CLAUDE_CODE_VERSION="${version}"
        echo "       Setting Claude Code version: ${version}"
    fi

    # Parse model setting
    local model=$(grep -E "^[[:space:]]*model:" "${config_file}" | sed -E 's/^[[:space:]]*model:[[:space:]]*(.+)[[:space:]]*$/\1/' | tr -d '"' | tr -d "'")
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
    # Return empty config with projects structure
    print(json.dumps({
        'projects': {
            '/home/vcap/app': {
                'allowedTools': [],
                'mcpContextUris': [],
                'mcpServers': {},
                'enabledMcpjsonServers': [],
                'disabledMcpjsonServers': [],
                'hasTrustDialogAccepted': False,
                'projectOnboardingSeenCount': 0,
                'hasClaudeMdExternalIncludesApproved': False,
                'hasClaudeMdExternalIncludesWarningShown': False
            }
        }
    }))
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

# Output JSON in Claude Code format with projects structure
# The app directory is /home/vcap/app in Cloud Foundry
output = {
    'projects': {
        '/home/vcap/app': {
            'allowedTools': [],
            'mcpContextUris': [],
            'mcpServers': mcp_servers,
            'enabledMcpjsonServers': [],
            'disabledMcpjsonServers': [],
            'hasTrustDialogAccepted': False,
            'projectOnboardingSeenCount': 0,
            'hasClaudeMdExternalIncludesApproved': False,
            'hasClaudeMdExternalIncludesWarningShown': False
        }
    }
}
print(json.dumps(output, indent=2))
PYTHON_SCRIPT

    else
        # Fallback: create empty config if Python not available
        cat > "${output_file}" <<'EOF'
{
  "projects": {
    "/home/vcap/app": {
      "allowedTools": [],
      "mcpContextUris": [],
      "mcpServers": {},
      "enabledMcpjsonServers": [],
      "disabledMcpjsonServers": [],
      "hasTrustDialogAccepted": false,
      "projectOnboardingSeenCount": 0,
      "hasClaudeMdExternalIncludesApproved": false,
      "hasClaudeMdExternalIncludesWarningShown": false
    }
  }
}
EOF
        echo "       WARNING: Python3 not available for YAML parsing, using empty config"
        return 1
    fi

    # Validate generated JSON
    if [ -f "${output_file}" ]; then
        # Basic validation - check if file has proper structure
        if grep -q '"projects"' "${output_file}" && grep -q '"mcpServers"' "${output_file}"; then
            echo "       Generated .claude.json with MCP server configuration"
            return 0
        fi
    fi

    echo "       Failed to generate valid .claude.json"
    return 1
}

# Generate .claude.json configuration file with MCP servers under projects section
# Note: Claude Code uses .claude.json with a projects structure for MCP server configuration
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

    # No configuration found - create empty .claude.json with projects structure
    cat > "${output_file}" <<'EOF'
{
  "projects": {
    "/home/vcap/app": {
      "allowedTools": [],
      "mcpContextUris": [],
      "mcpServers": {},
      "enabledMcpjsonServers": [],
      "disabledMcpjsonServers": [],
      "hasTrustDialogAccepted": false,
      "projectOnboardingSeenCount": 0,
      "hasClaudeMdExternalIncludesApproved": false,
      "hasClaudeMdExternalIncludesWarningShown": false
    }
  }
}
EOF

    echo "       Created empty .claude.json (no MCP configuration found)"
    return 0
}

# Generate .claude/settings.json from configuration
# This creates Claude Code settings like alwaysThinkingEnabled and extraKnownMarketplaces
generate_claude_settings_json() {
    local build_dir=$1
    local settings_dir="${build_dir}/.claude"
    local settings_file="${settings_dir}/settings.json"

    # Create .claude directory if it doesn't exist
    mkdir -p "${settings_dir}"

    echo "-----> Generating Claude settings configuration"

    # Use Python to parse YAML and generate JSON with marketplace support
    if [ -n "${CLAUDE_CODE_CONFIG_FILE}" ] && [ -f "${CLAUDE_CODE_CONFIG_FILE}" ]; then
        if command -v python3 > /dev/null 2>&1; then
            # Run Python parser to generate settings JSON
            python3 - "${CLAUDE_CODE_CONFIG_FILE}" > "${settings_file}" <<'PYTHON_SCRIPT'
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
    print(json.dumps({"alwaysThinkingEnabled": True}))
    sys.exit(0)

settings = {}
marketplaces = {}
in_settings = False
in_marketplaces = False
current_marketplace = None

for line in lines:
    stripped = line.strip()

    # Detect settings section
    if re.match(r'^\s*settings:', stripped):
        in_settings = True
        in_marketplaces = False
        current_marketplace = None
        continue

    # Detect marketplaces section
    if re.match(r'^\s*marketplaces:', stripped):
        in_marketplaces = True
        in_settings = False
        current_marketplace = None
        continue

    # Exit sections when we hit other top-level keys (including mcpServers)
    if re.match(r'^\s*(mcpServers|mcp-servers):', stripped):
        in_settings = False
        in_marketplaces = False
        current_marketplace = None
        continue

    # Exit sections when we hit other non-indented keys
    if line and not line.startswith((' ', '\t')) and stripped and not stripped.startswith('#'):
        if not re.match(r'^\s*(settings|marketplaces|claudeCode):', stripped):
            in_settings = False
            in_marketplaces = False
            current_marketplace = None

    # Parse settings section
    if in_settings and re.match(r'^\s+alwaysThinkingEnabled:', line):
        match = re.search(r'alwaysThinkingEnabled:\s*(true|false)', stripped)
        if match:
            settings['alwaysThinkingEnabled'] = match.group(1) == 'true'

    # Parse marketplaces section
    if in_marketplaces:
        # Detect start of new marketplace entry
        if re.match(r'^\s+-\s+name:', line):
            match = re.search(r'name:\s*(.+)', stripped)
            if match:
                current_marketplace = match.group(1).strip()
                marketplaces[current_marketplace] = {"source": {}}

        # Parse marketplace properties
        elif current_marketplace:
            # Parse source type (github or git)
            if re.match(r'^\s+source:', line):
                match = re.search(r'source:\s*(.+)', stripped)
                if match:
                    source_type = match.group(1).strip()
                    marketplaces[current_marketplace]['source']['source'] = source_type

            # Parse repo (for github)
            elif re.match(r'^\s+repo:', line):
                match = re.search(r'repo:\s*(.+)', stripped)
                if match:
                    repo = match.group(1).strip()
                    marketplaces[current_marketplace]['source']['repo'] = repo

            # Parse url (for git)
            elif re.match(r'^\s+url:', line):
                match = re.search(r'url:\s*(.+)', stripped)
                if match:
                    url = match.group(1).strip()
                    marketplaces[current_marketplace]['source']['url'] = url

# Build final settings object
output = {}

# Add alwaysThinkingEnabled (default to true if not specified)
output['alwaysThinkingEnabled'] = settings.get('alwaysThinkingEnabled', True)

# Add marketplaces if any were configured
if marketplaces:
    output['extraKnownMarketplaces'] = marketplaces

print(json.dumps(output, indent=2))
PYTHON_SCRIPT

            if [ -f "${settings_file}" ]; then
                # Validate generated JSON
                if grep -q '"alwaysThinkingEnabled"' "${settings_file}"; then
                    echo "       Created ${settings_file} with configuration from YAML"

                    # Report marketplace configuration (count using Python for accuracy)
                    if grep -q 'extraKnownMarketplaces' "${settings_file}"; then
                        local marketplace_count=$(python3 -c "
import json, sys
try:
    with open('${settings_file}', 'r') as f:
        data = json.load(f)
    print(len(data.get('extraKnownMarketplaces', {})))
except:
    print(0)
" 2>/dev/null)
                        if [ -n "${marketplace_count}" ] && [ "${marketplace_count}" -gt 0 ]; then
                            echo "       Configured ${marketplace_count} plugin marketplace(s)"
                        fi
                    fi
                    return 0
                fi
            fi
        fi
    fi

    # Fallback: create default settings if Python parsing failed
    echo "       Using default settings with extended thinking enabled"
    cat > "${settings_file}" <<'EOF'
{
  "alwaysThinkingEnabled": true
}
EOF

    echo "       Created ${settings_file} with default configuration"
    return 0
}

# Validate MCP server configuration in .claude.json
validate_mcp_config() {
    local build_dir=$1
    local config_file="${build_dir}/.claude.json"

    if [ ! -f "${config_file}" ]; then
        echo "       WARNING: .claude.json not found"
        return 1
    fi

    # Basic validation - check JSON structure
    if ! grep -q '"projects"' "${config_file}" || ! grep -q '"mcpServers"' "${config_file}"; then
        echo "       WARNING: .claude.json missing projects or mcpServers section"
        return 1
    fi

    # Check if mcpServers has any servers configured
    # Note: grep -c outputs "0" even when no matches are found, so we don't need || echo "0"
    local server_count=$(grep -c '"type"' "${config_file}" 2>/dev/null || true)

    if [ "${server_count}" -eq 0 ] 2>/dev/null; then
        echo "       No MCP servers configured (using empty configuration)"
    else
        echo "       Validated .claude.json with ${server_count} MCP server(s)"
    fi

    return 0
}

# Generate MCP server configuration (.claude.json)
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

# ============================================================================
# Skills Configuration Functions
# ============================================================================

# Validate Skill structure
# Verifies SKILL.md exists and has proper frontmatter
validate_skill_structure() {
    local skill_dir=$1
    local skill_name=$(basename "${skill_dir}")
    
    # Check if SKILL.md exists
    if [ ! -f "${skill_dir}/SKILL.md" ]; then
        echo "       WARNING: Skill missing SKILL.md: ${skill_name}" >&2
        return 1
    fi
    
    # Check for YAML frontmatter
    if ! head -1 "${skill_dir}/SKILL.md" | grep -q "^---"; then
        echo "       WARNING: SKILL.md missing YAML frontmatter: ${skill_name}" >&2
        return 1
    fi
    
    # Extract and validate frontmatter fields
    local has_name=false
    local has_description=false
    
    # Read first 20 lines to check frontmatter
    while IFS= read -r line; do
        if echo "${line}" | grep -q "^name:"; then
            has_name=true
        fi
        if echo "${line}" | grep -q "^description:"; then
            has_description=true
        fi
        # Stop at closing ---
        if echo "${line}" | grep -q "^---" && [ "${has_name}" = true ]; then
            break
        fi
    done < <(head -20 "${skill_dir}/SKILL.md" | tail -n +2)
    
    if [ "${has_name}" = false ]; then
        echo "       WARNING: SKILL.md missing 'name' field: ${skill_name}" >&2
        return 1
    fi
    
    if [ "${has_description}" = false ]; then
        echo "       WARNING: SKILL.md missing 'description' field: ${skill_name}" >&2
        return 1
    fi
    
    return 0
}

# List installed Skills for debugging
list_installed_skills() {
    local skills_dir=$1
    
    if [ ! -d "${skills_dir}" ]; then
        echo "       No Skills directory found"
        return 0
    fi
    
    local skill_count=0
    for skill_dir in "${skills_dir}"/*; do
        if [ -d "${skill_dir}" ] && [ -f "${skill_dir}/SKILL.md" ]; then
            skill_count=$((skill_count + 1))
            local skill_name=$(basename "${skill_dir}")
            echo "       - ${skill_name}"
        fi
    done
    
    if [ ${skill_count} -eq 0 ]; then
        echo "       No Skills found"
    else
        echo "       Total Skills: ${skill_count}"
    fi
    
    return 0
}

# Main Skills configuration function
configure_skills() {
    local build_dir=$1
    local cache_dir=$2

    echo "-----> Configuring Claude Skills"

    # Create Skills directory
    local skills_dir="${build_dir}/.claude/skills"
    mkdir -p "${skills_dir}"

    # Check for bundled Skills (already in .claude/skills/)
    local bundled_count=0
    if [ -d "${skills_dir}" ]; then
        for skill_dir in "${skills_dir}"/*; do
            if [ -d "${skill_dir}" ] && [ -f "${skill_dir}/SKILL.md" ]; then
                bundled_count=$((bundled_count + 1))
            fi
        done
    fi

    if [ ${bundled_count} -gt 0 ]; then
        echo "       Found ${bundled_count} bundled Skill(s)"
    else
        echo "       No bundled Skills found"
    fi

    # Validate all Skills
    echo "       Validating Skills..."
    local valid_count=0
    local invalid_count=0

    for skill_dir in "${skills_dir}"/*; do
        if [ -d "${skill_dir}" ]; then
            if validate_skill_structure "${skill_dir}"; then
                valid_count=$((valid_count + 1))
            else
                invalid_count=$((invalid_count + 1))
            fi
        fi
    done

    echo "       Valid Skills: ${valid_count}"
    if [ ${invalid_count} -gt 0 ]; then
        echo "       Invalid Skills: ${invalid_count} (see warnings above)"
    fi

    # List installed Skills
    echo "       Installed Skills:"
    list_installed_skills "${skills_dir}"

    return 0
}
