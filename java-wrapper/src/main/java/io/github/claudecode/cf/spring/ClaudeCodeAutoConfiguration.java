package io.github.claudecode.cf.spring;

import io.github.claudecode.cf.ClaudeCodeExecutor;
import io.github.claudecode.cf.ClaudeCodeExecutorImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for Claude Code CLI integration.
 * <p>
 * This configuration automatically creates a {@link ClaudeCodeExecutor} bean
 * when the required environment variables are present and the feature is enabled.
 * </p>
 *
 * <h2>Configuration Properties</h2>
 * <p>
 * Configure Claude Code integration in your application.yml or application.properties:
 * </p>
 * <pre>
 * claude-code:
 *   enabled: true
 *   cli-path: ${CLAUDE_CLI_PATH}  # Optional, defaults to env var
 *   api-key: ${ANTHROPIC_API_KEY}  # Optional, defaults to env var
 * </pre>
 *
 * <h2>Usage</h2>
 * <p>
 * Simply add this library to your classpath and inject {@link ClaudeCodeExecutor}:
 * </p>
 * <pre>{@code
 * @Service
 * public class MyService {
 *     private final ClaudeCodeExecutor executor;
 *     
 *     public MyService(ClaudeCodeExecutor executor) {
 *         this.executor = executor;
 *     }
 *     
 *     public String analyze(String code) {
 *         return executor.execute("Analyze: " + code);
 *     }
 * }
 * }</pre>
 *
 * @author Claude Code Buildpack Team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnClass(ClaudeCodeExecutor.class)
@EnableConfigurationProperties(ClaudeCodeProperties.class)
public class ClaudeCodeAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeCodeAutoConfiguration.class);

    /**
     * Constructs a new ClaudeCodeAutoConfiguration instance.
     */
    public ClaudeCodeAutoConfiguration() {
    }

    /**
     * Create a ClaudeCodeExecutor bean if not already present.
     * <p>
     * This bean will be created automatically when:
     * </p>
     * <ul>
     *   <li>claude-code.enabled is true (or not set)</li>
     *   <li>Required environment variables are present</li>
     *   <li>No custom ClaudeCodeExecutor bean is defined</li>
     * </ul>
     *
     * @param properties the configuration properties
     * @return a ClaudeCodeExecutor instance
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "claude-code", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ClaudeCodeExecutor claudeCodeExecutor(ClaudeCodeProperties properties) {
        logger.info("Configuring Claude Code CLI integration");
        
        String cliPath = properties.getCliPath();
        String apiKey = properties.getApiKey();
        
        if (cliPath != null && !cliPath.isEmpty() && apiKey != null && !apiKey.isEmpty()) {
            logger.info("Creating ClaudeCodeExecutor with explicit configuration");
            return new ClaudeCodeExecutorImpl(cliPath, apiKey);
        } else {
            logger.info("Creating ClaudeCodeExecutor with environment variables");
            return new ClaudeCodeExecutorImpl();
        }
    }

    /**
     * Create a ClaudeCodeController bean if webflux is available.
     *
     * @param executor the Claude Code executor
     * @return a ClaudeCodeController instance
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.web.reactive.config.EnableWebFlux")
    @ConditionalOnProperty(prefix = "claude-code", name = "controller-enabled", havingValue = "true", matchIfMissing = true)
    public ClaudeCodeController claudeCodeController(ClaudeCodeExecutor executor) {
        logger.info("Creating ClaudeCodeController for REST API endpoints");
        return new ClaudeCodeController(executor);
    }
}
