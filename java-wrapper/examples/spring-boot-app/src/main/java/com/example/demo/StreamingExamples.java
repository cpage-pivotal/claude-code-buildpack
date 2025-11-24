package com.example.demo;

import io.github.claudecode.cf.ClaudeCodeExecutor;
import io.github.claudecode.cf.ClaudeCodeExecutorImpl;
import io.github.claudecode.cf.ClaudeCodeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Comprehensive examples demonstrating proper streaming usage patterns.
 * <p>
 * This class shows various ways to use the streaming API correctly, emphasizing
 * proper resource management with try-with-resources.
 * </p>
 */
public class StreamingExamples {

    private static final Logger logger = LoggerFactory.getLogger(StreamingExamples.class);
    
    private final ClaudeCodeExecutor executor;

    public StreamingExamples(ClaudeCodeExecutor executor) {
        this.executor = executor;
    }

    /**
     * Example 1: Basic streaming with try-with-resources (RECOMMENDED).
     * <p>
     * This is the safest and most straightforward way to use streaming.
     * The Stream is automatically closed when the try block exits.
     * </p>
     */
    public void basicStreamingExample(String prompt) {
        logger.info("=== Basic Streaming Example ===");
        
        // RECOMMENDED: Use try-with-resources
        try (Stream<String> lines = executor.executeStreaming(prompt)) {
            lines.forEach(line -> {
                System.out.println("Received: " + line);
                logger.debug("Line: {}", line);
            });
        }
        
        logger.info("Streaming completed and resources cleaned up");
    }

    /**
     * Example 2: Streaming with custom options.
     * <p>
     * Demonstrates how to configure timeout, model, and other options
     * while maintaining proper resource management.
     * </p>
     */
    public void streamingWithOptionsExample(String prompt) {
        logger.info("=== Streaming with Options Example ===");
        
        ClaudeCodeOptions options = ClaudeCodeOptions.builder()
            .timeout(Duration.ofMinutes(5))
            .model("sonnet")
            .dangerouslySkipPermissions(true)
            .build();
        
        try (Stream<String> lines = executor.executeStreaming(prompt, options)) {
            lines.forEach(line -> System.out.println(line));
        }
        
        logger.info("Streaming with options completed");
    }

    /**
     * Example 3: Collecting streaming results.
     * <p>
     * Shows how to collect lines into a list while streaming.
     * Useful when you need to process the complete output after streaming.
     * </p>
     */
    public List<String> collectStreamingResultsExample(String prompt) {
        logger.info("=== Collecting Streaming Results Example ===");
        
        List<String> collectedLines = new ArrayList<>();
        
        try (Stream<String> lines = executor.executeStreaming(prompt)) {
            lines.forEach(line -> {
                collectedLines.add(line);
                logger.debug("Collected line: {}", line);
            });
        }
        
        logger.info("Collected {} lines", collectedLines.size());
        return collectedLines;
    }

    /**
     * Example 4: Streaming with line counting and progress tracking.
     * <p>
     * Demonstrates how to track progress while streaming, useful for
     * long-running operations where you want to show progress to users.
     * </p>
     */
    public void streamingWithProgressExample(String prompt) {
        logger.info("=== Streaming with Progress Example ===");
        
        AtomicInteger lineCount = new AtomicInteger(0);
        
        try (Stream<String> lines = executor.executeStreaming(prompt)) {
            lines.forEach(line -> {
                int count = lineCount.incrementAndGet();
                if (count % 10 == 0) {
                    logger.info("Progress: {} lines received", count);
                }
                System.out.println(line);
            });
        }
        
        logger.info("Streaming completed. Total lines: {}", lineCount.get());
    }

    /**
     * Example 5: Filtering and transforming streaming output.
     * <p>
     * Shows how to apply stream operations (filter, map, etc.) to the
     * output while maintaining proper resource management.
     * </p>
     */
    public void streamingWithFiltering(String prompt) {
        logger.info("=== Streaming with Filtering Example ===");
        
        try (Stream<String> lines = executor.executeStreaming(prompt)) {
            lines
                .filter(line -> !line.trim().isEmpty())  // Filter out empty lines
                .map(String::toUpperCase)                 // Transform to uppercase
                .forEach(line -> System.out.println("Filtered: " + line));
        }
        
        logger.info("Filtered streaming completed");
    }

    /**
     * Example 6: Error handling during streaming.
     * <p>
     * Demonstrates proper error handling when streaming fails or is interrupted.
     * </p>
     */
    public void streamingWithErrorHandlingExample(String prompt) {
        logger.info("=== Streaming with Error Handling Example ===");
        
        try (Stream<String> lines = executor.executeStreaming(prompt)) {
            lines.forEach(line -> {
                try {
                    // Simulate processing that might fail
                    processLine(line);
                    System.out.println("Processed: " + line);
                } catch (Exception e) {
                    logger.error("Error processing line: {}", line, e);
                    // Continue processing other lines
                }
            });
            
        } catch (Exception e) {
            logger.error("Streaming failed", e);
            // Handle streaming failure
            System.err.println("Error: " + e.getMessage());
        }
        
        logger.info("Streaming with error handling completed");
    }

    /**
     * Example 7: Early termination of streaming.
     * <p>
     * Shows how to stop streaming early based on a condition.
     * The Stream is still properly closed via try-with-resources.
     * </p>
     */
    public void streamingWithEarlyTerminationExample(String prompt) {
        logger.info("=== Streaming with Early Termination Example ===");
        
        try (Stream<String> lines = executor.executeStreaming(prompt)) {
            // Use limit() to stop after 10 lines
            lines.limit(10)
                .forEach(line -> System.out.println("Limited: " + line));
        }
        
        logger.info("Early terminated streaming completed");
    }

    /**
     * Example 8: Parallel processing of streamed lines (ADVANCED).
     * <p>
     * Demonstrates parallel processing of output lines.
     * WARNING: This can reorder lines and should only be used when order doesn't matter.
     * </p>
     */
    public void streamingWithParallelProcessingExample(String prompt) {
        logger.info("=== Streaming with Parallel Processing Example ===");
        
        try (Stream<String> lines = executor.executeStreaming(prompt)) {
            lines
                .parallel()  // Enable parallel processing
                .filter(line -> !line.trim().isEmpty())
                .forEach(line -> {
                    // Simulate expensive processing
                    String processed = expensiveProcessing(line);
                    System.out.println("Parallel processed: " + processed);
                });
        }
        
        logger.info("Parallel streaming completed");
    }

    /**
     * ANTI-PATTERN: What NOT to do - not closing the stream.
     * <p>
     * This method demonstrates incorrect usage that will leak resources.
     * DON'T DO THIS IN PRODUCTION CODE!
     * </p>
     */
    @SuppressWarnings("resource")
    public void antiPatternNoTryWithResourcesExample(String prompt) {
        logger.warn("=== ANTI-PATTERN Example - DO NOT USE ===");
        
        // BAD: Stream is never closed, process continues running!
        Stream<String> lines = executor.executeStreaming(prompt);
        lines.limit(5).forEach(System.out::println);
        
        // The underlying process is still running and consuming resources!
        logger.error("Stream not closed - process still running! This is a resource leak!");
    }

    /**
     * CORRECT PATTERN: Manual cleanup (when try-with-resources is not possible).
     * <p>
     * If you must manage the Stream manually, always use try-finally.
     * However, try-with-resources is strongly preferred.
     * </p>
     */
    public void manualCleanupExample(String prompt) {
        logger.info("=== Manual Cleanup Example ===");
        
        Stream<String> lines = executor.executeStreaming(prompt);
        try {
            lines.limit(5).forEach(System.out::println);
        } finally {
            // CRITICAL: Always close in finally block
            lines.close();
            logger.info("Stream manually closed");
        }
    }

    // Helper methods for examples

    private void processLine(String line) {
        // Simulate line processing
        if (line.contains("error")) {
            throw new RuntimeException("Simulated processing error");
        }
    }

    private String expensiveProcessing(String line) {
        // Simulate expensive processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return line.toUpperCase();
    }

    /**
     * Main method to run all examples (for testing purposes).
     */
    public static void main(String[] args) {
        // Check if Claude Code is available
        ClaudeCodeExecutor executor = new ClaudeCodeExecutorImpl();
        
        if (!executor.isAvailable()) {
            System.err.println("Claude Code CLI is not available. Please ensure:");
            System.err.println("1. CLAUDE_CLI_PATH environment variable is set");
            System.err.println("2. ANTHROPIC_API_KEY environment variable is set");
            System.err.println("3. Claude Code CLI is installed");
            return;
        }
        
        StreamingExamples examples = new StreamingExamples(executor);
        String prompt = "Say hello in 3 different languages";
        
        // Run examples (comment out the ones you don't want to run)
        try {
            examples.basicStreamingExample(prompt);
            // examples.streamingWithOptionsExample(prompt);
            // examples.collectStreamingResultsExample(prompt);
            // examples.streamingWithProgressExample(prompt);
            // examples.streamingWithFiltering(prompt);
            // examples.streamingWithErrorHandlingExample(prompt);
            // examples.streamingWithEarlyTerminationExample(prompt);
            // examples.streamingWithParallelProcessingExample(prompt);
            
            // WARNING: This example intentionally leaks resources!
            // examples.antiPatternNoTryWithResourcesExample(prompt);
            
            // examples.manualCleanupExample(prompt);
            
        } catch (Exception e) {
            System.err.println("Example failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

