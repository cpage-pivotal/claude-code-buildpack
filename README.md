# Claude Code CLI Buildpack for Cloud Foundry

A Cloud Foundry supply buildpack that bundles the Claude Code CLI into Java application containers, enabling AI-assisted coding capabilities in production environments.

## Overview

This buildpack installs the Claude Code CLI and Node.js runtime into your Cloud Foundry application container, allowing your Java applications to invoke Claude Code commands and stream their output in real-time.

## Features

- 🚀 Automated installation of Node.js and Claude Code CLI
- 🔐 Secure API key management via environment variables
- 🔌 MCP (Model Context Protocol) server support ✅
- ☕ Java wrapper library for easy integration (Phase 3)
- 📡 Real-time streaming output support
- 💾 Intelligent caching for faster builds

## Quick Start

### 1. Enable the Buildpack

Add the buildpack to your `manifest.yml`:

```yaml
---
applications:
- name: my-java-app
  buildpacks:
    - nodejs_buildpack
    - https://github.com/your-org/claude-code-buildpack
    - java_buildpack
  env:
    ANTHROPIC_API_KEY: sk-ant-xxxxxxxxxxxxx
    CLAUDE_CODE_ENABLED: true
```

### 2. Alternative: Use Configuration File

Create `.claude-code-config.yml` in your application root:

```yaml
claudeCode:
  enabled: true

  # Optional: Set log level for verbose output
  logLevel: debug  # Options: debug, info, warn, error

  # Optional: Pin to specific Claude Code version
  version: "latest"

  # Optional: Set default Claude model
  model: sonnet  # Options: sonnet, opus, haiku
```

**Note:** You still need to set `ANTHROPIC_API_KEY` as an environment variable for security reasons.

### 3. Deploy Your Application

```bash
cf push
```

## Detection

The buildpack will activate when any of the following conditions are met:

1. `.claude-code-config.yml` file exists in the application root
2. `CLAUDE_CODE_ENABLED=true` environment variable is set
3. `claude-code-enabled: true` is specified in `manifest.yml`

## Spring Boot JAR Deployment

When deploying a Spring Boot JAR file, you need to embed `.claude-code-config.yml` in the JAR root for the buildpack to detect it during staging.

**Add to your `pom.xml`:**

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>

        <!-- Add config file to JAR root after Spring Boot repackaging -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-antrun-plugin</artifactId>
            <version>3.1.0</version>
            <executions>
                <execution>
                    <id>add-config-to-jar</id>
                    <phase>package</phase>
                    <goals>
                        <goal>run</goal>
                    </goals>
                    <configuration>
                        <target>
                            <jar destfile="${project.build.directory}/${project.build.finalName}.jar" update="true">
                                <fileset dir="${project.basedir}" includes=".claude-code-config.yml"/>
                            </jar>
                        </target>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

**Build and verify:**

```bash
# Build your JAR
mvn clean package

# Verify the config file is at the JAR root (not in BOOT-INF/classes/)
jar tf target/my-app.jar | head -20
```

You should see `.claude-code-config.yml` listed at the root of the JAR.

**Deploy the JAR:**

```yaml
# manifest.yml
applications:
  - name: my-java-app
    path: target/my-app.jar  # Deploy the JAR directly
    buildpacks:
      - nodejs_buildpack
      - https://github.com/your-org/claude-code-buildpack
      - java_buildpack
```

**Important:** The config file must be at the JAR root, not inside `BOOT-INF/classes/`, for the buildpack to detect it during staging. Spring Boot's repackaging places resources in `BOOT-INF/classes/` by default, so the `maven-antrun-plugin` is required to add the file to the JAR root after repackaging.

## Configuration

### Configuration File Settings

You can configure Claude Code using `.claude-code-config.yml` in your application root:

```yaml
claudeCode:
  enabled: true

  # Log level - controls verbosity of CLI output
  # Options: debug, info, warn, error
  # Default: info
  logLevel: debug

  # Claude Code CLI version
  # Default: latest
  version: "2.0.50"

  # Default Claude model to use
  # Options: sonnet, opus, haiku
  # Default: sonnet
  model: sonnet

  # MCP servers configuration (see MCP section below)
  mcpServers:
    # ...
```

**Setting Priority:** Configuration file values take precedence over environment variables, which take precedence over defaults.

### Environment Variables

#### Required

- `ANTHROPIC_API_KEY`: Your Anthropic API key (format: `sk-ant-...`)

#### Optional

- `CLAUDE_CODE_ENABLED`: Enable/disable the buildpack (default: `false`)
- `CLAUDE_CODE_VERSION`: Specific version to install (default: `latest`)
  - *Can also be set in config file as `version`*
- `CLAUDE_CODE_LOG_LEVEL`: CLI log level (default: `info`)
  - *Can also be set in config file as `logLevel`*
- `CLAUDE_CODE_MODEL`: Default model to use (default: `sonnet`)
  - *Can also be set in config file as `model`*
- `NODE_VERSION`: Node.js version for CLI (default: `20.11.0`)

## Usage in Java Applications

After deployment, the Claude Code CLI is available at the path specified in the `CLAUDE_CLI_PATH` environment variable.

### Basic Example

```java
import java.io.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ClaudeExample {
    public static String executeClaudeCode(String prompt) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            System.getenv("CLAUDE_CLI_PATH"),
            "-p", prompt,
            "--dangerously-skip-permissions"
        );

        // Pass environment variables to subprocess
        Map<String, String> env = pb.environment();
        env.put("ANTHROPIC_API_KEY", System.getenv("ANTHROPIC_API_KEY"));
        env.put("HOME", System.getenv("HOME"));

        // Redirect stderr to stdout to avoid buffer deadlock
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // CRITICAL: Close stdin so CLI doesn't wait for input
        process.getOutputStream().close();

        // Read output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                System.out.println(line);
            }
        }

        // Wait for process with timeout
        boolean finished = process.waitFor(3, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Process timed out");
        }

        // Check exit code
        if (process.exitValue() != 0) {
            throw new RuntimeException("Process failed with exit code: " + process.exitValue());
        }

        return output.toString();
    }
}
```

### Spring Boot REST Endpoint Example

```java
@RestController
@RequestMapping("/api/claude")
public class ClaudeController {

    @GetMapping("/execute")
    public ResponseEntity<String> execute(@RequestParam String prompt) {
        try {
            String result = executeClaudeCode(prompt);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error: " + e.getMessage());
        }
    }

    private String executeClaudeCode(String prompt) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            System.getenv("CLAUDE_CLI_PATH"),
            "-p", prompt,
            "--dangerously-skip-permissions"
        );

        Map<String, String> env = pb.environment();
        env.put("ANTHROPIC_API_KEY", System.getenv("ANTHROPIC_API_KEY"));
        env.put("HOME", System.getenv("HOME"));

        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.getOutputStream().close();  // Close stdin

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(3, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Timeout");
        }

        if (process.exitValue() != 0) {
            throw new RuntimeException("Failed with exit code: " + process.exitValue());
        }

        return output.toString();
    }
}
```

### Critical Requirements

When using `ProcessBuilder` to invoke Claude Code:

1. **✅ DO close stdin**: `process.getOutputStream().close()`
2. **✅ DO redirect stderr**: `pb.redirectErrorStream(true)`
3. **✅ DO pass API key**: Add `ANTHROPIC_API_KEY` to subprocess environment
4. **✅ DO use timeout**: `process.waitFor(3, TimeUnit.MINUTES)`

Without these, your process will hang or timeout!

## Installation Directory Structure

```
/home/vcap/
├── app/                        # Application directory
│   ├── .profile.d/
│   │   └── claude-code-env.sh  # Runtime environment setup
│   └── .claude.json            # Claude Code configuration
└── deps/{INDEX}/               # Buildpack dependencies
    ├── bin/
    │   └── claude              # Claude Code CLI symlink
    ├── node/
    │   ├── bin/
    │   │   ├── node
    │   │   └── npm
    │   └── lib/
    ├── lib/
    │   └── node_modules/
    │       └── @anthropic-ai/
    │           └── claude-code/
    └── config.yml              # Buildpack configuration
```

## MCP (Model Context Protocol) Server Configuration

Claude Code supports MCP servers to extend its capabilities with additional tools and integrations. The buildpack automatically generates a `.claude.json` configuration file from your `.claude-code-config.yml`.

### Configuring MCP Servers

Create a `.claude-code-config.yml` file in your application root:

```yaml
claudeCode:
  enabled: true

  # Optional: Increase verbosity for debugging
  logLevel: debug  # Options: debug, info, warn, error

  mcpServers:
    # Filesystem server - provides file system access
    - name: filesystem
      type: stdio
      command: npx
      args:
        - "-y"
        - "@modelcontextprotocol/server-filesystem"
      env:
        ALLOWED_DIRECTORIES: "/home/vcap/app,/tmp"

    # GitHub server - provides GitHub API integration
    - name: github
      type: stdio
      command: npx
      args:
        - "-y"
        - "@modelcontextprotocol/server-github"
      env:
        GITHUB_PERSONAL_ACCESS_TOKEN: "${GITHUB_TOKEN}"

    # PostgreSQL server - provides database access
    - name: postgres
      type: stdio
      command: npx
      args:
        - "-y"
        - "@modelcontextprotocol/server-postgres"
      env:
        POSTGRES_CONNECTION_STRING: "${POSTGRES_URL}"
```

### Available MCP Servers

#### Local MCP Servers (stdio transport)

The following MCP servers run as local processes:

| Server | Package | Description |
|--------|---------|-------------|
| **filesystem** | `@modelcontextprotocol/server-filesystem` | File system operations |
| **github** | `@modelcontextprotocol/server-github` | GitHub API integration |
| **postgres** | `@modelcontextprotocol/server-postgres` | PostgreSQL database access |
| **sequential-thinking** | `@modelcontextprotocol/server-sequential-thinking` | Complex reasoning capabilities |
| **brave-search** | `@modelcontextprotocol/server-brave-search` | Web search integration |

#### Remote MCP Servers (SSE/HTTP transport)

Remote MCP servers can be hosted anywhere and accessed via:
- **SSE (Server-Sent Events)**: For streaming updates and real-time data
- **HTTP**: For request/response interactions with streaming support

See `examples/.claude-code-config-remote-mcp.yml` for detailed configuration examples.

### Environment Variable Substitution

MCP server environment variables support Cloud Foundry environment variable substitution:

```yaml
mcpServers:
  - name: github
    type: stdio
    command: npx
    args:
      - "-y"
      - "@modelcontextprotocol/server-github"
    env:
      # ${VAR_NAME} is replaced at runtime with the CF environment variable
      GITHUB_PERSONAL_ACCESS_TOKEN: "${GITHUB_TOKEN}"
```

Set the environment variables in your manifest:

```yaml
applications:
- name: my-app
  env:
    GITHUB_TOKEN: ghp_xxxxxxxxxxxxx
    POSTGRES_URL: postgresql://localhost/mydb
```

### Generated Configuration

During staging, the buildpack parses `.claude-code-config.yml` and generates `.claude.json`:

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
    }
  }
}
```

This file is placed in `/home/vcap/app/.claude.json` and used by the Claude Code CLI at runtime.

### Remote MCP Servers

In addition to local stdio-based MCP servers, Claude Code supports remote MCP servers using SSE and HTTP transports:

```yaml
mcpServers:
  # SSE (Server-Sent Events) Transport
  - name: remote-data-service
    type: sse
    url: "https://mcp.example.com/api/data/sse"
    env:
      API_TOKEN: "${DATA_SERVICE_TOKEN}"

  # Streamable HTTP Transport
  - name: llm-gateway
    type: http
    url: "https://llm-gateway.example.com/mcp"
    env:
      GATEWAY_TOKEN: "${LLM_GATEWAY_TOKEN}"
```

**Remote Server Requirements**:
- Must implement the MCP protocol specification
- Must use HTTPS for security
- Should support authentication via headers or environment variables
- Must be accessible from Cloud Foundry (check security groups)

### Example Configurations

See the `examples/` directory for complete configuration examples:

- `examples/.claude-code-config.yml` - Full featured example with local (stdio) MCP servers
- `examples/.claude-code-config-remote-mcp.yml` - Remote MCP servers with SSE and HTTP transports
- `examples/.claude-code-config-minimal.yml` - Minimal configuration without MCP servers

## Development

### Prerequisites

- Bash 4.0+
- curl
- tar
- Cloud Foundry CLI

### Testing Locally

```bash
# Run detection
./bin/detect /path/to/app

# Run supply phase
./bin/supply /path/to/app /path/to/cache /path/to/deps 0
```

### Running Tests

```bash
# Run unit tests
./tests/unit/run_tests.sh

# Run integration tests (requires CF environment)
./tests/integration/run_tests.sh
```

## Architecture

This is a **supply buildpack** that works in conjunction with the Java buildpack:

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
│   - Configured with API key             │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│   Node.js Runtime                        │
│   - Required for Claude CLI (npm)       │
└─────────────────────────────────────────┘
```

## Security Considerations

- **API Keys**: Never log API keys; they are only available via environment variables
- **Permissions**: Claude Code runs with `--dangerously-skip-permissions` for automation
- **Access Control**: Limit file system access through MCP configuration (Phase 2)
- **Network**: Use Cloud Foundry security groups to restrict network access

## Troubleshooting

### Claude Code not found

Ensure the buildpack was detected and applied:

```bash
cf logs my-app --recent | grep "Claude Code"
```

### API Key issues

Verify your API key is set:

```bash
cf env my-app | grep ANTHROPIC_API_KEY
```

### Installation failures

Check buildpack logs during staging:

```bash
cf logs my-app --recent
```

### Remote MCP Server Connection Failures

**Problem**: Remote MCP servers (SSE/HTTP) show "Failed to connect" even though `curl` can reach them.

**Symptoms**:
- `claude mcp list` shows `✗ Failed to connect` for remote MCP servers
- Local stdio MCP servers work fine
- Works on your local machine but fails in Cloud Foundry

**Cause**: TAS/Cloud Foundry uses internal CA certificates. Node.js needs explicit configuration to trust them.

**Solution**: The buildpack automatically handles this (as of this update). If you're using an older version, you can manually verify the fix is working:

```bash
# SSH into your app (if you have access)
cf ssh my-app

# Check if the certificate bundle is created
$ ls -la /tmp/cf-ca-bundle.crt
$ env | grep NODE_EXTRA_CA_CERTS
```

If you see `NODE_EXTRA_CA_CERTS=/tmp/cf-ca-bundle.crt`, the buildpack configured it correctly.

**Manual Workaround** (for older buildpack versions):

Create `.profile.d/node-ca-certs.sh` in your application:

```bash
#!/bin/bash
if [ -d "$CF_SYSTEM_CERT_PATH" ]; then
    cat "$CF_SYSTEM_CERT_PATH"/*.crt > /tmp/ca-bundle.crt 2>/dev/null
    export NODE_EXTRA_CA_CERTS=/tmp/ca-bundle.crt
fi
```

Then redeploy with `cf push`.

## Roadmap

### Phase 1: Core Buildpack ✅ Complete
- [x] Basic detection and supply scripts
- [x] Node.js installation
- [x] Claude Code CLI installation
- [x] Environment variable handling
- [x] Unit tests and documentation

### Phase 2: Configuration Management ✅ Complete
- [x] MCP server configuration parsing
- [x] `.claude.json` generation from YAML
- [x] Python-based YAML parser
- [x] Configuration validation
- [x] Unit tests (12 tests passing)
- [x] Documentation and examples

### Phase 3: Java Integration (Planned)
- [ ] Java wrapper library
- [ ] Spring Boot integration
- [ ] Server-Sent Events support

### Phase 4: Production Readiness (Planned)
- [ ] Security hardening
- [ ] Performance optimization
- [ ] Monitoring and logging

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

MIT License - see [LICENSE](LICENSE) for details.

## Support

- **Issues**: https://github.com/your-org/claude-code-buildpack/issues
- **Documentation**: See [docs/](docs/) directory
- **Slack**: #claude-code-buildpack

## Credits

Built with ❤️ for the Cloud Foundry community.

Powered by:
- [Claude Code CLI](https://www.npmjs.com/package/@anthropic-ai/claude-code)
- [Anthropic Claude](https://www.anthropic.com/)
- [Cloud Foundry](https://www.cloudfoundry.org/)
