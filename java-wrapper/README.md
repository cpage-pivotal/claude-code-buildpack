# Claude Code Cloud Foundry Java Wrapper

A Java library for seamless integration of Claude Code CLI in Cloud Foundry applications. This wrapper provides a clean, type-safe API for invoking Claude Code commands from Java applications with support for synchronous, asynchronous, and streaming execution modes.

## Features

- ✅ **Simple API** - Easy-to-use interface for Claude Code CLI execution
- ✅ **Multiple Execution Modes** - Synchronous, asynchronous, and streaming support
- ✅ **Spring Boot Auto-Configuration** - Zero-configuration setup for Spring Boot applications
- ✅ **REST API Controller** - Pre-built endpoints for web applications
- ✅ **Best Practices** - Follows Cloud Foundry and ProcessBuilder best practices
- ✅ **Comprehensive Error Handling** - Detailed exception information and logging
- ✅ **Production Ready** - Timeouts, resource cleanup, and security considerations
- ✅ **OpenAI-Compatible LLMs** - Use GPT-4, Ollama, vLLM, or any OpenAI-compatible endpoint

## Requirements

- Java 17 or higher
- Claude Code CLI installed via the buildpack (automatically handled in Cloud Foundry)
- `CLAUDE_CLI_PATH` environment variable (set by buildpack)
- Authentication credentials (one of the following):
  - `ANTHROPIC_API_KEY` environment variable
  - `CLAUDE_CODE_OAUTH_TOKEN` environment variable

## Installation

### Maven

Add the GCP Artifact Registry repository and dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>gcp-maven-public</id>
        <name>GCP Artifact Registry - Public Maven Repository</name>
        <url>https://us-central1-maven.pkg.dev/cf-mcp/maven-public</url>
        <releases>
            <enabled>true</enabled>
        </releases>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>org.tanzu.claudecode</groupId>
        <artifactId>claude-code-cf-wrapper</artifactId>
        <version>1.1.1</version>
    </dependency>
</dependencies>
```

**Note:** This is a public repository - no authentication required!

### Gradle

Add the repository and dependency to your `build.gradle`:

```gradle
repositories {
    maven {
        url = uri("https://us-central1-maven.pkg.dev/cf-mcp/maven-public")
    }
}

dependencies {
    implementation 'org.tanzu.claudecode:claude-code-cf-wrapper:1.1.1'
}
```

## Quick Start

### Basic Usage

```java
import io.github.claudecode.cf.ClaudeCodeExecutor;
import io.github.claudecode.cf.ClaudeCodeExecutorImpl;

public class Example {
    public static void main(String[] args) {
        // Create executor (uses environment variables)
        ClaudeCodeExecutor executor = new ClaudeCodeExecutorImpl();
        
        // Execute a prompt
        String result = executor.execute("Analyze this code for potential bugs");
        System.out.println(result);
    }
}
```

### With Options

```java
import io.github.claudecode.cf.ClaudeCodeOptions;
import java.time.Duration;

ClaudeCodeOptions options = ClaudeCodeOptions.builder()
    .timeout(Duration.ofMinutes(5))
    .model("opus")
    .dangerouslySkipPermissions(true)
    .build();

String result = executor.execute("Generate unit tests", options);
```

### Asynchronous Execution

```java
import java.util.concurrent.CompletableFuture;

CompletableFuture<String> future = executor.executeAsync("Refactor this function");

future.thenAccept(result -> {
    System.out.println("Result: " + result);
}).exceptionally(error -> {
    System.err.println("Error: " + error.getMessage());
    return null;
});
```

### Streaming Output

```java
import java.util.stream.Stream;

try (Stream<String> lines = executor.executeStreaming("Explain this algorithm")) {
    lines.forEach(System.out::println);
}
```

## Spring Boot Integration

### Auto-Configuration

The library provides automatic Spring Boot configuration. Simply add it to your classpath:

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### Dependency Injection

Inject `ClaudeCodeExecutor` anywhere in your application:

```java
@Service
public class CodeAnalysisService {
    private final ClaudeCodeExecutor executor;
    
    public CodeAnalysisService(ClaudeCodeExecutor executor) {
        this.executor = executor;
    }
    
    public String analyzeCode(String code) {
        return executor.execute("Analyze: " + code);
    }
}
```

### Configuration Properties

Configure in `application.yml`:

```yaml
claude-code:
  enabled: true
  controller-enabled: true  # Enable REST API endpoints
```

Or `application.properties`:

```properties
claude-code.enabled=true
claude-code.controller-enabled=true
```

### OpenAI-Compatible LLM Configuration

The wrapper supports OpenAI-compatible models (GPT-4, Ollama, vLLM, Tanzu Platform GenAI, etc.) as an alternative to Anthropic Claude.

#### Option 1: Automatic GenAI Service Detection (Recommended for Cloud Foundry)

When you bind a GenAI service to your application, the wrapper automatically detects and configures it:

```bash
# Bind a GenAI service
cf bind-service my-app chat-llm
cf restage my-app
```

**Requirements:**
- Add `java-cfenv-boot` dependency (for VCAP_SERVICES processing)
- Bind a service tagged with `genai` and having `chat` capability

```xml
<dependency>
    <groupId>io.pivotal.cfenv</groupId>
    <artifactId>java-cfenv-boot</artifactId>
    <version>3.1.5</version>
</dependency>
```

**How it works:**
1. `ClaudeCodeGenAICfEnvProcessor` detects bound GenAI services automatically
2. Reads `api_base`, `api_key`, and `model_name` from service credentials
3. Configures `claude-code.openai.*` properties automatically
4. Buildpack installs LiteLLM proxy during staging
5. Runtime routes Claude CLI through the proxy to your GenAI service

**No configuration files needed!** Just bind the service and deploy.

#### Option 2: Manual Configuration

For non-Cloud Foundry environments or explicit configuration:

```yaml
claude-code:
  enabled: true
  use-openai-provider: true
  openai:
    base-url: https://api.openai.com/v1
    api-key: ${OPENAI_API_KEY}
    model: gpt-4o
    proxy-port: 4000  # Optional, default: 4000
```

The wrapper automatically:
1. Detects OpenAI provider configuration
2. Sets up environment variables for the LiteLLM proxy
3. Routes Claude CLI requests through the translation proxy

#### Checking Which Provider is Active

```java
@Service
public class MyService {
    @Autowired
    private ClaudeCodeExecutor executor;

    public void checkProvider() {
        if (executor instanceof ClaudeCodeExecutorImpl impl) {
            if (impl.isUsingOpenAiProvider()) {
                var config = impl.getOpenAiConfig();
                System.out.println("Using OpenAI model: " + config.getModel());
                System.out.println("Base URL: " + config.getBaseUrl());
            } else {
                System.out.println("Using Anthropic Claude");
            }
        }
    }
}
```

#### Supported Providers
#### Supported Providers

| Provider | Base URL | Configuration |
|----------|----------|---------------|
| **Tanzu Platform GenAI** | Auto-detected via service binding | `cf bind-service my-app chat-llm` |
| **OpenAI** | `https://api.openai.com/v1` | Manual config or service binding |
| **Azure OpenAI** | `https://{resource}.openai.azure.com` | Manual config |
| **Ollama** | `http://localhost:11434/v1` | Manual config |
| **vLLM** | `http://localhost:8000/v1` | Manual config |
| **Together AI** | `https://api.together.xyz/v1` | Manual config |

## REST API Endpoints

When Spring Boot auto-configuration is enabled, the following endpoints are automatically available:

### POST /api/claude/execute

Synchronous execution endpoint.

**Request:**
```json
{
  "prompt": "Analyze this code for bugs",
  "model": "sonnet",
  "timeoutSeconds": 180
}
```

**Response:**
```json
{
  "success": true,
  "result": "Analysis results here...",
  "error": null
}
```

### POST /api/claude/execute-async

Asynchronous execution endpoint.

**Request:** Same as `/execute`

**Response:** Same as `/execute` (but returns immediately)

### POST /api/claude/stream

Server-Sent Events streaming endpoint.

**Request:** Same as `/execute`

**Response:** Text stream (content-type: text/event-stream)

### GET /api/claude/health

Health check endpoint.

**Response:**
```json
{
  "status": "UP",
  "available": true,
  "version": "2.0.50"
}
```

## Advanced Usage

### Custom Executor Configuration

```java
// Use explicit configuration instead of environment variables
ClaudeCodeExecutor executor = new ClaudeCodeExecutorImpl(
    "/path/to/claude",
    "sk-ant-xxxxx"
);
```

### Check Availability

```java
if (executor.isAvailable()) {
    System.out.println("Claude Code CLI is ready");
    System.out.println("Version: " + executor.getVersion());
} else {
    System.err.println("Claude Code CLI is not available");
}
```

### Custom Environment Variables

```java
ClaudeCodeOptions options = ClaudeCodeOptions.builder()
    .env("CUSTOM_VAR", "value")
    .build();

String result = executor.execute("prompt", options);
```

## Error Handling

### Exception Handling

```java
import io.github.claudecode.cf.ClaudeCodeExecutionException;

try {
    String result = executor.execute("prompt");
} catch (ClaudeCodeExecutionException e) {
    System.err.println("Execution failed: " + e.getMessage());
    
    if (e.hasExitCode()) {
        System.err.println("Exit code: " + e.getExitCode());
    }
    
    if (e.hasStderr()) {
        System.err.println("Error output: " + e.getStderr());
    }
} catch (IllegalArgumentException e) {
    System.err.println("Invalid arguments: " + e.getMessage());
}
```

### Timeout Handling

```java
import java.util.concurrent.TimeoutException;

try {
    ClaudeCodeOptions options = ClaudeCodeOptions.builder()
        .timeout(Duration.ofSeconds(30))
        .build();
    
    String result = executor.execute("prompt", options);
} catch (TimeoutException e) {
    System.err.println("Command timed out");
}
```

## Cloud Foundry Deployment

### Application Setup

1. Add the library to your `pom.xml` or `build.gradle`
2. Create a `.claude-code-config.yml` configuration file
3. Set authentication credentials in your manifest (`ANTHROPIC_API_KEY` or `CLAUDE_CODE_OAUTH_TOKEN`)

### Configuration File Location

For Spring Boot applications, place `.claude-code-config.yml` in `src/main/resources/`:

- Spring Boot automatically packages it in the JAR at `BOOT-INF/classes/.claude-code-config.yml`
- The buildpack finds it automatically when the Java buildpack explodes the JAR during deployment
- No additional Maven/Gradle configuration needed - just standard Spring Boot resource handling

### manifest.yml Example

```yaml
applications:
- name: my-java-app
  buildpacks:
    - https://github.com/cloudfoundry/nodejs-buildpack
    - https://github.com/your-org/claude-code-buildpack
    - https://github.com/cloudfoundry/java-buildpack
  env:
    # Choose one authentication method:
    ANTHROPIC_API_KEY: sk-ant-xxxxxxxxxxxxx         # Option 1: API key
    # CLAUDE_CODE_OAUTH_TOKEN: <your-oauth-token>  # Option 2: OAuth token
```

### .claude-code-config.yml Example

Place this file in `src/main/resources/.claude-code-config.yml`:

```yaml
claudeCode:
  enabled: true
  logLevel: info
  model: sonnet
  
  settings:
    alwaysThinkingEnabled: true
  
  mcpServers:
    - name: filesystem
      type: stdio
      command: npx
      args:
        - "-y"
        - "@modelcontextprotocol/server-filesystem"
      env:
        ALLOWED_DIRECTORIES: "/home/vcap/app,/tmp"
```

Spring Boot will automatically package this in your JAR, and the buildpack will find it during deployment.

## Best Practices

### 1. Always Use Timeouts

```java
// Good
ClaudeCodeOptions options = ClaudeCodeOptions.builder()
    .timeout(Duration.ofMinutes(3))
    .build();

// Bad - uses default timeout, might be too long for your use case
executor.execute("prompt");
```

### 2. Handle Errors Gracefully

```java
// Good
try {
    return executor.execute(prompt);
} catch (ClaudeCodeExecutionException e) {
    logger.error("Claude Code execution failed", e);
    return "Error processing request";
}

// Bad - lets exceptions propagate to users
return executor.execute(prompt);
```

### 3. Use Async for Long Operations

```java
// Good - non-blocking
CompletableFuture<String> future = executor.executeAsync(prompt);

// Bad - blocks thread
String result = executor.execute(prompt);
```

### 4. Close Streams Properly

```java
// Good - try-with-resources
try (Stream<String> lines = executor.executeStreaming(prompt)) {
    lines.forEach(System.out::println);
}

// Bad - stream not closed
Stream<String> lines = executor.executeStreaming(prompt);
lines.forEach(System.out::println);
```

## Security Considerations

### API Key Management

- Never hardcode API keys in your code
- Use environment variables or Cloud Foundry user-provided services
- The library automatically masks sensitive information in logs

### Permission Model

- The library uses `--dangerously-skip-permissions` by default
- This is recommended for automated environments like Cloud Foundry
- Configure MCP servers to limit file system and network access

### Input Validation

```java
// Validate user input before passing to Claude
if (userPrompt.length() > 10000) {
    throw new IllegalArgumentException("Prompt too long");
}

String sanitized = sanitizeInput(userPrompt);
String result = executor.execute(sanitized);
```

## Performance Tips

### 1. Reuse Executor Instances

```java
// Good - create once, reuse
private final ClaudeCodeExecutor executor = new ClaudeCodeExecutorImpl();

public String process(String prompt) {
    return executor.execute(prompt);
}

// Bad - creates new instance each time
public String process(String prompt) {
    ClaudeCodeExecutor executor = new ClaudeCodeExecutorImpl();
    return executor.execute(prompt);
}
```

### 2. Use Appropriate Execution Mode

- **Synchronous**: Short operations, need immediate result
- **Asynchronous**: Long operations, can process result later
- **Streaming**: Very long operations, want progress updates

### 3. Configure Timeouts Appropriately

```java
// Quick operations
ClaudeCodeOptions quick = ClaudeCodeOptions.builder()
    .timeout(Duration.ofSeconds(30))
    .build();

// Complex operations
ClaudeCodeOptions complex = ClaudeCodeOptions.builder()
    .timeout(Duration.ofMinutes(5))
    .build();
```

## Troubleshooting

### "CLAUDE_CLI_PATH not set"

**Solution:** Ensure the Claude Code buildpack is properly configured in your Cloud Foundry deployment.

### "ANTHROPIC_API_KEY not set"

**Solution:** Set the API key in your manifest.yml or environment:

```bash
cf set-env my-app ANTHROPIC_API_KEY sk-ant-xxxxx
cf restage my-app
```

### Command Times Out

**Solution:** Increase the timeout or use async execution:

```java
ClaudeCodeOptions options = ClaudeCodeOptions.builder()
    .timeout(Duration.ofMinutes(10))
    .build();
```

### Process Hangs

**Solution:** This library already implements best practices to prevent hangs. If you still experience issues:
1. Check that you're using the latest version
2. Verify environment variables are properly set
3. Check Cloud Foundry logs for details

## Examples

See the `examples/` directory for complete sample applications:

- `simple-cli-app/` - Basic command-line application
- `spring-boot-rest-api/` - Spring Boot REST API
- `streaming-example/` - Real-time streaming with SSE

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

This project is licensed under the MIT License - see [LICENSE](LICENSE) file for details.

## Support

- **Issues**: https://github.com/your-org/claude-code-buildpack/issues
- **Documentation**: https://github.com/your-org/claude-code-buildpack
- **Buildpack**: See main [README.md](../README.md) for buildpack documentation

## Changelog

### 1.2.0 (2025-12-19)

- **NEW**: Automatic GenAI service detection via `ClaudeCodeGenAICfEnvProcessor`
- **NEW**: Zero-config OpenAI provider mode when GenAI service is bound
- **NEW**: Support for Tanzu Platform GenAI services
- Added OpenAI-compatible LLM provider support
- New configuration properties: `use-openai-provider`, `openai.base-url`, `openai.api-key`, `openai.model`
- New methods: `isUsingOpenAiProvider()`, `getOpenAiConfig()`
- LiteLLM proxy integration for API translation
- Support for GPT-4, Ollama, vLLM, and other OpenAI-compatible endpoints
- Handles both flat and nested credential structures from `VCAP_SERVICES`

### 1.1.1 (2025-12-01)

- Bug fixes and improvements
- OAuth token authentication support

### 1.0.0 (2025-11-23)

- Initial release
- Core executor implementation
- Spring Boot auto-configuration
- REST API controller
- Comprehensive documentation
