# Claude Code Spring Boot Demo

This is an example Spring Boot application demonstrating integration with Claude Code CLI using the Java wrapper library.

## Features

- ✅ Auto-configured Claude Code integration
- ✅ REST API endpoints for code analysis
- ✅ Unit test generation
- ✅ Code refactoring with streaming output
- ✅ Interactive code reviews
- ✅ Health checks

## Prerequisites

- Java 21 or higher
- Maven 3.6 or higher
- Cloud Foundry CLI (for deployment)
- Anthropic API key

## Project Structure

```
spring-boot-app/
├── .claude-code-config.yml           # Root copy for CF buildpack detection
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/demo/
│       │       ├── DemoApplication.java
│       │       └── DemoController.java
│       └── resources/
│           ├── .claude-code-config.yml   # Packaged in JAR
│           └── application.yml
├── pom.xml
└── manifest.yml
```

**Important:** The `.claude-code-config.yml` file must be in TWO locations:
1. **Application root** - For Cloud Foundry buildpack detection during staging
2. **src/main/resources/** - To be packaged in the JAR for runtime access

The Maven build automatically includes the file from `src/main/resources/` in the JAR.

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

The `.claude-code-config.yml` must exist in TWO locations:
1. **Application root** - For buildpack detection
2. **src/main/resources/** - Packaged in JAR for runtime

See [DEPLOYMENT.md](DEPLOYMENT.md) for detailed deployment checklist and troubleshooting.

## API Usage

### Health Check

```bash
curl https://claude-code-demo.example.com/demo/status
```

### Code Analysis

```bash
curl -X POST https://claude-code-demo.example.com/demo/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "code": "public void processData(List<String> data) { for(int i=0; i<data.size(); i++) { System.out.println(data.get(i)); } }"
  }'
```

### Generate Tests

```bash
curl -X POST https://claude-code-demo.example.com/demo/generate-tests \
  -H "Content-Type: application/json" \
  -d '{
    "code": "public class Calculator { public int add(int a, int b) { return a + b; } }"
  }'
```

### Streaming Refactor

```bash
curl -X POST https://claude-code-demo.example.com/demo/refactor/stream \
  -H "Content-Type: application/json" \
  -d '{
    "code": "public void test() { int x = 1; int y = 2; int z = x + y; return z; }"
  }'
```

### Code Review

```bash
curl -X POST https://claude-code-demo.example.com/demo/review \
  -H "Content-Type: application/json" \
  -d '{
    "code": "...",
    "focus": "security"
  }'
```

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/demo/status` | GET | Health check |
| `/demo/analyze` | POST | Analyze code for issues |
| `/demo/generate-tests` | POST | Generate unit tests |
| `/demo/refactor/stream` | POST | Refactor code (streaming) |
| `/demo/review` | POST | Perform code review |
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
    io.github.claudecode: DEBUG
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
