# Spring Boot Deployment Best Practices for Cloud Foundry

This document outlines best practices for deploying Spring Boot applications with Claude Code CLI integration to Cloud Foundry.

## Configuration File Placement

The `.claude-code-config.yml` file must be in **TWO locations**:

### 1. Application Root (Required for Buildpack Detection)
```
/my-app/.claude-code-config.yml
```

The Claude Code buildpack's detect script looks for this file in the BUILD_DIR during staging. Without it, the buildpack won't be applied.

### 2. src/main/resources (Required for Runtime)
```
/my-app/src/main/resources/.claude-code-config.yml
```

This copy gets packaged into the JAR file at `BOOT-INF/classes/.claude-code-config.yml` and is available to the application at runtime via the classpath.

## Maven Configuration

Add the following to your `pom.xml` to ensure YAML files are properly handled:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
        
        <!-- Ensure .claude-code-config.yml is included in the JAR -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-resources-plugin</artifactId>
            <configuration>
                <nonFilteredFileExtensions>
                    <nonFilteredFileExtension>yml</nonFilteredFileExtension>
                    <nonFilteredFileExtension>yaml</nonFilteredFileExtension>
                </nonFilteredFileExtensions>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Deployment Checklist

### Pre-Deployment

- [ ] **Configuration Files**
  - [ ] `.claude-code-config.yml` exists in application root
  - [ ] `.claude-code-config.yml` exists in `src/main/resources/`
  - [ ] Both files have identical content
  - [ ] MCP server configurations are correct

- [ ] **Build Verification**
  ```bash
  # Build the application
  mvn clean package
  
  # Verify config is in JAR
  jar tf target/your-app.jar | grep claude-code-config
  # Should output: BOOT-INF/classes/.claude-code-config.yml
  ```

- [ ] **Environment Variables**
  - [ ] `ANTHROPIC_API_KEY` is set (or will be set in CF)
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
      ANTHROPIC_API_KEY: ((your-api-key))
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
- Ensure `.claude-code-config.yml` exists in application root
- Or set `CLAUDE_CODE_ENABLED=true` in manifest
- Check buildpack order (nodejs → claude-code → java)

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

## File Synchronization

To keep both copies of `.claude-code-config.yml` in sync:

### Option 1: Manual Sync
```bash
# After editing the file in src/main/resources
cp src/main/resources/.claude-code-config.yml .
```

### Option 2: Maven Copy Plugin
Add to your `pom.xml`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-resources-plugin</artifactId>
    <executions>
        <execution>
            <id>copy-config-to-root</id>
            <phase>validate</phase>
            <goals>
                <goal>copy-resources</goal>
            </goals>
            <configuration>
                <outputDirectory>${basedir}</outputDirectory>
                <resources>
                    <resource>
                        <directory>src/main/resources</directory>
                        <includes>
                            <include>.claude-code-config.yml</include>
                        </includes>
                    </resource>
                </resources>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Option 3: Git Pre-commit Hook
```bash
# .git/hooks/pre-commit
#!/bin/bash
cp src/main/resources/.claude-code-config.yml .claude-code-config.yml
git add .claude-code-config.yml
```

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
- Keep both config files in sync
- Use `src/main/resources/` as the source of truth
- Version control both copies
- Test the built JAR before deploying
- Use environment variables for sensitive data
- Verify health endpoints after deployment

❌ **DON'T:**
- Hardcode API keys in config files
- Commit API keys to version control
- Forget to update both config copies
- Skip testing the packaged JAR
- Deploy without verifying buildpack order

## Resources

- [Claude Code Buildpack README](../../../README.md)
- [DESIGN.md](../../../DESIGN.md)
- [Java Wrapper Documentation](../../README.md)
- [Cloud Foundry Buildpack Documentation](https://docs.cloudfoundry.org/buildpacks/)
