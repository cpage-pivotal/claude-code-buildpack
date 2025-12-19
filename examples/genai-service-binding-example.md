# Automatic GenAI Service Detection Example

This example demonstrates zero-configuration OpenAI provider mode using automatic GenAI service detection.

## Prerequisites

1. Java 17+
2. Maven or Gradle
3. Cloud Foundry CLI
4. A GenAI service instance (e.g., Tanzu Platform GenAI)

## Quick Start

### 1. Add Dependencies

**pom.xml:**
```xml
<dependencies>
    <!-- Claude Code Java Wrapper -->
    <dependency>
        <groupId>org.tanzu.claudecode</groupId>
        <artifactId>claude-code-cf-wrapper</artifactId>
        <version>1.2.0</version>
    </dependency>
    
    <!-- Cloud Foundry Environment Processing (enables automatic detection) -->
    <dependency>
        <groupId>io.pivotal.cfenv</groupId>
        <artifactId>java-cfenv-boot</artifactId>
        <version>3.5.0</version>
    </dependency>
    
    <!-- Spring Boot Starter Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

### 2. Create a Simple Service

**src/main/java/com/example/demo/ChatService.java:**
```java
package com.example.demo;

import org.springframework.stereotype.Service;
import org.tanzu.claudecode.cf.ClaudeCodeExecutor;
import org.tanzu.claudecode.cf.ClaudeCodeExecutorImpl;

@Service
public class ChatService {
    
    private final ClaudeCodeExecutor executor;
    
    public ChatService(ClaudeCodeExecutor executor) {
        this.executor = executor;
    }
    
    public String chat(String message) {
        // Log which provider is being used
        if (executor instanceof ClaudeCodeExecutorImpl impl) {
            if (impl.isUsingOpenAiProvider()) {
                var config = impl.getOpenAiConfig();
                System.out.println("Using OpenAI provider: " + config.getModel());
            } else {
                System.out.println("Using Anthropic Claude");
            }
        }
        
        // Execute the chat request
        return executor.execute(message);
    }
}
```

### 3. Create a REST Controller

**src/main/java/com/example/demo/ChatController.java:**
```java
package com.example.demo;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    private final ChatService chatService;
    
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }
    
    @PostMapping
    public String chat(@RequestBody String message) {
        return chatService.chat(message);
    }
}
```

### 4. Enable OpenAI Provider Mode in Buildpack

**src/main/resources/.claude-code-config.yml:**
```yaml
# Enable OpenAI provider mode so the buildpack installs LiteLLM during staging
use-openai-provider: true

# Optional: enable the built-in Claude Code REST API
claude-code:
  enabled: true
  controller-enabled: true
```

**Note:** This tells the buildpack to install LiteLLM during staging. The actual OpenAI credentials (base-url, api-key, model) are automatically configured at runtime from the bound GenAI service.

### 5. Deploy to Cloud Foundry

**manifest.yml:**
```yaml
applications:
  - name: genai-chat-demo
    memory: 1G
    path: target/demo-0.0.1-SNAPSHOT.jar
    buildpacks:
      - nodejs_buildpack
      - https://github.com/your-org/claude-code-buildpack
      - java_buildpack
    services:
      - chat-llm  # Your GenAI service instance
```

**Deploy:**
```bash
# Build the application
mvn clean package

# Push to Cloud Foundry
cf push

# That's it! No manual configuration needed.
```

## How It Works

1. **Buildpack Staging**: 
   - Detects `.claude-code-config.yml` with `use-openai-provider: true`
   - Installs Python and LiteLLM proxy components
   - Creates proxy startup script at `$DEPS_DIR/{index}/bin/start-litellm-proxy.sh`

2. **Service Binding Detection**: 
   - When the application starts, `ClaudeCodeGenAICfEnvProcessor` scans `VCAP_SERVICES`
   - Extracts `api_base`, `api_key`, and `model_name` from bound GenAI service credentials

3. **Automatic Configuration**: 
   - Sets `claude-code.use-openai-provider=true`
   - Configures `claude-code.openai.*` properties from service credentials

4. **Proxy Startup**: 
   - `ClaudeCodeExecutorImpl` starts the LiteLLM proxy on-demand with the configured credentials
   - Proxy translates Anthropic API calls to OpenAI format

5. **Runtime Routing**: 
   - Claude CLI requests are routed through LiteLLM to your GenAI service

## Verifying the Configuration

Check the staging logs to confirm LiteLLM was installed:

```bash
cf logs genai-chat-demo --recent | grep -i "openai\|litellm"
```

You should see:
```
OpenAI provider mode detected
-----> Installing OpenAI Provider Support (LiteLLM)
       Installing Python runtime...
       Installing LiteLLM proxy...
```

Check the runtime logs to confirm the OpenAI provider is active:

```bash
cf logs genai-chat-demo --recent | grep "OpenAI"
```

You should see:
```
Initialized ClaudeCodeExecutor with OpenAI-compatible provider: model=tanzu-gpt-oss-120b
```

## Testing

Test the endpoint:

```bash
curl -X POST https://genai-chat-demo.apps.example.com/api/chat \
  -H "Content-Type: text/plain" \
  -d "Explain quantum computing in simple terms"
```

## Supported GenAI Services

The automatic detection works with any service that:
- Has tag `genai` or label starting with `genai`
- AND meets one of these criteria:
  - Has `model_capabilities` including `chat`, OR
  - Has `api_base` in credentials (flat or nested `endpoint` structure)

This covers:
- **Tanzu Platform GenAI** - Automatically detected via nested `endpoint` structure
- **Custom OpenAI-compatible services** - Exposed via Cloud Foundry User-Provided Services
- **Third-party GenAI service brokers** - Any broker that follows the GenAI service conventions

**Note**: The `model_capabilities` field is optional. If your service has an `api_base` endpoint, it will be detected automatically.

## Alternative: Manual Configuration

If you prefer explicit configuration over automatic detection, see the main [Java Wrapper README](../java-wrapper/README.md) for manual configuration options.

## Troubleshooting

### LiteLLM not installed during staging

**Cause:** The `.claude-code-config.yml` file is missing or doesn't have `use-openai-provider: true`.

**Solution:** Ensure you have `src/main/resources/.claude-code-config.yml` with:
```yaml
use-openai-provider: true
```

**Why:** `VCAP_SERVICES` is not available during staging, so the buildpack can't detect service bindings at build time. The `use-openai-provider` flag tells the buildpack to install LiteLLM, and the actual credentials are configured at runtime from the bound service.

### Provider not detected at runtime

**Check:** Is `java-cfenv-boot` dependency included?

```bash
# Verify in your pom.xml or build.gradle
mvn dependency:tree | grep cfenv
```

### Wrong model being used

**Check:** The logs to see which model was detected:

```bash
cf logs genai-chat-demo --recent | grep "model="
```

The model name comes from `credentials.endpoint.name` or `credentials.model_name` in `VCAP_SERVICES`.

