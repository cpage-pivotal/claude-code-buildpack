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

# Parse settings section from YAML configuration
# Extracts alwaysThinkingEnabled and permissions.deny array
parse_settings_from_yaml() {
    local config_file=$1
    local output_file=$2

    if [ ! -f "${config_file}" ]; then
        return 1
    fi

    # Check if settings section exists
    if ! grep -q "^\s*settings:" "${config_file}"; then
        return 1
    fi

    # Use Python for YAML parsing
    if command -v python3 > /dev/null 2>&1; then
        python3 - "${config_file}" > "${output_file}" 2>&1 <<'PYTHON_SCRIPT'
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
    # Return default settings
    print(json.dumps({"alwaysThinkingEnabled": True}), file=sys.stdout)
    sys.exit(0)

settings = {
    "alwaysThinkingEnabled": True  # Default value
}
in_settings = False
in_permissions = False
in_deny = False
deny_list = []

for i, line in enumerate(lines):
    stripped = line.strip()

    # Detect start of settings section
    if re.match(r'settings:', stripped):
        in_settings = True
        continue

    # Exit settings section if we hit a non-indented, non-comment line
    if in_settings and line and not line.startswith((' ', '\t', '-', '#')) and stripped:
        in_settings = False
        break

    if in_settings:
        # Parse alwaysThinkingEnabled
        if re.match(r'alwaysThinkingEnabled:', stripped):
            match = re.search(r'alwaysThinkingEnabled:\s*(true|false)', stripped)
            if match:
                settings['alwaysThinkingEnabled'] = match.group(1) == 'true'

        # Detect permissions section
        if re.match(r'permissions:', stripped):
            in_permissions = True
            continue

        # Detect deny array within permissions
        if in_permissions and re.match(r'deny:', stripped):
            in_deny = True
            continue

        # Parse deny list items
        if in_deny and re.match(r'-\s+', stripped):
            # Extract the deny item, handling quotes and comments
            # Format: - "item" # comment  OR  - item # comment
            match = re.search(r'-\s+(["\']?)(.+?)\1(?:\s*#.*)?$', stripped)
            if match:
                deny_item = match.group(2).strip()
                # Remove any trailing comments that weren't caught by regex
                if '#' in deny_item and not deny_item.startswith('#'):
                    # Find the last quote (if any) before the comment
                    quote_pos = max(deny_item.rfind('"'), deny_item.rfind("'"))
                    hash_pos = deny_item.find('#')
                    # Only strip comment if it's after any quotes
                    if hash_pos > quote_pos:
                        deny_item = deny_item[:hash_pos].strip()
                deny_list.append(deny_item)

        # Exit deny section if we hit a non-list-item line
        if in_deny and not re.match(r'-\s+', stripped) and stripped and not stripped.startswith('#'):
            in_deny = False

# Add permissions.deny to settings if we found deny rules
if deny_list:
    settings['permissions'] = {
        'deny': deny_list
    }

# Output JSON
print(json.dumps(settings, indent=2), file=sys.stdout)
PYTHON_SCRIPT

        if [ $? -eq 0 ]; then
            return 0
        else
            return 1
        fi
    else
        # Fallback: create default settings if Python not available
        echo '{"alwaysThinkingEnabled": true}' > "${output_file}"
        return 1
    fi
}

# Generate .claude/settings.json from configuration
# This creates Claude Code settings like alwaysThinkingEnabled and permissions.deny
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
            echo "       Parsing settings from configuration file..."

            # Parse settings using Python
            local temp_settings="${build_dir}/.claude-settings-temp.json"
            if parse_settings_from_yaml "${CLAUDE_CODE_CONFIG_FILE}" "${temp_settings}"; then
                # Check if we got valid JSON
                if [ -f "${temp_settings}" ] && grep -q '"alwaysThinkingEnabled"' "${temp_settings}"; then
                    mv "${temp_settings}" "${settings_file}"
                    echo "       Created ${settings_file} from configuration"

                    # Log what was configured
                    if grep -q '"permissions"' "${settings_file}"; then
                        # Use Python to accurately count deny rules
                        local deny_count=$(python3 -c "import json; data=json.load(open('${settings_file}')); print(len(data.get('permissions', {}).get('deny', [])))" 2>/dev/null || echo "0")
                        if [ "${deny_count}" -gt 0 ]; then
                            echo "       Configured ${deny_count} deny rule(s)"
                        fi
                    fi

                    return 0
                fi
            fi

            # Clean up temp file if it exists
            rm -f "${temp_settings}"
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
