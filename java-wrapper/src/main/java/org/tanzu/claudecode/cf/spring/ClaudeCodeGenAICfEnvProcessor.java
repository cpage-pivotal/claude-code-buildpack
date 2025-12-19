package org.tanzu.claudecode.cf.spring;

import io.pivotal.cfenv.core.CfCredentials;
import io.pivotal.cfenv.core.CfService;
import io.pivotal.cfenv.spring.boot.CfEnvProcessor;
import io.pivotal.cfenv.spring.boot.CfEnvProcessorProperties;

import java.util.ArrayList;
import java.util.Map;

/**
 * Automatically configures Claude Code to use a bound GenAI service as an OpenAI-compatible provider.
 * <p>
 * When a GenAI service is bound to the application, this processor:
 * </p>
 * <ul>
 *   <li>Enables OpenAI provider mode in the Claude Code buildpack</li>
 *   <li>Configures the LiteLLM proxy to use the bound service's endpoint</li>
 *   <li>Maps the service credentials to claude-code.openai.* properties</li>
 * </ul>
 * <p>
 * This allows seamless integration with Tanzu Platform GenAI services or any
 * OpenAI-compatible endpoint exposed via VCAP_SERVICES.
 * </p>
 * <h2>Service Detection</h2>
 * <p>
 * The processor accepts services that meet the following criteria:
 * </p>
 * <ul>
 *   <li>Tagged with "genai" or labeled starting with "genai"</li>
 *   <li>Have "chat" in model_capabilities (if present), OR</li>
 *   <li>Have an api_base in credentials (flat or nested endpoint structure)</li>
 * </ul>
 * <h2>Usage</h2>
 * <p>
 * Simply bind a GenAI service to your application and the processor will automatically
 * configure Claude Code to use it:
 * </p>
 * <pre>
 * cf bind-service my-app chat-llm
 * cf restage my-app
 * </pre>
 * <p>
 * No additional configuration is required. The processor reads credentials from VCAP_SERVICES
 * and configures both the buildpack (for LiteLLM installation) and the Java wrapper
 * (for runtime execution).
 * </p>
 * <h2>Supported Credential Structures</h2>
 * <p>
 * The processor handles multiple credential formats:
 * </p>
 * <pre>
 * // Flat structure
 * {
 *   "credentials": {
 *     "api_base": "https://...",
 *     "api_key": "...",
 *     "model_name": "..."
 *   }
 * }
 * 
 * // Nested endpoint structure (Tanzu Platform GenAI)
 * {
 *   "credentials": {
 *     "endpoint": {
 *       "api_base": "https://...",
 *       "api_key": "...",
 *       "name": "..."
 *     }
 *   }
 * }
 * 
 * // With model_capabilities (optional)
 * {
 *   "credentials": {
 *     "model_capabilities": ["chat"],
 *     "api_base": "https://...",
 *     "api_key": "...",
 *     "model_name": "..."
 *   }
 * }
 * </pre>
 *
 * @author Claude Code Buildpack Team
 * @since 1.2.0
 * @see ClaudeCodeProperties
 * @see ClaudeCodeAutoConfiguration
 */
public class ClaudeCodeGenAICfEnvProcessor implements CfEnvProcessor {

    @Override
    public boolean accept(CfService service) {
        // Accept services tagged with "genai" or labeled starting with "genai"
        boolean isGenAIService = service.existsByTagIgnoreCase("genai") 
            || service.existsByLabelStartsWith("genai");
        
        if (!isGenAIService) {
            return false;
        }
        
        // Check if model_capabilities exists and contains "chat"
        @SuppressWarnings("unchecked")
        ArrayList<String> modelCapabilities = (ArrayList<String>) 
            service.getCredentials().getMap().get("model_capabilities");
        
        if (modelCapabilities != null) {
            // If model_capabilities is present, it must contain "chat"
            return modelCapabilities.contains("chat");
        }
        
        // If model_capabilities is not present, check if we have the required endpoint structure
        // This handles Tanzu Platform GenAI services that may not expose model_capabilities
        @SuppressWarnings("unchecked")
        Map<String, Object> endpoint = (Map<String, Object>) 
            service.getCredentials().getMap().get("endpoint");
        
        // Accept if we have an endpoint with api_base (indicates it's a chat-capable service)
        if (endpoint != null && endpoint.get("api_base") != null) {
            return true;
        }
        
        // Also accept if we have api_base directly in credentials (flat structure)
        return service.getCredentials().getString("api_base") != null;
    }

    @Override
    public void process(CfCredentials cfCredentials, Map<String, Object> properties) {
        // Enable OpenAI provider mode for Claude Code buildpack
        properties.put("claude-code.use-openai-provider", true);
        
        // Extract endpoint configuration from credentials
        // Handle both direct credentials and nested endpoint structure
        String apiBase = getApiBase(cfCredentials);
        String apiKey = getApiKey(cfCredentials);
        String modelName = getModelName(cfCredentials);
        
        if (apiBase != null) {
            properties.put("claude-code.openai.base-url", apiBase);
        }
        
        if (apiKey != null) {
            properties.put("claude-code.openai.api-key", apiKey);
        }
        
        if (modelName != null) {
            properties.put("claude-code.openai.model", modelName);
        }
    }

    @Override
    public CfEnvProcessorProperties getProperties() {
        return CfEnvProcessorProperties.builder()
                .propertyPrefixes("claude-code.openai")
                .serviceName("GenAI Chat (Claude Code)")
                .build();
    }
    
    /**
     * Extract API base URL from credentials, handling nested endpoint structure.
     * <p>
     * Tries the following paths in order:
     * </p>
     * <ol>
     *   <li>credentials.api_base (flat structure)</li>
     *   <li>credentials.endpoint.api_base (nested structure)</li>
     * </ol>
     *
     * @param credentials the Cloud Foundry service credentials
     * @return the API base URL, or null if not found
     */
    private String getApiBase(CfCredentials credentials) {
        // Try direct api_base first
        String apiBase = credentials.getString("api_base");
        if (apiBase != null) {
            return apiBase;
        }
        
        // Try nested endpoint.api_base
        @SuppressWarnings("unchecked")
        Map<String, Object> endpoint = (Map<String, Object>) 
            credentials.getMap().get("endpoint");
        if (endpoint != null) {
            return (String) endpoint.get("api_base");
        }
        
        return null;
    }
    
    /**
     * Extract API key from credentials, handling nested endpoint structure.
     * <p>
     * Tries the following paths in order:
     * </p>
     * <ol>
     *   <li>credentials.api_key (flat structure)</li>
     *   <li>credentials.endpoint.api_key (nested structure)</li>
     * </ol>
     *
     * @param credentials the Cloud Foundry service credentials
     * @return the API key, or null if not found
     */
    private String getApiKey(CfCredentials credentials) {
        // Try direct api_key first
        String apiKey = credentials.getString("api_key");
        if (apiKey != null) {
            return apiKey;
        }
        
        // Try nested endpoint.api_key
        @SuppressWarnings("unchecked")
        Map<String, Object> endpoint = (Map<String, Object>) 
            credentials.getMap().get("endpoint");
        if (endpoint != null) {
            return (String) endpoint.get("api_key");
        }
        
        return null;
    }
    
    /**
     * Extract model name from credentials, handling nested endpoint structure.
     * <p>
     * Tries the following paths in order:
     * </p>
     * <ol>
     *   <li>credentials.model_name (flat structure)</li>
     *   <li>credentials.endpoint.name (nested structure - Tanzu Platform)</li>
     *   <li>credentials.name (fallback)</li>
     * </ol>
     *
     * @param credentials the Cloud Foundry service credentials
     * @return the model name, or null if not found
     */
    private String getModelName(CfCredentials credentials) {
        // Try direct model_name first
        String modelName = credentials.getString("model_name");
        if (modelName != null) {
            return modelName;
        }
        
        // Try nested endpoint.name (Tanzu Platform GenAI format)
        @SuppressWarnings("unchecked")
        Map<String, Object> endpoint = (Map<String, Object>) 
            credentials.getMap().get("endpoint");
        if (endpoint != null) {
            String name = (String) endpoint.get("name");
            if (name != null) {
                return name;
            }
        }
        
        // Fall back to service name
        return credentials.getString("name");
    }
}

