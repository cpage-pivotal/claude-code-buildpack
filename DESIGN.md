# Cloud Foundry Claude Code CLI Buildpack - Design & Implementation Plan

## Executive Summary

This document outlines the design and implementation plan for a Cloud Foundry buildpack that bundles the Claude Code CLI into Java application containers. The buildpack will enable Java applications to invoke Claude Code CLI commands and stream their output in real-time.

## Project Overview

### Goals
1. Bundle Claude Code CLI into Cloud Foundry containers during staging
2. Configure Anthropic API authentication via application manifest
3. Support MCP (Model Context Protocol) server configuration
4. Enable Java applications to invoke and stream CLI output
5. Maintain compatibility with existing Cloud Foundry Java buildpacks

### Non-Goals
- Replace the standard Java buildpack (this will be a supply buildpack)
- Provide a web UI for Claude Code interaction
- Manage Claude Code versioning post-deployment

---

## Architecture Overview

### Buildpack Type
**Supply Buildpack** - This buildpack will act as a non-final buildpack that supplies dependencies to the final Java buildpack.

### Component Stack
```
┌─────────────────────────────────────────┐
│   Java Application                      │
│   - ProcessBuilder to invoke claude     │
│   - Stream handling for real-time output│
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│   Claude Code CLI                        │
│   - Installed at /home/vcap/deps/X/bin  │
│   - Configured with API key & MCP       │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│   Node.js Runtime                        │
│   - Required for Claude CLI (npm)       │
└─────────────────────────────────────────┘
```

---

## Technical Specifications

### 1. Buildpack Structure

```
claude-code-buildpack/
├── bin/
│   ├── detect              # Detection script
│   ├── supply              # Installation script
│   └── finalize            # (Optional) Configuration finalization
├── lib/
│   ├── installer.sh        # Claude Code installation logic
│   ├── mcp_configurator.sh # MCP server configuration
│   └── validator.sh        # Validation utilities
├── config/
│   └── buildpack.yml       # Buildpack configuration
├── manifest.yml            # Dependency manifest
└── README.md
```

### 2. Detection Logic (`bin/detect`)

The buildpack should detect when:
- The application has `claude-code-enabled: true` in manifest.yml, OR
- The application includes a `.claude-code-config.yml` file in the root, OR
- Environment variable `CLAUDE_CODE_ENABLED=true` is set

**Exit Codes:**
- 0: Apply this buildpack
- 1: Skip this buildpack

**Output:**
- Buildpack name and version to stdout

### 3. Supply Phase (`bin/supply`)

#### Responsibilities:
1. **Install Node.js** (if not already present)
   - Use Cloud Foundry's Node.js binaries
   - Install to `/home/vcap/deps/{INDEX}/node`

2. **Install Claude Code CLI**
   - Execute: `npm install -g @anthropic-ai/claude-code`
   - Install location: `/home/vcap/deps/{INDEX}/bin/claude`
   - Create symlink to make accessible in PATH

3. **Configure Authentication**
   - Read `ANTHROPIC_API_KEY` from environment or manifest
   - Store in `/home/vcap/deps/{INDEX}/.profile.d/claude-code-env.sh`
   - Set up environment variables for runtime

4. **Configure MCP Servers**
   - Parse MCP configuration from manifest
   - Generate `.claude.json` configuration file
   - Place in `/home/vcap/app/.claude.json`

5. **Create Configuration Files**
   - Generate `config.yml` in `/home/vcap/deps/{INDEX}/config.yml`
   - Include paths, binaries, and environment setup

#### Arguments:
- `BUILD_DIR`: Application directory
- `CACHE_DIR`: Cache directory for buildpack assets
- `DEPS_DIR`: Dependencies directory
- `INDEX`: Buildpack index in the chain

---

## Configuration Management

### 1. Application Manifest Configuration

```yaml
---
applications:
- name: my-java-app
  buildpacks:
    - https://github.com/cloudfoundry/nodejs-buildpack
    - https://github.com/your-org/claude-code-buildpack
    - https://github.com/cloudfoundry/java-buildpack
  env:
    ANTHROPIC_API_KEY: sk-ant-xxxxxxxxxxxxx
    CLAUDE_CODE_ENABLED: true
    CLAUDE_CODE_VERSION: latest  # Optional: specify version
  claude-code-config:
    mcp-servers:
      - name: filesystem
        type: stdio
        command: npx
        args:
          - "-y"
          - "@modelcontextprotocol/server-filesystem"
        env:
          ALLOWED_DIRECTORIES: "/home/vcap/app,/tmp"
      - name: github
        type: stdio
        command: npx
        args:
          - "-y"
          - "@modelcontextprotocol/server-github"
        env:
          GITHUB_PERSONAL_ACCESS_TOKEN: ghp_xxxxxxxxxxxxx
```

### 2. Alternative: Standalone Configuration File

`.claude-code-config.yml` in application root:

```yaml
claudeCode:
  enabled: true
  version: "2.0.50"  # Optional: pin to specific version
  
  authentication:
    # Will fall back to ANTHROPIC_API_KEY env var if not specified
    apiKey: ${ANTHROPIC_API_KEY}
  
  mcpServers:
    - name: filesystem
      type: stdio
      command: npx
      args:
        - "-y"
        - "@modelcontextprotocol/server-filesystem"
      env:
        ALLOWED_DIRECTORIES: "/home/vcap/app,/tmp"
    
    - name: postgres
      type: stdio
      command: npx
      args:
        - "-y"
        - "@modelcontextprotocol/server-postgres"
      env:
        POSTGRES_CONNECTION_STRING: ${POSTGRES_URL}
    
    - name: sequential-thinking
      type: stdio
      command: npx
      args:
        - "-y"
        - "@modelcontextprotocol/server-sequential-thinking"
```

### 3. Environment Variables

**Required:**
- `ANTHROPIC_API_KEY`: Authentication token for Claude API

**Optional:**
- `CLAUDE_CODE_VERSION`: Specific version to install (default: latest)
- `CLAUDE_CODE_ENABLED`: Enable/disable buildpack (default: false)
- `CLAUDE_CODE_LOG_LEVEL`: CLI log level (default: info)
- `CLAUDE_CODE_MODEL`: Default model to use (default: sonnet)
- `NODE_VERSION`: Node.js version for CLI (default: latest LTS)

---

## Java Integration

### 1. Claude Code Wrapper Library

Create a Java library to simplify Claude Code invocation:

```java
// Interface
public interface ClaudeCodeExecutor {
    Stream<String> executeStreaming(String prompt);
    String execute(String prompt);
    CompletableFuture<String> executeAsync(String prompt);
}

// Implementation
public class ClaudeCodeExecutorImpl implements ClaudeCodeExecutor {
    private final String claudePath;
    private final Map<String, String> environment;
    
    public ClaudeCodeExecutorImpl() {
        this.claudePath = System.getenv("CLAUDE_CLI_PATH");
        this.environment = buildEnvironment();
    }
    
    @Override
    public Stream<String> executeStreaming(String prompt) {
        ProcessBuilder pb = new ProcessBuilder(
            claudePath,
            "-p", prompt,
            "--dangerously-skip-permissions",
            "--output-format", "text"
        );
        
        pb.environment().putAll(environment);
        pb.redirectErrorStream(true);
        
        try {
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            
            return reader.lines().onClose(() -> {
                try {
                    process.waitFor();
                    reader.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to execute Claude Code", e);
        }
    }
    
    @Override
    public String execute(String prompt) {
        return executeStreaming(prompt)
            .collect(Collectors.joining("\n"));
    }
    
    @Override
    public CompletableFuture<String> executeAsync(String prompt) {
        return CompletableFuture.supplyAsync(() -> execute(prompt));
    }
    
    private Map<String, String> buildEnvironment() {
        Map<String, String> env = new HashMap<>();
        env.put("ANTHROPIC_API_KEY", System.getenv("ANTHROPIC_API_KEY"));
        env.put("HOME", System.getProperty("user.home"));
        return env;
    }
}
```

### 2. Spring Boot Integration Example

```java
@RestController
@RequestMapping("/api/claude")
public class ClaudeCodeController {
    
    private final ClaudeCodeExecutor executor;
    
    public ClaudeCodeController(ClaudeCodeExecutor executor) {
        this.executor = executor;
    }
    
    @PostMapping("/execute")
    public Flux<String> executeStreaming(@RequestBody PromptRequest request) {
        return Flux.fromStream(executor.executeStreaming(request.getPrompt()))
            .delayElements(Duration.ofMillis(10)); // Smooth streaming
    }
    
    @PostMapping("/execute-sync")
    public ResponseEntity<String> execute(@RequestBody PromptRequest request) {
        String result = executor.execute(request.getPrompt());
        return ResponseEntity.ok(result);
    }
}
```

### 3. Real-time Streaming with Server-Sent Events (SSE)

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> streamClaudeOutput(
    @RequestParam String prompt) {
    
    return Flux.fromStream(executor.executeStreaming(prompt))
        .map(line -> ServerSentEvent.<String>builder()
            .data(line)
            .build())
        .doOnError(e -> log.error("Streaming error", e))
        .doFinally(signal -> log.info("Stream completed"));
}
```

---

## MCP Server Configuration

### 1. Configuration Generation

The buildpack will generate a `.claude.json` file at runtime:

```json
{
  "mcpServers": {
    "filesystem": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem"],
      "env": {
        "ALLOWED_DIRECTORIES": "/home/vcap/app,/tmp"
      }
    },
    "github": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "env": {
        "GITHUB_PERSONAL_ACCESS_TOKEN": "ghp_xxxxx"
      }
    }
  }
}
```

### 2. Supported MCP Server Types

#### Pre-configured Servers:
- **filesystem**: File system operations
- **github**: GitHub integration
- **postgres**: PostgreSQL database access
- **sequential-thinking**: Complex reasoning
- **brave-search**: Web search capabilities

#### Custom Servers:
Support for any MCP-compatible server via:
- npm packages
- Local scripts
- Remote endpoints

---

## Security Considerations

### 1. API Key Management
- **Never log API keys** in buildpack output
- Store keys in environment variables only
- Use Cloud Foundry user-provided services for production
- Mask keys in error messages and logs

### 2. Permission Model
- Claude Code runs with `--dangerously-skip-permissions` for automation
- Limit file system access through MCP configuration
- Restrict network access via Cloud Foundry security groups
- Use read-only mounts where possible

### 3. Resource Limits
- Set memory limits for Claude Code processes
- Implement timeouts for CLI invocations
- Monitor CPU usage and implement throttling
- Prevent fork bombs through ulimit settings

### 4. Input Validation
- Sanitize all user inputs before passing to CLI
- Validate prompt lengths and formats
- Prevent command injection through proper escaping
- Implement rate limiting on API endpoints

---

## Performance Optimization

### 1. Buildpack Caching
- Cache downloaded Node.js binaries
- Cache npm packages between builds
- Cache Claude Code CLI installation
- Store in `CACHE_DIR` for reuse

### 2. Runtime Optimization
- Keep Claude Code processes warm when possible
- Use connection pooling for repeated invocations
- Implement result caching for identical prompts
- Use async execution for non-blocking operations

### 3. Memory Management
- Set appropriate heap sizes for Java application
- Monitor Claude Code memory usage
- Implement circuit breakers for resource exhaustion
- Clean up streams and processes properly

---

## Testing Strategy

### 1. Unit Tests
- Buildpack detection logic
- Configuration parsing
- Environment variable handling
- MCP server configuration generation

### 2. Integration Tests
- Full buildpack staging process
- Claude Code CLI installation verification
- MCP server connectivity tests
- Java wrapper library functionality

### 3. End-to-End Tests
```bash
# Test script structure
1. Create sample Java application
2. Add Claude Code configuration
3. Deploy to Cloud Foundry
4. Verify CLI is available
5. Test streaming invocation
6. Verify MCP servers are connected
7. Test error handling scenarios
```

### 4. Performance Tests
- CLI invocation latency
- Streaming throughput
- Memory usage under load
- Concurrent execution handling

---

## Implementation Phases

### Phase 1: Core Buildpack (Week 1-2)
- [ ] Implement `bin/detect` script
- [ ] Implement `bin/supply` script
- [ ] Create Node.js installation logic
- [ ] Create Claude Code CLI installation logic
- [ ] Implement basic environment variable handling
- [ ] Create unit tests

### Phase 2: Configuration Management (Week 2-3)
- [ ] Implement manifest parsing
- [ ] Implement `.claude-code-config.yml` parsing
- [ ] Create MCP server configuration generator
- [ ] Implement `.claude.json` generation
- [ ] Add configuration validation
- [ ] Create integration tests

### Phase 3: Java Integration (Week 3-4)
- [ ] Develop Java wrapper library
- [ ] Implement streaming functionality
- [ ] Create Spring Boot integration examples
- [ ] Add SSE support
- [ ] Create comprehensive documentation
- [ ] Develop sample applications

### Phase 4: Security & Production Readiness (Week 4-5)
- [ ] Implement security hardening
- [ ] Add comprehensive error handling
- [ ] Create production-ready examples
- [ ] Implement monitoring and logging
- [ ] Add health check endpoints
- [ ] Performance optimization

### Phase 5: Documentation & Release (Week 5-6)
- [ ] Write comprehensive README
- [ ] Create deployment guides
- [ ] Develop troubleshooting documentation
- [ ] Create video tutorials
- [ ] Package for distribution
- [ ] Publish to GitHub/buildpack registry

---

## File Structure for Deliverables

```
claude-code-buildpack/
├── bin/
│   ├── detect
│   ├── supply
│   └── finalize
├── lib/
│   ├── installer.sh
│   ├── mcp_configurator.sh
│   ├── validator.sh
│   └── environment.sh
├── resources/
│   ├── default-config.yml
│   └── mcp-templates/
│       ├── filesystem.json
│       ├── github.json
│       └── postgres.json
├── tests/
│   ├── unit/
│   ├── integration/
│   └── fixtures/
├── examples/
│   ├── spring-boot-streaming/
│   ├── plain-java/
│   └── reactive-webflux/
├── java-wrapper/
│   ├── src/
│   │   └── main/
│   │       └── java/
│   │           └── com/claudecode/
│   ├── pom.xml
│   └── README.md
├── docs/
│   ├── ARCHITECTURE.md
│   ├── DEPLOYMENT.md
│   ├── CONFIGURATION.md
│   ├── SECURITY.md
│   └── TROUBLESHOOTING.md
├── manifest.yml
├── buildpack.yml
├── VERSION
├── LICENSE
└── README.md
```

---

## Deployment Example

### 1. Deploy with Buildpack

```bash
# Clone the buildpack
git clone https://github.com/your-org/claude-code-buildpack.git

# Package the buildpack
cd claude-code-buildpack
./package.sh

# Upload to Cloud Foundry
cf create-buildpack claude-code-buildpack claude-code-buildpack.zip 1

# Deploy application
cd my-java-app
cf push -b nodejs_buildpack -b claude-code-buildpack -b java_buildpack
```

### 2. Application Setup

```java
// pom.xml
<dependency>
    <groupId>com.claudecode</groupId>
    <artifactId>claude-code-cf-wrapper</artifactId>
    <version>1.0.0</version>
</dependency>

// Application code
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
    
    @Bean
    public ClaudeCodeExecutor claudeCodeExecutor() {
        return new ClaudeCodeExecutorImpl();
    }
}
```

---

## Monitoring & Observability

### 1. Logging
- Log buildpack installation progress
- Log Claude Code invocations (without sensitive data)
- Log MCP server connection status
- Implement structured logging (JSON format)

### 2. Metrics
- Claude Code invocation count
- Average execution time
- Error rate
- Memory usage
- Token consumption (if available)

### 3. Health Checks
```java
@RestController
public class HealthController {
    
    @GetMapping("/health/claude-code")
    public ResponseEntity<Health> claudeHealth() {
        boolean available = isClaudeAvailable();
        boolean mcpConnected = areMcpServersConnected();
        
        return ResponseEntity.ok(Health.builder()
            .status(available && mcpConnected ? "UP" : "DOWN")
            .withDetail("cli-available", available)
            .withDetail("mcp-connected", mcpConnected)
            .build());
    }
}
```

---

## Error Handling

### 1. Buildpack Errors
- Missing dependencies
- Failed installations
- Invalid configurations
- Permission issues

### 2. Runtime Errors
- API key issues
- MCP server connection failures
- CLI execution timeouts
- Resource exhaustion

### 3. Error Response Format
```json
{
  "error": "CLAUDE_CODE_EXECUTION_FAILED",
  "message": "Failed to execute Claude Code command",
  "details": {
    "command": "claude -p 'analyze code'",
    "exitCode": 1,
    "stderr": "API authentication failed"
  },
  "timestamp": "2025-11-22T10:30:00Z"
}
```

---

## Dependencies

### Required:
- Node.js (LTS version)
- npm (bundled with Node.js)
- @anthropic-ai/claude-code (npm package)
- Bash (for buildpack scripts)

### Optional:
- MCP server packages (as configured)
- Java wrapper library (for easier integration)

---

## Future Enhancements

### Phase 2 Features:
1. **Multi-model Support**: Allow switching between Claude models
2. **Cost Tracking**: Monitor and report API usage costs
3. **Response Caching**: Cache responses for identical prompts
4. **Batch Processing**: Support batch command execution
5. **Web UI**: Optional dashboard for monitoring and management

### Phase 3 Features:
1. **Auto-scaling**: Scale based on Claude Code usage
2. **Blue-Green Deployments**: Support zero-downtime updates
3. **Advanced MCP**: Custom MCP server development tools
4. **CI/CD Integration**: GitHub Actions, GitLab CI support
5. **Marketplace**: Pre-configured application templates

---

## Success Criteria

### Functionality:
- ✅ Claude Code CLI successfully installed in container
- ✅ API key properly configured and working
- ✅ MCP servers successfully configured and connected
- ✅ Java applications can invoke CLI
- ✅ Real-time streaming works correctly

### Performance:
- CLI invocation latency < 2s (cold start)
- Streaming throughput > 100 lines/sec
- Memory overhead < 256MB
- Buildpack staging time < 60s

### Reliability:
- 99.9% success rate for CLI invocations
- Proper error handling and recovery
- No resource leaks
- Graceful degradation on failures

### Security:
- No exposed API keys in logs
- Proper permission management
- Secure MCP server configuration
- Input sanitization implemented

---

## Conclusion

This buildpack will enable seamless integration of Claude Code CLI into Cloud Foundry Java applications, providing developers with powerful AI-assisted coding capabilities directly in their production environments. The phased approach ensures a stable, secure, and performant implementation.

**Next Steps:**
1. Review and approve this plan
2. Set up development environment
3. Begin Phase 1 implementation
4. Establish continuous feedback loop
5. Iterate based on testing results

---

## Contact & Support

- **Project Lead**: [Your Name]
- **Repository**: https://github.com/your-org/claude-code-buildpack
- **Issues**: https://github.com/your-org/claude-code-buildpack/issues
- **Slack Channel**: #claude-code-buildpack
