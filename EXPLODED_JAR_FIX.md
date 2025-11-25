# Exploded Spring Boot JAR Support - Implementation Summary

## Issue Identified

The buildpack was not installing MCP servers from `.claude-code-config.yml` files embedded in Spring Boot JAR deployments.

### Root Cause

When deploying Spring Boot JARs to Cloud Foundry:
1. The Java buildpack runs **before** the Claude Code supply buildpack
2. The Java buildpack **explodes** (unzips) the JAR file into the `BUILD_DIR`
3. The original JAR file no longer exists as a `.jar` file
4. The JAR contents are expanded into directories: `BOOT-INF/`, `META-INF/`, `org/`, etc.
5. The original buildpack logic only looked for `.jar` files and tried to extract the config using `unzip`

### Discovery Process

Through debugging, we found:
```
BUILD_DIR contents:
drwxr-xr-x 4 vcap vcap   71 Nov 25 14:31 BOOT-INF
drwxr-xr-x 4 vcap vcap   54 Nov 25 14:31 META-INF
drwxr-xr-x 3 vcap vcap   29 Feb  1  1980 org
drwxr-xr-x 2 vcap vcap   32 Nov 25 14:31 .profile.d
Pattern /tmp/app/*.jar is not a file (no JARs found)
```

The config file location in a Spring Boot JAR:
```
BOOT-INF/classes/.claude-code-config.yml  (when placed in src/main/resources/)
```

## Solution Implemented

Updated `bin/supply` to handle **exploded Spring Boot JARs** by checking for the config file in the exploded directory structure.

### Changes to bin/supply

```bash
# Extract .claude-code-config.yml from JAR if it's a Spring Boot application
if [ ! -f "${BUILD_DIR}/.claude-code-config.yml" ]; then
    echo "       Checking for config in JAR files..."
    
    # First, check if this is an exploded Spring Boot JAR (Java buildpack explodes JARs)
    if [ -f "${BUILD_DIR}/BOOT-INF/classes/.claude-code-config.yml" ]; then
        echo "       Found .claude-code-config.yml in exploded JAR (BOOT-INF/classes/)"
        cp "${BUILD_DIR}/BOOT-INF/classes/.claude-code-config.yml" "${BUILD_DIR}/.claude-code-config.yml"
        echo "       Copied .claude-code-config.yml to application root"
    else
        # Fall back to looking for actual JAR files (in case Java buildpack runs after us)
        jar_count=0
        for jar_file in "${BUILD_DIR}"/*.jar; do
            if [ -f "${jar_file}" ]; then
                jar_count=$((jar_count + 1))
                echo "       Found JAR #${jar_count}: $(basename ${jar_file})"
                # Try to extract .claude-code-config.yml from the JAR root
                if unzip -p "${jar_file}" .claude-code-config.yml > "${BUILD_DIR}/.claude-code-config.yml" 2>/dev/null; then
                    echo "       Extracted .claude-code-config.yml from JAR root"
                    break
                fi
                # Try to extract from Spring Boot JAR structure (BOOT-INF/classes/)
                if unzip -p "${jar_file}" BOOT-INF/classes/.claude-code-config.yml > "${BUILD_DIR}/.claude-code-config.yml" 2>/dev/null; then
                    echo "       Extracted .claude-code-config.yml from BOOT-INF/classes/"
                    break
                fi
            fi
        done
        if [ ${jar_count} -eq 0 ]; then
            echo "       No JAR files or exploded JAR config found"
        fi
    fi
fi
```

### Logic Flow

1. **Primary path**: Check for exploded JAR at `BUILD_DIR/BOOT-INF/classes/.claude-code-config.yml`
   - This handles the most common case in Cloud Foundry deployments
   - Java buildpack has already exploded the JAR before our supply buildpack runs
   
2. **Fallback path**: Look for actual `.jar` files and extract
   - Supports both JAR root and BOOT-INF/classes/ locations within the JAR
   - Handles edge cases where buildpack ordering might differ

## Configuration File Location

### Recommended Approach: src/main/resources/

**Location:** `src/main/resources/.claude-code-config.yml`

**Advantages:**
- ✅ No Maven/Gradle plugin configuration needed
- ✅ Spring Boot automatically packages it in the JAR
- ✅ Standard Spring Boot convention for resources
- ✅ Buildpack automatically finds it in exploded JAR

**Maven setup:**
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

That's it! No additional configuration needed. Just place your `.claude-code-config.yml` in `src/main/resources/` and Spring Boot handles the rest.

## Verification

After deploying, the staging logs now show:

```
-----> Claude Code CLI Buildpack
Validating environment...
Checking prerequisites...
API key format validated
Installing Node.js...
Using cached Node.js v20.11.0
Extracting Node.js...
Node.js v20.11.0 installed successfully
Installing Claude Code CLI...
Installing @anthropic-ai/claude-code...
added 5 packages in 2s
Claude Code CLI installed successfully
Configuring environment...
Creating configuration files...
Checking for config in JAR files...
Found .claude-code-config.yml in exploded JAR (BOOT-INF/classes/)
Copied .claude-code-config.yml to application root
-----> Configuring MCP servers
Found .claude-code-config.yml configuration file
Setting log level: info
Setting Claude Code version: latest
Setting Claude Code model: sonnet
Generated .claude.json with MCP server configuration
Created .claude.json from .claude-code-config.yml
Validated .claude.json with 1 MCP server(s)
-----> Configuring Claude settings
-----> Generating Claude settings configuration
Using default settings with extended thinking enabled
Created /tmp/app/.claude/settings.json with default configuration
Verifying installation...
Installation verified successfully
Claude Code CLI installed successfully!
Version: 2.0.53 (Claude Code)
```

Key indicators of success:
- ✅ `Found .claude-code-config.yml in exploded JAR (BOOT-INF/classes/)`
- ✅ `Created .claude.json from .claude-code-config.yml`
- ✅ `Validated .claude.json with 1 MCP server(s)`

## Documentation Updates

### DESIGN.md
- Updated Spring Boot JAR Support section
- Documented exploded JAR handling
- Explained buildpack order and timing
- Listed all supported config file locations

### QUICKSTART.md
- Added clear "Option 1" (recommended) and "Option 2" (alternative) sections
- Simplified instructions for the recommended approach
- Included verification steps for both approaches
- Reduced complexity for new users

### java-wrapper/README.md
- Added configuration file location documentation
- Recommended src/main/resources/ approach
- Explained Spring Boot automatic packaging
- Simplified example configuration

## Testing Results

### Test Deployment
- **Application:** claude-code-demo (Spring Boot)
- **Config Location:** `BOOT-INF/classes/.claude-code-config.yml`
- **MCP Servers:** 1 (github SSE server)
- **Result:** ✅ SUCCESS

### Staging Output
```
Found .claude-code-config.yml in exploded JAR (BOOT-INF/classes/)
Copied .claude-code-config.yml to application root
Validated .claude.json with 1 MCP server(s)
```

### Application Startup
```
Started DemoApplication in 2.53 seconds (process running for 3.404)
ClaudeCodeExecutor initialized
ClaudeCodeController created
```

## Commits

1. **78960d8** - Fix: Extract .claude-code-config.yml from Spring Boot JAR structure (BOOT-INF/classes/)
   - Initial attempt to extract from JAR files
   - Added support for BOOT-INF/classes/ path in JAR extraction

2. **3bf68ed** - Add debug logging to JAR extraction logic
   - Added logging to diagnose why config wasn't being found
   - Discovered that Java buildpack explodes JARs

3. **70fc39b** - Fix: Handle exploded Spring Boot JARs - copy config from BOOT-INF/classes/
   - Implemented the actual fix for exploded JARs
   - Added primary path for exploded JAR detection
   - Kept fallback path for edge cases

4. **9fe9b4a** - Update documentation to reflect exploded JAR support
   - Updated DESIGN.md, QUICKSTART.md, and java-wrapper/README.md
   - Documented both approaches with recommendations
   - Simplified instructions for users

## Impact

### Before Fix
- ❌ Config files in Spring Boot JARs were not found
- ❌ MCP servers were not installed
- ❌ Empty configuration generated
- ❌ Users had to use environment variables or complex workarounds

### After Fix
- ✅ Config files automatically found in exploded JARs
- ✅ MCP servers properly installed and configured
- ✅ Settings.json generated with correct values
- ✅ Simple, standard Spring Boot resource approach works
- ✅ Supports both BOOT-INF/classes/ and JAR root locations

## Lessons Learned

1. **Buildpack Ordering Matters:** The Java buildpack runs first and transforms the application structure
2. **Test in Real Environment:** Local testing with JAR files doesn't reflect Cloud Foundry staging behavior
3. **Add Debug Logging:** Essential for diagnosing staging issues remotely
4. **Document Both Approaches:** Users may have different requirements/constraints
5. **Recommend Simple Solutions:** Prefer standard conventions over complex workarounds

## Future Considerations

1. Consider adding validation that warns users if config file is in unexpected location
2. Add integration tests that verify both deployment approaches
3. Document buildpack ordering requirements more prominently
4. Consider supporting Gradle equivalent of maven-antrun-plugin

## Related Files

- `bin/supply` - Main supply script with JAR handling logic
- `bin/detect` - Detection script (already supported JAR inspection)
- `lib/mcp_configurator.sh` - MCP configuration generation
- `DESIGN.md` - Architecture documentation
- `QUICKSTART.md` - User quick start guide
- `java-wrapper/README.md` - Java wrapper library documentation

## Branch

**Branch:** `timeout`  
**Remote:** https://github.com/cpage-pivotal/claude-code-buildpack/tree/timeout

