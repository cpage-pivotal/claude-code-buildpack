# Claude Code Spring Boot Demo

This is an example Spring Boot application demonstrating integration with Claude Code CLI using the Java wrapper library.

## Features

- ✅ Auto-configured Claude Code integration
- ✅ REST API endpoints for executing prompts
- ✅ Multiple execution patterns (sync, async, streaming)
- ✅ Structured and plain text responses
- ✅ Health checks

## Prerequisites

- Java 21 or higher
- Maven 3.6 or higher
- Cloud Foundry CLI (for deployment)
- Anthropic API key

## Project Structure

```
spring-boot-app/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/demo/
│       │       ├── DemoApplication.java
│       │       └── DemoController.java
│       └── resources/
│           ├── .claude-code-config.yml   # Config file location
│           └── application.yml
├── pom.xml
└── manifest.yml
```

**Configuration File Location:** Place `.claude-code-config.yml` in `src/main/resources/`:
- Spring Boot automatically packages it into the JAR at `BOOT-INF/classes/.claude-code-config.yml`
- The buildpack automatically detects and extracts it during staging
- No additional Maven plugins required

## Local Development

### Build

```bash
mvn clean package
```

### Run Locally (requires Claude Code CLI installed)

```bash
# Set environment variables
export CLAUDE_CLI_PATH=/path/to/claude
export ANTHROPIC_API_KEY=sk-ant-xxxxx

# Run the application
mvn spring-boot:run
```

## Cloud Foundry Deployment

**⚠️ Important:** Before deploying, read [DEPLOYMENT.md](DEPLOYMENT.md) for complete best practices.

### Quick Deploy

```bash
# Set the API key variable
cf set-env claude-code-demo ANTHROPIC_API_KEY sk-ant-xxxxx

# Push the application with buildpacks
cf push -b nodejs_buildpack -b claude-code-buildpack -b java_buildpack
```

### Verify Deployment

```bash
# Check the app is running
cf app claude-code-demo

# Test health endpoint
curl https://claude-code-demo.example.com/api/claude/health

# Check logs
cf logs claude-code-demo --recent
```

### Configuration

Place `.claude-code-config.yml` in `src/main/resources/` and Spring Boot will automatically package it in your JAR. The buildpack will detect it during staging.

See [DEPLOYMENT.md](DEPLOYMENT.md) for detailed deployment checklist and troubleshooting.

## API Usage

### Health Check

```bash
http https://claude-code-demo.example.com/demo/status
```

### Execute Prompt with Structured Response

The `/demo/execute-with-response` endpoint accepts any prompt and returns a structured response with success status.

```bash
http POST https://claude-code-demo.example.com/demo/execute-with-response \
  prompt="Analyze this Java code for potential bugs, performance issues, and best practices:

public void processData(List<String> data) {
    for(int i=0; i<data.size(); i++) {
        System.out.println(data.get(i));
    }
}"
```

### Execute Prompt with Custom Options

The `/demo/execute-with-options` endpoint demonstrates using custom execution options (timeout, model selection).

```bash
http POST https://claude-code-demo.example.com/demo/execute-with-options \
  prompt="Generate JUnit 5 unit tests for this Java code:

public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }
}"
```

### Execute Prompt with Streaming

The `/demo/execute-streaming` endpoint executes prompts with streaming output for real-time responses.

```bash
http --stream POST https://claude-code-demo.example.com/demo/execute-streaming \
  prompt="Refactor this Java code to improve readability and maintainability:

public void test() {
    int x = 1;
    int y = 2;
    int z = x + y;
    return z;
}"
```

### Execute Prompt with Simple Response

The `/demo/execute` endpoint accepts any prompt and returns a plain text response.

```bash
http POST https://claude-code-demo.example.com/demo/execute \
  prompt="Perform a detailed code review focusing on security for this authentication code:

public void authenticate(String username, String password) {
    // authentication logic here
}"
```

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/demo/status` | GET | Health check |
| `/demo/execute` | POST | Execute prompt with simple response |
| `/demo/execute-with-response` | POST | Execute prompt with structured response |
| `/demo/execute-with-options` | POST | Execute prompt with custom options |
| `/demo/execute-streaming` | POST | Execute prompt with streaming output |
| `/api/claude/execute` | POST | Direct CLI execution |
| `/api/claude/stream` | POST | Streaming execution |
| `/api/claude/health` | GET | CLI health check |

## Configuration

Edit `application.yml` to customize:

```yaml
claude-code:
  enabled: true
  controller-enabled: true

logging:
  level:
    org.tanzu.claudecode: DEBUG
```

## Troubleshooting

### "Claude CLI not available"

Ensure environment variables are set:
```bash
cf env claude-code-demo
```

Should show:
- `CLAUDE_CLI_PATH`
- `ANTHROPIC_API_KEY`

### Logs

```bash
# View recent logs
cf logs claude-code-demo --recent

# Stream logs
cf logs claude-code-demo
```

## License

MIT License
