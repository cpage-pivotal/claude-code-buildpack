package org.tanzu.claudecode.cf.spring;

import org.tanzu.claudecode.cf.ClaudeCodeExecutor;
import org.tanzu.claudecode.cf.ClaudeCodeOptions;
import org.tanzu.claudecode.cf.ConversationSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

/**
 * Spring Boot REST controller for Claude Code conversational sessions.
 * <p>
 * This controller provides REST endpoints for managing long-running conversational
 * sessions where users can ask multiple questions within a single chat context.
 * Each session maintains its own Claude CLI process and conversation history.
 * </p>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>POST /api/claude/sessions - Create a new conversation session</li>
 *   <li>POST /api/claude/sessions/{sessionId}/messages - Send message to a session</li>
 *   <li>DELETE /api/claude/sessions/{sessionId} - Close a session</li>
 *   <li>GET /api/claude/sessions/{sessionId}/status - Check session status</li>
 * </ul>
 *
 * <h2>Session Lifecycle</h2>
 * <p>Sessions are managed with the following lifecycle:</p>
 * <ol>
 *   <li><b>Creation</b>: POST /sessions returns a unique sessionId</li>
 *   <li><b>Active Use</b>: POST /sessions/{id}/messages sends messages</li>
 *   <li><b>Closure</b>: DELETE /sessions/{id} explicitly closes the session</li>
 *   <li><b>Auto-Expiration</b>: Sessions auto-expire after inactivity timeout (default: 30 minutes)</li>
 * </ol>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // 1. Create a session
 * POST /api/claude/sessions
 * Content-Type: application/json
 * 
 * {
 *   "model": "sonnet",
 *   "sessionInactivityTimeoutMinutes": 45,
 *   "workingDirectory": "/home/vcap/app"
 * }
 * 
 * Response: { "sessionId": "550e8400-e29b-41d4-a716-446655440000", "success": true }
 * 
 * // 2. Send messages to the session
 * POST /api/claude/sessions/550e8400-e29b-41d4-a716-446655440000/messages
 * Content-Type: application/json
 * 
 * {
 *   "message": "What is the capital of France?"
 * }
 * 
 * Response: { "response": "The capital of France is Paris.", "success": true }
 * 
 * // 3. Send follow-up (context maintained)
 * POST /api/claude/sessions/550e8400-e29b-41d4-a716-446655440000/messages
 * Content-Type: application/json
 * 
 * {
 *   "message": "What is its population?"
 * }
 * 
 * Response: { "response": "Paris has a population of approximately 2.1 million...", "success": true }
 * 
 * // 4. Check session status
 * GET /api/claude/sessions/550e8400-e29b-41d4-a716-446655440000/status
 * 
 * Response: { "active": true, "sessionId": "550e8400-e29b-41d4-a716-446655440000" }
 * 
 * // 5. Close session when done
 * DELETE /api/claude/sessions/550e8400-e29b-41d4-a716-446655440000
 * 
 * Response: { "message": "Session closed successfully", "success": true }
 * }</pre>
 *
 * <h2>Error Handling</h2>
 * <p>The controller handles the following error scenarios:</p>
 * <ul>
 *   <li><b>404 Not Found</b>: Session does not exist or has expired</li>
 *   <li><b>400 Bad Request</b>: Invalid request body or parameters</li>
 *   <li><b>500 Internal Server Error</b>: Unexpected execution errors</li>
 * </ul>
 *
 * @author Claude Code Buildpack Team
 * @since 1.1.0
 * @see ClaudeCodeExecutor
 * @see ConversationSessionManager
 */
@RestController
@RequestMapping("/api/claude/sessions")
public class ConversationController {

    private static final Logger logger = LoggerFactory.getLogger(ConversationController.class);
    
    private final ClaudeCodeExecutor executor;

    /**
     * Constructs a new controller with the given executor.
     * <p>
     * The executor should be configured as a Spring bean with the appropriate
     * session management capabilities.
     * </p>
     *
     * @param executor the Claude Code executor with conversational session support
     */
    public ConversationController(ClaudeCodeExecutor executor) {
        this.executor = executor;
    }

    /**
     * Create a new conversation session.
     * <p>
     * This endpoint creates a new long-running Claude CLI process and returns
     * a unique session ID that can be used to send messages within this conversation.
     * </p>
     * <p>
     * The session will automatically expire after the configured inactivity timeout
     * (default: 30 minutes). You can customize the timeout in the request body.
     * </p>
     *
     * @param request optional session configuration (model, timeout, working directory)
     * @return response containing the sessionId
     */
    @PostMapping
    public ResponseEntity<CreateSessionResponse> createSession(
            @RequestBody(required = false) CreateSessionRequest request) {
        
        logger.info("Creating new conversation session");
        
        try {
            String sessionId;
            
            if (request != null && request.hasOptions()) {
                // Create session with custom options
                ClaudeCodeOptions options = buildOptions(request);
                sessionId = executor.createConversationSession(options);
                logger.info("Created conversation session {} with custom options", sessionId);
            } else {
                // Create session with default options
                sessionId = executor.createConversationSession();
                logger.info("Created conversation session {} with default options", sessionId);
            }
            
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateSessionResponse(true, sessionId, null));
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid session creation request", e);
            return ResponseEntity.badRequest()
                .body(new CreateSessionResponse(false, null, e.getMessage()));
                
        } catch (Exception e) {
            logger.error("Failed to create conversation session", e);
            return ResponseEntity.internalServerError()
                .body(new CreateSessionResponse(false, null, 
                    "Failed to create session: " + e.getMessage()));
        }
    }

    /**
     * Send a message to an existing conversation session.
     * <p>
     * This endpoint sends a message to the specified session and returns Claude's response.
     * The response maintains context from all previous messages in this session.
     * </p>
     * <p>
     * <b>Note</b>: This is a blocking operation that waits for Claude's complete response
     * before returning. For long responses, consider implementing streaming in a future enhancement.
     * </p>
     *
     * @param sessionId the unique session ID
     * @param request the message to send
     * @return response containing Claude's reply
     */
    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<SendMessageResponse> sendMessage(
            @PathVariable String sessionId,
            @RequestBody SendMessageRequest request) {
        
        logger.info("Sending message to session {}", sessionId);
        
        // Validate request
        if (request == null || request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            logger.warn("Invalid message request for session {}: message is null or empty", sessionId);
            return ResponseEntity.badRequest()
                .body(new SendMessageResponse(false, null, "Message cannot be null or empty"));
        }
        
        try {
            // Send message and get response
            String response = executor.sendMessage(sessionId, request.getMessage());
            logger.info("Message sent successfully to session {}, response length: {} chars", 
                       sessionId, response.length());
            
            return ResponseEntity.ok(new SendMessageResponse(true, response, null));
            
        } catch (ConversationSessionManager.SessionNotFoundException e) {
            logger.warn("Session not found: {}", sessionId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new SendMessageResponse(false, null, 
                    "Session not found or has expired: " + sessionId));
                    
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid message request for session {}", sessionId, e);
            return ResponseEntity.badRequest()
                .body(new SendMessageResponse(false, null, e.getMessage()));
                
        } catch (Exception e) {
            logger.error("Failed to send message to session {}", sessionId, e);
            return ResponseEntity.internalServerError()
                .body(new SendMessageResponse(false, null, 
                    "Failed to send message: " + e.getMessage()));
        }
    }

    /**
     * Close a conversation session.
     * <p>
     * This endpoint explicitly closes a session and releases its resources.
     * After closing, the session ID cannot be used for further messages.
     * </p>
     * <p>
     * This operation is <b>idempotent</b> - closing an already closed session
     * or a non-existent session will succeed without error.
     * </p>
     *
     * @param sessionId the unique session ID to close
     * @return success response
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<CloseSessionResponse> closeSession(@PathVariable String sessionId) {
        
        logger.info("Closing conversation session {}", sessionId);
        
        try {
            // Close the session (idempotent operation)
            executor.closeConversationSession(sessionId);
            logger.info("Session {} closed successfully", sessionId);
            
            return ResponseEntity.ok(new CloseSessionResponse(
                true, 
                "Session closed successfully",
                null
            ));
            
        } catch (Exception e) {
            // Even if there's an error, we'll consider it a success if the session is gone
            logger.warn("Error closing session {} (session may already be closed)", sessionId, e);
            return ResponseEntity.ok(new CloseSessionResponse(
                true,
                "Session closed (was already closed or did not exist)",
                null
            ));
        }
    }

    /**
     * Check if a conversation session is active.
     * <p>
     * This endpoint provides a non-throwing way to check if a session exists
     * and is still active. This is useful for:
     * </p>
     * <ul>
     *   <li>Checking if a session has expired before sending a message</li>
     *   <li>Monitoring session health</li>
     *   <li>UI state management</li>
     * </ul>
     *
     * @param sessionId the unique session ID to check
     * @return status response indicating if session is active
     */
    @GetMapping("/{sessionId}/status")
    public ResponseEntity<SessionStatusResponse> getSessionStatus(@PathVariable String sessionId) {
        
        logger.debug("Checking status for session {}", sessionId);
        
        try {
            boolean active = executor.isSessionActive(sessionId);
            
            logger.debug("Session {} is {}", sessionId, active ? "active" : "inactive");
            
            return ResponseEntity.ok(new SessionStatusResponse(
                active,
                sessionId,
                active ? "Session is active" : "Session is inactive or does not exist"
            ));
            
        } catch (Exception e) {
            logger.error("Error checking session status for {}", sessionId, e);
            return ResponseEntity.internalServerError()
                .body(new SessionStatusResponse(
                    false,
                    sessionId,
                    "Error checking session status: " + e.getMessage()
                ));
        }
    }

    /**
     * Build ClaudeCodeOptions from the session creation request.
     */
    private ClaudeCodeOptions buildOptions(CreateSessionRequest request) {
        ClaudeCodeOptions.Builder builder = ClaudeCodeOptions.builder();
        
        if (request.getModel() != null && !request.getModel().isEmpty()) {
            builder.model(request.getModel());
        }
        
        if (request.getSessionInactivityTimeoutMinutes() != null && 
            request.getSessionInactivityTimeoutMinutes() > 0) {
            builder.sessionInactivityTimeout(
                Duration.ofMinutes(request.getSessionInactivityTimeoutMinutes())
            );
        }
        
        if (request.getWorkingDirectory() != null && !request.getWorkingDirectory().isEmpty()) {
            builder.workingDirectory(request.getWorkingDirectory());
        }
        
        if (request.getAdditionalEnv() != null && !request.getAdditionalEnv().isEmpty()) {
            builder.env(request.getAdditionalEnv());
        }
        
        return builder.build();
    }

    // ========== Request/Response Models ==========

    /**
     * Request model for creating a new conversation session.
     */
    public static class CreateSessionRequest {
        private String model;
        private Integer sessionInactivityTimeoutMinutes;
        private String workingDirectory;
        private Map<String, String> additionalEnv;

        /**
         * Check if this request has any custom options.
         */
        public boolean hasOptions() {
            return (model != null && !model.isEmpty()) ||
                   sessionInactivityTimeoutMinutes != null ||
                   (workingDirectory != null && !workingDirectory.isEmpty()) ||
                   (additionalEnv != null && !additionalEnv.isEmpty());
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Integer getSessionInactivityTimeoutMinutes() {
            return sessionInactivityTimeoutMinutes;
        }

        public void setSessionInactivityTimeoutMinutes(Integer sessionInactivityTimeoutMinutes) {
            this.sessionInactivityTimeoutMinutes = sessionInactivityTimeoutMinutes;
        }

        public String getWorkingDirectory() {
            return workingDirectory;
        }

        public void setWorkingDirectory(String workingDirectory) {
            this.workingDirectory = workingDirectory;
        }

        public Map<String, String> getAdditionalEnv() {
            return additionalEnv;
        }

        public void setAdditionalEnv(Map<String, String> additionalEnv) {
            this.additionalEnv = additionalEnv;
        }
    }

    /**
     * Response model for session creation.
     */
    public static class CreateSessionResponse {
        private boolean success;
        private String sessionId;
        private String error;

        public CreateSessionResponse() {
        }

        public CreateSessionResponse(boolean success, String sessionId, String error) {
            this.success = success;
            this.sessionId = sessionId;
            this.error = error;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    /**
     * Request model for sending a message to a session.
     */
    public static class SendMessageRequest {
        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    /**
     * Response model for message sending.
     */
    public static class SendMessageResponse {
        private boolean success;
        private String response;
        private String error;

        public SendMessageResponse() {
        }

        public SendMessageResponse(boolean success, String response, String error) {
            this.success = success;
            this.response = response;
            this.error = error;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getResponse() {
            return response;
        }

        public void setResponse(String response) {
            this.response = response;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    /**
     * Response model for session closure.
     */
    public static class CloseSessionResponse {
        private boolean success;
        private String message;
        private String error;

        public CloseSessionResponse() {
        }

        public CloseSessionResponse(boolean success, String message, String error) {
            this.success = success;
            this.message = message;
            this.error = error;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    /**
     * Response model for session status check.
     */
    public static class SessionStatusResponse {
        private boolean active;
        private String sessionId;
        private String message;

        public SessionStatusResponse() {
        }

        public SessionStatusResponse(boolean active, String sessionId, String message) {
            this.active = active;
            this.sessionId = sessionId;
            this.message = message;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}

