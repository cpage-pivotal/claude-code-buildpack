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
 * </pre>
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
     * If not specified, uses the ANTHROPIC_API_KEY environment variable.
     */
    private String apiKey;

    /**
     * Whether to enable the REST API controller.
     * Default: true
     */
    private boolean controllerEnabled = true;

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
     * Returns the API key, falling back to ANTHROPIC_API_KEY environment variable.
     *
     * @return the API key
     */
    public String getApiKey() {
        // Fall back to environment variable if not set
        if (apiKey == null || apiKey.isEmpty()) {
            return System.getenv("ANTHROPIC_API_KEY");
        }
        return apiKey;
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
}

