#!/usr/bin/env bash
# lib/installer.sh: Installation logic for Node.js and Claude Code CLI

# Node.js version to install (LTS)
NODE_VERSION="${NODE_VERSION:-20.11.0}"
NODE_BASE_URL="https://nodejs.org/dist"

# Install Node.js
install_nodejs() {
    local install_dir=$1
    local cache_dir=$2

    local node_dir="${install_dir}/node"
    local node_archive="node-v${NODE_VERSION}-linux-x64.tar.xz"
    local node_url="${NODE_BASE_URL}/v${NODE_VERSION}/${node_archive}"
    local cache_file="${cache_dir}/${node_archive}"

    # Check if Node.js is already cached
    if [ -f "${cache_file}" ]; then
        echo "       Using cached Node.js v${NODE_VERSION}"
    else
        echo "       Downloading Node.js v${NODE_VERSION}..."
        curl -L -s "${node_url}" -o "${cache_file}"

        if [ $? -ne 0 ]; then
            echo "       ERROR: Failed to download Node.js"
            return 1
        fi
    fi

    # Extract Node.js
    echo "       Extracting Node.js..."
    mkdir -p "${node_dir}"
    tar xJf "${cache_file}" -C "${node_dir}" --strip-components=1

    if [ $? -ne 0 ]; then
        echo "       ERROR: Failed to extract Node.js"
        return 1
    fi

    # Verify installation
    if [ ! -x "${node_dir}/bin/node" ]; then
        echo "       ERROR: Node.js binary not found after extraction"
        return 1
    fi

    # Add to PATH for build time
    export PATH="${node_dir}/bin:${PATH}"

    echo "       Node.js v${NODE_VERSION} installed successfully"
    return 0
}

# Install Claude Code CLI
install_claude_code() {
    local install_dir=$1
    local cache_dir=$2

    local claude_version="${CLAUDE_CODE_VERSION:-latest}"
    local npm_cache="${cache_dir}/npm-cache"

    # Ensure Node.js is in PATH
    export PATH="${install_dir}/node/bin:${PATH}"

    # Set npm cache directory
    export npm_config_cache="${npm_cache}"
    mkdir -p "${npm_cache}"

    # Install Claude Code globally to our deps directory
    local npm_prefix="${install_dir}"

    echo "       Installing @anthropic-ai/claude-code..."

    if [ "${claude_version}" == "latest" ]; then
        npm install -g --prefix="${npm_prefix}" @anthropic-ai/claude-code
    else
        npm install -g --prefix="${npm_prefix}" "@anthropic-ai/claude-code@${claude_version}"
    fi

    if [ $? -ne 0 ]; then
        echo "       ERROR: Failed to install Claude Code CLI"
        return 1
    fi

    # npm install -g automatically creates the bin/claude symlink
    # No need to create it manually or chmod it

    echo "       Claude Code CLI installed successfully"
    return 0
}

# Get installed Claude Code version
get_claude_version() {
    local install_dir=$1

    export PATH="${install_dir}/node/bin:${PATH}"

    if [ -x "${install_dir}/bin/claude" ]; then
        "${install_dir}/bin/claude" --version 2>/dev/null || echo "unknown"
    else
        echo "not installed"
    fi
}

# Verify Claude Code installation
verify_installation() {
    local install_dir=$1

    # Check Node.js
    if [ ! -x "${install_dir}/node/bin/node" ]; then
        echo "       ERROR: Node.js verification failed"
        return 1
    fi

    # Check npm
    if [ ! -x "${install_dir}/node/bin/npm" ]; then
        echo "       ERROR: npm verification failed"
        return 1
    fi

    # Check Claude Code CLI
    if [ ! -x "${install_dir}/bin/claude" ]; then
        echo "       ERROR: Claude Code CLI verification failed"
        return 1
    fi

    echo "       Installation verified successfully"
    return 0
}

# Clean up installation artifacts (optional, for reducing slug size)
cleanup_installation() {
    local install_dir=$1
    local cache_dir=$2

    # Remove npm cache to save space
    rm -rf "${cache_dir}/npm-cache"

    # Remove unnecessary Node.js files
    rm -rf "${install_dir}/node/include"
    rm -rf "${install_dir}/node/share/doc"
    rm -rf "${install_dir}/node/share/man"

    echo "       Cleaned up installation artifacts"
}
