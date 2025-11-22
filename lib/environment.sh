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

# Log level configuration (if not already set)
if [ -z "\$CLAUDE_CODE_LOG_LEVEL" ]; then
    export CLAUDE_CODE_LOG_LEVEL="info"
fi

# Model configuration (if not already set)
if [ -z "\$CLAUDE_CODE_MODEL" ]; then
    export CLAUDE_CODE_MODEL="sonnet"
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

    # Always create a basic .claude.json for the CLI
    # This prevents the CLI from hanging while trying to create/find config
    parse_and_create_claude_config "${build_dir}" "${build_dir}/.claude.json"
}

# Parse configuration and create .claude.json
parse_and_create_claude_config() {
    local build_dir=$1
    local output_file=$2

    # This is a simplified implementation
    # In a production buildpack, you'd use a proper YAML parser

    # For now, create a basic .claude.json template
    cat > "${output_file}" <<'EOF'
{
  "mcpServers": {}
}
EOF

    echo "       Note: MCP server configuration will be enhanced in Phase 2"
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
