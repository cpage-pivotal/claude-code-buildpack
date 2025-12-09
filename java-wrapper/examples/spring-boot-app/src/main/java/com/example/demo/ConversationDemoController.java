package com.example.demo;

import org.tanzu.claudecode.cf.ClaudeCodeExecutor;
import org.tanzu.claudecode.cf.ClaudeCodeOptions;
import org.tanzu.claudecode.cf.ConversationSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demo controller for testing conversational session functionality via HTTP.
 * <p>
 * This controller provides convenient endpoints for manually testing conversational
 * sessions from HTTP clients like Postman, curl, or httpie. It complements the
 * production {@link org.tanzu.claudecode.cf.spring.ConversationController} with
 * additional features for testing and demonstration.
 * </p>
 *
 * <h2>Quick Start Guide</h2>
 * <pre>{@code
 * # 1. Start a conversation
 * POST http://localhost:8080/demo/conversation/start
 * Response: { "sessionId": "abc-123", "message": "Conversation started" }
 *
 * # 2. Ask a question
 * POST http://localhost:8080/demo/conversation/abc-123/ask
 * Body: { "message": "What is the capital of France?" }
 * Response: { "response": "The capital of France is Paris.", "history": [...] }
 *
 * # 3. Ask a follow-up (context maintained!)
 * POST http://localhost:8080/demo/conversation/abc-123/ask
 * Body: { "message": "What is its population?" }
 * Response: { "response": "Paris has a population of...", "history": [...] }
 *
 * # 4. View conversation history
 * GET http://localhost:8080/demo/conversation/abc-123/history
 * Response: { "sessionId": "abc-123", "messages": [...] }
 *
 * # 5. End the conversation
 * DELETE http://localhost:8080/demo/conversation/abc-123
 * Response: { "message": "Conversation ended", "totalMessages": 2 }
 * }</pre>
 *
 * <h2>Available Endpoints</h2>
 * <ul>
 *   <li>POST /demo/conversation/start - Start a new conversation</li>
 *   <li>POST /demo/conversation/{id}/ask - Send a message</li>
 *   <li>GET /demo/conversation/{id}/history - View conversation history</li>
 *   <li>GET /demo/conversation/{id}/status - Check if conversation is active</li>
 *   <li>DELETE /demo/conversation/{id} - End the conversation</li>
 *   <li>GET /demo/conversation/active - List all active conversations</li>
 *   <li>POST /demo/conversation/multi-turn - Run a pre-built multi-turn demo</li>
 * </ul>
 *
 * @author Claude Code Buildpack Team
 * @since 1.1.0
 */
@RestController
@RequestMapping("/demo/conversation")
public class ConversationDemoController {

    private static final Logger logger = LoggerFactory.getLogger(ConversationDemoController.class);
    
    private final ClaudeCodeExecutor executor;
    
    // Store conversation history for demonstration purposes
    // In production, you'd use a proper database
    private final Map<String, ConversationHistory> conversationHistories = new ConcurrentHashMap<>();

    public ConversationDemoController(ClaudeCodeExecutor executor) {
        this.executor = executor;
    }

    /**
     * Start a new conversation with optional configuration.
     * <p>
     * Example:
     * <pre>{@code
     * POST /demo/conversation/start
     * Body (optional): {
     *   "model": "sonnet",
     *   "timeoutMinutes": 45,
     *   "nickname": "My Code Review Session"
     * }
     * }</pre>
     * </p>
     *
     * @param request optional configuration
     * @return response with sessionId
     */
    @PostMapping("/start")
    public ResponseEntity<StartConversationResponse> startConversation(
            @RequestBody(required = false) StartConversationRequest request) {
        
        logger.info("Starting new conversation");
        
        try {
            String sessionId;
            
            if (request != null && request.hasOptions()) {
                ClaudeCodeOptions options = ClaudeCodeOptions.builder()
                    .model(request.getModel() != null ? request.getModel() : "sonnet")
                    .sessionInactivityTimeout(Duration.ofMinutes(
                        request.getTimeoutMinutes() != null ? request.getTimeoutMinutes() : 30
                    ))
                    .build();
                    
                sessionId = executor.createConversationSession(options);
                logger.info("Created conversation {} with custom options", sessionId);
            } else {
                sessionId = executor.createConversationSession();
                logger.info("Created conversation {} with default options", sessionId);
            }
            
            // Initialize conversation history
            String nickname = request != null && request.getNickname() != null 
                ? request.getNickname() 
                : "Conversation " + sessionId.substring(0, 8);
            conversationHistories.put(sessionId, new ConversationHistory(sessionId, nickname));
            
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(new StartConversationResponse(
                    true,
                    sessionId,
                    nickname,
                    "Conversation started! You can now send messages to /demo/conversation/" + sessionId + "/ask",
                    null
                ));
            
        } catch (Exception e) {
            logger.error("Failed to start conversation", e);
            return ResponseEntity.internalServerError()
                .body(new StartConversationResponse(
                    false,
                    null,
                    null,
                    null,
                    "Failed to start conversation: " + e.getMessage()
                ));
        }
    }

    /**
     * Send a message to an existing conversation.
     * <p>
     * This maintains the full conversation history and shows Claude's ability
     * to reference previous messages.
     * </p>
     * <p>
     * Example:
     * <pre>{@code
     * POST /demo/conversation/{sessionId}/ask
     * Body: { "message": "What is the capital of France?" }
     * }</pre>
     * </p>
     *
     * @param sessionId the conversation ID
     * @param request the message to send
     * @return Claude's response with conversation history
     */
    @PostMapping("/{sessionId}/ask")
    public ResponseEntity<AskResponse> ask(
            @PathVariable String sessionId,
            @RequestBody AskRequest request) {
        
        logger.info("Sending message to conversation {}", sessionId);
        
        if (request == null || request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            logger.warn("Invalid message for conversation {}", sessionId);
            return ResponseEntity.badRequest()
                .body(new AskResponse(
                    false,
                    null,
                    null,
                    "Message cannot be null or empty"
                ));
        }
        
        try {
            // Send message to Claude
            long startTime = System.currentTimeMillis();
            String response = executor.sendMessage(sessionId, request.getMessage());
            long duration = System.currentTimeMillis() - startTime;
            
            logger.info("Received response for conversation {} in {}ms", sessionId, duration);
            
            // Update conversation history
            ConversationHistory history = conversationHistories.get(sessionId);
            if (history == null) {
                // Session exists in executor but not in our history map
                history = new ConversationHistory(sessionId, "Conversation " + sessionId.substring(0, 8));
                conversationHistories.put(sessionId, history);
            }
            
            history.addExchange(request.getMessage(), response);
            
            return ResponseEntity.ok(new AskResponse(
                true,
                response,
                history.getExchanges(),
                null
            ));
            
        } catch (ConversationSessionManager.SessionNotFoundException e) {
            logger.warn("Conversation not found: {}", sessionId);
            // Clean up history if session is gone
            conversationHistories.remove(sessionId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new AskResponse(
                    false,
                    null,
                    null,
                    "Conversation not found or has expired: " + sessionId
                ));
                
        } catch (Exception e) {
            logger.error("Failed to send message to conversation {}", sessionId, e);
            return ResponseEntity.internalServerError()
                .body(new AskResponse(
                    false,
                    null,
                    null,
                    "Failed to send message: " + e.getMessage()
                ));
        }
    }

    /**
     * Get the full conversation history.
     * <p>
     * Example:
     * <pre>{@code
     * GET /demo/conversation/{sessionId}/history
     * }</pre>
     * </p>
     *
     * @param sessionId the conversation ID
     * @return the complete conversation history
     */
    @GetMapping("/{sessionId}/history")
    public ResponseEntity<ConversationHistoryResponse> getHistory(@PathVariable String sessionId) {
        
        logger.debug("Retrieving history for conversation {}", sessionId);
        
        ConversationHistory history = conversationHistories.get(sessionId);
        if (history == null) {
            logger.warn("No history found for conversation {}", sessionId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ConversationHistoryResponse(
                    false,
                    null,
                    null,
                    null,
                    0,
                    "No history found for conversation: " + sessionId
                ));
        }
        
        boolean isActive = executor.isSessionActive(sessionId);
        
        return ResponseEntity.ok(new ConversationHistoryResponse(
            true,
            sessionId,
            history.getNickname(),
            history.getExchanges(),
            history.getExchanges().size(),
            isActive ? "Conversation is active" : "Conversation has ended"
        ));
    }

    /**
     * Check if a conversation is still active.
     * <p>
     * Example:
     * <pre>{@code
     * GET /demo/conversation/{sessionId}/status
     * }</pre>
     * </p>
     *
     * @param sessionId the conversation ID
     * @return the conversation status
     */
    @GetMapping("/{sessionId}/status")
    public ResponseEntity<ConversationStatusResponse> getStatus(@PathVariable String sessionId) {
        
        logger.debug("Checking status for conversation {}", sessionId);
        
        boolean isActive = executor.isSessionActive(sessionId);
        ConversationHistory history = conversationHistories.get(sessionId);
        
        return ResponseEntity.ok(new ConversationStatusResponse(
            isActive,
            sessionId,
            history != null ? history.getNickname() : null,
            history != null ? history.getExchanges().size() : 0,
            isActive ? "Conversation is active and ready" : "Conversation is inactive or expired"
        ));
    }

    /**
     * End a conversation.
     * <p>
     * Example:
     * <pre>{@code
     * DELETE /demo/conversation/{sessionId}
     * }</pre>
     * </p>
     *
     * @param sessionId the conversation ID
     * @return confirmation message
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<EndConversationResponse> endConversation(@PathVariable String sessionId) {
        
        logger.info("Ending conversation {}", sessionId);
        
        try {
            ConversationHistory history = conversationHistories.remove(sessionId);
            int messageCount = history != null ? history.getExchanges().size() : 0;
            
            executor.closeConversationSession(sessionId);
            
            return ResponseEntity.ok(new EndConversationResponse(
                true,
                "Conversation ended successfully",
                messageCount,
                null
            ));
            
        } catch (Exception e) {
            logger.warn("Error ending conversation {} (may already be closed)", sessionId, e);
            conversationHistories.remove(sessionId);
            return ResponseEntity.ok(new EndConversationResponse(
                true,
                "Conversation ended (was already closed or did not exist)",
                0,
                null
            ));
        }
    }

    /**
     * List all active conversations.
     * <p>
     * Example:
     * <pre>{@code
     * GET /demo/conversation/active
     * }</pre>
     * </p>
     *
     * @return list of active conversations
     */
    @GetMapping("/active")
    public ResponseEntity<ActiveConversationsResponse> listActiveConversations() {
        
        logger.debug("Listing active conversations");
        
        List<ConversationSummary> summaries = new ArrayList<>();
        
        for (Map.Entry<String, ConversationHistory> entry : conversationHistories.entrySet()) {
            String sessionId = entry.getKey();
            ConversationHistory history = entry.getValue();
            boolean isActive = executor.isSessionActive(sessionId);
            
            if (isActive) {
                summaries.add(new ConversationSummary(
                    sessionId,
                    history.getNickname(),
                    history.getExchanges().size(),
                    true
                ));
            } else {
                // Clean up inactive conversations from history
                conversationHistories.remove(sessionId);
            }
        }
        
        return ResponseEntity.ok(new ActiveConversationsResponse(
            true,
            summaries,
            summaries.size(),
            null
        ));
    }

    /**
     * Run a pre-built multi-turn conversation demo.
     * <p>
     * This demonstrates context preservation by asking related questions
     * in sequence and showing how Claude maintains context.
     * </p>
     * <p>
     * Example:
     * <pre>{@code
     * POST /demo/conversation/multi-turn
     * Body (optional): { "topic": "Python programming" }
     * }</pre>
     * </p>
     *
     * @param request optional topic configuration
     * @return the complete conversation
     */
    @PostMapping("/multi-turn")
    public ResponseEntity<MultiTurnDemoResponse> runMultiTurnDemo(
            @RequestBody(required = false) MultiTurnDemoRequest request) {
        
        logger.info("Running multi-turn demo");
        
        String topic = request != null && request.getTopic() != null 
            ? request.getTopic() 
            : "Python programming";
        
        String sessionId = null;
        List<ConversationExchange> exchanges = new ArrayList<>();
        
        try {
            // Start conversation
            sessionId = executor.createConversationSession();
            String nickname = "Multi-turn Demo: " + topic;
            conversationHistories.put(sessionId, new ConversationHistory(sessionId, nickname));
            
            logger.info("Started demo conversation {} about {}", sessionId, topic);
            
            // Question 1: Initial topic
            String q1 = "Tell me about " + topic;
            String r1 = executor.sendMessage(sessionId, q1);
            exchanges.add(new ConversationExchange(1, q1, r1));
            logger.info("Demo Q1 completed");
            
            // Question 2: Follow-up using pronoun (tests context)
            String q2 = "What are the main advantages of using it?";
            String r2 = executor.sendMessage(sessionId, q2);
            exchanges.add(new ConversationExchange(2, q2, r2));
            logger.info("Demo Q2 completed (pronoun reference)");
            
            // Question 3: Request for example (continues context)
            String q3 = "Can you show me a simple code example?";
            String r3 = executor.sendMessage(sessionId, q3);
            exchanges.add(new ConversationExchange(3, q3, r3));
            logger.info("Demo Q3 completed");
            
            // Question 4: Reference to previous response
            String q4 = "Can you explain the code you just showed me in more detail?";
            String r4 = executor.sendMessage(sessionId, q4);
            exchanges.add(new ConversationExchange(4, q4, r4));
            logger.info("Demo Q4 completed (references previous response)");
            
            // Update history
            ConversationHistory history = conversationHistories.get(sessionId);
            if (history != null) {
                exchanges.forEach(ex -> history.addExchange(ex.getQuestion(), ex.getResponse()));
            }
            
            return ResponseEntity.ok(new MultiTurnDemoResponse(
                true,
                sessionId,
                topic,
                exchanges,
                "Demo completed successfully. Notice how Claude maintained context throughout the conversation! " +
                "Session " + sessionId + " is still active - you can continue the conversation at /demo/conversation/" + 
                sessionId + "/ask",
                null
            ));
            
        } catch (Exception e) {
            logger.error("Multi-turn demo failed", e);
            
            // Clean up if session was created
            if (sessionId != null) {
                try {
                    executor.closeConversationSession(sessionId);
                    conversationHistories.remove(sessionId);
                } catch (Exception cleanupEx) {
                    logger.warn("Failed to clean up demo session", cleanupEx);
                }
            }
            
            return ResponseEntity.internalServerError()
                .body(new MultiTurnDemoResponse(
                    false,
                    sessionId,
                    topic,
                    exchanges,
                    null,
                    "Demo failed: " + e.getMessage()
                ));
        }
    }

    // ========== Data Models ==========

    public static class StartConversationRequest {
        private String model;
        private Integer timeoutMinutes;
        private String nickname;

        public boolean hasOptions() {
            return model != null || timeoutMinutes != null;
        }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public Integer getTimeoutMinutes() { return timeoutMinutes; }
        public void setTimeoutMinutes(Integer timeoutMinutes) { this.timeoutMinutes = timeoutMinutes; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
    }

    public static class StartConversationResponse {
        private boolean success;
        private String sessionId;
        private String nickname;
        private String message;
        private String error;

        public StartConversationResponse(boolean success, String sessionId, String nickname, 
                                        String message, String error) {
            this.success = success;
            this.sessionId = sessionId;
            this.nickname = nickname;
            this.message = message;
            this.error = error;
        }

        public boolean isSuccess() { return success; }
        public String getSessionId() { return sessionId; }
        public String getNickname() { return nickname; }
        public String getMessage() { return message; }
        public String getError() { return error; }
    }

    public static class AskRequest {
        private String message;

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class AskResponse {
        private boolean success;
        private String response;
        private List<ConversationExchange> history;
        private String error;

        public AskResponse(boolean success, String response, List<ConversationExchange> history, String error) {
            this.success = success;
            this.response = response;
            this.history = history;
            this.error = error;
        }

        public boolean isSuccess() { return success; }
        public String getResponse() { return response; }
        public List<ConversationExchange> getHistory() { return history; }
        public String getError() { return error; }
    }

    public static class ConversationHistoryResponse {
        private boolean success;
        private String sessionId;
        private String nickname;
        private List<ConversationExchange> exchanges;
        private int totalMessages;
        private String message;

        public ConversationHistoryResponse(boolean success, String sessionId, String nickname,
                                          List<ConversationExchange> exchanges, int totalMessages, String message) {
            this.success = success;
            this.sessionId = sessionId;
            this.nickname = nickname;
            this.exchanges = exchanges;
            this.totalMessages = totalMessages;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getSessionId() { return sessionId; }
        public String getNickname() { return nickname; }
        public List<ConversationExchange> getExchanges() { return exchanges; }
        public int getTotalMessages() { return totalMessages; }
        public String getMessage() { return message; }
    }

    public static class ConversationStatusResponse {
        private boolean active;
        private String sessionId;
        private String nickname;
        private int messageCount;
        private String message;

        public ConversationStatusResponse(boolean active, String sessionId, String nickname, 
                                         int messageCount, String message) {
            this.active = active;
            this.sessionId = sessionId;
            this.nickname = nickname;
            this.messageCount = messageCount;
            this.message = message;
        }

        public boolean isActive() { return active; }
        public String getSessionId() { return sessionId; }
        public String getNickname() { return nickname; }
        public int getMessageCount() { return messageCount; }
        public String getMessage() { return message; }
    }

    public static class EndConversationResponse {
        private boolean success;
        private String message;
        private int totalMessages;
        private String error;

        public EndConversationResponse(boolean success, String message, int totalMessages, String error) {
            this.success = success;
            this.message = message;
            this.totalMessages = totalMessages;
            this.error = error;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getTotalMessages() { return totalMessages; }
        public String getError() { return error; }
    }

    public static class ActiveConversationsResponse {
        private boolean success;
        private List<ConversationSummary> conversations;
        private int count;
        private String error;

        public ActiveConversationsResponse(boolean success, List<ConversationSummary> conversations, 
                                          int count, String error) {
            this.success = success;
            this.conversations = conversations;
            this.count = count;
            this.error = error;
        }

        public boolean isSuccess() { return success; }
        public List<ConversationSummary> getConversations() { return conversations; }
        public int getCount() { return count; }
        public String getError() { return error; }
    }

    public static class ConversationSummary {
        private String sessionId;
        private String nickname;
        private int messageCount;
        private boolean active;

        public ConversationSummary(String sessionId, String nickname, int messageCount, boolean active) {
            this.sessionId = sessionId;
            this.nickname = nickname;
            this.messageCount = messageCount;
            this.active = active;
        }

        public String getSessionId() { return sessionId; }
        public String getNickname() { return nickname; }
        public int getMessageCount() { return messageCount; }
        public boolean isActive() { return active; }
    }

    public static class MultiTurnDemoRequest {
        private String topic;

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
    }

    public static class MultiTurnDemoResponse {
        private boolean success;
        private String sessionId;
        private String topic;
        private List<ConversationExchange> conversation;
        private String message;
        private String error;

        public MultiTurnDemoResponse(boolean success, String sessionId, String topic,
                                    List<ConversationExchange> conversation, String message, String error) {
            this.success = success;
            this.sessionId = sessionId;
            this.topic = topic;
            this.conversation = conversation;
            this.message = message;
            this.error = error;
        }

        public boolean isSuccess() { return success; }
        public String getSessionId() { return sessionId; }
        public String getTopic() { return topic; }
        public List<ConversationExchange> getConversation() { return conversation; }
        public String getMessage() { return message; }
        public String getError() { return error; }
    }

    public static class ConversationExchange {
        private int turn;
        private String question;
        private String response;

        public ConversationExchange(int turn, String question, String response) {
            this.turn = turn;
            this.question = question;
            this.response = response;
        }

        public int getTurn() { return turn; }
        public String getQuestion() { return question; }
        public String getResponse() { return response; }
    }

    // Helper class to track conversation history
    private static class ConversationHistory {
        private final String sessionId;
        private final String nickname;
        private final List<ConversationExchange> exchanges = new ArrayList<>();
        private int turnCounter = 0;

        public ConversationHistory(String sessionId, String nickname) {
            this.sessionId = sessionId;
            this.nickname = nickname;
        }

        public void addExchange(String question, String response) {
            exchanges.add(new ConversationExchange(++turnCounter, question, response));
        }

        public String getSessionId() { return sessionId; }
        public String getNickname() { return nickname; }
        public List<ConversationExchange> getExchanges() { return new ArrayList<>(exchanges); }
    }
}

