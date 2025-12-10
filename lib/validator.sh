#!/usr/bin/env bash
# lib/validator.sh: Validation utilities for buildpack

# Validate environment and prerequisites
validate_environment() {
    local build_dir=$1

    echo "       Checking prerequisites..."

    # Check for required commands
    if ! command -v curl &> /dev/null; then
        echo "       ERROR: curl is required but not installed"
        return 1
    fi

    if ! command -v tar &> /dev/null; then
        echo "       ERROR: tar is required but not installed"
        return 1
    fi

    # Validate ANTHROPIC_API_KEY or CLAUDE_CODE_OAUTH_TOKEN
    if [ -z "${ANTHROPIC_API_KEY}" ] && [ -z "${CLAUDE_CODE_OAUTH_TOKEN}" ]; then
        echo "       WARNING: Neither ANTHROPIC_API_KEY nor CLAUDE_CODE_OAUTH_TOKEN is set"
        echo "       Claude Code requires authentication to function"
        echo "       Set either ANTHROPIC_API_KEY or CLAUDE_CODE_OAUTH_TOKEN in your manifest"
    elif [ -n "${ANTHROPIC_API_KEY}" ]; then
        # Basic validation - check if it looks like an Anthropic API key
        if [[ ! "${ANTHROPIC_API_KEY}" =~ ^sk-ant- ]]; then
            echo "       WARNING: ANTHROPIC_API_KEY format appears invalid"
            echo "       Expected format: sk-ant-..."
        else
            echo "       API key format validated"
        fi
    elif [ -n "${CLAUDE_CODE_OAUTH_TOKEN}" ]; then
        echo "       OAuth token detected (using CLAUDE_CODE_OAUTH_TOKEN)"
    fi

    # Check disk space (basic check)
    local available_space=$(df -k "${build_dir}" | awk 'NR==2 {print $4}')
    local required_space=524288  # 512MB in KB

    if [ "${available_space}" -lt "${required_space}" ]; then
        echo "       WARNING: Low disk space (available: ${available_space}KB, recommended: ${required_space}KB)"
    fi

    return 0
}

# Validate configuration file
validate_config_file() {
    local config_file=$1

    if [ ! -f "${config_file}" ]; then
        echo "       ERROR: Configuration file not found: ${config_file}"
        return 1
    fi

    # Basic YAML validation (check if file is readable)
    if [ ! -r "${config_file}" ]; then
        echo "       ERROR: Configuration file not readable: ${config_file}"
        return 1
    fi

    echo "       Configuration file validated"
    return 0
}

# Validate Node.js version
validate_nodejs_version() {
    local node_version=$1

    # Check if version string is valid (basic check)
    if [[ ! "${node_version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo "       ERROR: Invalid Node.js version format: ${node_version}"
        echo "       Expected format: X.Y.Z (e.g., 20.11.0)"
        return 1
    fi

    return 0
}

# Validate installation directory
validate_install_dir() {
    local install_dir=$1

    if [ ! -d "${install_dir}" ]; then
        echo "       ERROR: Installation directory does not exist: ${install_dir}"
        return 1
    fi

    if [ ! -w "${install_dir}" ]; then
        echo "       ERROR: Installation directory is not writable: ${install_dir}"
        return 1
    fi

    return 0
}

# Validate MCP server configuration (basic)
validate_mcp_config() {
    local config=$1

    # This is a placeholder for Phase 2
    # In Phase 2, we'll implement proper MCP configuration validation

    echo "       MCP validation (Phase 2 feature)"
    return 0
}

# Check if a command exists
command_exists() {
    local cmd=$1
    command -v "${cmd}" &> /dev/null
}

# Validate required tools
validate_required_tools() {
    local missing_tools=()

    local required_tools=("curl" "tar" "mkdir" "chmod" "ln")

    for tool in "${required_tools[@]}"; do
        if ! command_exists "${tool}"; then
            missing_tools+=("${tool}")
        fi
    done

    if [ ${#missing_tools[@]} -gt 0 ]; then
        echo "       ERROR: Missing required tools: ${missing_tools[*]}"
        return 1
    fi

    echo "       All required tools are available"
    return 0
}
