package org.tanzu.claudecode.cf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages a single conversational session with Claude Code CLI.
 * <p>
 * This class maintains conversation context by using Claude CLI's built-in session
 * management with the --session-id flag. Each message spawns a new Claude CLI process
 * with the same session ID, allowing the CLI to load and update the conversation history
 * stored in ~/.claude/sessions/.
 * </p>
 *
 * <h2>Session Persistence</h2>
 * <p>
 * Unlike a traditional long-running process, this implementation:
 * </p>
 * <ul>
 *   <li>Spawns a new `claude -p` process for each message</li>
 *   <li>Uses `--session-id` flag to maintain conversation continuity</li>
 *   <li>Leverages Claude CLI's disk-based session storage</li>
 *   <li>Avoids issues with hanging interactive processes</li>
 * </ul>
 *
 * <h2>Lifecycle Management</h2>
 * <p>
 * A session has the following lifecycle states:
 * </p>
 * <ul>
 *   <li><b>ACTIVE</b> - Session is ready to accept messages</li>
 *   <li><b>CLOSED</b> - Session has been explicitly closed</li>
 *   <li><b>EXPIRED</b> - Session has been inactive beyond the timeout threshold</li>
 *   <li><b>FAILED</b> - Session operations have failed</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. Concurrent access to the same session will be
 * serialized using internal locking to ensure only one message is processed
 * at a time per session.
 * </p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create a session with options
 * ClaudeCodeOptions options = ClaudeCodeOptions.builder()
 *     .timeout(Duration.ofMinutes(5))
 *     .model("sonnet")
 *     .build();
 * 
 * ConversationSession session = new ConversationSession(options);
 * 
 * try {
 *     // Send multiple messages in the same context
 *     String response1 = session.sendMessage("What is the capital of France?");
 *     String response2 = session.sendMessage("What is its population?"); // Context maintained
 *     
 *     // Check if session is still active
 *     if (session.isActive()) {
 *         String response3 = session.sendMessage("Tell me more about it");
 *     }
 * } finally {
 *     // Always close the session to clean up resources
 *     session.close();
 * }
 * }</pre>
 *
 * @author Claude Code Buildpack Team
 * @since 1.1.0
 * @see ConversationSessionManager
 */
public class ConversationSession implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ConversationSession.class);
    
    /**
     * Timeout for a single Claude CLI execution (per message).
     */
    private static final Duration EXECUTION_TIMEOUT = Duration.ofMinutes(5);

    private final String sessionId;
    private final ClaudeCodeOptions options;
    private final Instant createdAt;
    private volatile Instant lastActivity;
    private volatile SessionState state;
    private final Lock messageLock;
    private volatile boolean isFirstMessage = true;

    /**
     * Session state enumeration.
     */
    public enum SessionState {
        /** Session is active and accepting messages */
        ACTIVE,
        /** Session has been explicitly closed */
        CLOSED,
        /** Session operations have failed */
        FAILED
    }

    /**
     * Creates a new conversation session with default options.
     */
    public ConversationSession() {
        this(ClaudeCodeOptions.defaults());
    }

    /**
     * Creates a new conversation session with custom options.
     * <p>
     * This constructor initializes a new conversation session with a unique ID.
     * The session ID will be used with the --session-id flag in subsequent
     * Claude CLI invocations to maintain conversation context.
     * </p>
     *
     * @param options configuration options for the Claude CLI
     * @throws IllegalArgumentException if options is null
     */
    public ConversationSession(ClaudeCodeOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("Options cannot be null");
        }

        this.sessionId = UUID.randomUUID().toString();
        this.options = options;
        this.createdAt = Instant.now();
        this.lastActivity = this.createdAt;
        this.state = SessionState.ACTIVE;
        this.messageLock = new ReentrantLock();

        logger.info("Created conversation session: {}", sessionId);
    }

    /**
     * Gets the unique identifier for this session.
     *
     * @return the session ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Gets the time when this session was created.
     *
     * @return the creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets the time of the last activity (message sent or received) in this session.
     *
     * @return the last activity timestamp
     */
    public Instant getLastActivity() {
        return lastActivity;
    }

    /**
     * Gets the current state of this session.
     *
     * @return the session state
     */
    public SessionState getState() {
        return state;
    }

    /**
     * Checks if this session is currently active and accepting messages.
     *
     * @return true if the session is active, false otherwise
     */
    public boolean isActive() {
        return state == SessionState.ACTIVE;
    }

    /**
     * Checks if this session has expired based on the inactivity timeout.
     *
     * @param inactivityTimeout the maximum duration of inactivity before expiration
     * @return true if the session has been inactive longer than the timeout
     * @throws IllegalArgumentException if inactivityTimeout is null or negative
     */
    public boolean isExpired(Duration inactivityTimeout) {
        if (inactivityTimeout == null || inactivityTimeout.isNegative()) {
            throw new IllegalArgumentException("Inactivity timeout must be positive");
        }
        
        Duration inactiveDuration = Duration.between(lastActivity, Instant.now());
        return inactiveDuration.compareTo(inactivityTimeout) > 0;
    }

    /**
     * Updates the last activity timestamp to the current time.
     * <p>
     * This method is called automatically when messages are sent or received,
     * but can also be called manually to extend the session lifetime.
     * </p>
     */
    public void updateActivity() {
        this.lastActivity = Instant.now();
    }

    /**
     * Sends a message to Claude and returns the response.
     * <p>
     * This method spawns a new Claude CLI process with the --session-id flag
     * to maintain conversation context across invocations. The method is thread-safe -
     * concurrent calls will be serialized to ensure proper message ordering.
     * </p>
     * <p>
     * The method will block until a response is received or the execution timeout is reached.
     * </p>
     *
     * @param prompt the message to send to Claude
     * @return Claude's response
     * @throws IllegalStateException if the session is not active
     * @throws IllegalArgumentException if prompt is null or empty
     * @throws ConversationSessionException if execution fails or times out
     */
    public String sendMessage(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be null or empty");
        }
        
        if (!isActive()) {
            throw new IllegalStateException(
                String.format("Session %s is not active (state: %s)", sessionId, state)
            );
        }

        logger.info("=== SENDING MESSAGE TO SESSION {} ===", sessionId);
        logger.info("Prompt: '{}'", prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt);
        logger.info("Session state: {}, Created: {}, Last activity: {}", state, createdAt, lastActivity);
        
        messageLock.lock();
        logger.debug("Acquired message lock for session {}", sessionId);
        
        try {
            logger.debug("Sending message to session {}: {} chars", sessionId, prompt.length());
            updateActivity();
            logger.debug("Activity updated, calling executeWithSessionId...");
            
            // Execute claude -p with --session-id flag
            String response = executeWithSessionId(prompt);
            
            updateActivity();
            logger.info("=== MESSAGE COMPLETE FOR SESSION {} ===", sessionId);
            logger.info("Response received: {} chars", response.length());
            logger.debug("Response preview: {}", response.length() > 100 ? response.substring(0, 100) + "..." : response);
            
            return response;
            
        } catch (Exception e) {
            logger.error("=== MESSAGE FAILED FOR SESSION {} ===", sessionId);
            logger.error("Error type: {}", e.getClass().getName());
            logger.error("Error message: {}", e.getMessage());
            logger.error("Full stack trace:", e);
            state = SessionState.FAILED;
            throw new ConversationSessionException(
                "Failed to execute Claude CLI for session", e
            );
        } finally {
            messageLock.unlock();
            logger.debug("Released message lock for session {}", sessionId);
        }
    }

    /**
     * Closes this session and marks it as closed.
     * <p>
     * This method is idempotent - calling it multiple times is safe.
     * The session files remain in ~/.claude/sessions/ and could be resumed later
     * using the --resume flag if needed.
     * </p>
     */
    @Override
    public void close() {
        if (state == SessionState.CLOSED) {
            logger.debug("Session {} already closed", sessionId);
            return;
        }

        messageLock.lock();
        try {
            state = SessionState.CLOSED;
            logger.info("Conversation session closed: {}", sessionId);
            // Session files remain in ~/.claude/sessions/ for potential --resume
            
        } finally {
            messageLock.unlock();
        }
    }

    /**
     * Executes Claude CLI with the session ID to send a message and get a response.
     *
     * @param prompt the message to send
     * @return Claude's response
     * @throws IOException if process execution fails
     * @throws InterruptedException if execution is interrupted
     * @throws ConversationSessionException if execution times out or exits with non-zero code
     */
    private String executeWithSessionId(String prompt) throws IOException, InterruptedException {
        String claudePath = getRequiredEnv("CLAUDE_CLI_PATH");
        
        List<String> command = new ArrayList<>();
        command.add(claudePath);
        command.add("-p");  // Print mode - execute and exit
        
        // Use --session-id for first message (creates session), --resume for subsequent messages
        if (isFirstMessage) {
            command.add("--session-id");
            command.add(sessionId);
            logger.debug("First message - using --session-id to create session {}", sessionId);
        } else {
            command.add("--resume");
            command.add(sessionId);
            logger.debug("Subsequent message - using --resume for session {}", sessionId);
        }
        
        // Add --dangerously-skip-permissions if configured
        if (options.isDangerouslySkipPermissions()) {
            command.add("--dangerously-skip-permissions");
        }
        
        // Add model if specified
        if (options.getModel() != null && !options.getModel().isEmpty()) {
            command.add("--model");
            command.add(options.getModel());
        }
        
        // Add the prompt as the last argument
        command.add(prompt);
        
        ProcessBuilder pb = new ProcessBuilder(command);
        
        // Set up environment variables
        pb.environment().put("ANTHROPIC_API_KEY", getRequiredEnv("ANTHROPIC_API_KEY"));
        
        String home = System.getenv("HOME");
        if (home != null && !home.isEmpty()) {
            pb.environment().put("HOME", home);
        }
        
        // Add additional environment variables from options
        pb.environment().putAll(options.getAdditionalEnv());
        
        // Set working directory if specified
        if (options.getWorkingDirectory() != null && !options.getWorkingDirectory().isEmpty()) {
            pb.directory(new File(options.getWorkingDirectory()));
        }
        
        // Redirect stderr to stdout for easier reading
        pb.redirectErrorStream(true);
        
        // DEBUG: Log the full command
        logger.info("Executing Claude CLI for session {}: {}", sessionId, String.join(" ", command));
        logger.debug("Working directory: {}", pb.directory() != null ? pb.directory() : "(default)");
        logger.debug("HOME: {}", home);
        logger.debug("CLAUDE_CLI_PATH: {}", claudePath);
        logger.debug("Prompt length: {} chars", prompt.length());
        
        long startTime = System.currentTimeMillis();
        
        // Start the process
        logger.info("Starting Claude CLI process for session {}", sessionId);
        Process process = pb.start();
        logger.info("Process started, PID: {}, alive: {}", process.pid(), process.isAlive());
        
        // CRITICAL: Close stdin immediately so CLI doesn't wait for input
        // Even though we pass the prompt as an argument, the CLI still checks stdin
        try {
            process.getOutputStream().close();
            logger.debug("Closed stdin for process {}", process.pid());
        } catch (IOException e) {
            logger.warn("Failed to close stdin (non-fatal): {}", e.getMessage());
        }
        
        // KEY FIX: Wait for process to complete FIRST with timeout
        // This prevents hanging forever if process doesn't produce output
        logger.info("Waiting for process to complete for session {} (timeout: {}min)", 
                   sessionId, EXECUTION_TIMEOUT.toMinutes());
        
        boolean completed = process.waitFor(
            EXECUTION_TIMEOUT.toMillis(), 
            TimeUnit.MILLISECONDS
        );
        
        long waitTime = System.currentTimeMillis() - startTime;
        logger.info("Process wait completed: {}, elapsed: {}ms", completed, waitTime);
        
        if (!completed) {
            logger.error("Claude CLI timed out after {}ms for session {}", waitTime, sessionId);
            process.destroyForcibly();
            throw new ConversationSessionException(
                "Claude CLI execution timed out after " + EXECUTION_TIMEOUT.toMinutes() + " minutes"
            );
        }
        
        // NOW read the output (process has completed, stream is closed, won't hang)
        logger.info("Process completed, now reading output for session {}", sessionId);
        StringBuilder output = new StringBuilder();
        int linesRead = 0;
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                linesRead++;
            }
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        int exitCode = process.exitValue();
        
        logger.info("Claude CLI completed for session {} - Exit code: {}, Total time: {}ms, Lines read: {}, Output size: {} chars", 
                   sessionId, exitCode, totalTime, linesRead, output.length());
        
        if (exitCode != 0) {
            String errorOutput = output.toString();
            logger.error("Claude CLI exited with code {} for session {}: {}", exitCode, sessionId, errorOutput);
            throw new ConversationSessionException(
                "Claude CLI exited with code " + exitCode + ": " + errorOutput
            );
        }
        
        // Mark that we've successfully sent the first message
        if (isFirstMessage) {
            isFirstMessage = false;
            logger.debug("First message completed successfully, subsequent messages will use --resume");
        }
        
        String result = output.toString().trim();
        logger.info("Returning response for session {} ({} chars)", sessionId, result.length());
        logger.debug("Response preview: {}", result.length() > 100 ? result.substring(0, 100) + "..." : result);
        
        return result;
    }

    /**
     * Gets a required environment variable or throws an exception.
     *
     * @param name the environment variable name
     * @return the environment variable value
     * @throws IllegalStateException if the variable is not set
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

    @Override
    public String toString() {
        return String.format(
            "ConversationSession{id='%s', state=%s, created=%s, lastActivity=%s}",
            sessionId, state, createdAt, lastActivity
        );
    }

    /**
     * Exception thrown when conversation session operations fail.
     */
    public static class ConversationSessionException extends RuntimeException {
        
        /**
         * Constructs a new exception with the specified message.
         *
         * @param message the detail message
         */
        public ConversationSessionException(String message) {
            super(message);
        }

        /**
         * Constructs a new exception with the specified message and cause.
         *
         * @param message the detail message
         * @param cause the cause
         */
        public ConversationSessionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
