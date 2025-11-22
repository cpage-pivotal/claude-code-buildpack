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

## Step 3: Deploy

```bash
cf push
```

That's it! The buildpack will:
1. Install Node.js
2. Install Claude Code CLI
3. Configure your environment
4. Build your Java application

## Step 4: Verify Installation

Check that Claude Code is available:

```bash
cf ssh my-java-app
> echo $CLAUDE_CLI_PATH
/home/vcap/deps/1/bin/claude

> $CLAUDE_CLI_PATH --version
2.0.50  # or whatever version was installed
```

## Step 5: Use in Your Java Code

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
