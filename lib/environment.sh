#!/usr/bin/env bash
# lib/environment.sh: Environment variable setup and configuration

# Set up environment variables for Claude Code
setup_environment() {
    local deps_dir=$1
    local build_dir=$2
    local index=$3

    # Create profile.d directory in BUILD_DIR (not DEPS_DIR!)
    # Cloud Foundry sources scripts from /home/vcap/app/.profile.d/ at runtime
    mkdir -p "${build_dir}/.profile.d"
    local profile_script="${build_dir}/.profile.d/claude-code-env.sh"

    # Use the actual index number in the script (not ${DEPS_INDEX} which may not be set at runtime)
    cat > "${profile_script}" <<EOF
# Claude Code CLI environment configuration

# Add Claude Code to PATH
export PATH="\$DEPS_DIR/${index}/bin:\$PATH"

# Add Node.js to PATH
export PATH="\$DEPS_DIR/${index}/node/bin:\$PATH"

# Set Claude CLI path for Java applications
export CLAUDE_CLI_PATH="\$DEPS_DIR/${index}/bin/claude"

# Set home directory for Claude configuration
export CLAUDE_CONFIG_HOME="\$HOME"

# Copy Claude settings from app directory to HOME directory at runtime
# Claude Code CLI looks for settings at ~/.claude/settings.json
# but the buildpack creates them at /home/vcap/app/.claude/
if [ -d "/home/vcap/app/.claude" ] && [ "\$HOME" != "/home/vcap/app" ]; then
    # Create .claude directory in HOME if it doesn't exist
    mkdir -p "\$HOME/.claude"

    # Copy settings.json if it exists in app directory
    if [ -f "/home/vcap/app/.claude/settings.json" ]; then
        cp "/home/vcap/app/.claude/settings.json" "\$HOME/.claude/settings.json"
    fi

    # Copy .claude.json (MCP config) if it exists in app directory
    if [ -f "/home/vcap/app/.claude.json" ]; then
        cp "/home/vcap/app/.claude.json" "\$HOME/.claude.json"
    fi
fi

# Log level configuration (from config file or default)
# Priority: config file > environment variable > default
if [ -z "\$CLAUDE_CODE_LOG_LEVEL" ]; then
    export CLAUDE_CODE_LOG_LEVEL="${CLAUDE_CODE_LOG_LEVEL:-info}"
fi

# Model configuration (from config file or default)
if [ -z "\$CLAUDE_CODE_MODEL" ]; then
    export CLAUDE_CODE_MODEL="${CLAUDE_CODE_MODEL:-sonnet}"
fi

# Configure Node.js to trust Cloud Foundry system certificates
# This is critical for remote MCP servers (SSE/HTTP) that use internal CAs
if [ -n "\$CF_SYSTEM_CERT_PATH" ] && [ -d "\$CF_SYSTEM_CERT_PATH" ]; then
    # Concatenate all certificate files into a single bundle
    # NODE_EXTRA_CA_CERTS requires a file path, not a directory
    CA_BUNDLE="/tmp/cf-ca-bundle.crt"
    cat "\$CF_SYSTEM_CERT_PATH"/*.crt > "\$CA_BUNDLE" 2>/dev/null

    if [ -f "\$CA_BUNDLE" ]; then
        export NODE_EXTRA_CA_CERTS="\$CA_BUNDLE"
    fi
fi
EOF

    chmod +x "${profile_script}"

    # Export environment for build time
    export PATH="${deps_dir}/bin:${deps_dir}/node/bin:${PATH}"
    export CLAUDE_CLI_PATH="${deps_dir}/bin/claude"
}

# Create configuration files
create_config_files() {
    local deps_dir=$1
    local build_dir=$2

    # Create buildpack config file
    local config_file="${deps_dir}/config.yml"

    cat > "${config_file}" <<EOF
---
name: claude-code-buildpack
config:
  version: ${CLAUDE_CODE_VERSION:-latest}
  cli_path: ${deps_dir}/bin/claude
  node_path: ${deps_dir}/node/bin/node
  npm_path: ${deps_dir}/node/bin/npm
  config_home: /home/vcap/app
EOF

    # Note: .claude.json is now created by the Claude configurator (lib/claude_configurator.sh)
    # See configure_mcp_servers() function for MCP server configuration
}

# Get Claude Code version (if needed for other scripts)
get_claude_code_version() {
    echo "${CLAUDE_CODE_VERSION:-latest}"
}

# Export API key handling (without exposing it in logs)
setup_api_key() {
    local deps_dir=$1

    if [ -z "${ANTHROPIC_API_KEY}" ]; then
        echo "       WARNING: ANTHROPIC_API_KEY not set"
        echo "       Claude Code will not function without an API key"
        return 1
    fi

    # API key is available via environment variable
    # We don't write it to disk for security reasons
    echo "       API key detected (not logged for security)"
    return 0
}
