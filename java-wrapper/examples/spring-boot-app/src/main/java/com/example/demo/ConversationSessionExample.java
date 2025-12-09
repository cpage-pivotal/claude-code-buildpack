package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tanzu.claudecode.cf.ClaudeCodeOptions;
import org.tanzu.claudecode.cf.ConversationSession;

import java.time.Duration;

/**
 * Examples demonstrating the usage of ConversationSession for maintaining
 * conversational context across multiple interactions with Claude.
 * <p>
 * These examples show how to create sessions, send multiple messages in
 * the same context, and properly manage session lifecycle.
 * </p>
 *
 * @author Claude Code Buildpack Team
 * @since 1.1.0
 */
public class ConversationSessionExample {

    private static final Logger logger = LoggerFactory.getLogger(ConversationSessionExample.class);

    /**
     * Example 1: Basic conversation with explicit close.
     * <p>
     * This example shows the simplest usage pattern: create a session, send
     * multiple messages, and explicitly close the session.
     * </p>
     */
    public static void basicConversation() {
        logger.info("=== Example 1: Basic Conversation ===");
        
        ConversationSession session = null;
        try {
            // Create a new session
            session = new ConversationSession();
            
            logger.info("Session created: {}", session.getSessionId());
            
            // First message
            String response1 = session.sendMessage("What is the capital of France?");
            logger.info("Response 1: {}", response1);
            
            // Second message - Claude remembers the context
            String response2 = session.sendMessage("What is its population?");
            logger.info("Response 2: {}", response2);
            
            // Third message - still in the same context
            String response3 = session.sendMessage("Tell me about its history");
            logger.info("Response 3: {}", response3);
            
        } finally {
            // CRITICAL: Always close the session
            if (session != null) {
                session.close();
                logger.info("Session closed");
            }
        }
    }

    /**
     * Example 2: Conversation with try-with-resources.
     * <p>
     * This example demonstrates the recommended pattern using try-with-resources
     * for automatic session cleanup.
     * </p>
     */
    public static void conversationWithAutoClose() {
        logger.info("=== Example 2: Try-With-Resources ===");
        
        // Automatically closes the session when done
        try (ConversationSession session = new ConversationSession()) {
            logger.info("Session created: {}", session.getSessionId());
            
            // Send messages
            String response1 = session.sendMessage("Explain recursion in simple terms");
            logger.info("Response 1: {}", response1);
            
            String response2 = session.sendMessage("Give me a Java example");
            logger.info("Response 2: {}", response2);
            
            String response3 = session.sendMessage("What are the common pitfalls?");
            logger.info("Response 3: {}", response3);
            
        } // Session automatically closed here
        
        logger.info("Session automatically closed");
    }

    /**
     * Example 3: Conversation with custom options.
     * <p>
     * This example shows how to configure session behavior with custom options
     * like timeout and model selection.
     * </p>
     */
    public static void conversationWithOptions() {
        logger.info("=== Example 3: Custom Options ===");
        
        // Configure options
        ClaudeCodeOptions options = ClaudeCodeOptions.builder()
            .timeout(Duration.ofMinutes(5))
            .model("sonnet")
            .dangerouslySkipPermissions(true)
            .build();
        
        try (ConversationSession session = new ConversationSession(options)) {
            logger.info("Session created with custom options: {}", session.getSessionId());
            
            // Ask for code review in context
            String response1 = session.sendMessage(
                "Review this code:\n" +
                "public int divide(int a, int b) {\n" +
                "    return a / b;\n" +
                "}"
            );
            logger.info("Response 1: {}", response1);
            
            // Follow-up question in context
            String response2 = session.sendMessage("How should I fix the issues you mentioned?");
            logger.info("Response 2: {}", response2);
            
            // Another follow-up
            String response3 = session.sendMessage("Add unit tests for the fixed version");
            logger.info("Response 3: {}", response3);
        }
    }

    /**
     * Example 4: Multi-turn Q&A session.
     * <p>
     * This example demonstrates a complex multi-turn conversation where
     * context is essential for understanding follow-up questions.
     * </p>
     */
    public static void multiTurnQA() {
        logger.info("=== Example 4: Multi-Turn Q&A ===");
        
        try (ConversationSession session = new ConversationSession()) {
            logger.info("Session created: {}", session.getSessionId());
            
            // Build up context over multiple turns
            String[] questions = {
                "I'm building a REST API for a library system",
                "What entities should I create?",
                "Show me the Book entity with JPA annotations",
                "Add validation annotations",
                "Now show me the corresponding repository interface",
                "What endpoints should the BookController have?"
            };
            
            for (int i = 0; i < questions.length; i++) {
                logger.info("Question {}: {}", i + 1, questions[i]);
                String response = session.sendMessage(questions[i]);
                logger.info("Response {}: {} chars", i + 1, response.length());
                // In a real application, you would process or display the response
            }
            
            logger.info("Completed multi-turn conversation with {} messages", questions.length);
        }
    }

    /**
     * Example 5: Session state checking.
     * <p>
     * This example shows how to check session state and handle different
     * session conditions.
     * </p>
     */
    public static void sessionStateManagement() {
        logger.info("=== Example 5: Session State Management ===");
        
        ConversationSession session = new ConversationSession();
        
        try {
            // Check session state
            logger.info("Session ID: {}", session.getSessionId());
            logger.info("Is active: {}", session.isActive());
            logger.info("Created at: {}", session.getCreatedAt());
            logger.info("Last activity: {}", session.getLastActivity());
            logger.info("State: {}", session.getState());
            
            // Send a message
            if (session.isActive()) {
                String response = session.sendMessage("Hello Claude!");
                logger.info("Response: {}", response);
            }
            
            // Check if expired (with 30-minute timeout)
            boolean expired = session.isExpired(Duration.ofMinutes(30));
            logger.info("Is expired (30 min): {}", expired);
            
            // Update activity manually if needed
            session.updateActivity();
            logger.info("Activity updated: {}", session.getLastActivity());
            
        } catch (IllegalStateException e) {
            logger.error("Session is not active: {}", e.getMessage());
        } finally {
            session.close();
            
            // After closing, session is no longer active
            logger.info("After close - Is active: {}", session.isActive());
            logger.info("After close - State: {}", session.getState());
        }
    }

    /**
     * Example 6: Error handling.
     * <p>
     * This example demonstrates proper error handling for session operations.
     * </p>
     */
    public static void errorHandling() {
        logger.info("=== Example 6: Error Handling ===");
        
        ConversationSession session = new ConversationSession();
        
        try {
            // Send a valid message
            String response1 = session.sendMessage("What is Java?");
            logger.info("Valid message succeeded");
            
            // Close the session
            session.close();
            logger.info("Session closed");
            
            // Try to send a message after closing (will throw IllegalStateException)
            try {
                session.sendMessage("This will fail");
            } catch (IllegalStateException e) {
                logger.warn("Expected error after close: {}", e.getMessage());
            }
            
        } finally {
            // Safe to call close multiple times
            session.close();
            logger.info("Second close call completed (idempotent)");
        }
        
        // Test invalid prompt
        try (ConversationSession validSession = new ConversationSession()) {
            try {
                validSession.sendMessage("");
            } catch (IllegalArgumentException e) {
                logger.warn("Expected error for empty prompt: {}", e.getMessage());
            }
            
            try {
                validSession.sendMessage(null);
            } catch (IllegalArgumentException e) {
                logger.warn("Expected error for null prompt: {}", e.getMessage());
            }
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
        logger.info("Starting ConversationSession Examples");
        logger.info("======================================");
        
        try {
            // Run examples sequentially
            basicConversation();
            System.out.println();
            
            conversationWithAutoClose();
            System.out.println();
            
            conversationWithOptions();
            System.out.println();
            
            multiTurnQA();
            System.out.println();
            
            sessionStateManagement();
            System.out.println();
            
            errorHandling();
            System.out.println();
            
            logger.info("All examples completed successfully!");
            
        } catch (Exception e) {
            logger.error("Example execution failed", e);
            System.exit(1);
        }
    }
}

