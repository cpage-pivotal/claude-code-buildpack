package org.tanzu.claudecode.cf;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration options for Claude Code CLI command execution.
 * <p>
 * This class provides a builder pattern for configuring Claude Code execution
 * parameters such as timeout, model selection, additional environment variables,
 * and conversational session settings.
 * </p>
 *
 * <h2>Single-Shot Execution Options</h2>
 * <pre>{@code
 * ClaudeCodeOptions options = ClaudeCodeOptions.builder()
 *     .timeout(Duration.ofMinutes(5))
 *     .model("opus")
 *     .dangerouslySkipPermissions(true)
 *     .env("CUSTOM_VAR", "value")
 *     .build();
 * }</pre>
 *
 * <h2>Conversational Session Options</h2>
 * <pre>{@code
 * ClaudeCodeOptions options = ClaudeCodeOptions.builder()
 *     .timeout(Duration.ofMinutes(5))
 *     .model("sonnet")
 *     .sessionInactivityTimeout(Duration.ofMinutes(45))  // Custom session timeout
 *     .workingDirectory("/home/vcap/app")                // Custom working directory
 *     .build();
 * 
 * String sessionId = executor.createConversationSession(options);
 * }</pre>
 *
 * @author Claude Code Buildpack Team
 * @since 1.0.0
 */
public class ClaudeCodeOptions {

    private final Duration timeout;
    private final String model;
    private final boolean dangerouslySkipPermissions;
    private final Map<String, String> additionalEnv;
    
    // Conversational session configuration (since 1.1.0)
    private final Duration sessionInactivityTimeout;
    private final String workingDirectory;

    private ClaudeCodeOptions(Builder builder) {
        this.timeout = builder.timeout;
        this.model = builder.model;
        this.dangerouslySkipPermissions = builder.dangerouslySkipPermissions;
        this.additionalEnv = new HashMap<>(builder.additionalEnv);
        this.sessionInactivityTimeout = builder.sessionInactivityTimeout;
        this.workingDirectory = builder.workingDirectory;
    }

    /**
     * Get the timeout duration for command execution.
     *
     * @return the timeout duration
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Get the Claude model to use (e.g., "sonnet", "opus", "haiku").
     *
     * @return the model name, or null for default
     */
    public String getModel() {
        return model;
    }

    /**
     * Check if permissions should be skipped.
     *
     * @return true if --dangerously-skip-permissions should be used
     */
    public boolean isDangerouslySkipPermissions() {
        return dangerouslySkipPermissions;
    }

    /**
     * Get additional environment variables to pass to the CLI.
     *
     * @return a map of environment variable names to values
     */
    public Map<String, String> getAdditionalEnv() {
        return new HashMap<>(additionalEnv);
    }

    /**
     * Get the inactivity timeout for conversational sessions.
     * <p>
     * This timeout determines how long a conversation session can remain idle
     * before being automatically expired and cleaned up. The timeout is checked
     * by the background cleanup task every 5 minutes.
     * </p>
     * <p>
     * If not set, the {@link ConversationSessionManager} will use its default
     * timeout (typically 30 minutes).
     * </p>
     *
     * @return the session inactivity timeout, or null to use manager default
     * @since 1.1.0
     * @see ConversationSessionManager
     */
    public Duration getSessionInactivityTimeout() {
        return sessionInactivityTimeout;
    }

    /**
     * Get the working directory for the Claude CLI process.
     * <p>
     * The working directory affects where the Claude CLI looks for configuration
     * files and stores session history. Claude CLI uses directory-based session
     * tracking in {@code ~/.claude/sessions/}.
     * </p>
     * <p>
     * If not set, the Claude CLI will use the current working directory of the
     * Java process (typically the application root in Cloud Foundry).
     * </p>
     *
     * @return the working directory path, or null for default
     * @since 1.1.0
     */
    public String getWorkingDirectory() {
        return workingDirectory;
    }

    /**
     * Create a new builder for ClaudeCodeOptions.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create default options with sensible defaults.
     *
     * @return default ClaudeCodeOptions
     */
    public static ClaudeCodeOptions defaults() {
        return builder().build();
    }

    /**
     * Builder for ClaudeCodeOptions.
     */
    public static class Builder {
        private Duration timeout = Duration.ofMinutes(3);
        private String model;
        private boolean dangerouslySkipPermissions = true;
        private Map<String, String> additionalEnv = new HashMap<>();
        
        // Conversational session configuration (since 1.1.0)
        private Duration sessionInactivityTimeout;
        private String workingDirectory;

        /**
         * Constructs a new Builder instance.
         */
        public Builder() {
        }

        /**
         * Set the timeout for command execution.
         * <p>
         * Default: 3 minutes
         * </p>
         *
         * @param timeout the timeout duration
         * @return this builder
         * @throws IllegalArgumentException if timeout is null or negative
         */
        public Builder timeout(Duration timeout) {
            Objects.requireNonNull(timeout, "Timeout cannot be null");
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("Timeout must be positive");
            }
            this.timeout = timeout;
            return this;
        }

        /**
         * Set the Claude model to use.
         * <p>
         * Valid values: "sonnet", "opus", "haiku"
         * Default: Uses the model configured in .claude.json or Claude Code defaults
         * </p>
         *
         * @param model the model name
         * @return this builder
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * Set whether to skip permission prompts.
         * <p>
         * When true, the --dangerously-skip-permissions flag is added.
         * This is recommended for automated/non-interactive environments like Cloud Foundry.
         * </p>
         * <p>
         * Default: true
         * </p>
         *
         * @param skip true to skip permissions
         * @return this builder
         */
        public Builder dangerouslySkipPermissions(boolean skip) {
            this.dangerouslySkipPermissions = skip;
            return this;
        }

        /**
         * Add an additional environment variable to pass to the CLI.
         *
         * @param name the environment variable name
         * @param value the environment variable value
         * @return this builder
         * @throws IllegalArgumentException if name or value is null
         */
        public Builder env(String name, String value) {
            Objects.requireNonNull(name, "Environment variable name cannot be null");
            Objects.requireNonNull(value, "Environment variable value cannot be null");
            this.additionalEnv.put(name, value);
            return this;
        }

        /**
         * Add multiple environment variables to pass to the CLI.
         *
         * @param env a map of environment variable names to values
         * @return this builder
         * @throws IllegalArgumentException if env is null
         */
        public Builder env(Map<String, String> env) {
            Objects.requireNonNull(env, "Environment variables map cannot be null");
            this.additionalEnv.putAll(env);
            return this;
        }

        /**
         * Set the inactivity timeout for conversational sessions.
         * <p>
         * This timeout determines how long a conversation session can remain idle
         * before being automatically expired and cleaned up. Sessions that exceed
         * this timeout will be closed by the background cleanup task.
         * </p>
         * <p>
         * Default: null (uses {@link ConversationSessionManager} default of 30 minutes)
         * </p>
         * <p>
         * <strong>Example:</strong>
         * </p>
         * <pre>{@code
         * ClaudeCodeOptions options = ClaudeCodeOptions.builder()
         *     .sessionInactivityTimeout(Duration.ofMinutes(45))
         *     .build();
         * 
         * // Session will auto-expire after 45 minutes of inactivity
         * String sessionId = executor.createConversationSession(options);
         * }</pre>
         *
         * @param timeout the session inactivity timeout duration
         * @return this builder
         * @throws IllegalArgumentException if timeout is null, zero, or negative
         * @since 1.1.0
         */
        public Builder sessionInactivityTimeout(Duration timeout) {
            Objects.requireNonNull(timeout, "Session inactivity timeout cannot be null");
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("Session inactivity timeout must be positive");
            }
            this.sessionInactivityTimeout = timeout;
            return this;
        }

        /**
         * Set the working directory for the Claude CLI process.
         * <p>
         * The working directory affects where the Claude CLI looks for configuration
         * files and stores session history. This is particularly useful in Cloud Foundry
         * environments where you want to control the session storage location.
         * </p>
         * <p>
         * Default: null (uses current working directory of Java process)
         * </p>
         * <p>
         * <strong>Example:</strong>
         * </p>
         * <pre>{@code
         * ClaudeCodeOptions options = ClaudeCodeOptions.builder()
         *     .workingDirectory("/home/vcap/app")
         *     .build();
         * 
         * String sessionId = executor.createConversationSession(options);
         * }</pre>
         *
         * @param directory the working directory path
         * @return this builder
         * @throws IllegalArgumentException if directory is null or empty
         * @since 1.1.0
         */
        public Builder workingDirectory(String directory) {
            if (directory == null || directory.trim().isEmpty()) {
                throw new IllegalArgumentException("Working directory cannot be null or empty");
            }
            this.workingDirectory = directory;
            return this;
        }

        /**
         * Build the ClaudeCodeOptions instance.
         *
         * @return a new ClaudeCodeOptions instance
         */
        public ClaudeCodeOptions build() {
            return new ClaudeCodeOptions(this);
        }
    }

    @Override
    public String toString() {
        return "ClaudeCodeOptions{" +
                "timeout=" + timeout +
                ", model='" + model + '\'' +
                ", dangerouslySkipPermissions=" + dangerouslySkipPermissions +
                ", additionalEnv=" + additionalEnv.keySet() +
                ", sessionInactivityTimeout=" + sessionInactivityTimeout +
                ", workingDirectory='" + workingDirectory + '\'' +
                '}';
    }
}

