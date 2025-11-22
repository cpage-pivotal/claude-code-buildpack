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

### Basic Example

```java
String claudePath = System.getenv("CLAUDE_CLI_PATH");

ProcessBuilder pb = new ProcessBuilder(
    claudePath,
    "-p", "What is the capital of France?",
    "--dangerously-skip-permissions"
);

pb.environment().put("ANTHROPIC_API_KEY", System.getenv("ANTHROPIC_API_KEY"));

Process process = pb.start();

BufferedReader reader = new BufferedReader(
    new InputStreamReader(process.getInputStream())
);

String line;
while ((line = reader.readLine()) != null) {
    System.out.println(line);
}

process.waitFor();
```

### Streaming Example

```java
Stream<String> stream = new BufferedReader(
    new InputStreamReader(process.getInputStream())
).lines();

stream.forEach(line -> {
    // Process each line as it arrives
    logger.info("Claude: {}", line);
});
```

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
