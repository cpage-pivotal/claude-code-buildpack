package org.tanzu.claudecode.cf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages multiple conversational sessions with automatic cleanup of inactive sessions.
 * <p>
 * This class provides centralized management for multiple {@link ConversationSession}
 * instances, including:
 * </p>
 * <ul>
 *   <li>Creating new sessions with unique IDs</li>
 *   <li>Retrieving existing sessions by ID</li>
 *   <li>Explicitly closing sessions</li>
 *   <li>Automatically expiring inactive sessions based on configurable timeout</li>
 *   <li>Periodic cleanup of expired sessions</li>
 * </ul>
 *
 * <h2>Session Lifecycle</h2>
 * <p>
 * Sessions are stored in memory using a {@link ConcurrentHashMap}. Each session has:
 * </p>
 * <ul>
 *   <li>A unique session ID (UUID)</li>
 *   <li>Creation timestamp</li>
 *   <li>Last activity timestamp</li>
 *   <li>An underlying Claude CLI process</li>
 * </ul>
 *
 * <h2>Automatic Cleanup</h2>
 * <p>
 * A background scheduled task runs every 5 minutes to:
 * </p>
 * <ul>
 *   <li>Identify sessions that have been inactive beyond the configured timeout</li>
 *   <li>Close and remove expired sessions</li>
 *   <li>Clean up resources associated with closed sessions</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is fully thread-safe:
 * </p>
 * <ul>
 *   <li>Uses {@link ConcurrentHashMap} for session storage</li>
 *   <li>Individual sessions handle their own thread safety</li>
 *   <li>Cleanup task safely iterates over sessions</li>
 * </ul>
 *
 * <h2>Resource Management</h2>
 * <p>
 * Always call {@link #shutdown()} when done to ensure:
 * </p>
 * <ul>
 *   <li>All sessions are properly closed</li>
 *   <li>Background cleanup thread is stopped</li>
 *   <li>No resource leaks occur</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create manager with 30-minute inactivity timeout
 * ConversationSessionManager manager = new ConversationSessionManager(Duration.ofMinutes(30));
 * 
 * try {
 *     // Create a new session
 *     String sessionId = manager.createSession();
 *     
 *     // Use the session
 *     ConversationSession session = manager.getSession(sessionId);
 *     String response = session.sendMessage("Hello Claude!");
 *     
 *     // Close session when done
 *     manager.closeSession(sessionId);
 *     
 * } finally {
 *     // Always shutdown the manager
 *     manager.shutdown();
 * }
 * }</pre>
 *
 * @author Claude Code Buildpack Team
 * @since 1.1.0
 * @see ConversationSession
 */
public class ConversationSessionManager {

    private static final Logger logger = LoggerFactory.getLogger(ConversationSessionManager.class);
    
    /**
     * How often the cleanup task runs (5 minutes).
     */
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(5);
    
    /**
     * Default inactivity timeout (30 minutes).
     */
    private static final Duration DEFAULT_INACTIVITY_TIMEOUT = Duration.ofMinutes(30);

    private final ConcurrentHashMap<String, ConversationSession> sessions;
    private final ScheduledExecutorService cleanupExecutor;
    private final Duration inactivityTimeout;
    private volatile boolean isShutdown = false;

    /**
     * Creates a new session manager with default inactivity timeout (30 minutes).
     */
    public ConversationSessionManager() {
        this(DEFAULT_INACTIVITY_TIMEOUT);
    }

    /**
     * Creates a new session manager with custom inactivity timeout.
     * <p>
     * The cleanup task will run every 5 minutes to check for and remove
     * sessions that have been inactive longer than the specified timeout.
     * </p>
     *
     * @param inactivityTimeout the maximum duration of inactivity before session expiration
     * @throws IllegalArgumentException if inactivityTimeout is null or negative
     */
    public ConversationSessionManager(Duration inactivityTimeout) {
        if (inactivityTimeout == null || inactivityTimeout.isNegative() || inactivityTimeout.isZero()) {
            throw new IllegalArgumentException("Inactivity timeout must be positive");
        }

        this.inactivityTimeout = inactivityTimeout;
        this.sessions = new ConcurrentHashMap<>();
        
        // Create cleanup executor with daemon thread
        this.cleanupExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "conversation-session-cleanup");
            t.setDaemon(true);
            return t;
        });
        
        // Schedule periodic cleanup
        this.cleanupExecutor.scheduleAtFixedRate(
            this::cleanup,
            CLEANUP_INTERVAL.toMillis(),
            CLEANUP_INTERVAL.toMillis(),
            TimeUnit.MILLISECONDS
        );
        
        logger.info("ConversationSessionManager initialized with inactivity timeout: {}", inactivityTimeout);
    }

    /**
     * Creates a new conversation session with default options.
     * <p>
     * This method:
     * </p>
     * <ul>
     *   <li>Creates a new {@link ConversationSession}</li>
     *   <li>Starts a Claude CLI process</li>
     *   <li>Stores the session in the manager</li>
     *   <li>Returns the unique session ID</li>
     * </ul>
     *
     * @return the unique session ID
     * @throws IllegalStateException if the manager has been shut down
     * @throws ConversationSession.ConversationSessionException if session creation fails
     */
    public String createSession() {
        return createSession(ClaudeCodeOptions.defaults());
    }

    /**
     * Creates a new conversation session with custom options.
     * <p>
     * This method allows you to configure the session with custom timeout,
     * model selection, and other options.
     * </p>
     *
     * @param options configuration options for the session
     * @return the unique session ID
     * @throws IllegalArgumentException if options is null
     * @throws IllegalStateException if the manager has been shut down
     * @throws ConversationSession.ConversationSessionException if session creation fails
     */
    public String createSession(ClaudeCodeOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("Options cannot be null");
        }
        
        ensureNotShutdown();
        
        // Create new session
        ConversationSession session = new ConversationSession(options);
        String sessionId = session.getSessionId();
        
        // Store session
        sessions.put(sessionId, session);
        
        logger.info("Created conversation session: {} (total active: {})", sessionId, sessions.size());
        
        return sessionId;
    }

    /**
     * Retrieves an existing conversation session by ID.
     * <p>
     * This method returns the session if it exists and is active. If the session
     * does not exist or has been closed, a {@link SessionNotFoundException} is thrown.
     * </p>
     *
     * @param sessionId the unique session ID
     * @return the conversation session
     * @throws IllegalArgumentException if sessionId is null or empty
     * @throws SessionNotFoundException if the session does not exist
     * @throws IllegalStateException if the manager has been shut down
     */
    public ConversationSession getSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Session ID cannot be null or empty");
        }
        
        ensureNotShutdown();
        
        ConversationSession session = sessions.get(sessionId);
        
        if (session == null) {
            logger.warn("Session not found: {}", sessionId);
            throw new SessionNotFoundException("Session not found: " + sessionId);
        }
        
        // Check if session is still active
        if (!session.isActive()) {
            logger.warn("Session {} is not active (state: {})", sessionId, session.getState());
            // Remove inactive session
            sessions.remove(sessionId);
            throw new SessionNotFoundException("Session is not active: " + sessionId);
        }
        
        return session;
    }

    /**
     * Checks if a session exists and is active.
     * <p>
     * This is a non-throwing alternative to {@link #getSession(String)} for
     * checking session existence.
     * </p>
     *
     * @param sessionId the unique session ID
     * @return true if the session exists and is active, false otherwise
     */
    public boolean isSessionActive(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return false;
        }
        
        if (isShutdown) {
            return false;
        }
        
        ConversationSession session = sessions.get(sessionId);
        return session != null && session.isActive();
    }

    /**
     * Explicitly closes a conversation session and removes it from the manager.
     * <p>
     * This method:
     * </p>
     * <ul>
     *   <li>Retrieves the session by ID</li>
     *   <li>Closes the session (terminates Claude CLI process)</li>
     *   <li>Removes the session from storage</li>
     * </ul>
     * <p>
     * This method is idempotent - calling it multiple times with the same
     * session ID is safe (subsequent calls will be no-ops).
     * </p>
     *
     * @param sessionId the unique session ID
     * @throws IllegalArgumentException if sessionId is null or empty
     */
    public void closeSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Session ID cannot be null or empty");
        }
        
        ConversationSession session = sessions.remove(sessionId);
        
        if (session != null) {
            logger.info("Closing conversation session: {} (remaining active: {})", 
                       sessionId, sessions.size());
            
            try {
                session.close();
            } catch (Exception e) {
                logger.error("Error closing session {}", sessionId, e);
            }
        } else {
            logger.debug("Session {} not found (already closed or never existed)", sessionId);
        }
    }

    /**
     * Gets the number of currently active sessions.
     *
     * @return the number of active sessions
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Gets a snapshot of all active session IDs.
     * <p>
     * This returns a copy of the session ID set at the time of the call.
     * The returned set is independent of the manager's internal state.
     * </p>
     *
     * @return a set of active session IDs
     */
    public Set<String> getActiveSessionIds() {
        return new HashSet<>(sessions.keySet());
    }

    /**
     * Gets session statistics for monitoring.
     *
     * @return session statistics
     */
    public SessionStatistics getStatistics() {
        return new SessionStatistics(
            sessions.size(),
            inactivityTimeout,
            sessions.values().stream()
                .mapToLong(s -> Duration.between(s.getCreatedAt(), s.getLastActivity()).toMillis())
                .average()
                .orElse(0.0)
        );
    }

    /**
     * Background cleanup task that expires inactive sessions.
     * <p>
     * This method:
     * </p>
     * <ul>
     *   <li>Iterates through all active sessions</li>
     *   <li>Checks if each session has exceeded the inactivity timeout</li>
     *   <li>Closes and removes expired sessions</li>
     * </ul>
     * <p>
     * This task is automatically scheduled to run every 5 minutes.
     * </p>
     */
    public void cleanup() {
        if (isShutdown) {
            return;
        }
        
        logger.debug("Running session cleanup (active sessions: {})", sessions.size());
        
        List<String> expiredSessions = new ArrayList<>();
        
        // Identify expired sessions
        for (Map.Entry<String, ConversationSession> entry : sessions.entrySet()) {
            String sessionId = entry.getKey();
            ConversationSession session = entry.getValue();
            
            try {
                // Check if session is expired or no longer active
                if (!session.isActive() || session.isExpired(inactivityTimeout)) {
                    expiredSessions.add(sessionId);
                }
            } catch (Exception e) {
                logger.error("Error checking session {} for expiration", sessionId, e);
                // Add to expired list to clean up problematic session
                expiredSessions.add(sessionId);
            }
        }
        
        // Close and remove expired sessions
        if (!expiredSessions.isEmpty()) {
            logger.info("Cleaning up {} expired session(s)", expiredSessions.size());
            
            for (String sessionId : expiredSessions) {
                try {
                    closeSession(sessionId);
                } catch (Exception e) {
                    logger.error("Error cleaning up session {}", sessionId, e);
                }
            }
        }
        
        logger.debug("Cleanup complete (remaining active: {})", sessions.size());
    }

    /**
     * Shuts down the session manager.
     * <p>
     * This method:
     * </p>
     * <ul>
     *   <li>Closes all active sessions</li>
     *   <li>Stops the cleanup executor</li>
     *   <li>Waits for cleanup task to complete (up to 5 seconds)</li>
     *   <li>Marks the manager as shut down</li>
     * </ul>
     * <p>
     * After shutdown, no new sessions can be created or retrieved.
     * This method is idempotent - calling it multiple times is safe.
     * </p>
     */
    public void shutdown() {
        if (isShutdown) {
            logger.debug("ConversationSessionManager already shut down");
            return;
        }
        
        logger.info("Shutting down ConversationSessionManager (active sessions: {})", sessions.size());
        
        isShutdown = true;
        
        // Stop accepting new cleanup tasks
        cleanupExecutor.shutdown();
        
        // Close all active sessions
        List<String> sessionIds = new ArrayList<>(sessions.keySet());
        for (String sessionId : sessionIds) {
            try {
                closeSession(sessionId);
            } catch (Exception e) {
                logger.error("Error closing session {} during shutdown", sessionId, e);
            }
        }
        
        // Wait for cleanup executor to finish
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Cleanup executor did not terminate within 5 seconds, forcing shutdown");
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted while waiting for cleanup executor termination", e);
            Thread.currentThread().interrupt();
            cleanupExecutor.shutdownNow();
        }
        
        logger.info("ConversationSessionManager shut down complete");
    }

    /**
     * Ensures the manager has not been shut down.
     *
     * @throws IllegalStateException if the manager has been shut down
     */
    private void ensureNotShutdown() {
        if (isShutdown) {
            throw new IllegalStateException("ConversationSessionManager has been shut down");
        }
    }

    /**
     * Exception thrown when a requested session cannot be found.
     */
    public static class SessionNotFoundException extends RuntimeException {
        
        /**
         * Constructs a new exception with the specified message.
         *
         * @param message the detail message
         */
        public SessionNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Statistics about session management.
     */
    public static class SessionStatistics {
        private final int activeSessionCount;
        private final Duration inactivityTimeout;
        private final double averageSessionAgeMillis;

        /**
         * Constructs session statistics.
         *
         * @param activeSessionCount number of active sessions
         * @param inactivityTimeout configured inactivity timeout
         * @param averageSessionAgeMillis average session age in milliseconds
         */
        public SessionStatistics(int activeSessionCount, Duration inactivityTimeout, double averageSessionAgeMillis) {
            this.activeSessionCount = activeSessionCount;
            this.inactivityTimeout = inactivityTimeout;
            this.averageSessionAgeMillis = averageSessionAgeMillis;
        }

        /**
         * Gets the number of active sessions.
         *
         * @return active session count
         */
        public int getActiveSessionCount() {
            return activeSessionCount;
        }

        /**
         * Gets the configured inactivity timeout.
         *
         * @return inactivity timeout
         */
        public Duration getInactivityTimeout() {
            return inactivityTimeout;
        }

        /**
         * Gets the average session age in milliseconds.
         *
         * @return average session age
         */
        public double getAverageSessionAgeMillis() {
            return averageSessionAgeMillis;
        }

        @Override
        public String toString() {
            return String.format(
                "SessionStatistics{active=%d, timeout=%s, avgAge=%.2fms}",
                activeSessionCount, inactivityTimeout, averageSessionAgeMillis
            );
        }
    }
}

