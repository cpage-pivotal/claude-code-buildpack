package org.tanzu.claudecode.cf.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.tanzu.claudecode.cf.ClaudeCodeExecutor;
import org.tanzu.claudecode.cf.ClaudeCodeExecutorImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.stream.Collectors;

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
     * <p>
     * If OpenAI provider mode is configured ({@code claude-code.use-openai-provider=true}),
     * the executor will use a LiteLLM proxy to translate between Anthropic and OpenAI API formats.
     * </p>
     *
     * @param properties the configuration properties
     * @return a ClaudeCodeExecutor instance
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "claude-code", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ClaudeCodeExecutor claudeCodeExecutor(ClaudeCodeProperties properties) {
        logger.info("Configuring Claude Code CLI integration");
        
        // Check for OpenAI provider mode
        if (properties.isOpenaiProviderConfigured()) {
            // Try to discover actual model name from config URL if available
            discoverModelNameIfNeeded(properties);
            
            logger.info("Creating ClaudeCodeExecutor with OpenAI-compatible provider: model={}", 
                       properties.getOpenai().getModel());
            return new ClaudeCodeExecutorImpl(properties);
        }
        
        // Standard Anthropic provider mode
        String cliPath = properties.getCliPath();
        String apiKey = properties.getApiKey();
        
        if (cliPath != null && !cliPath.isEmpty() && apiKey != null && !apiKey.isEmpty()) {
            logger.info("Creating ClaudeCodeExecutor with explicit Anthropic configuration");
            return new ClaudeCodeExecutorImpl(cliPath, apiKey);
        } else {
            logger.info("Creating ClaudeCodeExecutor with environment variables");
            return new ClaudeCodeExecutorImpl();
        }
    }
    
    /**
     * Discovers the actual model name from the Tanzu GenAI config URL if available.
     * <p>
     * For Tanzu Platform GenAI services, the endpoint name (e.g., "tanzu-gpt-oss-120b-presidio-v1030-c3c82b2")
     * is different from the actual model name (e.g., "gpt-oss-120b"). This method queries the
     * config_url endpoint to discover the available model names.
     * </p>
     * <p>
     * See: <a href="https://techdocs.broadcom.com/us/en/vmware-tanzu/platform/ai-services/10-3/ai/how-to-guides-discover-models-and-send-openai-requests-to-them.html">
     * Tanzu AI Services - Discover models</a>
     * </p>
     *
     * @param properties the configuration properties to update with discovered model name
     */
    private void discoverModelNameIfNeeded(ClaudeCodeProperties properties) {
        String configUrl = properties.getOpenai().getConfigUrl();
        String apiKey = properties.getOpenai().getApiKey();
        String currentModel = properties.getOpenai().getModel();
        
        if (configUrl == null || configUrl.isEmpty()) {
            logger.debug("No config URL available for model discovery");
            return;
        }
        
        // Check if environment variable override is set
        String envModel = System.getenv("CLAUDE_CODE_OPENAI_MODEL");
        if (envModel != null && !envModel.isEmpty()) {
            logger.info("Using model name from CLAUDE_CODE_OPENAI_MODEL environment variable: {}", envModel);
            properties.getOpenai().setModel(envModel);
            return;
        }
        
        logger.info("Discovering model name from config URL: {}", configUrl);
        
        try {
            HttpURLConnection connection = (HttpURLConnection) new URI(configUrl).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                String responseBody;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    responseBody = reader.lines().collect(Collectors.joining());
                }
                
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(responseBody);
                
                // Extract the first advertised model with CHAT capability
                JsonNode advertisedModels = root.get("advertisedModels");
                if (advertisedModels != null && advertisedModels.isArray() && advertisedModels.size() > 0) {
                    for (JsonNode model : advertisedModels) {
                        String modelName = model.get("name").asText();
                        JsonNode capabilities = model.get("capabilities");
                        
                        // Check if this model has CHAT capability
                        boolean hasChat = false;
                        if (capabilities != null && capabilities.isArray()) {
                            for (JsonNode cap : capabilities) {
                                if ("CHAT".equalsIgnoreCase(cap.asText())) {
                                    hasChat = true;
                                    break;
                                }
                            }
                        }
                        
                        if (hasChat && modelName != null && !modelName.isEmpty()) {
                            logger.info("Discovered chat model from config URL: {} (was: {})", 
                                       modelName, currentModel);
                            properties.getOpenai().setModel(modelName);
                            return;
                        }
                    }
                    
                    // If no CHAT model found, use the first model
                    String firstModel = advertisedModels.get(0).get("name").asText();
                    if (firstModel != null && !firstModel.isEmpty()) {
                        logger.info("Using first advertised model from config URL: {} (was: {})", 
                                   firstModel, currentModel);
                        properties.getOpenai().setModel(firstModel);
                        return;
                    }
                }
                
                logger.warn("No models found in config URL response, keeping current model: {}", currentModel);
            } else {
                logger.warn("Failed to fetch config URL (status {}), keeping current model: {}", 
                           responseCode, currentModel);
            }
        } catch (Exception e) {
            logger.warn("Error discovering model name from config URL: {}. Keeping current model: {}", 
                       e.getMessage(), currentModel);
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

