package com.example.demo;

import org.tanzu.claudecode.cf.ClaudeCodeExecutor;
import org.tanzu.claudecode.cf.ClaudeCodeExecutorImpl;
import org.tanzu.claudecode.cf.ClaudeCodeOptions;
import org.tanzu.claudecode.cf.ConversationSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Comprehensive examples demonstrating conversational session usage patterns.
 * <p>
 * This class shows practical examples of using the conversational API for
 * multi-turn conversations with Claude, including error handling, timeout
 * management, and best practices for resource management.
 * </p>
 *
 * <h2>Examples Included</h2>
 * <ol>
 *   <li>Basic conversation with explicit close</li>
 *   <li>Multi-turn Q&A session with context</li>
 *   <li>Session with custom configuration</li>
 *   <li>Error handling for expired sessions</li>
 *   <li>Proper resource management with try-finally</li>
 *   <li>Session status checking</li>
 *   <li>Long-running analysis conversation</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <p>Run individual example methods or use the main method to run all examples:</p>
 * <pre>{@code
 * ConversationalExamples examples = new ConversationalExamples();
 * examples.example1_BasicConversation();
 * }</pre>
 *
 * @author Claude Code Buildpack Team
 * @since 1.1.0
 * @see ClaudeCodeExecutor
 * @see ConversationSessionManager
 */
public class ConversationalExamples {

    private static final Logger logger = LoggerFactory.getLogger(ConversationalExamples.class);

    /**
     * Example 1: Basic conversation with explicit close.
     * <p>
     * Demonstrates the simplest usage pattern: create a session, send messages,
     * and explicitly close when done.
     * </p>
     */
    public void example1_BasicConversation() {
        logger.info("=== Example 1: Basic Conversation ===");

        ClaudeCodeExecutor executor = new ClaudeCodeExecutorImpl();

        // Create a new conversation session
        String sessionId = executor.createConversationSession();
        logger.info("Created session: {}", sessionId);

        try {
            // First message
            String response1 = executor.sendMessage(sessionId, "Hello, Claude!");
            logger.info("Response 1: {}", response1);

            // Second message - context is maintained
            String response2 = executor.sendMessage(sessionId, "What is 2 + 2?");
            logger.info("Response 2: {}", response2);

            // Third message - Claude remembers the conversation
            String response3 = executor.sendMessage(sessionId, "Can you remind me what I asked first?");
            logger.info("Response 3: {}", response3);

        } finally {
            // Always close the session when done
            executor.closeConversationSession(sessionId);
            logger.info("Session closed");
        }
    }

    /**
     * Example 2: Multi-turn Q&A session with context.
     * <p>
     * Demonstrates how Claude maintains context across multiple messages,
     * allowing natural follow-up questions without repeating context.
     * </p>
     */
    public void example2_MultiTurnQA() {
        logger.info("=== Example 2: Multi-turn Q&A Session ===");

        ClaudeCodeExecutor executor = new ClaudeCodeExecutorImpl();
        String sessionId = executor.createConversationSession();

        try {
            // Ask about a topic
            String response1 = executor.sendMessage(sessionId,
                "Tell me about the Python programming language");
            logger.info("Q1 - About Python:\n{}", response1);

            // Follow-up question - "it" refers to Python from context
            String response2 = executor.sendMessage(sessionId,
                "What are the main advantages of using it?");
            logger.info("Q2 - Advantages:\n{}", response2);

            // Another follow-up - context still maintained
            String response3 = executor.sendMessage(sessionId,
                "Can you give me a simple code example?");
            logger.info("Q3 - Example:\n{}", response3);

            // Ask for clarification on previous response
            String response4 = executor.sendMessage(sessionId,
                "Can you explain the code you just showed me?");
            logger.info("Q4 - Explanation:\n{}", response4);

        } finally {
            executor.closeConversationSession(sessionId);
            logger.info("Session closed");
        }
    }

    /**
     * Example 3: Session with custom configuration.
     * <p>
     * Demonstrates creating a session with custom options including model selection,
     * custom timeout, and working directory configuration.
     * </p>
     */
    public void example3_CustomConfiguration() {
        logger.info("=== Example 3: Custom Configuration ===");

        ClaudeCodeExecutor executor = new ClaudeCodeExecutorImpl();

        // Configure session with custom options
        ClaudeCodeOptions options = ClaudeCodeOptions.builder()
            .model("sonnet")  // Use Sonnet model
            .sessionInactivityTimeout(Duration.ofMinutes(45))  // 45-minute timeout
            .workingDirectory("/home/vcap/app")  // Custom working directory
            .build();

        String sessionId = executor.createConversationSession(options);
        logger.info("Created session with custom config: {}", sessionId);

        try {
            // Use the session
            String response = executor.sendMessage(sessionId,
                "Analyze the current directory structure");
            logger.info("Response: {}", response);

            logger.info("Session will auto-expire after 45 minutes of inactivity");

        } finally {
            executor.closeConversationSession(sessionId);
            logger.info("Session closed");
        }
    }

    /**
     * Example 4: Error handling for expired sessions.
     * <p>
     * Demonstrates proper error handling when attempting to use a session
     * that has expired or doesn't exist.
     * </p>
     */
    public void example4_ErrorHandling() {
        logger.info("=== Example 4: Error Handling ===");

        ClaudeCodeExecutor executor = new ClaudeCodeExecutorImpl();

        // Create a session with very short timeout for demonstration
        ClaudeCodeOptions options = ClaudeCodeOptions.builder()
            .sessionInactivityTimeout(Duration.ofSeconds(5))
            .build();

        String sessionId = executor.createConversationSession(options);
        logger.info("Created session with 5-second timeout: {}", sessionId);

        try {
            // Send a message (this will succeed)
            String response1 = executor.sendMessage(sessionId, "Hello");
            logger.info("First message succeeded: {}", response1);

            // Wait for session to expire
            logger.info("Waiting 6 seconds for session to expire...");
            Thread.sleep(6000);

            // Try to send another message (this will fail)
            try {
                String response2 = executor.sendMessage(sessionId, "Are you still there?");
                logger.info("Second message succeeded: {}", response2);

            } catch (ConversationSessionManager.SessionNotFoundException e) {
                logger.warn("Session expired as expected: {}", e.getMessage());
                logger.info("Client should handle this by creating a new session");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while waiting", e);

        } finally {
            // Close is idempotent - safe to call even if session already expired
            executor.closeConversationSession(sessionId);
            logger.info("Session closed (idempotent operation)");
        }
    }

    /**
     * Example 5: Proper resource management with try-finally.
     * <p>
     * Demonstrates the recommended pattern for ensuring sessions are always
     * closed, even if exceptions occur during message sending.
     * </p>
     */
    public void example5_ResourceManagement() {
        logger.info("=== Example 5: Resource Management ===");

        ClaudeCodeExecutor executor = new ClaudeCodeExecutorImpl();
        String sessionId = null;

        try {
            // Create session
            sessionId = executor.createConversationSession();
            logger.info("Session created: {}", sessionId);

            // Send messages - these might throw exceptions
            String response1 = executor.sendMessage(sessionId,
                "What is the capital of France?");
            logger.info("Response 1: {}", response1);

            // Even if this throws an exception, finally block will close session
            String response2 = executor.sendMessage(sessionId,
                "What is its population?");
            logger.info("Response 2: {}", response2);

        } catch (Exception e) {
            logger.error("Error during conversation", e);
            // Session will still be closed in finally block

        } finally {
            // Always close the session, even if there was an error
            if (sessionId != null) {
                executor.closeConversationSession(sessionId);
                logger.info("Session closed in finally block");
            }
        }
    }

    /**
     * Example 6: Session status checking.
     * <p>
     * Demonstrates checking session status before sending messages,
     * useful for long-lived sessions or when session lifetime is uncertain.
     * </p>
     */
    public void example6_StatusChecking() {
        logger.info("=== Example 6: Session Status Checking ===");

        ClaudeCodeExecutor executor = new ClaudeCodeExecutorImpl();
        String sessionId = executor.createConversationSession();

        try {
            // Check if session is active before sending message
            if (executor.isSessionActive(sessionId)) {
                logger.info("Session {} is active", sessionId);

                String response = executor.sendMessage(sessionId, "Hello!");
                logger.info("Response: {}", response);
            } else {
                logger.warn("Session {} is not active", sessionId);
            }

            // Close the session
            executor.closeConversationSession(sessionId);

            // Check status after closing
            if (!executor.isSessionActive(sessionId)) {
                logger.info("Session {} is no longer active (as expected)", sessionId);
            }

            // Checking status of non-existent session (doesn't throw)
            boolean exists = executor.isSessionActive("non-existent-session-id");
            logger.info("Non-existent session active: {}", exists);  // false

        } finally {
            // Idempotent close
            executor.closeConversationSession(sessionId);
        }
    }

    /**
     * Example 7: Long-running analysis conversation.
     * <p>
     * Demonstrates a practical use case: analyzing code across multiple questions
     * while maintaining context about the codebase being discussed.
     * </p>
     */
    public void example7_LongRunningAnalysis() {
        logger.info("=== Example 7: Long-running Analysis ===");

        ClaudeCodeExecutor executor = new ClaudeCodeExecutorImpl();

        // Configure for longer timeout suitable for code review sessions
        ClaudeCodeOptions options = ClaudeCodeOptions.builder()
            .model("sonnet")
            .sessionInactivityTimeout(Duration.ofMinutes(60))  // 1 hour timeout
            .workingDirectory("/path/to/project")
            .build();

        String sessionId = executor.createConversationSession(options);
        logger.info("Created analysis session: {}", sessionId);

        try {
            // Initial context-setting message
            String response1 = executor.sendMessage(sessionId,
                "I have a Java Spring Boot application. I want to review it for best practices.");
            logger.info("Initial context set: {}", response1);

            // Question about specific aspect
            String response2 = executor.sendMessage(sessionId,
                "Can you check if I'm handling exceptions properly in my REST controllers?");
            logger.info("Exception handling review: {}", response2);

            // Follow-up based on previous answer
            String response3 = executor.sendMessage(sessionId,
                "Should I create custom exception classes or use standard ones?");
            logger.info("Exception design advice: {}", response3);

            // Ask about another aspect (context still maintained)
            String response4 = executor.sendMessage(sessionId,
                "Now let's look at my database layer. Any recommendations?");
            logger.info("Database review: {}", response4);

            // Final summary question
            String response5 = executor.sendMessage(sessionId,
                "Can you summarize the key improvements we discussed?");
            logger.info("Summary: {}", response5);

            logger.info("Analysis session complete");

        } finally {
            executor.closeConversationSession(sessionId);
            logger.info("Session closed");
        }
    }

    /**
     * Example 8: Multiple concurrent sessions.
     * <p>
     * Demonstrates managing multiple independent conversation sessions
     * simultaneously, useful for multi-user applications.
     * </p>
     */
    public void example8_MultipleSessions() {
        logger.info("=== Example 8: Multiple Concurrent Sessions ===");

        ClaudeCodeExecutor executor = new ClaudeCodeExecutorImpl();

        // Create two independent sessions
        String session1 = executor.createConversationSession();
        String session2 = executor.createConversationSession();
        logger.info("Created two sessions: {} and {}", session1, session2);

        try {
            // Each session maintains its own context
            
            // Session 1: Discussing Python
            String response1a = executor.sendMessage(session1,
                "Tell me about Python programming");
            logger.info("Session 1: {}", response1a);

            // Session 2: Discussing Java (independent context)
            String response2a = executor.sendMessage(session2,
                "Tell me about Java programming");
            logger.info("Session 2: {}", response2a);

            // Continue session 1 - knows we're talking about Python
            String response1b = executor.sendMessage(session1,
                "What are its main use cases?");
            logger.info("Session 1 follow-up: {}", response1b);

            // Continue session 2 - knows we're talking about Java
            String response2b = executor.sendMessage(session2,
                "What are its main use cases?");
            logger.info("Session 2 follow-up: {}", response2b);

            logger.info("Both sessions maintained independent contexts");

        } finally {
            // Close both sessions
            executor.closeConversationSession(session1);
            executor.closeConversationSession(session2);
            logger.info("Both sessions closed");
        }
    }

    /**
     * Example 9: Graceful handling of invalid messages.
     * <p>
     * Demonstrates validation and error handling for invalid input.
     * </p>
     */
    public void example9_InputValidation() {
        logger.info("=== Example 9: Input Validation ===");

        ClaudeCodeExecutor executor = new ClaudeCodeExecutorImpl();
        String sessionId = executor.createConversationSession();

        try {
            // Valid message
            String response1 = executor.sendMessage(sessionId, "Hello");
            logger.info("Valid message succeeded: {}", response1);

            // Try to send null message (will throw IllegalArgumentException)
            try {
                executor.sendMessage(sessionId, null);
                logger.error("Should not reach here - null message should be rejected");

            } catch (IllegalArgumentException e) {
                logger.info("Null message rejected as expected: {}", e.getMessage());
            }

            // Try to send empty message (will throw IllegalArgumentException)
            try {
                executor.sendMessage(sessionId, "   ");
                logger.error("Should not reach here - empty message should be rejected");

            } catch (IllegalArgumentException e) {
                logger.info("Empty message rejected as expected: {}", e.getMessage());
            }

            // Session is still valid after validation errors
            String response2 = executor.sendMessage(sessionId, "Are you still there?");
            logger.info("Session still works after validation errors: {}", response2);

        } finally {
            executor.closeConversationSession(sessionId);
            logger.info("Session closed");
        }
    }

    /**
     * Example 10: Best practices summary.
     * <p>
     * Demonstrates the recommended pattern incorporating all best practices:
     * try-finally for cleanup, status checking, error handling, and logging.
     * </p>
     */
    public void example10_BestPractices() {
        logger.info("=== Example 10: Best Practices ===");

        ClaudeCodeExecutor executor = new ClaudeCodeExecutorImpl();
        String sessionId = null;

        try {
            // 1. Create session with appropriate timeout
            ClaudeCodeOptions options = ClaudeCodeOptions.builder()
                .sessionInactivityTimeout(Duration.ofMinutes(30))
                .model("sonnet")
                .build();

            sessionId = executor.createConversationSession(options);
            logger.info("Session created: {}", sessionId);

            // 2. Check session is active before use (optional but good practice)
            if (!executor.isSessionActive(sessionId)) {
                logger.error("Session is not active immediately after creation");
                return;
            }

            // 3. Send messages with error handling
            try {
                String response = executor.sendMessage(sessionId,
                    "What is the capital of France?");
                logger.info("Response: {}", response);

                // 4. Validate responses if needed
                if (response == null || response.trim().isEmpty()) {
                    logger.warn("Received empty response from Claude");
                }

            } catch (ConversationSessionManager.SessionNotFoundException e) {
                logger.error("Session expired or not found: {}", e.getMessage());
                // Could create a new session here and retry
                
            } catch (IllegalArgumentException e) {
                logger.error("Invalid message: {}", e.getMessage());
                // Handle validation errors
                
            } catch (Exception e) {
                logger.error("Unexpected error during message send", e);
                // Handle other errors
            }

        } catch (Exception e) {
            logger.error("Error during session creation or use", e);

        } finally {
            // 5. Always close session in finally block
            if (sessionId != null) {
                try {
                    executor.closeConversationSession(sessionId);
                    logger.info("Session closed successfully");
                } catch (Exception e) {
                    logger.error("Error closing session (session may already be closed)", e);
                }
            }
        }
    }

    /**
     * Main method to run all examples.
     * <p>
     * <b>Note:</b> These examples require the Claude CLI to be installed
     * and properly configured. Some examples intentionally trigger errors
     * to demonstrate error handling.
     * </p>
     *
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        logger.info("Starting Conversational API Examples");
        logger.info("=====================================\n");

        ConversationalExamples examples = new ConversationalExamples();

        try {
            // Run all examples
            examples.example1_BasicConversation();
            pause();

            examples.example2_MultiTurnQA();
            pause();

            examples.example3_CustomConfiguration();
            pause();

            examples.example4_ErrorHandling();
            pause();

            examples.example5_ResourceManagement();
            pause();

            examples.example6_StatusChecking();
            pause();

            examples.example7_LongRunningAnalysis();
            pause();

            examples.example8_MultipleSessions();
            pause();

            examples.example9_InputValidation();
            pause();

            examples.example10_BestPractices();

            logger.info("\n=====================================");
            logger.info("All examples completed successfully");

        } catch (Exception e) {
            logger.error("Error running examples", e);
        }
    }

    /**
     * Pause between examples for readability.
     */
    private static void pause() {
        try {
            logger.info("\n--- Pausing 2 seconds before next example ---\n");
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

