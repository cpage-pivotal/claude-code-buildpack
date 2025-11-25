# Quick Start Guide

Get Claude Code CLI running in your Cloud Foundry Java application in 5 minutes.

## Prerequisites

- Cloud Foundry CLI installed
- Access to a Cloud Foundry environment
- Anthropic API key (get one at https://console.anthropic.com/)
- Java application ready to deploy

## Step 1: Get Your API Key

Sign up at https://console.anthropic.com/ and generate an API key. It should start with `sk-ant-`.

## Step 2: Add Buildpack to Your Application

Edit your `manifest.yml`:

```yaml
---
applications:
- name: my-java-app
  buildpacks:
    - nodejs_buildpack
    - https://github.com/your-org/claude-code-buildpack
    - java_buildpack
  env:
    ANTHROPIC_API_KEY: sk-ant-xxxxxxxxxxxxx  # Your API key
    CLAUDE_CODE_ENABLED: true
```

**Important**: The buildpack order matters! Node.js must come first, then Claude Code, then Java.

## Step 3: Add Configuration File

Create a `.claude-code-config.yml` file for your Spring Boot application. You have two options:

### Option 1: Use Spring Boot Resources (Recommended)

Place the config file in your resources directory:

```bash
# Create config in src/main/resources/
cat > src/main/resources/.claude-code-config.yml <<EOF
claudeCode:
  enabled: true
  version: "latest"
  logLevel: info
  model: sonnet
  
  settings:
    alwaysThinkingEnabled: true
  
  mcpServers: []
EOF
```

Spring Boot will automatically include this in your JAR at `BOOT-INF/classes/.claude-code-config.yml`, and the buildpack will find it.

### Option 2: Add to JAR Root (Alternative)

If you need the config at the JAR root, add this plugin to your `pom.xml`:

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

## Step 4: Build and Verify

Build your application:

```bash
mvn clean package
```

### Option 1 Verification (Resources Directory)

If you used Option 1, verify the config is in BOOT-INF/classes/:

```bash
unzip -l target/my-app.jar | grep claude-code-config
```

You should see:
```
203  11-25-2025 08:45   BOOT-INF/classes/.claude-code-config.yml
```

### Option 2 Verification (JAR Root)

If you used Option 2 with the Maven plugin, verify the config is at JAR root:

```bash
jar tf target/my-app.jar | head -20
```

You should see `.claude-code-config.yml` listed at the root of the JAR.

## Step 5: Deploy

Update your manifest to deploy the JAR:

```yaml
# manifest.yml
applications:
  - name: my-java-app
    path: target/my-app.jar  # Now you can deploy the JAR
    buildpacks:
      - nodejs_buildpack
      - https://github.com/your-org/claude-code-buildpack
      - java_buildpack
    env:
      ANTHROPIC_API_KEY: sk-ant-xxxxxxxxxxxxx
      CLAUDE_CODE_ENABLED: true
```

Then push your application:

```bash
cf push
```

The buildpack will:
1. Install Node.js
2. Install Claude Code CLI
3. Configure your environment
4. Build your Java application

## Step 6: Verify Installation

Check that Claude Code is available:

```bash
cf ssh my-java-app
> echo $CLAUDE_CLI_PATH
/home/vcap/deps/1/bin/claude

> $CLAUDE_CLI_PATH --version
2.0.50  # or whatever version was installed
```

## Step 7: Use in Your Java Code

### Basic Example (Correct Pattern)

```java
import java.io.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public String executeClaudeCode(String prompt) throws Exception {
    ProcessBuilder pb = new ProcessBuilder(
        System.getenv("CLAUDE_CLI_PATH"),
        "-p", prompt,
        "--dangerously-skip-permissions"
    );

    // Pass environment variables to subprocess
    Map<String, String> env = pb.environment();
    env.put("ANTHROPIC_API_KEY", System.getenv("ANTHROPIC_API_KEY"));
    env.put("HOME", System.getenv("HOME"));

    // Redirect stderr to stdout (prevents buffer deadlock)
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

    // Wait with timeout
    boolean finished = process.waitFor(3, TimeUnit.MINUTES);
    if (!finished) {
        process.destroyForcibly();
        throw new RuntimeException("Process timed out");
    }

    // Check exit code
    if (process.exitValue() != 0) {
        throw new RuntimeException("Process failed: " + process.exitValue());
    }

    return output.toString();
}
```

### Spring Boot REST Controller Example

```java
@RestController
@RequestMapping("/api/claude")
public class ClaudeController {

    @GetMapping("/ask")
    public ResponseEntity<String> ask(@RequestParam String question) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                System.getenv("CLAUDE_CLI_PATH"),
                "-p", question,
                "--dangerously-skip-permissions"
            );

            Map<String, String> env = pb.environment();
            env.put("ANTHROPIC_API_KEY", System.getenv("ANTHROPIC_API_KEY"));
            env.put("HOME", System.getenv("HOME"));

            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.getOutputStream().close();

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
                return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                    .body("Request timed out");
            }

            if (process.exitValue() != 0) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Claude Code failed");
            }

            return ResponseEntity.ok(output.toString());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error: " + e.getMessage());
        }
    }
}
```

### ⚠️ Critical Requirements

Your code **MUST** include these steps or it will hang:

1. ✅ **Close stdin**: `process.getOutputStream().close()`
2. ✅ **Redirect stderr**: `pb.redirectErrorStream(true)`
3. ✅ **Pass API key**: Add to `pb.environment()`
4. ✅ **Use timeout**: `process.waitFor(3, TimeUnit.MINUTES)`

**Without these, your process will timeout after 2 minutes!**

## Common Issues

### Buildpack Not Detected

Make sure you have one of these:
- `CLAUDE_CODE_ENABLED=true` in your manifest
- `.claude-code-config.yml` in your app root

### API Key Not Working

Verify:
```bash
cf env my-java-app | grep ANTHROPIC_API_KEY
```

The key should start with `sk-ant-`.

### CLI Not Found

Check the buildpack was applied:
```bash
cf logs my-java-app --recent | grep "Claude Code"
```

You should see: `-----> Claude Code CLI Buildpack`

## Next Steps

- Read the full [README.md](README.md) for advanced features
- Check out [DESIGN.md](DESIGN.md) for architecture details
- See [examples/](examples/) for more sample configurations
- Review Phase 2 features for MCP server support

## Getting Help

- GitHub Issues: https://github.com/your-org/claude-code-buildpack/issues
- Documentation: See [docs/](docs/) directory

## Environment Variables Reference

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `ANTHROPIC_API_KEY` | Yes | - | Your Anthropic API key |
| `CLAUDE_CODE_ENABLED` | No | false | Enable the buildpack |
| `CLAUDE_CODE_VERSION` | No | latest | Specific version to install |
| `CLAUDE_CODE_LOG_LEVEL` | No | info | CLI log level |
| `CLAUDE_CODE_MODEL` | No | sonnet | Default Claude model |
| `NODE_VERSION` | No | 20.11.0 | Node.js version |
