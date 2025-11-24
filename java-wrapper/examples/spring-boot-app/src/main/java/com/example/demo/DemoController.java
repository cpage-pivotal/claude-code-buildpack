package com.example.demo;

import io.github.claudecode.cf.ClaudeCodeExecutor;
import io.github.claudecode.cf.ClaudeCodeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Example controller demonstrating various Claude Code usage patterns.
 */
@RestController
@RequestMapping("/demo")
public class DemoController {

    private static final Logger logger = LoggerFactory.getLogger(DemoController.class);
    
    private final ClaudeCodeExecutor executor;

    public DemoController(ClaudeCodeExecutor executor) {
        this.executor = executor;
    }

    public record PromptRequest(String prompt) {}

    /**
     * Execute a prompt and return structured response.
     * 
     * Example: POST /demo/execute-with-response
     * Body: { "prompt": "Analyze this Java code for potential bugs: public void test() { ... }" }
     */
    @PostMapping("/execute-with-response")
    public Mono<AnalysisResponse> executeWithResponse(@RequestBody PromptRequest request) {
        String prompt = request.prompt();
        if (prompt == null || prompt.trim().isEmpty()) {
            logger.warn("Received null or empty prompt");
            return Mono.just(new AnalysisResponse(false, "Error: Prompt cannot be null or empty"));
        }
        
        logger.info("Executing prompt: {}", prompt.substring(0, Math.min(50, prompt.length())));
        
        return Mono.fromFuture(executor.executeAsync(prompt))
            .map(result -> new AnalysisResponse(true, result))
            .onErrorResume(e -> {
                logger.error("Execution failed", e);
                return Mono.just(new AnalysisResponse(false, "Error: " + e.getMessage()));
            });
    }

    /**
     * Execute a prompt with custom options.
     * 
     * Example: POST /demo/execute-with-options
     * Body: { "prompt": "Generate JUnit 5 unit tests for this Java code: public void test() { ... }" }
     */
    @PostMapping("/execute-with-options")
    public Mono<String> executeWithOptions(@RequestBody PromptRequest request) {
        String prompt = request.prompt();
        if (prompt == null || prompt.trim().isEmpty()) {
            logger.warn("Received null or empty prompt");
            return Mono.error(new IllegalArgumentException("Prompt cannot be null or empty"));
        }
        
        logger.info("Executing prompt with options: {}", prompt.substring(0, Math.min(50, prompt.length())));
        
        ClaudeCodeOptions options = ClaudeCodeOptions.builder()
            .timeout(Duration.ofMinutes(5))
            .model("sonnet")
            .build();
        
        return Mono.fromFuture(executor.executeAsync(prompt, options));
    }

    /**
     * Execute a prompt with streaming output.
     * <p>
     * This demonstrates true real-time streaming where output lines are sent to the
     * client as they are produced by the Claude Code CLI process. The Stream is
     * properly managed using Flux.using() to ensure resources are cleaned up.
     * </p>
     * 
     * Example: POST /demo/execute-streaming
     * Body: { "prompt": "Refactor this Java code to improve readability: public void test() { ... }" }
     */
    @PostMapping(value = "/execute-streaming", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> executeStreaming(@RequestBody PromptRequest request) {
        String prompt = request.prompt();
        if (prompt == null || prompt.trim().isEmpty()) {
            logger.warn("Received null or empty prompt");
            return Flux.error(new IllegalArgumentException("Prompt cannot be null or empty"));
        }
        
        logger.info("Executing prompt with streaming: {}", prompt.substring(0, Math.min(50, prompt.length())));
        
        // Use Flux.using() to ensure the Stream is properly closed
        return Flux.using(
            // Resource supplier: create the Stream
            () -> executor.executeStreaming(prompt),
            // Flux generator: convert Stream to Flux
            stream -> Flux.fromStream(stream),
            // Cleanup: close the Stream when done
            stream -> {
                logger.debug("Closing stream");
                stream.close();
            }
        )
        .doOnError(e -> logger.error("Streaming error", e))
        .doOnComplete(() -> logger.info("Streaming completed"))
        .doOnCancel(() -> logger.info("Streaming cancelled by client"));
    }

    /**
     * Execute a prompt with simple string response.
     * 
     * Example: POST /demo/execute
     * Body: { "prompt": "Perform a detailed code review focusing on security: public void authenticate() { ... }" }
     */
    @PostMapping("/execute")
    public Mono<String> execute(@RequestBody PromptRequest request) {
        String prompt = request.prompt();
        if (prompt == null || prompt.trim().isEmpty()) {
            logger.warn("Received null or empty prompt");
            return Mono.error(new IllegalArgumentException("Prompt cannot be null or empty"));
        }
        
        logger.info("Executing prompt: {}", prompt.substring(0, Math.min(50, prompt.length())));
        
        return Mono.fromFuture(executor.executeAsync(prompt));
    }

    /**
     * Simple health check.
     */
    @GetMapping("/status")
    public Mono<StatusResponse> getStatus() {
        boolean available = executor.isAvailable();
        String version = executor.getVersion();
        
        return Mono.just(new StatusResponse(
            available ? "READY" : "UNAVAILABLE",
            version
        ));
    }

    // Response models

    public static class AnalysisResponse {
        private boolean success;
        private String analysis;

        public AnalysisResponse(boolean success, String analysis) {
            this.success = success;
            this.analysis = analysis;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getAnalysis() {
            return analysis;
        }

        public void setAnalysis(String analysis) {
            this.analysis = analysis;
        }
    }

    public static class StatusResponse {
        private String status;
        private String version;

        public StatusResponse(String status, String version) {
            this.status = status;
            this.version = version;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }
}
