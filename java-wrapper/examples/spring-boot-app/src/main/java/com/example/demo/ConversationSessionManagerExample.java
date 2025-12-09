package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tanzu.claudecode.cf.ClaudeCodeOptions;
import org.tanzu.claudecode.cf.ConversationSession;
import org.tanzu.claudecode.cf.ConversationSessionManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Examples demonstrating the usage of ConversationSessionManager for managing
 * multiple concurrent conversational sessions with automatic cleanup.
 * <p>
 * These examples show how to create multiple sessions, manage their lifecycle,
 * and handle concurrent access patterns.
 * </p>
 *
 * @author Claude Code Buildpack Team
 * @since 1.1.0
 */
public class ConversationSessionManagerExample {

    private static final Logger logger = LoggerFactory.getLogger(ConversationSessionManagerExample.class);

    /**
     * Example 1: Basic session manager usage.
     * <p>
     * This example shows the simplest usage: create manager, create sessions,
     * use them, and clean up.
     * </p>
     */
    public static void basicManagerUsage() {
        logger.info("=== Example 1: Basic Manager Usage ===");
        
        // Create manager with default 30-minute timeout
        ConversationSessionManager manager = new ConversationSessionManager();
        
        try {
            // Create first session
            String sessionId1 = manager.createSession();
            logger.info("Created session 1: {}", sessionId1);
            
            // Use the session
            ConversationSession session1 = manager.getSession(sessionId1);
            String response1 = session1.sendMessage("What is Java?");
            logger.info("Session 1 response: {} chars", response1.length());
            
            // Create second session
            String sessionId2 = manager.createSession();
            logger.info("Created session 2: {}", sessionId2);
            
            // Use the second session
            ConversationSession session2 = manager.getSession(sessionId2);
            String response2 = session2.sendMessage("What is Python?");
            logger.info("Session 2 response: {} chars", response2.length());
            
            // Check active sessions
            logger.info("Active sessions: {}", manager.getActiveSessionCount());
            
            // Close sessions explicitly
            manager.closeSession(sessionId1);
            manager.closeSession(sessionId2);
            
            logger.info("Remaining active sessions: {}", manager.getActiveSessionCount());
            
        } finally {
            // Always shutdown the manager
            manager.shutdown();
        }
    }

    /**
     * Example 2: Multiple concurrent sessions.
     * <p>
     * This example demonstrates managing multiple sessions simultaneously,
     * each with independent conversation contexts.
     * </p>
     */
    public static void multipleConcurrentSessions() {
        logger.info("=== Example 2: Multiple Concurrent Sessions ===");
        
        ConversationSessionManager manager = new ConversationSessionManager(Duration.ofMinutes(30));
        
        try {
            // Create multiple sessions
            List<String> sessionIds = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String sessionId = manager.createSession();
                sessionIds.add(sessionId);
                logger.info("Created session {}: {}", i + 1, sessionId);
            }
            
            // Use each session with different contexts
            for (int i = 0; i < sessionIds.size(); i++) {
                String sessionId = sessionIds.get(i);
                ConversationSession session = manager.getSession(sessionId);
                
                // Each session maintains its own context
                String topic = "Topic " + (i + 1);
                String response1 = session.sendMessage("Tell me about " + topic);
                logger.info("Session {} ({}): initial response", i + 1, topic);
                
                String response2 = session.sendMessage("Tell me more about it");
                logger.info("Session {} ({}): follow-up response (context maintained)", i + 1, topic);
            }
            
            // Check statistics
            ConversationSessionManager.SessionStatistics stats = manager.getStatistics();
            logger.info("Statistics: {}", stats);
            
            // Close all sessions
            for (String sessionId : sessionIds) {
                manager.closeSession(sessionId);
            }
            
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Example 3: Session with custom options.
     * <p>
     * This example shows how to create sessions with custom configuration.
     * </p>
     */
    public static void sessionWithCustomOptions() {
        logger.info("=== Example 3: Custom Options ===");
        
        // Create manager with shorter timeout for this example
        ConversationSessionManager manager = new ConversationSessionManager(Duration.ofMinutes(15));
        
        try {
            // Configure custom options
            ClaudeCodeOptions options = ClaudeCodeOptions.builder()
                .timeout(Duration.ofMinutes(5))
                .model("sonnet")
                .dangerouslySkipPermissions(true)
                .build();
            
            // Create session with custom options
            String sessionId = manager.createSession(options);
            logger.info("Created session with custom options: {}", sessionId);
            
            // Use the session
            ConversationSession session = manager.getSession(sessionId);
            String response = session.sendMessage("Explain recursion with a Java example");
            logger.info("Response: {} chars", response.length());
            
            // Check if session is active
            boolean active = manager.isSessionActive(sessionId);
            logger.info("Session active: {}", active);
            
            // Close the session
            manager.closeSession(sessionId);
            
            // Verify it's closed
            active = manager.isSessionActive(sessionId);
            logger.info("Session active after close: {}", active);
            
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Example 4: Session expiration and cleanup.
     * <p>
     * This example demonstrates automatic expiration of inactive sessions.
     * </p>
     */
    public static void sessionExpirationDemo() {
        logger.info("=== Example 4: Session Expiration ===");
        
        // Create manager with very short timeout for demo
        ConversationSessionManager manager = new ConversationSessionManager(Duration.ofSeconds(10));
        
        try {
            // Create a session
            String sessionId = manager.createSession();
            logger.info("Created session: {}", sessionId);
            
            // Use it once
            ConversationSession session = manager.getSession(sessionId);
            session.sendMessage("Hello");
            logger.info("Session is active: {}", manager.isSessionActive(sessionId));
            
            // Wait for it to expire (demo only - in real apps, sessions would naturally expire)
            logger.info("Waiting 12 seconds for session to expire...");
            Thread.sleep(12000);
            
            // Manually trigger cleanup (normally happens automatically every 5 minutes)
            logger.info("Running cleanup...");
            manager.cleanup();
            
            // Check if session still exists
            boolean active = manager.isSessionActive(sessionId);
            logger.info("Session active after expiration: {}", active);
            logger.info("Active session count: {}", manager.getActiveSessionCount());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", e);
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Example 5: Error handling.
     * <p>
     * This example demonstrates proper error handling for various scenarios.
     * </p>
     */
    public static void errorHandling() {
        logger.info("=== Example 5: Error Handling ===");
        
        ConversationSessionManager manager = new ConversationSessionManager();
        
        try {
            // Try to get non-existent session
            try {
                manager.getSession("non-existent-id");
            } catch (ConversationSessionManager.SessionNotFoundException e) {
                logger.info("Expected error for non-existent session: {}", e.getMessage());
            }
            
            // Create a session and close it
            String sessionId = manager.createSession();
            logger.info("Created session: {}", sessionId);
            manager.closeSession(sessionId);
            
            // Try to get closed session
            try {
                manager.getSession(sessionId);
            } catch (ConversationSessionManager.SessionNotFoundException e) {
                logger.info("Expected error for closed session: {}", e.getMessage());
            }
            
            // Safe check using isSessionActive (doesn't throw)
            boolean active = manager.isSessionActive(sessionId);
            logger.info("Closed session is active: {}", active);
            
            // Closing non-existent session is safe (idempotent)
            manager.closeSession("non-existent-id");
            logger.info("Closing non-existent session is safe");
            
        } finally {
            manager.shutdown();
            
            // Operations after shutdown should fail
            try {
                manager.createSession();
            } catch (IllegalStateException e) {
                logger.info("Expected error after shutdown: {}", e.getMessage());
            }
        }
    }

    /**
     * Example 6: Concurrent access patterns.
     * <p>
     * This example demonstrates thread-safe concurrent operations on the manager.
     * </p>
     */
    public static void concurrentAccess() {
        logger.info("=== Example 6: Concurrent Access ===");
        
        ConversationSessionManager manager = new ConversationSessionManager();
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        try {
            // Create 10 sessions concurrently
            CountDownLatch createLatch = new CountDownLatch(10);
            List<String> sessionIds = new ArrayList<>();
            
            for (int i = 0; i < 10; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        String sessionId = manager.createSession();
                        synchronized (sessionIds) {
                            sessionIds.add(sessionId);
                        }
                        logger.info("Thread {} created session: {}", index, sessionId);
                    } finally {
                        createLatch.countDown();
                    }
                });
            }
            
            // Wait for all sessions to be created
            createLatch.await(30, TimeUnit.SECONDS);
            logger.info("Created {} sessions concurrently", sessionIds.size());
            
            // Use sessions concurrently
            CountDownLatch useLatch = new CountDownLatch(sessionIds.size());
            
            for (String sessionId : sessionIds) {
                executor.submit(() -> {
                    try {
                        ConversationSession session = manager.getSession(sessionId);
                        session.sendMessage("Concurrent test message");
                        logger.info("Thread used session: {}", sessionId);
                    } catch (Exception e) {
                        logger.error("Error using session", e);
                    } finally {
                        useLatch.countDown();
                    }
                });
            }
            
            // Wait for all sessions to be used
            useLatch.await(60, TimeUnit.SECONDS);
            logger.info("All sessions used successfully");
            
            // Get statistics
            ConversationSessionManager.SessionStatistics stats = manager.getStatistics();
            logger.info("Final statistics: {}", stats);
            
            // Close all sessions
            for (String sessionId : sessionIds) {
                manager.closeSession(sessionId);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", e);
        } finally {
            executor.shutdown();
            manager.shutdown();
        }
    }

    /**
     * Example 7: Session lifecycle monitoring.
     * <p>
     * This example shows how to monitor session statistics and lifecycle.
     * </p>
     */
    public static void sessionMonitoring() {
        logger.info("=== Example 7: Session Monitoring ===");
        
        ConversationSessionManager manager = new ConversationSessionManager(Duration.ofMinutes(30));
        
        try {
            // Create several sessions
            List<String> sessionIds = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                String sessionId = manager.createSession();
                sessionIds.add(sessionId);
                
                // Use each session
                ConversationSession session = manager.getSession(sessionId);
                session.sendMessage("Test message " + i);
            }
            
            // Monitor statistics
            logger.info("Active session count: {}", manager.getActiveSessionCount());
            
            Set<String> activeIds = manager.getActiveSessionIds();
            logger.info("Active session IDs: {}", activeIds);
            
            ConversationSessionManager.SessionStatistics stats = manager.getStatistics();
            logger.info("Session statistics:");
            logger.info("  - Active sessions: {}", stats.getActiveSessionCount());
            logger.info("  - Inactivity timeout: {}", stats.getInactivityTimeout());
            logger.info("  - Average session age: {:.2f}ms", stats.getAverageSessionAgeMillis());
            
            // Close sessions one by one and monitor
            for (String sessionId : sessionIds) {
                manager.closeSession(sessionId);
                logger.info("Closed session, remaining: {}", manager.getActiveSessionCount());
            }
            
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Main method to run all examples.
     * <p>
     * Note: These examples require a properly configured environment with
     * CLAUDE_CLI_PATH and ANTHROPIC_API_KEY set.
     * </p>
     */
    public static void main(String[] args) {
        logger.info("Starting ConversationSessionManager Examples");
        logger.info("============================================");
        
        try {
            // Run examples sequentially
            basicManagerUsage();
            System.out.println();
            
            multipleConcurrentSessions();
            System.out.println();
            
            sessionWithCustomOptions();
            System.out.println();
            
            sessionExpirationDemo();
            System.out.println();
            
            errorHandling();
            System.out.println();
            
            concurrentAccess();
            System.out.println();
            
            sessionMonitoring();
            System.out.println();
            
            logger.info("All examples completed successfully!");
            
        } catch (Exception e) {
            logger.error("Example execution failed", e);
            System.exit(1);
        }
    }
}

