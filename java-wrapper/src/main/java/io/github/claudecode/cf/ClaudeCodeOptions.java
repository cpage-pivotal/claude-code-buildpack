package io.github.claudecode.cf;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration options for Claude Code CLI command execution.
 * <p>
 * This class provides a builder pattern for configuring Claude Code execution
 * parameters such as timeout, model selection, and additional environment variables.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ClaudeCodeOptions options = ClaudeCodeOptions.builder()
 *     .timeout(Duration.ofMinutes(5))
 *     .model("opus")
 *     .dangerouslySkipPermissions(true)
 *     .env("CUSTOM_VAR", "value")
 *     .build();
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

    private ClaudeCodeOptions(Builder builder) {
        this.timeout = builder.timeout;
        this.model = builder.model;
        this.dangerouslySkipPermissions = builder.dangerouslySkipPermissions;
        this.additionalEnv = new HashMap<>(builder.additionalEnv);
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
                '}';
    }
}
