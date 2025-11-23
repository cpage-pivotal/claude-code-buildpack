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

    /**
     * Simple code analysis endpoint.
     * 
     * Example: POST /demo/analyze
     * Body: { "code": "public void test() { ... }" }
     */
    @PostMapping("/analyze")
    public Mono<AnalysisResponse> analyzeCode(@RequestBody CodeRequest request) {
        logger.info("Analyzing code snippet");
        
        String prompt = String.format(
            "Analyze this Java code for potential bugs, performance issues, and best practices:\n\n%s",
            request.getCode()
        );
        
        return Mono.fromFuture(executor.executeAsync(prompt))
            .map(result -> new AnalysisResponse(true, result))
            .onErrorResume(e -> {
                logger.error("Analysis failed", e);
                return Mono.just(new AnalysisResponse(false, "Error: " + e.getMessage()));
            });
    }

    /**
     * Generate unit tests for provided code.
     * 
     * Example: POST /demo/generate-tests
     * Body: { "code": "public void test() { ... }" }
     */
    @PostMapping("/generate-tests")
    public Mono<String> generateTests(@RequestBody CodeRequest request) {
        logger.info("Generating unit tests");
        
        String prompt = String.format(
            "Generate JUnit 5 unit tests for this Java code:\n\n%s",
            request.getCode()
        );
        
        ClaudeCodeOptions options = ClaudeCodeOptions.builder()
            .timeout(Duration.ofMinutes(5))
            .model("sonnet")
            .build();
        
        return Mono.fromFuture(executor.executeAsync(prompt, options));
    }

    /**
     * Refactor code with streaming output.
     * 
     * Example: POST /demo/refactor/stream
     * Body: { "code": "public void test() { ... }" }
     */
    @PostMapping(value = "/refactor/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> refactorCodeStreaming(@RequestBody CodeRequest request) {
        logger.info("Refactoring code with streaming");
        
        String prompt = String.format(
            "Refactor this Java code to improve readability and maintainability:\n\n%s",
            request.getCode()
        );
        
        return Flux.fromStream(() -> executor.executeStreaming(prompt))
            .delayElements(Duration.ofMillis(10))
            .doOnError(e -> logger.error("Streaming error", e))
            .doOnComplete(() -> logger.info("Streaming completed"));
    }

    /**
     * Interactive code review.
     * 
     * Example: POST /demo/review
     * Body: { "code": "...", "focus": "security" }
     */
    @PostMapping("/review")
    public Mono<String> reviewCode(@RequestBody ReviewRequest request) {
        logger.info("Performing code review with focus: {}", request.getFocus());
        
        String prompt = String.format(
            "Perform a detailed code review focusing on %s:\n\n%s",
            request.getFocus(),
            request.getCode()
        );
        
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

    // Request/Response models

    public static class CodeRequest {
        private String code;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    public static class ReviewRequest {
        private String code;
        private String focus = "general";

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getFocus() {
            return focus;
        }

        public void setFocus(String focus) {
            this.focus = focus;
        }
    }

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
