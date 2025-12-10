package org.tanzu.claudecode.cf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Default implementation of {@link ClaudeCodeExecutor}.
 * <p>
 * This implementation uses {@link ProcessBuilder} to invoke the Claude Code CLI
 * and follows best practices for process management in Cloud Foundry environments.
 * </p>
 *
 * <h2>Critical Implementation Details</h2>
 * <p>
 * This implementation follows the patterns documented in DESIGN.md to avoid
 * common ProcessBuilder pitfalls:
 * </p>
 * <ul>
 *   <li>Always closes stdin immediately to prevent CLI from waiting for input</li>
 *   <li>Redirects stderr to stdout to prevent buffer deadlock</li>
 *   <li>Passes environment variables to subprocess explicitly</li>
 *   <li>Uses timeouts to prevent indefinite hangs</li>
 *   <li>Properly cleans up resources</li>
 * </ul>
 *
 * <h2>Environment Variables</h2>
 * <p>Required environment variables:</p>
 * <ul>
 *   <li><code>CLAUDE_CLI_PATH</code> - Path to the Claude Code CLI executable</li>
 *   <li><code>ANTHROPIC_API_KEY</code> - Anthropic API authentication key</li>
 *   <li><code>HOME</code> - Home directory (for .claude.json configuration)</li>
 * </ul>
 *
 * @author Claude Code Buildpack Team
 * @since 1.0.0
 */
public class ClaudeCodeExecutorImpl implements ClaudeCodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeCodeExecutorImpl.class);
    
    // Shared executor service for timeout management
    private static final ScheduledExecutorService timeoutExecutor = 
        Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "claude-code-timeout");
            t.setDaemon(true);
            return t;
        });
    
    private final String claudePath;
    private final Map<String, String> baseEnvironment;
    
    // Lazy-initialized conversation session manager
    private volatile ConversationSessionManager sessionManager;

    /**
     * Constructs a new executor using environment variables.
     * <p>
     * Requires the following environment variables to be set:
     * </p>
     * <ul>
     *   <li>CLAUDE_CLI_PATH</li>
     *   <li>ANTHROPIC_API_KEY</li>
     * </ul>
     *
     * @throws IllegalStateException if required environment variables are not set
     */
    public ClaudeCodeExecutorImpl() {
        this.claudePath = getRequiredEnv("CLAUDE_CLI_PATH");
        this.baseEnvironment = buildBaseEnvironment();
        
        logger.info("Initialized ClaudeCodeExecutor with CLI path: {}", maskPath(claudePath));
    }

    /**
     * Constructs a new executor with explicit configuration.
     *
     * @param claudePath path to the Claude Code CLI executable
     * @param apiKey Anthropic API key
     * @throws IllegalArgumentException if any parameter is null or empty
     */
    public ClaudeCodeExecutorImpl(String claudePath, String apiKey) {
        if (claudePath == null || claudePath.isEmpty()) {
            throw new IllegalArgumentException("Claude CLI path cannot be null or empty");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        
        this.claudePath = claudePath;
        this.baseEnvironment = buildEnvironment(apiKey);
        
        logger.info("Initialized ClaudeCodeExecutor with explicit configuration");
    }

    @Override
    public String execute(String prompt) {
        return execute(prompt, ClaudeCodeOptions.defaults());
    }

    @Override
    public String execute(String prompt, ClaudeCodeOptions options) {
        validatePrompt(prompt);
        Objects.requireNonNull(options, "Options cannot be null");
        
        logger.debug("Executing Claude Code with prompt length: {}, options: {}", 
                    prompt.length(), options);

        List<String> command = buildCommand(prompt, options);
        ProcessBuilder pb = new ProcessBuilder(command);
        
        // Add environment variables to subprocess
        Map<String, String> env = pb.environment();
        env.putAll(baseEnvironment);
        env.putAll(options.getAdditionalEnv());
        
        // CRITICAL: Redirect stderr to stdout to prevent buffer deadlock
        pb.redirectErrorStream(true);
        
        try {
            Process process = pb.start();
            
            // CRITICAL: Close stdin immediately so CLI doesn't wait for input
            process.getOutputStream().close();
            
            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("Claude output: {}", line);
                }
            }
            
            // Wait for process to complete with timeout
            boolean finished = process.waitFor(
                options.getTimeout().toMillis(), 
                TimeUnit.MILLISECONDS
            );
            
            if (!finished) {
                logger.error("Claude Code command timed out after {}", options.getTimeout());
                process.destroyForcibly();
                throw new TimeoutException("Claude Code execution timed out after " + options.getTimeout());
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String outputStr = output.toString();
                logger.error("Claude Code failed with exit code: {}, output: {}", exitCode, outputStr);
                throw new ClaudeCodeExecutionException(
                    "Claude Code failed with exit code: " + exitCode,
                    exitCode,
                    outputStr
                );
            }
            
            String result = output.toString();
            logger.info("Claude Code executed successfully, output length: {}", result.length());
            return result;
            
        } catch (IOException e) {
            logger.error("Failed to execute Claude Code", e);
            throw new ClaudeCodeExecutionException("Failed to execute Claude Code", e);
        } catch (InterruptedException e) {
            logger.error("Claude Code execution interrupted", e);
            Thread.currentThread().interrupt();
            throw new ClaudeCodeExecutionException("Claude Code execution interrupted", e);
        } catch (TimeoutException e) {
            throw new ClaudeCodeExecutionException("Claude Code execution timed out", e);
        }
    }

    @Override
    public CompletableFuture<String> executeAsync(String prompt) {
        return executeAsync(prompt, ClaudeCodeOptions.defaults());
    }

    @Override
    public CompletableFuture<String> executeAsync(String prompt, ClaudeCodeOptions options) {
        validatePrompt(prompt);
        Objects.requireNonNull(options, "Options cannot be null");
        
        return CompletableFuture.supplyAsync(() -> execute(prompt, options));
    }

    @Override
    public Stream<String> executeStreaming(String prompt) {
        return executeStreaming(prompt, ClaudeCodeOptions.defaults());
    }

    @Override
    public Stream<String> executeStreaming(String prompt, ClaudeCodeOptions options) {
        validatePrompt(prompt);
        Objects.requireNonNull(options, "Options cannot be null");
        
        logger.debug("Starting streaming execution with prompt length: {}, options: {}", 
                    prompt.length(), options);

        List<String> command = buildCommand(prompt, options);
        ProcessBuilder pb = new ProcessBuilder(command);
        
        // Add environment variables to subprocess
        Map<String, String> env = pb.environment();
        env.putAll(baseEnvironment);
        env.putAll(options.getAdditionalEnv());
        
        // CRITICAL: Redirect stderr to stdout to prevent buffer deadlock
        pb.redirectErrorStream(true);
        
        try {
            Process process = pb.start();
            
            // CRITICAL: Close stdin immediately so CLI doesn't wait for input
            process.getOutputStream().close();
            
            // Create streaming handle for resource management
            StreamingProcessHandle handle = new StreamingProcessHandle(process, options.getTimeout());
            
            // Create stream that reads lines as they arrive
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            
            // Return stream with proper cleanup handlers
            return reader.lines()
                .onClose(() -> {
                    logger.debug("Stream closed, cleaning up resources");
                    handle.close();
                    try {
                        reader.close();
                    } catch (IOException e) {
                        logger.warn("Error closing reader", e);
                    }
                });
            
        } catch (IOException e) {
            logger.error("Failed to start streaming execution", e);
            throw new ClaudeCodeExecutionException("Failed to start Claude Code streaming", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            // Check if CLI path environment variable is set
            if (claudePath == null || claudePath.isEmpty()) {
                logger.warn("CLAUDE_CLI_PATH not set");
                return false;
            }
            
            // Check if CLI executable exists
            Path cliPath = Paths.get(claudePath);
            if (!Files.exists(cliPath)) {
                logger.warn("Claude CLI executable not found at: {}", claudePath);
                return false;
            }
            
            // Check if API key or OAuth token is set
            String apiKey = baseEnvironment.get("ANTHROPIC_API_KEY");
            String oauthToken = baseEnvironment.get("CLAUDE_CODE_OAUTH_TOKEN");
            if ((apiKey == null || apiKey.isEmpty()) && (oauthToken == null || oauthToken.isEmpty())) {
                logger.warn("Neither ANTHROPIC_API_KEY nor CLAUDE_CODE_OAUTH_TOKEN is set");
                return false;
            }
            
            logger.debug("Claude Code CLI is available");
            return true;
            
        } catch (Exception e) {
            logger.error("Error checking Claude Code availability", e);
            return false;
        }
    }

    @Override
    public String getVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder(claudePath, "--version");
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            process.getOutputStream().close();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            
            if (process.exitValue() == 0) {
                return output.toString().trim();
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("Failed to get Claude Code version", e);
            return null;
        }
    }

    // ==================== Conversational Session Methods ====================

    @Override
    public String createConversationSession() {
        return createConversationSession(ClaudeCodeOptions.defaults());
    }

    @Override
    public String createConversationSession(ClaudeCodeOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("Options cannot be null");
        }
        
        logger.debug("Creating conversation session with options: {}", options);
        
        // Lazy initialization of session manager with timeout from options
        ConversationSessionManager manager = getOrCreateSessionManager(options);
        
        try {
            String sessionId = manager.createSession(options);
            logger.info("Created conversation session: {}", sessionId);
            return sessionId;
        } catch (Exception e) {
            logger.error("Failed to create conversation session", e);
            throw new ClaudeCodeExecutionException("Failed to create conversation session", e);
        }
    }

    @Override
    public String sendMessage(String sessionId, String message) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Session ID cannot be null or empty");
        }
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be null or empty");
        }
        
        logger.debug("Sending message to session {}: {} chars", sessionId, message.length());
        
        // Get session manager (will throw if not initialized)
        ConversationSessionManager manager = getSessionManager();
        if (manager == null) {
            throw new IllegalStateException("No conversation sessions have been created");
        }
        
        try {
            // Get the session
            ConversationSession session = manager.getSession(sessionId);
            
            // Send message
            String response = session.sendMessage(message);
            
            logger.debug("Received response from session {}: {} chars", sessionId, response.length());
            return response;
            
        } catch (ConversationSessionManager.SessionNotFoundException e) {
            logger.warn("Session not found: {}", sessionId);
            throw e;
        } catch (ConversationSession.ConversationSessionException e) {
            logger.error("Failed to send message to session {}", sessionId, e);
            throw new ClaudeCodeExecutionException("Failed to send message to session", e);
        }
    }

    @Override
    public void closeConversationSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Session ID cannot be null or empty");
        }
        
        logger.debug("Closing conversation session: {}", sessionId);
        
        // Get session manager (safe if not initialized - will just return)
        ConversationSessionManager manager = getSessionManager();
        if (manager != null) {
            manager.closeSession(sessionId);
            logger.info("Closed conversation session: {}", sessionId);
        } else {
            logger.debug("Session manager not initialized, session {} may not exist", sessionId);
        }
    }

    @Override
    public boolean isSessionActive(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return false;
        }
        
        // Get session manager (safe if not initialized)
        ConversationSessionManager manager = getSessionManager();
        if (manager == null) {
            return false;
        }
        
        return manager.isSessionActive(sessionId);
    }

    /**
     * Get the session manager, or null if not yet initialized.
     *
     * @return the session manager, or null
     */
    private ConversationSessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Get or create the session manager (lazy initialization).
     * <p>
     * Uses double-checked locking for thread-safe lazy initialization.
     * The inactivity timeout is determined from the first session creation options.
     * If not specified in options, defaults to 30 minutes.
     * </p>
     *
     * @param options the options containing session configuration
     * @return the session manager
     */
    private ConversationSessionManager getOrCreateSessionManager(ClaudeCodeOptions options) {
        // Double-checked locking for lazy initialization
        if (sessionManager == null) {
            synchronized (this) {
                if (sessionManager == null) {
                    // Use timeout from options, or default to 30 minutes
                    java.time.Duration timeout = options.getSessionInactivityTimeout();
                    if (timeout == null) {
                        timeout = java.time.Duration.ofMinutes(30);
                        logger.info("Initializing conversation session manager with default timeout: {}", timeout);
                    } else {
                        logger.info("Initializing conversation session manager with custom timeout: {}", timeout);
                    }
                    sessionManager = new ConversationSessionManager(timeout);
                }
            }
        }
        return sessionManager;
    }

    /**
     * Shutdown the executor and clean up resources.
     * <p>
     * This method should be called when the executor is no longer needed to ensure
     * all conversation sessions are properly closed and resources are released.
     * </p>
     * <p>
     * Note: This is not part of the ClaudeCodeExecutor interface but is provided
     * for applications that need explicit lifecycle management (e.g., Spring beans
     * with @PreDestroy).
     * </p>
     */
    public void shutdown() {
        logger.info("Shutting down ClaudeCodeExecutor");
        
        ConversationSessionManager manager = getSessionManager();
        if (manager != null) {
            logger.info("Shutting down conversation session manager");
            manager.shutdown();
        }
        
        logger.info("ClaudeCodeExecutor shutdown complete");
    }

    // ==================== Private Utility Methods ====================

    /**
     * Build the command line arguments for Claude Code CLI.
     */
    private List<String> buildCommand(String prompt, ClaudeCodeOptions options) {
        List<String> command = new ArrayList<>();
        command.add(claudePath);
        command.add("-p");
        command.add(prompt);
        
        if (options.isDangerouslySkipPermissions()) {
            command.add("--dangerously-skip-permissions");
        }
        
        if (options.getModel() != null && !options.getModel().isEmpty()) {
            command.add("--model");
            command.add(options.getModel());
        }
        
        return command;
    }

    /**
     * Build base environment variables from system environment.
     */
    private Map<String, String> buildBaseEnvironment() {
        // Try to get API key or OAuth token
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        String oauthToken = System.getenv("CLAUDE_CODE_OAUTH_TOKEN");

        // At least one must be set
        if ((apiKey == null || apiKey.isEmpty()) && (oauthToken == null || oauthToken.isEmpty())) {
            throw new IllegalStateException(
                "Neither ANTHROPIC_API_KEY nor CLAUDE_CODE_OAUTH_TOKEN environment variable is set"
            );
        }

        return buildEnvironment(apiKey, oauthToken);
    }

    /**
     * Build environment variables with explicit API key.
     * @deprecated Use buildEnvironment(String, String) instead
     */
    @Deprecated
    private Map<String, String> buildEnvironment(String apiKey) {
        return buildEnvironment(apiKey, null);
    }

    /**
     * Build environment variables with explicit API key and/or OAuth token.
     */
    private Map<String, String> buildEnvironment(String apiKey, String oauthToken) {
        Map<String, String> env = new HashMap<>();

        // CRITICAL: Pass API key and/or OAuth token to subprocess
        // Claude CLI accepts either ANTHROPIC_API_KEY or CLAUDE_CODE_OAUTH_TOKEN
        if (apiKey != null && !apiKey.isEmpty()) {
            env.put("ANTHROPIC_API_KEY", apiKey);
        }
        if (oauthToken != null && !oauthToken.isEmpty()) {
            env.put("CLAUDE_CODE_OAUTH_TOKEN", oauthToken);
        }

        // Pass HOME directory (needed for .claude.json)
        String home = System.getenv("HOME");
        if (home != null && !home.isEmpty()) {
            env.put("HOME", home);
        }

        // Pass NODE_EXTRA_CA_CERTS if set (for Cloud Foundry SSL)
        String nodeCaCerts = System.getenv("NODE_EXTRA_CA_CERTS");
        if (nodeCaCerts != null && !nodeCaCerts.isEmpty()) {
            env.put("NODE_EXTRA_CA_CERTS", nodeCaCerts);
        }

        return env;
    }

    /**
     * Get a required environment variable or throw exception.
     */
    private String getRequiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(
                String.format("%s environment variable is not set", name)
            );
        }
        return value;
    }

    /**
     * Validate prompt is not null or empty.
     */
    private void validatePrompt(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be null or empty");
        }
    }

    /**
     * Mask sensitive path information for logging.
     */
    private String maskPath(String path) {
        if (path == null) {
            return "null";
        }
        // Keep only the last part of the path for security
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return ".../" + path.substring(lastSlash + 1);
        }
        return path;
    }

    /**
     * Inner class to manage streaming process lifecycle and timeout enforcement.
     * <p>
     * This class ensures that:
     * </p>
     * <ul>
     *   <li>Process is terminated when no longer needed</li>
     *   <li>Timeout is enforced by forcibly destroying the process</li>
     *   <li>Resources are cleaned up even if stream is not properly closed</li>
     * </ul>
     */
    private static class StreamingProcessHandle implements AutoCloseable {
        private final Process process;
        private final ScheduledFuture<?> timeoutTask;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        /**
         * Creates a new streaming process handle with timeout enforcement.
         *
         * @param process the process to manage
         * @param timeout the maximum duration before forcibly terminating the process
         */
        public StreamingProcessHandle(Process process, java.time.Duration timeout) {
            this.process = process;
            
            // Schedule timeout task to forcibly destroy process if it runs too long
            this.timeoutTask = timeoutExecutor.schedule(() -> {
                if (process.isAlive()) {
                    logger.warn("Streaming process timed out after {}, forcibly destroying", timeout);
                    process.destroyForcibly();
                }
            }, timeout.toMillis(), TimeUnit.MILLISECONDS);
            
            logger.debug("Created streaming process handle with timeout: {}", timeout);
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                logger.debug("Closing streaming process handle");
                
                // Cancel timeout task
                timeoutTask.cancel(false);
                
                // Destroy process if still alive
                if (process.isAlive()) {
                    logger.debug("Process still alive, destroying gracefully");
                    process.destroy();
                    
                    // Give it a moment to terminate gracefully
                    try {
                        boolean terminated = process.waitFor(1, TimeUnit.SECONDS);
                        if (!terminated) {
                            logger.warn("Process did not terminate gracefully, forcing");
                            process.destroyForcibly();
                        }
                    } catch (InterruptedException e) {
                        logger.warn("Interrupted while waiting for process termination", e);
                        Thread.currentThread().interrupt();
                        process.destroyForcibly();
                    }
                } else {
                    logger.debug("Process already terminated with exit code: {}", process.exitValue());
                }
            }
        }
    }
}

