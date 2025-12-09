package org.tanzu.claudecode.cf;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Interface for executing Claude Code CLI commands from Java applications.
 * <p>
 * This interface provides multiple execution modes:
 * </p>
 * <ul>
 *   <li><b>Synchronous execution</b> - blocks until command completes</li>
 *   <li><b>Asynchronous execution</b> - returns immediately with a CompletableFuture</li>
 *   <li><b>Streaming execution</b> - returns a Stream of output lines as they arrive</li>
 *   <li><b>Conversational sessions</b> - maintain context across multiple messages</li>
 * </ul>
 *
 * <h2>Single-Shot Execution</h2>
 * <p>For one-off prompts without maintaining conversation context:</p>
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
 * <h2>Conversational Sessions</h2>
 * <p>For multi-turn conversations where Claude maintains context:</p>
 * <pre>{@code
 * ClaudeCodeExecutor executor = new ClaudeCodeExecutorImpl();
 * 
 * // Create a conversation session
 * String sessionId = executor.createConversationSession();
 * 
 * try {
 *     // First message
 *     String response1 = executor.sendMessage(sessionId, "What is the capital of France?");
 *     // response1: "The capital of France is Paris."
 *     
 *     // Second message - Claude remembers the context
 *     String response2 = executor.sendMessage(sessionId, "What is its population?");
 *     // response2: "Paris has a population of approximately 2.1 million..."
 *     
 *     // Third message - context still maintained
 *     String response3 = executor.sendMessage(sessionId, "Tell me about its history");
 *     // response3: "Paris has a rich history dating back to..."
 *     
 * } finally {
 *     // Always close sessions when done
 *     executor.closeConversationSession(sessionId);
 * }
 * }</pre>
 *
 * @author Claude Code Buildpack Team
 * @since 1.0.0
 * @see ConversationSession
 * @see ConversationSessionManager
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

    // ==================== Conversational Session Methods ====================

    /**
     * Create a new conversation session with default options.
     * <p>
     * A conversation session maintains a long-running Claude CLI process that
     * preserves context across multiple messages. This enables natural multi-turn
     * conversations where Claude remembers previous exchanges.
     * </p>
     * <p>
     * Sessions are stored in memory and will expire after a period of inactivity
     * (default: 30 minutes). Sessions should be explicitly closed when done to
     * free resources immediately.
     * </p>
     * <p>
     * <strong>Example:</strong>
     * </p>
     * <pre>{@code
     * String sessionId = executor.createConversationSession();
     * try {
     *     String resp1 = executor.sendMessage(sessionId, "What is Java?");
     *     String resp2 = executor.sendMessage(sessionId, "Show me an example");
     * } finally {
     *     executor.closeConversationSession(sessionId);
     * }
     * }</pre>
     *
     * @return the unique session ID
     * @throws ClaudeCodeExecutionException if session creation fails
     * @since 1.1.0
     * @see #createConversationSession(ClaudeCodeOptions)
     * @see #sendMessage(String, String)
     * @see #closeConversationSession(String)
     */
    String createConversationSession();

    /**
     * Create a new conversation session with custom options.
     * <p>
     * This method allows you to configure the session with custom timeout,
     * model selection, and other options.
     * </p>
     * <p>
     * <strong>Example:</strong>
     * </p>
     * <pre>{@code
     * ClaudeCodeOptions options = ClaudeCodeOptions.builder()
     *     .timeout(Duration.ofMinutes(5))
     *     .model("sonnet")
     *     .build();
     * 
     * String sessionId = executor.createConversationSession(options);
     * try {
     *     String response = executor.sendMessage(sessionId, "Explain recursion");
     * } finally {
     *     executor.closeConversationSession(sessionId);
     * }
     * }</pre>
     *
     * @param options configuration options for the session
     * @return the unique session ID
     * @throws IllegalArgumentException if options is null
     * @throws ClaudeCodeExecutionException if session creation fails
     * @since 1.1.0
     * @see #createConversationSession()
     */
    String createConversationSession(ClaudeCodeOptions options);

    /**
     * Send a message within an existing conversation session.
     * <p>
     * This method sends a message to an active conversation session and blocks
     * until the complete response is received. The session maintains context,
     * so Claude will remember previous messages in the conversation.
     * </p>
     * <p>
     * <strong>Important:</strong> This method blocks until the entire response
     * is received. For real-time streaming within a session, see the future
     * enhancement documentation.
     * </p>
     * <p>
     * <strong>Example - Multi-turn conversation:</strong>
     * </p>
     * <pre>{@code
     * String sessionId = executor.createConversationSession();
     * 
     * // First message establishes context
     * String resp1 = executor.sendMessage(sessionId, "I'm building a REST API for a library");
     * 
     * // Subsequent messages reference the context
     * String resp2 = executor.sendMessage(sessionId, "What entities should I create?");
     * String resp3 = executor.sendMessage(sessionId, "Show me the Book entity");
     * String resp4 = executor.sendMessage(sessionId, "Add validation annotations");
     * 
     * executor.closeConversationSession(sessionId);
     * }</pre>
     *
     * @param sessionId the unique session ID
     * @param message the message to send to Claude
     * @return Claude's complete response
     * @throws IllegalArgumentException if sessionId or message is null or empty
     * @throws ConversationSessionManager.SessionNotFoundException if the session does not exist or has expired
     * @throws ClaudeCodeExecutionException if communication with Claude fails
     * @since 1.1.0
     * @see #createConversationSession()
     * @see #isSessionActive(String)
     */
    String sendMessage(String sessionId, String message);

    /**
     * Close a conversation session and release its resources.
     * <p>
     * This method explicitly closes a session, terminating the underlying
     * Claude CLI process and freeing resources immediately. Sessions will
     * also auto-expire after inactivity, but explicit closing is recommended
     * for prompt resource cleanup.
     * </p>
     * <p>
     * This method is idempotent - calling it multiple times with the same
     * session ID is safe (subsequent calls will be no-ops).
     * </p>
     * <p>
     * <strong>Example:</strong>
     * </p>
     * <pre>{@code
     * String sessionId = executor.createConversationSession();
     * try {
     *     // Use the session...
     *     executor.sendMessage(sessionId, "Hello");
     * } finally {
     *     // Always close in finally block
     *     executor.closeConversationSession(sessionId);
     * }
     * }</pre>
     *
     * @param sessionId the unique session ID
     * @throws IllegalArgumentException if sessionId is null or empty
     * @since 1.1.0
     * @see #createConversationSession()
     */
    void closeConversationSession(String sessionId);

    /**
     * Check if a conversation session exists and is active.
     * <p>
     * This is a non-throwing method to check session existence and state.
     * Use this to verify a session is still valid before sending messages.
     * </p>
     * <p>
     * A session is considered active if:
     * </p>
     * <ul>
     *   <li>It exists in the session manager</li>
     *   <li>It has not been explicitly closed</li>
     *   <li>It has not expired due to inactivity</li>
     *   <li>Its underlying process is still alive</li>
     * </ul>
     * <p>
     * <strong>Example:</strong>
     * </p>
     * <pre>{@code
     * String sessionId = executor.createConversationSession();
     * 
     * if (executor.isSessionActive(sessionId)) {
     *     executor.sendMessage(sessionId, "Hello");
     * } else {
     *     // Session expired or closed, create a new one
     *     sessionId = executor.createConversationSession();
     * }
     * }</pre>
     *
     * @param sessionId the unique session ID to check
     * @return true if the session exists and is active, false otherwise
     * @since 1.1.0
     * @see #createConversationSession()
     * @see #sendMessage(String, String)
     */
    boolean isSessionActive(String sessionId);

    // ==================== Utility Methods ====================

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

