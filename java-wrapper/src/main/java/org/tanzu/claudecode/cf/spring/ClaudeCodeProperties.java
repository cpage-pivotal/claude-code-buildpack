package org.tanzu.claudecode.cf.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Claude Code CLI integration.
 * <p>
 * These properties can be configured in application.yml or application.properties:
 * </p>
 * <pre>
 * claude-code:
 *   enabled: true
 *   cli-path: /path/to/claude
 *   api-key: sk-ant-xxxxx
 *   controller-enabled: true
 *   # OpenAI-compatible LLM settings (optional)
 *   use-openai-provider: false
 *   openai:
 *     base-url: https://api.openai.com/v1
 *     api-key: sk-xxxxx
 *     model: gpt-4o
 * </pre>
 * <p>
 * When {@code use-openai-provider} is true, the buildpack will use an OpenAI-compatible
 * LLM instead of Anthropic's Claude. This requires a LiteLLM proxy to translate between
 * Anthropic API format (used by Claude CLI) and OpenAI API format.
 * </p>
 *
 * @author Claude Code Buildpack Team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "claude-code")
public class ClaudeCodeProperties {

    /**
     * Constructs a new ClaudeCodeProperties instance with default values.
     */
    public ClaudeCodeProperties() {
    }

    /**
     * Whether Claude Code integration is enabled.
     * Default: true
     */
    private boolean enabled = true;

    /**
     * Path to the Claude Code CLI executable.
     * If not specified, uses the CLAUDE_CLI_PATH environment variable.
     */
    private String cliPath;

    /**
     * Anthropic API key for authentication.
     * If not specified, uses the ANTHROPIC_API_KEY or CLAUDE_CODE_OAUTH_TOKEN environment variable.
     */
    private String apiKey;

    /**
     * Claude Code OAuth token for authentication.
     * If not specified, uses the CLAUDE_CODE_OAUTH_TOKEN environment variable.
     */
    private String oauthToken;

    /**
     * Whether to enable the REST API controller.
     * Default: true
     */
    private boolean controllerEnabled = true;

    /**
     * Whether to use an OpenAI-compatible LLM provider instead of Anthropic.
     * When enabled, requires openai.base-url, openai.api-key, and openai.model to be set.
     * A LiteLLM proxy will be used to translate between Anthropic and OpenAI API formats.
     * Default: false
     */
    private boolean useOpenaiProvider = false;

    /**
     * OpenAI-compatible LLM configuration settings.
     * Only used when use-openai-provider is true.
     */
    private OpenAiConfig openai = new OpenAiConfig();

    /**
     * Returns whether Claude Code integration is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether Claude Code integration is enabled.
     *
     * @param enabled true to enable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the CLI path, falling back to CLAUDE_CLI_PATH environment variable.
     *
     * @return the CLI path
     */
    public String getCliPath() {
        // Fall back to environment variable if not set
        if (cliPath == null || cliPath.isEmpty()) {
            return System.getenv("CLAUDE_CLI_PATH");
        }
        return cliPath;
    }

    /**
     * Sets the CLI path.
     *
     * @param cliPath the CLI path
     */
    public void setCliPath(String cliPath) {
        this.cliPath = cliPath;
    }

    /**
     * Returns the API key, falling back to ANTHROPIC_API_KEY or CLAUDE_CODE_OAUTH_TOKEN environment variable.
     * Priority: configured apiKey > configured oauthToken > ANTHROPIC_API_KEY env > CLAUDE_CODE_OAUTH_TOKEN env
     *
     * @return the API key or OAuth token
     */
    public String getApiKey() {
        // Priority 1: explicitly configured API key
        if (apiKey != null && !apiKey.isEmpty()) {
            return apiKey;
        }
        // Priority 2: explicitly configured OAuth token
        if (oauthToken != null && !oauthToken.isEmpty()) {
            return oauthToken;
        }
        // Priority 3: ANTHROPIC_API_KEY environment variable
        String envApiKey = System.getenv("ANTHROPIC_API_KEY");
        if (envApiKey != null && !envApiKey.isEmpty()) {
            return envApiKey;
        }
        // Priority 4: CLAUDE_CODE_OAUTH_TOKEN environment variable
        return System.getenv("CLAUDE_CODE_OAUTH_TOKEN");
    }

    /**
     * Sets the API key.
     *
     * @param apiKey the API key
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Returns the OAuth token, falling back to CLAUDE_CODE_OAUTH_TOKEN environment variable.
     *
     * @return the OAuth token
     */
    public String getOauthToken() {
        // Fall back to environment variable if not set
        if (oauthToken == null || oauthToken.isEmpty()) {
            return System.getenv("CLAUDE_CODE_OAUTH_TOKEN");
        }
        return oauthToken;
    }

    /**
     * Sets the OAuth token.
     *
     * @param oauthToken the OAuth token
     */
    public void setOauthToken(String oauthToken) {
        this.oauthToken = oauthToken;
    }

    /**
     * Returns whether the REST API controller is enabled.
     *
     * @return true if controller is enabled
     */
    public boolean isControllerEnabled() {
        return controllerEnabled;
    }

    /**
     * Sets whether the REST API controller is enabled.
     *
     * @param controllerEnabled true to enable controller
     */
    public void setControllerEnabled(boolean controllerEnabled) {
        this.controllerEnabled = controllerEnabled;
    }

    /**
     * Returns whether to use an OpenAI-compatible LLM provider.
     *
     * @return true if OpenAI provider should be used
     */
    public boolean isUseOpenaiProvider() {
        return useOpenaiProvider;
    }

    /**
     * Sets whether to use an OpenAI-compatible LLM provider.
     *
     * @param useOpenaiProvider true to use OpenAI provider
     */
    public void setUseOpenaiProvider(boolean useOpenaiProvider) {
        this.useOpenaiProvider = useOpenaiProvider;
    }

    /**
     * Returns the OpenAI configuration.
     *
     * @return the OpenAI configuration
     */
    public OpenAiConfig getOpenai() {
        return openai;
    }

    /**
     * Sets the OpenAI configuration.
     *
     * @param openai the OpenAI configuration
     */
    public void setOpenai(OpenAiConfig openai) {
        this.openai = openai;
    }

    /**
     * Checks if OpenAI provider is properly configured.
     * Returns true if use-openai-provider is enabled and all required OpenAI settings are present.
     *
     * @return true if OpenAI provider is fully configured
     */
    public boolean isOpenaiProviderConfigured() {
        if (!useOpenaiProvider) {
            return false;
        }
        return openai != null 
            && openai.getBaseUrl() != null && !openai.getBaseUrl().isEmpty()
            && openai.getApiKey() != null && !openai.getApiKey().isEmpty()
            && openai.getModel() != null && !openai.getModel().isEmpty();
    }

    /**
     * Nested configuration class for OpenAI-compatible LLM settings.
     * <p>
     * These settings map to Spring AI OpenAI properties:
     * </p>
     * <ul>
     *   <li>{@code base-url} maps to {@code spring.ai.openai.base-url}</li>
     *   <li>{@code api-key} maps to {@code spring.ai.openai.api-key}</li>
     *   <li>{@code model} maps to {@code spring.ai.openai.chat.options.model}</li>
     * </ul>
     */
    public static class OpenAiConfig {

        /**
         * Base URL for the OpenAI-compatible API endpoint.
         * Examples: https://api.openai.com/v1, http://localhost:11434/v1 (Ollama)
         */
        private String baseUrl;

        /**
         * API key for authenticating with the OpenAI-compatible endpoint.
         */
        private String apiKey;

        /**
         * Model name to use with the OpenAI-compatible endpoint.
         * Examples: gpt-4o, gpt-4o-mini, llama3.2
         * <p>
         * If not specified and config-url is available, the model name will be
         * discovered automatically from the Tanzu GenAI service configuration endpoint.
         * </p>
         */
        private String model;

        /**
         * Configuration URL for discovering available models (Tanzu GenAI services).
         * This endpoint returns metadata about the service including available model names.
         * Example: https://genai-proxy.sys.example.com/endpoint-name/config/v1/endpoint
         */
        private String configUrl;

        /**
         * Port for the local LiteLLM proxy server.
         * Default: 4000
         */
        private int proxyPort = 4000;

        /**
         * Constructs a new OpenAiConfig with default values.
         */
        public OpenAiConfig() {
        }

        /**
         * Returns the base URL for the OpenAI-compatible API.
         *
         * @return the base URL
         */
        public String getBaseUrl() {
            return baseUrl;
        }

        /**
         * Sets the base URL for the OpenAI-compatible API.
         *
         * @param baseUrl the base URL
         */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /**
         * Returns the API key for the OpenAI-compatible endpoint.
         *
         * @return the API key
         */
        public String getApiKey() {
            return apiKey;
        }

        /**
         * Sets the API key for the OpenAI-compatible endpoint.
         *
         * @param apiKey the API key
         */
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        /**
         * Returns the model name for the OpenAI-compatible endpoint.
         *
         * @return the model name
         */
        public String getModel() {
            return model;
        }

        /**
         * Sets the model name for the OpenAI-compatible endpoint.
         *
         * @param model the model name
         */
        public void setModel(String model) {
            this.model = model;
        }

        /**
         * Returns the configuration URL for model discovery.
         *
         * @return the config URL
         */
        public String getConfigUrl() {
            return configUrl;
        }

        /**
         * Sets the configuration URL for model discovery.
         *
         * @param configUrl the config URL
         */
        public void setConfigUrl(String configUrl) {
            this.configUrl = configUrl;
        }

        /**
         * Returns the port for the local LiteLLM proxy server.
         *
         * @return the proxy port
         */
        public int getProxyPort() {
            return proxyPort;
        }

        /**
         * Sets the port for the local LiteLLM proxy server.
         *
         * @param proxyPort the proxy port
         */
        public void setProxyPort(int proxyPort) {
            this.proxyPort = proxyPort;
        }

        @Override
        public String toString() {
            return "OpenAiConfig{" +
                    "baseUrl='" + baseUrl + '\'' +
                    ", apiKey='" + (apiKey != null ? "[REDACTED]" : "null") + '\'' +
                    ", model='" + model + '\'' +
                    ", configUrl='" + configUrl + '\'' +
                    ", proxyPort=" + proxyPort +
                    '}';
        }
    }
}

