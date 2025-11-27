package org.tanzu.claudecode.cf;

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
 *   <li>Streaming execution - returns a Stream of output lines as they arrive</li>
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
 * // Streaming execution (MUST use try-with-resources)
 * try (Stream<String> lines = executor.executeStreaming("Refactor this function")) {
 *     lines.forEach(System.out::println);
 * }
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
     * available from the Claude Code CLI process. This enables true real-time streaming
     * where lines are processed as they are produced, not after the command completes.
     * </p>
     * <p>
     * <strong>CRITICAL - Resource Management:</strong> The returned Stream manages an
     * active subprocess and must be closed to prevent resource leaks. Always use
     * try-with-resources or explicitly call {@code close()} on the Stream:
     * </p>
     * <pre>{@code
     * // Recommended: try-with-resources
     * try (Stream<String> lines = executor.executeStreaming("analyze code")) {
     *     lines.forEach(System.out::println);
     * }
     * 
     * // Alternative: explicit close
     * Stream<String> lines = executor.executeStreaming("analyze code");
     * try {
     *     lines.forEach(System.out::println);
     * } finally {
     *     lines.close();
     * }
     * }</pre>
     * <p>
     * If the Stream is not closed, the underlying Claude Code process will continue
     * running until the configured timeout, wasting system resources.
     * </p>
     * <p>
     * <strong>Timeout Enforcement:</strong> The process will be forcibly terminated if
     * it exceeds the configured timeout duration (default: 3 minutes), even if the
     * Stream is still open. This prevents runaway processes.
     * </p>
     *
     * @param prompt the prompt to send to Claude Code
     * @return a Stream of output lines that must be closed when done
     * @throws IllegalArgumentException if prompt is null or empty
     * @throws ClaudeCodeExecutionException if command execution fails to start
     */
    Stream<String> executeStreaming(String prompt);

    /**
     * Execute a Claude Code command and return output as a stream of lines with options.
     * <p>
     * This method provides more control over streaming execution, including custom
     * timeout values, model selection, and additional CLI flags.
     * </p>
     * <p>
     * <strong>CRITICAL - Resource Management:</strong> The returned Stream manages an
     * active subprocess and must be closed to prevent resource leaks. Always use
     * try-with-resources or explicitly call {@code close()} on the Stream.
     * </p>
     * <pre>{@code
     * ClaudeCodeOptions options = ClaudeCodeOptions.builder()
     *     .timeout(Duration.ofMinutes(5))
     *     .model("sonnet")
     *     .build();
     * 
     * try (Stream<String> lines = executor.executeStreaming("analyze code", options)) {
     *     lines.forEach(System.out::println);
     * }
     * }</pre>
     *
     * @param prompt the prompt to send to Claude Code
     * @param options execution options (timeout, model, etc.)
     * @return a Stream of output lines that must be closed when done
     * @throws IllegalArgumentException if prompt is null or empty
     * @throws ClaudeCodeExecutionException if command execution fails to start
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

