package org.tanzu.claudecode.cf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the lifecycle of the LiteLLM proxy server for OpenAI provider mode.
 * <p>
 * The LiteLLM proxy translates Anthropic API calls (used by Claude CLI) to
 * OpenAI-compatible API format. This manager:
 * </p>
 * <ul>
 *   <li>Starts the proxy server on-demand when needed</li>
 *   <li>Ensures only one proxy instance is running</li>
 *   <li>Waits for the proxy to be ready before returning</li>
 *   <li>Handles cleanup on JVM shutdown</li>
 * </ul>
 *
 * @author Claude Code Buildpack Team
 * @since 1.2.0
 */
public class LiteLlmProxyManager {
    
    private static final Logger logger = LoggerFactory.getLogger(LiteLlmProxyManager.class);
    private static final AtomicReference<Process> proxyProcess = new AtomicReference<>();
    private static final AtomicBoolean isStarted = new AtomicBoolean(false);
    private static final int MAX_STARTUP_WAIT_SECONDS = 30;
    private static final int HEALTH_CHECK_INTERVAL_MS = 500;
    
    static {
        // Register shutdown hook to clean up proxy process
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Process process = proxyProcess.get();
            if (process != null && process.isAlive()) {
                logger.info("Shutting down LiteLLM proxy...");
                process.destroy();
                try {
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                }
            }
        }));
    }
    
    /**
     * Starts the LiteLLM proxy server if not already running.
     * <p>
     * This method is thread-safe and will only start one proxy instance even if
     * called concurrently. It waits for the proxy to be healthy before returning.
     * </p>
     *
     * @param environment environment variables to pass to the proxy (must include LITELLM_* vars)
     * @throws IOException if the proxy cannot be started or does not become healthy
     */
    public static void ensureProxyStarted(Map<String, String> environment) throws IOException {
        // Fast path: if already started and healthy, return immediately
        if (isStarted.get() && isProxyHealthy(environment)) {
            return;
        }
        
        synchronized (LiteLlmProxyManager.class) {
            // Double-check after acquiring lock
            if (isStarted.get() && isProxyHealthy(environment)) {
                return;
            }
            
            // If there's a process but it's not healthy, kill it
            Process existingProcess = proxyProcess.get();
            if (existingProcess != null && existingProcess.isAlive()) {
                logger.warn("Existing proxy process is not healthy, restarting...");
                existingProcess.destroy();
                try {
                    existingProcess.waitFor(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    existingProcess.destroyForcibly();
                }
            }
            
            startProxy(environment);
            waitForProxyHealth(environment);
            isStarted.set(true);
        }
    }
    
    /**
     * Starts the LiteLLM proxy process.
     */
    private static void startProxy(Map<String, String> environment) throws IOException {
        // Look for the startup script in DEPS_DIR
        String depsDir = System.getenv("DEPS_DIR");
        if (depsDir == null || depsDir.isEmpty()) {
            throw new IOException("DEPS_DIR environment variable is not set");
        }
        
        // Find the buildpack index (look for bin/start-litellm-proxy.sh in deps directories)
        Path startupScript = findLiteLlmStartupScript(depsDir);
        if (startupScript == null || !Files.exists(startupScript)) {
            throw new IOException("LiteLLM startup script not found. Ensure the buildpack installed LiteLLM support.");
        }
        
        logger.info("Starting LiteLLM proxy using script: {}", startupScript);
        logger.info("  OpenAI Base URL: {}", environment.get("LITELLM_OPENAI_BASE_URL"));
        logger.info("  OpenAI Model: {}", environment.get("LITELLM_OPENAI_MODEL"));
        logger.info("  Proxy Port: {}", environment.get("LITELLM_PORT"));
        
        // Build process with environment variables
        ProcessBuilder pb = new ProcessBuilder(startupScript.toString());
        pb.environment().putAll(environment);
        
        // Start the process
        Process process = pb.start();
        proxyProcess.set(process);
        
        // Log output in background threads
        startOutputLogger(process);
        
        logger.info("LiteLLM proxy process started with PID: {}", process.pid());
    }
    
    /**
     * Finds the LiteLLM startup script in the deps directory.
     */
    private static Path findLiteLlmStartupScript(String depsDir) {
        // Try each deps subdirectory (0, 1, 2, etc.)
        for (int i = 0; i < 10; i++) {
            Path script = Paths.get(depsDir, String.valueOf(i), "bin", "start-litellm-proxy.sh");
            if (Files.exists(script)) {
                return script;
            }
        }
        return null;
    }
    
    /**
     * Starts background threads to log proxy output.
     */
    private static void startOutputLogger(Process process) {
        Thread stdoutLogger = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[LiteLLM] {}", line);
                }
            } catch (IOException e) {
                logger.debug("LiteLLM stdout stream closed", e);
            }
        });
        stdoutLogger.setDaemon(true);
        stdoutLogger.setName("LiteLLM-stdout-logger");
        stdoutLogger.start();
        
        Thread stderrLogger = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.warn("[LiteLLM ERROR] {}", line);
                }
            } catch (IOException e) {
                logger.debug("LiteLLM stderr stream closed", e);
            }
        });
        stderrLogger.setDaemon(true);
        stderrLogger.setName("LiteLLM-stderr-logger");
        stderrLogger.start();
    }
    
    /**
     * Waits for the proxy to become healthy.
     */
    private static void waitForProxyHealth(Map<String, String> environment) throws IOException {
        int port = Integer.parseInt(environment.getOrDefault("LITELLM_PORT", "4000"));
        String healthUrl = "http://localhost:" + port + "/health";
        
        logger.info("Waiting for LiteLLM proxy to be ready at {}...", healthUrl);
        
        // Give the process a moment to actually start before we begin health checks
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during initial startup delay", e);
        }
        
        long startTime = System.currentTimeMillis();
        long maxWaitMs = MAX_STARTUP_WAIT_SECONDS * 1000L;
        int attempts = 0;
        
        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            Process process = proxyProcess.get();
            if (process == null || !process.isAlive()) {
                // Process died - check exit code
                int exitCode = process != null ? process.exitValue() : -1;
                throw new IOException("LiteLLM proxy process died during startup with exit code: " + exitCode);
            }
            
            attempts++;
            if (checkHealth(healthUrl)) {
                long elapsedMs = System.currentTimeMillis() - startTime;
                logger.info("LiteLLM proxy is ready! (took {}ms after {} health check attempts)", elapsedMs, attempts);
                return;
            }
            
            try {
                Thread.sleep(HEALTH_CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for proxy to start", e);
            }
        }
        
        throw new IOException("LiteLLM proxy failed to become healthy within " + 
                             MAX_STARTUP_WAIT_SECONDS + " seconds (tried " + attempts + " times)");
    }
    
    /**
     * Checks if the proxy is healthy.
     */
    private static boolean isProxyHealthy(Map<String, String> environment) {
        int port = Integer.parseInt(environment.getOrDefault("LITELLM_PORT", "4000"));
        String healthUrl = "http://localhost:" + port + "/health";
        return checkHealth(healthUrl);
    }
    
    /**
     * Performs a health check on the given URL.
     */
    private static boolean checkHealth(String healthUrl) {
        try {
            URI uri = URI.create(healthUrl);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            
            return responseCode == 200;
        } catch (IOException e) {
            // Expected during startup
            return false;
        }
    }
}

