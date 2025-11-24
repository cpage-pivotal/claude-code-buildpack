package io.github.claudecode.cf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Default implementation of {@link ClaudeCodeExecutor}.
 * <p>
 * This implementation uses {@link ProcessBuilder} to invoke the Claude Code CLI
 * and follows best practices for process management in Cloud Foundry environments.
 * </p>
 *
 * <h2>Critical Implementation Details</h2>
 * <p>
 * This implementation follows the patterns documented in DESIGN.md to avoid
 * common ProcessBuilder pitfalls:
 * </p>
 * <ul>
 *   <li>Always closes stdin immediately to prevent CLI from waiting for input</li>
 *   <li>Redirects stderr to stdout to prevent buffer deadlock</li>
 *   <li>Passes environment variables to subprocess explicitly</li>
 *   <li>Uses timeouts to prevent indefinite hangs</li>
 *   <li>Properly cleans up resources</li>
 * </ul>
 *
 * <h2>Environment Variables</h2>
 * <p>Required environment variables:</p>
 * <ul>
 *   <li><code>CLAUDE_CLI_PATH</code> - Path to the Claude Code CLI executable</li>
 *   <li><code>ANTHROPIC_API_KEY</code> - Anthropic API authentication key</li>
 *   <li><code>HOME</code> - Home directory (for .claude.json configuration)</li>
 * </ul>
 *
 * @author Claude Code Buildpack Team
 * @since 1.0.0
 */
public class ClaudeCodeExecutorImpl implements ClaudeCodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeCodeExecutorImpl.class);
    
    // Shared executor service for timeout management
    private static final ScheduledExecutorService timeoutExecutor = 
        Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "claude-code-timeout");
            t.setDaemon(true);
            return t;
        });
    
    private final String claudePath;
    private final Map<String, String> baseEnvironment;

    /**
     * Constructs a new executor using environment variables.
     * <p>
     * Requires the following environment variables to be set:
     * </p>
     * <ul>
     *   <li>CLAUDE_CLI_PATH</li>
     *   <li>ANTHROPIC_API_KEY</li>
     * </ul>
     *
     * @throws IllegalStateException if required environment variables are not set
     */
    public ClaudeCodeExecutorImpl() {
        this.claudePath = getRequiredEnv("CLAUDE_CLI_PATH");
        this.baseEnvironment = buildBaseEnvironment();
        
        logger.info("Initialized ClaudeCodeExecutor with CLI path: {}", maskPath(claudePath));
    }

    /**
     * Constructs a new executor with explicit configuration.
     *
     * @param claudePath path to the Claude Code CLI executable
     * @param apiKey Anthropic API key
     * @throws IllegalArgumentException if any parameter is null or empty
     */
    public ClaudeCodeExecutorImpl(String claudePath, String apiKey) {
        if (claudePath == null || claudePath.isEmpty()) {
            throw new IllegalArgumentException("Claude CLI path cannot be null or empty");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        
        this.claudePath = claudePath;
        this.baseEnvironment = buildEnvironment(apiKey);
        
        logger.info("Initialized ClaudeCodeExecutor with explicit configuration");
    }

    @Override
    public String execute(String prompt) {
        return execute(prompt, ClaudeCodeOptions.defaults());
    }

    @Override
    public String execute(String prompt, ClaudeCodeOptions options) {
        validatePrompt(prompt);
        Objects.requireNonNull(options, "Options cannot be null");
        
        logger.debug("Executing Claude Code with prompt length: {}, options: {}", 
                    prompt.length(), options);

        List<String> command = buildCommand(prompt, options);
        ProcessBuilder pb = new ProcessBuilder(command);
        
        // Add environment variables to subprocess
        Map<String, String> env = pb.environment();
        env.putAll(baseEnvironment);
        env.putAll(options.getAdditionalEnv());
        
        // CRITICAL: Redirect stderr to stdout to prevent buffer deadlock
        pb.redirectErrorStream(true);
        
        try {
            Process process = pb.start();
            
            // CRITICAL: Close stdin immediately so CLI doesn't wait for input
            process.getOutputStream().close();
            
            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("Claude output: {}", line);
                }
            }
            
            // Wait for process to complete with timeout
            boolean finished = process.waitFor(
                options.getTimeout().toMillis(), 
                TimeUnit.MILLISECONDS
            );
            
            if (!finished) {
                logger.error("Claude Code command timed out after {}", options.getTimeout());
                process.destroyForcibly();
                throw new TimeoutException("Claude Code execution timed out after " + options.getTimeout());
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String outputStr = output.toString();
                logger.error("Claude Code failed with exit code: {}, output: {}", exitCode, outputStr);
                throw new ClaudeCodeExecutionException(
                    "Claude Code failed with exit code: " + exitCode,
                    exitCode,
                    outputStr
                );
            }
            
            String result = output.toString();
            logger.info("Claude Code executed successfully, output length: {}", result.length());
            return result;
            
        } catch (IOException e) {
            logger.error("Failed to execute Claude Code", e);
            throw new ClaudeCodeExecutionException("Failed to execute Claude Code", e);
        } catch (InterruptedException e) {
            logger.error("Claude Code execution interrupted", e);
            Thread.currentThread().interrupt();
            throw new ClaudeCodeExecutionException("Claude Code execution interrupted", e);
        } catch (TimeoutException e) {
            throw new ClaudeCodeExecutionException("Claude Code execution timed out", e);
        }
    }

    @Override
    public CompletableFuture<String> executeAsync(String prompt) {
        return executeAsync(prompt, ClaudeCodeOptions.defaults());
    }

    @Override
    public CompletableFuture<String> executeAsync(String prompt, ClaudeCodeOptions options) {
        validatePrompt(prompt);
        Objects.requireNonNull(options, "Options cannot be null");
        
        return CompletableFuture.supplyAsync(() -> execute(prompt, options));
    }

    @Override
    public Stream<String> executeStreaming(String prompt) {
        return executeStreaming(prompt, ClaudeCodeOptions.defaults());
    }

    @Override
    public Stream<String> executeStreaming(String prompt, ClaudeCodeOptions options) {
        validatePrompt(prompt);
        Objects.requireNonNull(options, "Options cannot be null");
        
        logger.debug("Starting streaming execution with prompt length: {}, options: {}", 
                    prompt.length(), options);

        List<String> command = buildCommand(prompt, options);
        ProcessBuilder pb = new ProcessBuilder(command);
        
        // Add environment variables to subprocess
        Map<String, String> env = pb.environment();
        env.putAll(baseEnvironment);
        env.putAll(options.getAdditionalEnv());
        
        // CRITICAL: Redirect stderr to stdout to prevent buffer deadlock
        pb.redirectErrorStream(true);
        
        try {
            Process process = pb.start();
            
            // CRITICAL: Close stdin immediately so CLI doesn't wait for input
            process.getOutputStream().close();
            
            // Create streaming handle for resource management
            StreamingProcessHandle handle = new StreamingProcessHandle(process, options.getTimeout());
            
            // Create stream that reads lines as they arrive
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            
            // Return stream with proper cleanup handlers
            return reader.lines()
                .onClose(() -> {
                    logger.debug("Stream closed, cleaning up resources");
                    handle.close();
                    try {
                        reader.close();
                    } catch (IOException e) {
                        logger.warn("Error closing reader", e);
                    }
                });
            
        } catch (IOException e) {
            logger.error("Failed to start streaming execution", e);
            throw new ClaudeCodeExecutionException("Failed to start Claude Code streaming", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            // Check if CLI path environment variable is set
            if (claudePath == null || claudePath.isEmpty()) {
                logger.warn("CLAUDE_CLI_PATH not set");
                return false;
            }
            
            // Check if CLI executable exists
            Path cliPath = Paths.get(claudePath);
            if (!Files.exists(cliPath)) {
                logger.warn("Claude CLI executable not found at: {}", claudePath);
                return false;
            }
            
            // Check if API key is set
            String apiKey = baseEnvironment.get("ANTHROPIC_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                logger.warn("ANTHROPIC_API_KEY not set");
                return false;
            }
            
            logger.debug("Claude Code CLI is available");
            return true;
            
        } catch (Exception e) {
            logger.error("Error checking Claude Code availability", e);
            return false;
        }
    }

    @Override
    public String getVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder(claudePath, "--version");
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            process.getOutputStream().close();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            
            if (process.exitValue() == 0) {
                return output.toString().trim();
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("Failed to get Claude Code version", e);
            return null;
        }
    }

    /**
     * Build the command line arguments for Claude Code CLI.
     */
    private List<String> buildCommand(String prompt, ClaudeCodeOptions options) {
        List<String> command = new ArrayList<>();
        command.add(claudePath);
        command.add("-p");
        command.add(prompt);
        
        if (options.isDangerouslySkipPermissions()) {
            command.add("--dangerously-skip-permissions");
        }
        
        if (options.getModel() != null && !options.getModel().isEmpty()) {
            command.add("--model");
            command.add(options.getModel());
        }
        
        return command;
    }

    /**
     * Build base environment variables from system environment.
     */
    private Map<String, String> buildBaseEnvironment() {
        String apiKey = getRequiredEnv("ANTHROPIC_API_KEY");
        return buildEnvironment(apiKey);
    }

    /**
     * Build environment variables with explicit API key.
     */
    private Map<String, String> buildEnvironment(String apiKey) {
        Map<String, String> env = new HashMap<>();
        
        // CRITICAL: Pass API key to subprocess
        env.put("ANTHROPIC_API_KEY", apiKey);
        
        // Pass HOME directory (needed for .claude.json)
        String home = System.getenv("HOME");
        if (home != null && !home.isEmpty()) {
            env.put("HOME", home);
        }
        
        // Pass NODE_EXTRA_CA_CERTS if set (for Cloud Foundry SSL)
        String nodeCaCerts = System.getenv("NODE_EXTRA_CA_CERTS");
        if (nodeCaCerts != null && !nodeCaCerts.isEmpty()) {
            env.put("NODE_EXTRA_CA_CERTS", nodeCaCerts);
        }
        
        return env;
    }

    /**
     * Get a required environment variable or throw exception.
     */
    private String getRequiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(
                String.format("%s environment variable is not set", name)
            );
        }
        return value;
    }

    /**
     * Validate prompt is not null or empty.
     */
    private void validatePrompt(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be null or empty");
        }
    }

    /**
     * Mask sensitive path information for logging.
     */
    private String maskPath(String path) {
        if (path == null) {
            return "null";
        }
        // Keep only the last part of the path for security
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return ".../" + path.substring(lastSlash + 1);
        }
        return path;
    }

    /**
     * Inner class to manage streaming process lifecycle and timeout enforcement.
     * <p>
     * This class ensures that:
     * </p>
     * <ul>
     *   <li>Process is terminated when no longer needed</li>
     *   <li>Timeout is enforced by forcibly destroying the process</li>
     *   <li>Resources are cleaned up even if stream is not properly closed</li>
     * </ul>
     */
    private static class StreamingProcessHandle implements AutoCloseable {
        private final Process process;
        private final ScheduledFuture<?> timeoutTask;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        /**
         * Creates a new streaming process handle with timeout enforcement.
         *
         * @param process the process to manage
         * @param timeout the maximum duration before forcibly terminating the process
         */
        public StreamingProcessHandle(Process process, java.time.Duration timeout) {
            this.process = process;
            
            // Schedule timeout task to forcibly destroy process if it runs too long
            this.timeoutTask = timeoutExecutor.schedule(() -> {
                if (process.isAlive()) {
                    logger.warn("Streaming process timed out after {}, forcibly destroying", timeout);
                    process.destroyForcibly();
                }
            }, timeout.toMillis(), TimeUnit.MILLISECONDS);
            
            logger.debug("Created streaming process handle with timeout: {}", timeout);
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                logger.debug("Closing streaming process handle");
                
                // Cancel timeout task
                timeoutTask.cancel(false);
                
                // Destroy process if still alive
                if (process.isAlive()) {
                    logger.debug("Process still alive, destroying gracefully");
                    process.destroy();
                    
                    // Give it a moment to terminate gracefully
                    try {
                        boolean terminated = process.waitFor(1, TimeUnit.SECONDS);
                        if (!terminated) {
                            logger.warn("Process did not terminate gracefully, forcing");
                            process.destroyForcibly();
                        }
                    } catch (InterruptedException e) {
                        logger.warn("Interrupted while waiting for process termination", e);
                        Thread.currentThread().interrupt();
                        process.destroyForcibly();
                    }
                } else {
                    logger.debug("Process already terminated with exit code: {}", process.exitValue());
                }
            }
        }
    }
}
