package io.github.claudecode.cf;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Interface for executing Claude Code CLI commands from Java applications.
 * <p>
 * This interface provides multiple execution modes:
 * </p>
 * <ul>
 *   <li>Synchronous execution - blocks until command completes</li>
 *   <li>Asynchronous execution - returns immediately with a CompletableFuture</li>
 *   <li>Streaming execution - returns a Stream of output lines</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ClaudeCodeExecutor executor = new ClaudeCodeExecutorImpl();
 * 
 * // Synchronous execution
 * String result = executor.execute("Analyze this code for bugs");
 * 
 * // Asynchronous execution
 * CompletableFuture<String> future = executor.executeAsync("Generate unit tests");
 * future.thenAccept(result -> System.out.println(result));
 * 
 * // Streaming execution
 * Stream<String> lines = executor.executeStreaming("Refactor this function");
 * lines.forEach(System.out::println);
 * }</pre>
 *
 * @author Claude Code Buildpack Team
 * @since 1.0.0
 */
public interface ClaudeCodeExecutor {

    /**
     * Execute a Claude Code command synchronously.
     * <p>
     * This method blocks until the Claude Code CLI command completes and returns
     * the full output as a single string.
     * </p>
     *
     * @param prompt the prompt to send to Claude Code
     * @return the complete output from Claude Code
     * @throws IllegalArgumentException if prompt is null or empty
     * @throws ClaudeCodeExecutionException if command execution fails or times out
     */
    String execute(String prompt);

    /**
     * Execute a Claude Code command synchronously with options.
     * <p>
     * This method provides more control over command execution, including
     * custom timeout values, model selection, and additional CLI flags.
     * </p>
     *
     * @param prompt the prompt to send to Claude Code
     * @param options execution options (timeout, model, etc.)
     * @return the complete output from Claude Code
     * @throws IllegalArgumentException if prompt is null or empty
     * @throws ClaudeCodeExecutionException if command execution fails or times out
     */
    String execute(String prompt, ClaudeCodeOptions options);

    /**
     * Execute a Claude Code command asynchronously.
     * <p>
     * This method returns immediately with a CompletableFuture that will be
     * completed when the Claude Code command finishes. The future can be used
     * to attach callbacks or wait for completion at a later time.
     * </p>
     *
     * @param prompt the prompt to send to Claude Code
     * @return a CompletableFuture that completes with the command output
     * @throws IllegalArgumentException if prompt is null or empty
     */
    CompletableFuture<String> executeAsync(String prompt);

    /**
     * Execute a Claude Code command asynchronously with options.
     *
     * @param prompt the prompt to send to Claude Code
     * @param options execution options (timeout, model, etc.)
     * @return a CompletableFuture that completes with the command output
     * @throws IllegalArgumentException if prompt is null or empty
     */
    CompletableFuture<String> executeAsync(String prompt, ClaudeCodeOptions options);

    /**
     * Execute a Claude Code command and return output as a stream of lines.
     * <p>
     * This method returns a Stream that emits each line of output as it becomes
     * available. This is useful for real-time output display or processing large
     * responses incrementally.
     * </p>
     * <p>
     * <strong>Note:</strong> The returned Stream must be closed properly to avoid
     * resource leaks. Use try-with-resources or call {@code close()} explicitly.
     * </p>
     *
     * @param prompt the prompt to send to Claude Code
     * @return a Stream of output lines
     * @throws IllegalArgumentException if prompt is null or empty
     * @throws ClaudeCodeExecutionException if command execution fails
     */
    Stream<String> executeStreaming(String prompt);

    /**
     * Execute a Claude Code command and return output as a stream of lines with options.
     *
     * @param prompt the prompt to send to Claude Code
     * @param options execution options (timeout, model, etc.)
     * @return a Stream of output lines
     * @throws IllegalArgumentException if prompt is null or empty
     * @throws ClaudeCodeExecutionException if command execution fails
     */
    Stream<String> executeStreaming(String prompt, ClaudeCodeOptions options);

    /**
     * Check if Claude Code CLI is available and properly configured.
     * <p>
     * This method performs basic health checks:
     * </p>
     * <ul>
     *   <li>Verifies CLAUDE_CLI_PATH environment variable is set</li>
     *   <li>Verifies the CLI executable exists</li>
     *   <li>Verifies ANTHROPIC_API_KEY is set</li>
     * </ul>
     *
     * @return true if Claude Code CLI is available, false otherwise
     */
    boolean isAvailable();

    /**
     * Get the version of the Claude Code CLI.
     *
     * @return the version string, or null if version cannot be determined
     */
    String getVersion();
}
