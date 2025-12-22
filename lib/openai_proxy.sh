#!/usr/bin/env bash
# lib/openai_proxy.sh: LiteLLM proxy installation and configuration for OpenAI-compatible LLMs

# LiteLLM version to install
LITELLM_VERSION="${LITELLM_VERSION:-1.55.3}"

# Python version for LiteLLM (minimal version)
PYTHON_VERSION="${PYTHON_VERSION:-3.11.7}"
PYTHON_BASE_URL="https://github.com/indygreg/python-build-standalone/releases/download"

# Check if OpenAI provider mode is enabled
is_openai_provider_enabled() {
    local build_dir=$1
    
    # Check environment variable first
    if [ "${CLAUDE_CODE_USE_OPENAI_PROVIDER}" = "true" ]; then
        return 0
    fi
    
    # Check .claude-code-config.yml for openai provider setting
    local config_file="${build_dir}/.claude-code-config.yml"
    if [ -f "${config_file}" ]; then
        if grep -q "use-openai-provider: true" "${config_file}" 2>/dev/null; then
            return 0
        fi
    fi
    
    # Check application.yml for spring.ai.openai settings
    local app_config="${build_dir}/application.yml"
    if [ -f "${app_config}" ]; then
        if grep -q "spring.ai.openai" "${app_config}" 2>/dev/null; then
            return 0
        fi
    fi
    
    # Not enabled
    return 1
}

# Install Python (needed for LiteLLM)
install_python() {
    local install_dir=$1
    local cache_dir=$2
    
    local python_dir="${install_dir}/python"
    local python_archive="cpython-${PYTHON_VERSION}+20240107-x86_64-unknown-linux-gnu-install_only.tar.gz"
    local python_url="${PYTHON_BASE_URL}/20240107/${python_archive}"
    local cache_file="${cache_dir}/${python_archive}"
    
    # Check if Python is already cached
    if [ -f "${cache_file}" ]; then
        echo "       Using cached Python v${PYTHON_VERSION}"
    else
        echo "       Downloading Python v${PYTHON_VERSION}..."
        curl -L -s "${python_url}" -o "${cache_file}"
        
        if [ $? -ne 0 ]; then
            echo "       ERROR: Failed to download Python"
            return 1
        fi
    fi
    
    # Extract Python
    echo "       Extracting Python..."
    mkdir -p "${python_dir}"
    tar xzf "${cache_file}" -C "${python_dir}" --strip-components=1
    
    if [ $? -ne 0 ]; then
        echo "       ERROR: Failed to extract Python"
        return 1
    fi
    
    # Verify installation
    if [ ! -x "${python_dir}/bin/python3" ]; then
        echo "       ERROR: Python binary not found after extraction"
        return 1
    fi
    
    # Add to PATH for build time
    export PATH="${python_dir}/bin:${PATH}"
    
    echo "       Python v${PYTHON_VERSION} installed successfully"
    return 0
}

# Install LiteLLM
install_litellm() {
    local install_dir=$1
    local cache_dir=$2
    local index=$3
    
    # Ensure Python is in PATH
    export PATH="${install_dir}/python/bin:${PATH}"
    
    local pip_cache="${cache_dir}/pip-cache"
    mkdir -p "${pip_cache}"
    
    echo "       Installing LiteLLM v${LITELLM_VERSION}..."
    
    # Install LiteLLM with pip to the lib/python directory for imports
    pip3 install --cache-dir="${pip_cache}" --target="${install_dir}/lib/python" \
        "litellm[proxy]==${LITELLM_VERSION}"
    
    if [ $? -ne 0 ]; then
        echo "       ERROR: Failed to install LiteLLM"
        return 1
    fi
    
    # Also install to the Python directory to get CLI entry points (litellm command)
    echo "       Installing LiteLLM CLI entry points..."
    pip3 install --cache-dir="${pip_cache}" --prefix="${install_dir}/python" \
        "litellm[proxy]==${LITELLM_VERSION}"
    
    if [ $? -ne 0 ]; then
        echo "       WARNING: Failed to install LiteLLM CLI entry points, will use uvicorn fallback"
    fi
    
    # Fix shebang lines in CLI scripts to use portable path
    # During staging, Python is in a temp directory, but at runtime it's at /home/vcap/deps/$index/python/bin
    echo "       Fixing CLI script shebangs for runtime..."
    local runtime_python="/home/vcap/deps/${index}/python/bin/python3"
    for script in "${install_dir}/python/bin/"*; do
        if [ -f "$script" ] && head -1 "$script" | grep -q "^#!.*python"; then
            # Replace the first line with the correct runtime path
            sed -i "1s|^#!.*python.*|#!${runtime_python}|" "$script"
            echo "         Fixed shebang in $(basename "$script")"
        fi
    done
    
    echo "       LiteLLM v${LITELLM_VERSION} installed successfully"
    return 0
}

# Generate custom callback handler for message transformation
generate_litellm_callback_handler() {
    local deps_dir=$1
    
    # Place the callback handler directly in the deps directory
    # LiteLLM looks for callbacks relative to the config file directory or cwd
    local callback_file="${deps_dir}/tanzu_genai_handler.py"
    
    # Create a custom callback handler that transforms message content arrays to strings
    # This is necessary because Tanzu GenAI does NOT support the array-style content format
    # that OpenAI technically supports and that LiteLLM may produce when translating from Anthropic
    cat > "${callback_file}" <<'PYEOF'
"""
Tanzu GenAI Custom Handler for LiteLLM
--------------------------------------
This callback handler transforms messages before sending to the backend:
1. Converts array-style content to simple string content
   - Tanzu GenAI doesn't support: {"content": [{"type": "text", "text": "..."}]}
   - Tanzu GenAI requires: {"content": "..."}
2. Removes Anthropic-specific fields that can't be dropped by drop_params
"""

from litellm.integrations.custom_logger import CustomLogger
from typing import Any, Optional, Literal
import json


class TanzuGenAIHandler(CustomLogger):
    """Custom handler to transform requests for Tanzu GenAI compatibility."""
    
    def __init__(self):
        super().__init__()
    
    def _flatten_content(self, content):
        """Convert array-style content to string content."""
        if isinstance(content, str):
            return content
        
        if isinstance(content, list):
            # Extract text from each content block and join
            text_parts = []
            for item in content:
                if isinstance(item, dict):
                    if item.get("type") == "text":
                        text_parts.append(item.get("text", ""))
                    elif "text" in item:
                        text_parts.append(item.get("text", ""))
                elif isinstance(item, str):
                    text_parts.append(item)
            return "".join(text_parts)
        
        return content
    
    def _clean_message(self, msg):
        """Clean a single message, removing unsupported fields."""
        if not isinstance(msg, dict):
            return msg
        
        # Create a clean message with only supported fields
        clean = {}
        
        # Copy role
        if "role" in msg:
            clean["role"] = msg["role"]
        
        # Flatten content
        if "content" in msg:
            clean["content"] = self._flatten_content(msg["content"])
        
        # Copy tool-related fields (these are supported)
        if "tool_calls" in msg:
            clean["tool_calls"] = msg["tool_calls"]
        if "tool_call_id" in msg:
            clean["tool_call_id"] = msg["tool_call_id"]
        if "name" in msg:
            clean["name"] = msg["name"]
        
        # Skip Anthropic-specific fields: cache_control, etc.
        
        return clean
    
    async def async_pre_call_hook(
        self,
        user_api_key_dict,
        cache,
        data: dict,
        call_type: Literal[
            "completion",
            "text_completion",
            "embeddings",
            "image_generation",
            "moderation",
            "audio_transcription",
        ]
    ) -> Optional[dict]:
        """Transform request data before sending to the LLM backend."""
        
        if call_type not in ["completion", "text_completion"]:
            return data
        
        try:
            # Transform messages to flatten content arrays
            if "messages" in data and isinstance(data["messages"], list):
                data["messages"] = [self._clean_message(msg) for msg in data["messages"]]
                
            # Handle system message if present at top level
            if "system" in data:
                system = data.get("system")
                if isinstance(system, list):
                    # Convert system array to string
                    text_parts = []
                    for item in system:
                        if isinstance(item, dict) and item.get("type") == "text":
                            text_parts.append(item.get("text", ""))
                        elif isinstance(item, str):
                            text_parts.append(item)
                    # Add as system message at the start of messages
                    system_text = "".join(text_parts)
                    if system_text and "messages" in data:
                        # Insert system message if not already present
                        if not data["messages"] or data["messages"][0].get("role") != "system":
                            data["messages"].insert(0, {"role": "system", "content": system_text})
                    del data["system"]
                elif isinstance(system, str) and "messages" in data:
                    if not data["messages"] or data["messages"][0].get("role") != "system":
                        data["messages"].insert(0, {"role": "system", "content": system})
                    del data["system"]
            
            # Remove Anthropic-specific top-level fields
            for key in ["metadata", "anthropic_version"]:
                if key in data:
                    del data[key]
            
        except Exception as e:
            # Log but don't fail - let the request proceed
            print(f"TanzuGenAIHandler: Error transforming request: {e}")
        
        return data


# Create handler instance for LiteLLM to use
tanzu_genai_handler = TanzuGenAIHandler()
PYEOF

    chmod 644 "${callback_file}"
    echo "       Custom Tanzu GenAI callback handler generated: ${callback_file}"
}

# Generate LiteLLM configuration file
generate_litellm_config() {
    local deps_dir=$1
    local build_dir=$2
    
    local config_template="${deps_dir}/litellm_config_template.yaml"
    
    # Configuration template with placeholder variables
    # These will be substituted at runtime by the startup script
    #
    # Key configuration notes for Tanzu GenAI:
    # 1. Tanzu GenAI accepts BOTH paths:
    #    - {base_url}/openai/chat/completions
    #    - {base_url}/openai/v1/chat/completions
    # 2. Model name in request body should be "openai/{model}" format
    # 3. Use openai/ provider for proper tool calling support
    #    The openai/ provider appends /v1 to the path, which Tanzu GenAI accepts
    #    LiteLLM strips the "openai/" prefix when sending to the backend, so we double it
    # 4. Custom callback handler to flatten array-style content to strings
    cat > "${config_template}" <<'EOF'
# LiteLLM Proxy Configuration
# Generated by Claude Code Buildpack for OpenAI-compatible LLM support
#
# This is a template file. The startup script will substitute variables at runtime.

model_list:
  # Wildcard model: accepts any model name from Claude CLI and routes to configured OpenAI model
  - model_name: "*"
    litellm_params:
      # Use openai/ provider for proper tool calling support
      # The openai/ provider appends /v1 to the api_base path
      # Tanzu GenAI accepts both /openai/chat/completions AND /openai/v1/chat/completions
      # 
      # IMPORTANT: LiteLLM strips the "openai/" prefix when sending to the backend
      # So we use "openai/openai/{model}" which becomes "openai/{model}" after stripping
      # This is required because Tanzu GenAI expects model name like "openai/gpt-oss-120b"
      model: "openai/openai/${LITELLM_OPENAI_MODEL}"
      api_key: "${LITELLM_OPENAI_API_KEY}"
      # Set api_base to {base_url}/openai
      # LiteLLM openai/ provider will append /v1/chat/completions
      # Result: {base_url}/openai/v1/chat/completions (which Tanzu GenAI accepts)
      api_base: "${LITELLM_OPENAI_BASE_URL}/openai"

general_settings:
  # Enable Anthropic-compatible endpoint for Claude CLI
  enable_anthropic_messages_endpoint: true
  
  # Logging
  master_key: null  # No master key required for local use
  
litellm_settings:
  # Request timeout
  request_timeout: 300
  
  # Retry settings
  num_retries: 3
  retry_after: 5
  
  # Drop params not supported by the model
  # This helps when Claude CLI sends Anthropic-specific params that OpenAI doesn't support
  drop_params: true
  
  # Custom callback to transform messages for Tanzu GenAI compatibility
  # This flattens array-style content to simple strings, which Tanzu GenAI requires
  callbacks: ["tanzu_genai_handler.tanzu_genai_handler"]

router_settings:
  # Single routing since we have one model
  routing_strategy: "simple-shuffle"
EOF

    chmod 644 "${config_template}"
    echo "       LiteLLM configuration template generated: ${config_template}"
}

# Create LiteLLM startup script
create_litellm_startup_script() {
    local deps_dir=$1
    local build_dir=$2
    local index=$3
    
    local startup_script="${deps_dir}/bin/start-litellm-proxy.sh"
    
    cat > "${startup_script}" <<EOF
#!/usr/bin/env bash
# LiteLLM Proxy Startup Script
# Generated by Claude Code Buildpack

# Buildpack index (set at build time)
DEPS_IDX="${index}"

# Set up Python environment
export PATH="\$DEPS_DIR/\$DEPS_IDX/python/bin:\$PATH"
export PYTHONPATH="\$DEPS_DIR/\$DEPS_IDX/lib/python:\$PYTHONPATH"

# Configuration
CONFIG_TEMPLATE="\$DEPS_DIR/\$DEPS_IDX/litellm_config_template.yaml"
CONFIG_FILE="\$DEPS_DIR/\$DEPS_IDX/litellm_config.yaml"
PORT="\${LITELLM_PORT:-4000}"
HOST="127.0.0.1"

# Validate required environment variables
if [ -z "\$LITELLM_OPENAI_BASE_URL" ]; then
    echo "ERROR: LITELLM_OPENAI_BASE_URL is not set" >&2
    exit 1
fi

if [ -z "\$LITELLM_OPENAI_API_KEY" ]; then
    echo "ERROR: LITELLM_OPENAI_API_KEY is not set" >&2
    exit 1
fi

if [ -z "\$LITELLM_OPENAI_MODEL" ]; then
    echo "ERROR: LITELLM_OPENAI_MODEL is not set" >&2
    exit 1
fi

echo "Starting LiteLLM proxy..."
echo "  OpenAI Base URL: \$LITELLM_OPENAI_BASE_URL"
echo "  OpenAI Model: \$LITELLM_OPENAI_MODEL"
echo "  Proxy Port: \$PORT"
echo "  Config Template: \$CONFIG_TEMPLATE"

# Check if config template exists
if [ ! -f "\$CONFIG_TEMPLATE" ]; then
    echo "ERROR: Config template not found: \$CONFIG_TEMPLATE" >&2
    exit 1
fi

echo "Generating runtime config from template..."
# Substitute environment variables in the template to create the actual config
cat "\$CONFIG_TEMPLATE" | \\
    sed "s|\\\${LITELLM_OPENAI_MODEL}|\$LITELLM_OPENAI_MODEL|g" | \\
    sed "s|\\\${LITELLM_OPENAI_BASE_URL}|\$LITELLM_OPENAI_BASE_URL|g" | \\
    sed "s|\\\${LITELLM_OPENAI_API_KEY}|\$LITELLM_OPENAI_API_KEY|g" \\
    > "\$CONFIG_FILE"

if [ ! -f "\$CONFIG_FILE" ]; then
    echo "ERROR: Failed to generate config file: \$CONFIG_FILE" >&2
    exit 1
fi

echo "Config file generated successfully"
echo "Config file: \$CONFIG_FILE"

echo "Checking Python environment..."
echo "Python path: \$(which python3)"
echo "Python version: \$(python3 --version 2>&1)"
echo "PYTHONPATH: \$PYTHONPATH"

# Test if litellm module is available
if ! python3 -c "import litellm" 2>/dev/null; then
    echo "ERROR: LiteLLM module not found in Python path" >&2
    echo "Python sys.path:" >&2
    python3 -c "import sys; print('\\n'.join(sys.path))" >&2
    exit 1
fi

echo "LiteLLM module found, starting proxy server..."

# ============================================
# DIAGNOSTICS - Debug container environment
# ============================================
echo ""
echo "=== CONTAINER DIAGNOSTICS ==="
echo "HOME: \$HOME"
echo "USER: \$(whoami)"
echo "PWD: \$(pwd)"
echo "DEPS_DIR: \$DEPS_DIR"
echo ""

echo "=== Testing writable directories ==="
touch /tmp/litellm_test 2>&1 && echo "/tmp is writable" && rm /tmp/litellm_test 2>/dev/null
touch \$HOME/litellm_test 2>&1 && echo "\$HOME is writable" && rm \$HOME/litellm_test 2>/dev/null || echo "\$HOME is NOT writable"
echo ""

echo "=== Checking litellm entry point ==="
which litellm 2>&1 || echo "litellm command not found in PATH"
LITELLM_BIN="\$DEPS_DIR/\$DEPS_IDX/python/bin/litellm"
if [ -f "\$LITELLM_BIN" ]; then
    echo "litellm binary exists at: \$LITELLM_BIN"
    ls -la "\$LITELLM_BIN"
    echo "First line (shebang):"
    head -1 "\$LITELLM_BIN"
else
    echo "litellm binary NOT found at: \$LITELLM_BIN"
    echo "Contents of \$DEPS_DIR/\$DEPS_IDX/python/bin/:"
    ls -la "\$DEPS_DIR/\$DEPS_IDX/python/bin/" 2>/dev/null | head -20 || echo "Directory not found"
fi
echo ""

echo "=== Testing litellm --version ==="
python3 -c "import litellm; print('LiteLLM version:', litellm.version)" 2>&1 || echo "Failed to get litellm version"
echo ""

echo "=== Checking uvicorn ==="
python3 -c "import uvicorn; print('uvicorn version:', uvicorn.__version__)" 2>&1 || echo "uvicorn NOT available"
echo ""

echo "=== Checking fastapi ==="
python3 -c "import fastapi; print('fastapi version:', fastapi.__version__)" 2>&1 || echo "fastapi NOT available"
echo ""

echo "=== Environment variables ==="
env | grep -i python || true
env | grep -i litellm || true
env | grep -i deps || true
echo "=== END DIAGNOSTICS ==="
echo ""

# Verify config file exists and is readable
if [ ! -r "\$CONFIG_FILE" ]; then
    echo "ERROR: Config file not readable: \$CONFIG_FILE" >&2
    exit 1
fi

echo "Config file contents:"
cat "\$CONFIG_FILE"

echo ""
echo "Attempting to start LiteLLM proxy server..."

# Set environment variables that LiteLLM might need
export LITELLM_CONFIG_PATH="\$CONFIG_FILE"

# Configure SSL certificates for outbound HTTPS connections
# Cloud Foundry containers have CA certificates at /etc/ssl/certs/ca-certificates.crt
if [ -f "/etc/ssl/certs/ca-certificates.crt" ]; then
    export REQUESTS_CA_BUNDLE="/etc/ssl/certs/ca-certificates.crt"
    export SSL_CERT_FILE="/etc/ssl/certs/ca-certificates.crt"
    export CURL_CA_BUNDLE="/etc/ssl/certs/ca-certificates.crt"
    echo "SSL certificates configured: /etc/ssl/certs/ca-certificates.crt"
else
    echo "WARNING: CA certificates not found at /etc/ssl/certs/ca-certificates.crt"
fi

# Test network connectivity to the OpenAI base URL
echo ""
echo "=== Testing network connectivity ==="
echo "Testing connection to: \$LITELLM_OPENAI_BASE_URL"

# Extract hostname from URL
OPENAI_HOST=\$(echo "\$LITELLM_OPENAI_BASE_URL" | sed -E 's|https?://([^/:]+).*|\1|')
echo "Hostname: \$OPENAI_HOST"

# Test DNS resolution
if command -v nslookup >/dev/null 2>&1; then
    echo "DNS lookup:"
    nslookup "\$OPENAI_HOST" 2>&1 | head -5 || echo "DNS lookup failed or timed out"
elif command -v host >/dev/null 2>&1; then
    echo "DNS lookup:"
    host "\$OPENAI_HOST" 2>&1 | head -5 || echo "DNS lookup failed"
else
    echo "No DNS lookup tools available (nslookup/host)"
fi

# Test HTTPS connection with curl
if command -v curl >/dev/null 2>&1; then
    echo ""
    echo "Testing HTTPS connection with curl:"
    curl -v --max-time 5 "\$LITELLM_OPENAI_BASE_URL" 2>&1 | head -30 || echo "curl test failed or timed out"
else
    echo "curl not available for connectivity test"
fi
echo "=== END CONNECTIVITY TEST ==="
echo ""

# Method 1: Try the litellm CLI if available (preferred - handles config loading properly)
echo "Trying Method 1: litellm CLI command"
if command -v litellm >/dev/null 2>&1; then
    echo "Using litellm CLI command"
    echo "Executing: litellm --config \$CONFIG_FILE --host \$HOST --port \$PORT --detailed_debug"
    litellm --config "\$CONFIG_FILE" --host "\$HOST" --port "\$PORT" --detailed_debug 2>&1
    
    EXIT_CODE=\$?
    echo "litellm CLI exited with code: \$EXIT_CODE" >&2
    exit \$EXIT_CODE
fi

# Method 2: Try python -m litellm (also handles config loading properly)
echo "Trying Method 2: python3 -m litellm"
echo "Executing: python3 -m litellm --config \$CONFIG_FILE --host \$HOST --port \$PORT --detailed_debug"
python3 -m litellm --config "\$CONFIG_FILE" --host "\$HOST" --port "\$PORT" --detailed_debug 2>&1

EXIT_CODE=\$?
echo "python -m litellm exited with code: \$EXIT_CODE" >&2

# If python -m litellm failed, try uvicorn as last resort
if [ \$EXIT_CODE -ne 0 ]; then
    echo ""
    echo "python -m litellm failed, trying uvicorn fallback..."
    echo "WARNING: uvicorn may not load config properly - model list may not initialize"
    
    # Method 3: Try using uvicorn directly (last resort - may not load config)
    echo "Trying Method 3: uvicorn with litellm.proxy.proxy_server:app"
    if python3 -c "import uvicorn" 2>/dev/null; then
        echo "Starting with uvicorn..."
        echo "Executing: python3 -m uvicorn litellm.proxy.proxy_server:app --host \$HOST --port \$PORT --log-level debug"
        python3 -m uvicorn litellm.proxy.proxy_server:app \\
            --host "\$HOST" \\
            --port "\$PORT" \\
            --log-level debug 2>&1
        
        EXIT_CODE=\$?
        echo "uvicorn exited with code: \$EXIT_CODE" >&2
    fi
fi

exit \$EXIT_CODE
EOF

    chmod +x "${startup_script}"
    echo "       LiteLLM startup script created: ${startup_script}"
}

# Set up OpenAI provider environment variables
setup_openai_environment() {
    local deps_dir=$1
    local build_dir=$2
    local index=$3
    
    # Append to the existing profile script
    local profile_script="${build_dir}/.profile.d/claude-code-env.sh"
    
    cat >> "${profile_script}" <<EOF

# ============================================
# OpenAI Provider Mode Configuration (LiteLLM)
# ============================================

# Add Python to PATH for LiteLLM
export PATH="\$DEPS_DIR/${index}/python/bin:\$PATH"

# Set Python path for LiteLLM libraries
export PYTHONPATH="\$DEPS_DIR/${index}/lib/python:\$PYTHONPATH"

# NOTE: The LiteLLM proxy is started on-demand by the Java wrapper
# when OpenAI provider mode is detected via VCAP_SERVICES or configuration.
# The proxy startup script is available at:
#   \$DEPS_DIR/${index}/bin/start-litellm-proxy.sh
EOF

    echo "       OpenAI environment configuration added to profile"
}

# Verify LiteLLM installation
verify_litellm_installation() {
    local install_dir=$1
    
    # Check Python
    if [ ! -x "${install_dir}/python/bin/python3" ]; then
        echo "       ERROR: Python verification failed"
        return 1
    fi
    
    # Check LiteLLM is installed
    export PATH="${install_dir}/python/bin:${PATH}"
    export PYTHONPATH="${install_dir}/lib/python:${PYTHONPATH}"
    
    if ! python3 -c "import litellm" 2>/dev/null; then
        echo "       ERROR: LiteLLM verification failed"
        return 1
    fi
    
    echo "       LiteLLM installation verified successfully"
    return 0
}

# Main entry point for installing OpenAI provider support
install_openai_provider_support() {
    local install_dir=$1
    local cache_dir=$2
    local build_dir=$3
    local index=$4
    
    echo "-----> Installing OpenAI Provider Support (LiteLLM)"
    
    # Install Python
    echo "       Installing Python runtime..."
    install_python "${install_dir}" "${cache_dir}"
    if [ $? -ne 0 ]; then
        echo "       ERROR: Failed to install Python"
        return 1
    fi
    
    # Install LiteLLM
    echo "       Installing LiteLLM proxy..."
    install_litellm "${install_dir}" "${cache_dir}" "${index}"
    if [ $? -ne 0 ]; then
        echo "       ERROR: Failed to install LiteLLM"
        return 1
    fi
    
    # Generate custom callback handler for message transformation
    echo "       Generating custom callback handler..."
    generate_litellm_callback_handler "${install_dir}"
    
    # Generate configuration
    echo "       Generating LiteLLM configuration..."
    generate_litellm_config "${install_dir}" "${build_dir}"
    
    # Create startup script
    echo "       Creating LiteLLM startup script..."
    create_litellm_startup_script "${install_dir}" "${build_dir}" "${index}"
    
    # Set up environment
    echo "       Configuring OpenAI provider environment..."
    setup_openai_environment "${install_dir}" "${build_dir}" "${index}"
    
    # Verify installation
    echo "       Verifying LiteLLM installation..."
    verify_litellm_installation "${install_dir}"
    if [ $? -ne 0 ]; then
        echo "       WARNING: LiteLLM verification failed, but continuing..."
    fi
    
    echo "       OpenAI provider support installed successfully!"
    return 0
}

