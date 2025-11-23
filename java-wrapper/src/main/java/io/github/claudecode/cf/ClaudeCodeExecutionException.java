package io.github.claudecode.cf;

/**
 * Exception thrown when Claude Code CLI execution fails.
 * <p>
 * This exception wraps various error conditions that can occur during
 * Claude Code command execution, including:
 * </p>
 * <ul>
 *   <li>Process execution failures</li>
 *   <li>Timeouts</li>
 *   <li>Non-zero exit codes</li>
 *   <li>I/O errors</li>
 *   <li>Configuration issues</li>
 * </ul>
 *
 * @author Claude Code Buildpack Team
 * @since 1.0.0
 */
public class ClaudeCodeExecutionException extends RuntimeException {

    /** The exit code from the CLI process, if available. */
    private final Integer exitCode;
    
    /** The stderr output from the CLI process, if available. */
    private final String stderr;

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public ClaudeCodeExecutionException(String message) {
        super(message);
        this.exitCode = null;
        this.stderr = null;
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public ClaudeCodeExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.exitCode = null;
        this.stderr = null;
    }

    /**
     * Constructs a new exception with detailed error information.
     *
     * @param message the detail message
     * @param exitCode the exit code from the CLI process
     * @param stderr the stderr output from the CLI
     */
    public ClaudeCodeExecutionException(String message, int exitCode, String stderr) {
        super(message);
        this.exitCode = exitCode;
        this.stderr = stderr;
    }

    /**
     * Constructs a new exception with detailed error information and a cause.
     *
     * @param message the detail message
     * @param exitCode the exit code from the CLI process
     * @param stderr the stderr output from the CLI
     * @param cause the cause
     */
    public ClaudeCodeExecutionException(String message, int exitCode, String stderr, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
        this.stderr = stderr;
    }

    /**
     * Get the exit code from the CLI process, if available.
     *
     * @return the exit code, or null if not available
     */
    public Integer getExitCode() {
        return exitCode;
    }

    /**
     * Get the stderr output from the CLI process, if available.
     *
     * @return the stderr output, or null if not available
     */
    public String getStderr() {
        return stderr;
    }

    /**
     * Check if this exception has exit code information.
     *
     * @return true if exit code is available
     */
    public boolean hasExitCode() {
        return exitCode != null;
    }

    /**
     * Check if this exception has stderr output.
     *
     * @return true if stderr output is available
     */
    public boolean hasStderr() {
        return stderr != null && !stderr.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        if (exitCode != null) {
            sb.append(" [Exit code: ").append(exitCode).append("]");
        }
        if (stderr != null && !stderr.isEmpty()) {
            sb.append(" [stderr: ").append(stderr).append("]");
        }
        return sb.toString();
    }
}
