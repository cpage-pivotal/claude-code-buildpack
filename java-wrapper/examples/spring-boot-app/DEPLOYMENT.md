# Spring Boot Deployment Best Practices for Cloud Foundry

This document outlines best practices for deploying Spring Boot applications with Claude Code CLI integration to Cloud Foundry.

## Maven Dependency Configuration

This example application uses the Claude Code CF Wrapper library, which is published to GCP Artifact Registry as a public Maven repository.

### Adding the Repository

Add the following repository to your `pom.xml`:

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
```

### Adding the Dependency

```xml
<dependencies>
    <!-- Claude Code CF Wrapper -->
    <dependency>
        <groupId>org.tanzu.claudecode</groupId>
        <artifactId>claude-code-cf-wrapper</artifactId>
        <version>1.1.1</version>
    </dependency>
</dependencies>
```

**No authentication required** - the repository is publicly accessible! Maven will automatically download the artifact when you build your project.

### Verifying the Setup

Test that Maven can resolve the dependency:

```bash
# Clean build to force dependency download
mvn clean compile

# Check resolved dependencies
mvn dependency:tree | grep claude-code-cf-wrapper
```

## Configuration File Placement

Place `.claude-code-config.yml` in `src/main/resources/`:

```
/my-app/src/main/resources/.claude-code-config.yml
```

**How it works:**
1. Spring Boot packages the file into your JAR at `BOOT-INF/classes/.claude-code-config.yml`
2. When you deploy to Cloud Foundry, the Java buildpack explodes (extracts) the JAR
3. The Claude Code buildpack's supply script automatically finds and extracts the config from `BOOT-INF/classes/`
4. No additional Maven plugins or dual file locations required

## Maven Configuration

Add the following to your `pom.xml` to ensure YAML and Markdown files are properly packaged:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
        
        <!-- Ensure resource files are not filtered/corrupted -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-resources-plugin</artifactId>
            <configuration>
                <nonFilteredFileExtensions>
                    <nonFilteredFileExtension>yml</nonFilteredFileExtension>
                    <nonFilteredFileExtension>yaml</nonFilteredFileExtension>
                    <nonFilteredFileExtension>md</nonFilteredFileExtension>
                </nonFilteredFileExtensions>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Bundled Skills

To include Skills with your application, place them in `src/main/resources/.claude/skills/`:

```
my-app/
├── src/
│   └── main/
│       └── resources/
│           ├── .claude/
│           │   └── skills/
│           │       ├── my-skill/
│           │       │   └── SKILL.md
│           │       └── another-skill/
│           │           ├── SKILL.md
│           │           └── scripts/
│           │               └── helper.py
│           ├── .claude-code-config.yml
│           └── application.yml
├── pom.xml
└── .claude-code-config.yml  (copy for buildpack detection)
```

Spring Boot will automatically package `src/main/resources/.claude/` into `BOOT-INF/classes/.claude/` in the JAR, and the buildpack will extract it to `/home/vcap/app/.claude/` during staging.

**No Maven plugins needed!** This follows standard Spring Boot resource conventions.

## Deployment Checklist

### Pre-Deployment

- [ ] **Configuration Files**
  - [ ] `.claude-code-config.yml` exists in `src/main/resources/`
  - [ ] MCP server configurations are correct
  - [ ] Bundled Skills (if any) are in `src/main/resources/.claude/skills/` directory

- [ ] **Build Verification**
  ```bash
  # Build the application
  mvn clean package
  
  # Verify config is in JAR
  jar tf target/your-app.jar | grep claude-code-config
  # Should output: BOOT-INF/classes/.claude-code-config.yml
  
  # Verify Skills are in JAR (if bundled)
  jar tf target/your-app.jar | grep "BOOT-INF/classes/.claude/"
  # Should output: BOOT-INF/classes/.claude/skills/my-skill/SKILL.md (etc.)
  ```

- [ ] **Environment Variables**
  - [ ] Authentication credentials set (or will be set in CF):
    - [ ] `ANTHROPIC_API_KEY` (API key option), or
    - [ ] `CLAUDE_CODE_OAUTH_TOKEN` (OAuth token option)
  - [ ] `CLAUDE_CODE_ENABLED=true` if not using config file

- [ ] **Manifest Configuration**
  ```yaml
  applications:
  - name: my-app
    buildpacks:
      - nodejs_buildpack       # Provides Node.js for Claude CLI
      - claude-code-buildpack  # Installs Claude Code CLI
      - java_buildpack         # Runs Java application
    env:
      # Choose one authentication method:
      ANTHROPIC_API_KEY: ((your-api-key))              # Option 1: API key
      # CLAUDE_CODE_OAUTH_TOKEN: ((your-oauth-token))  # Option 2: OAuth token
      CLAUDE_CODE_ENABLED: true
  ```

### Deployment

```bash
# Set environment variables
cf set-env my-app ANTHROPIC_API_KEY sk-ant-xxxxx

# Push with buildpacks
cf push

# Or push with inline buildpacks
cf push -b nodejs_buildpack -b claude-code-buildpack -b java_buildpack
```

### Post-Deployment Verification

```bash
# Check application status
cf app my-app

# Verify Claude Code CLI is available
cf ssh my-app
$ echo $CLAUDE_CLI_PATH
$ ls -la $CLAUDE_CLI_PATH
$ exit

# Check application logs
cf logs my-app --recent

# Test the health endpoint
curl https://my-app.example.com/api/claude/health

# Expected response:
# {
#   "status": "UP",
#   "available": true,
#   "version": "2.0.50"
# }
```

## Common Issues and Solutions

### Issue: Buildpack Not Applied
**Symptom:** Claude Code CLI not available in container

**Solution:**
- Ensure `.claude-code-config.yml` exists in `src/main/resources/`
- Or set `CLAUDE_CODE_ENABLED=true` in manifest
- Check buildpack order (nodejs → claude-code → java)
- Verify config is packaged in JAR: `jar tf target/your-app.jar | grep claude-code-config`

### Issue: Configuration Not Found at Runtime
**Symptom:** Application can't find `.claude-code-config.yml`

**Solution:**
- Verify file is in `src/main/resources/`
- Check JAR contents: `jar tf target/your-app.jar | grep claude`
- Should see: `BOOT-INF/classes/.claude-code-config.yml`

### Issue: Environment Variables Not Set
**Symptom:** `CLAUDE_CLI_PATH` or `ANTHROPIC_API_KEY` not available

**Solution:**
- Check CF environment: `cf env my-app`
- Verify `.profile.d` script was created by buildpack
- Check logs during staging for errors

### Issue: MCP Servers Not Connecting
**Symptom:** Remote MCP servers show as failed in health check

**Solution:**
- Verify `NODE_EXTRA_CA_CERTS` is set for TLS certificates
- Check security groups allow outbound HTTPS
- Verify MCP server URLs are accessible from CF

## Testing Locally

Before deploying to Cloud Foundry, test locally:

```bash
# Build the JAR
mvn clean package

# Run with Spring Boot
java -jar target/your-app.jar \
  -DCLAUDE_CLI_PATH=/usr/local/bin/claude \
  -DANTHROPIC_API_KEY=sk-ant-xxxxx

# Or use environment variables
export CLAUDE_CLI_PATH=/usr/local/bin/claude
export ANTHROPIC_API_KEY=sk-ant-xxxxx
java -jar target/your-app.jar
```

## Best Practices Summary

✅ **DO:**
- Place config file in `src/main/resources/`
- Test the built JAR before deploying
- Use environment variables for sensitive data
- Verify health endpoints after deployment
- Verify config is packaged in JAR with `jar tf`
- Follow standard Spring Boot resource conventions

❌ **DON'T:**
- Hardcode API keys in config files
- Commit API keys to version control
- Skip testing the packaged JAR
- Deploy without verifying buildpack order
- Use complex Maven plugins when standard conventions work

## Publishing Your Own Version

If you need to fork and publish your own version of the wrapper library:

1. **Fork the repository** and make your changes
2. **Update the POM** with your own groupId and version
3. **Deploy to your own repository**:
   ```bash
   cd java-wrapper
   mvn clean deploy
   ```

See [java-wrapper/GCP_DEPLOYMENT.md](../../GCP_DEPLOYMENT.md) for detailed instructions on publishing to GCP Artifact Registry.

## Resources

- [Claude Code Buildpack README](../../../README.md)
- [DESIGN.md](../../../DESIGN.md)
- [Java Wrapper Documentation](../../README.md)
- [GCP Artifact Registry Deployment Guide](../../GCP_DEPLOYMENT.md)
- [Cloud Foundry Buildpack Documentation](https://docs.cloudfoundry.org/buildpacks/)
