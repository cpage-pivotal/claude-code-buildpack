package io.github.claudecode.cf.spring;

import io.github.claudecode.cf.ClaudeCodeExecutor;
import io.github.claudecode.cf.ClaudeCodeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Spring Boot REST controller for Claude Code CLI integration.
 * <p>
 * This controller provides REST endpoints for executing Claude Code commands
 * from web applications. It demonstrates both synchronous and reactive patterns.
 * </p>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>POST /api/claude/execute - Synchronous execution</li>
 *   <li>POST /api/claude/execute-async - Asynchronous execution</li>
 *   <li>POST /api/claude/stream - Server-Sent Events streaming</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Synchronous request
 * POST /api/claude/execute
 * Content-Type: application/json
 * 
 * {
 *   "prompt": "Analyze this code for bugs",
 *   "model": "sonnet",
 *   "timeoutSeconds": 180
 * }
 * }</pre>
 *
 * @author Claude Code Buildpack Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/claude")
public class ClaudeCodeController {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeCodeController.class);
    
    private final ClaudeCodeExecutor executor;

    /**
     * Constructs a new controller with the given executor.
     *
     * @param executor the Claude Code executor
     */
    public ClaudeCodeController(ClaudeCodeExecutor executor) {
        this.executor = executor;
    }

    /**
     * Execute a Claude Code command synchronously.
     * <p>
     * This endpoint blocks until the command completes and returns the full output.
     * Suitable for short-running commands or when you need the complete result immediately.
     * </p>
     *
     * @param request the prompt request
     * @return the command output
     */
    @PostMapping("/execute")
    public ResponseEntity<ClaudeCodeResponse> execute(@RequestBody ClaudeCodeRequest request) {
        logger.info("Received synchronous execute request");
        
        try {
            ClaudeCodeOptions options = buildOptions(request);
            String result = executor.execute(request.getPrompt(), options);
            
            return ResponseEntity.ok(new ClaudeCodeResponse(
                true,
                result,
                null
            ));
            
        } catch (Exception e) {
            logger.error("Failed to execute Claude Code", e);
            return ResponseEntity.internalServerError()
                .body(new ClaudeCodeResponse(
                    false,
                    null,
                    e.getMessage()
                ));
        }
    }

    /**
     * Execute a Claude Code command asynchronously.
     * <p>
     * This endpoint returns immediately and the command runs in the background.
     * The response includes the full output once the command completes.
     * </p>
     *
     * @param request the prompt request
     * @return a Mono that emits the command output
     */
    @PostMapping("/execute-async")
    public Mono<ClaudeCodeResponse> executeAsync(@RequestBody ClaudeCodeRequest request) {
        logger.info("Received asynchronous execute request");
        
        ClaudeCodeOptions options = buildOptions(request);
        
        return Mono.fromFuture(executor.executeAsync(request.getPrompt(), options))
            .map(result -> new ClaudeCodeResponse(true, result, null))
            .onErrorResume(e -> {
                logger.error("Failed to execute Claude Code asynchronously", e);
                return Mono.just(new ClaudeCodeResponse(false, null, e.getMessage()));
            });
    }

    /**
     * Execute a Claude Code command with Server-Sent Events streaming.
     * <p>
     * This endpoint streams the output line-by-line as it becomes available.
     * Ideal for long-running commands or when you want to display progress in real-time.
     * </p>
     * <p>
     * The response uses the text/event-stream content type, allowing clients to
     * receive updates as they happen.
     * </p>
     *
     * @param request the prompt request
     * @return a Flux that emits output lines as Server-Sent Events
     */
    @PostMapping("/stream")
    public Flux<String> stream(@RequestBody ClaudeCodeRequest request) {
        logger.info("Received streaming execute request");
        
        ClaudeCodeOptions options = buildOptions(request);
        
        return Flux.fromStream(() -> executor.executeStreaming(request.getPrompt(), options))
            .delayElements(Duration.ofMillis(10)) // Smooth streaming
            .doOnError(e -> logger.error("Streaming error", e))
            .doOnComplete(() -> logger.info("Streaming completed"));
    }

    /**
     * Health check endpoint for Claude Code CLI.
     *
     * @return health status
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        boolean available = executor.isAvailable();
        String version = executor.getVersion();
        
        return ResponseEntity.ok(new HealthResponse(
            available ? "UP" : "DOWN",
            available,
            version
        ));
    }

    /**
     * Build ClaudeCodeOptions from the request.
     */
    private ClaudeCodeOptions buildOptions(ClaudeCodeRequest request) {
        ClaudeCodeOptions.Builder builder = ClaudeCodeOptions.builder();
        
        if (request.getTimeoutSeconds() != null) {
            builder.timeout(Duration.ofSeconds(request.getTimeoutSeconds()));
        }
        
        if (request.getModel() != null && !request.getModel().isEmpty()) {
            builder.model(request.getModel());
        }
        
        if (request.getAdditionalEnv() != null) {
            builder.env(request.getAdditionalEnv());
        }
        
        return builder.build();
    }

    /**
     * Request model for Claude Code execution.
     */
    public static class ClaudeCodeRequest {
        private String prompt;
        private String model;
        private Integer timeoutSeconds;
        private java.util.Map<String, String> additionalEnv;

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Integer getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(Integer timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public java.util.Map<String, String> getAdditionalEnv() {
            return additionalEnv;
        }

        public void setAdditionalEnv(java.util.Map<String, String> additionalEnv) {
            this.additionalEnv = additionalEnv;
        }
    }

    /**
     * Response model for Claude Code execution.
     */
    public static class ClaudeCodeResponse {
        private boolean success;
        private String result;
        private String error;

        public ClaudeCodeResponse() {
        }

        public ClaudeCodeResponse(boolean success, String result, String error) {
            this.success = success;
            this.result = result;
            this.error = error;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    /**
     * Response model for health check.
     */
    public static class HealthResponse {
        private String status;
        private boolean available;
        private String version;

        public HealthResponse() {
        }

        public HealthResponse(String status, boolean available, String version) {
            this.status = status;
            this.available = available;
            this.version = version;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public boolean isAvailable() {
            return available;
        }

        public void setAvailable(boolean available) {
            this.available = available;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }
}
