# Bundled Skills for Spring Boot Application

This directory contains Claude Skills that will be bundled with your Spring Boot application and deployed to Cloud Foundry.

## Location

**Important:** Skills should be in `src/main/resources/.claude/skills/`, not in the project root.

This follows Spring Boot resource conventions - everything in `src/main/resources/` gets packaged into `BOOT-INF/classes/` in the JAR automatically.

## How It Works

1. **Development**: Create Skills in `src/main/resources/.claude/skills/`
2. **Packaging**: Spring Boot packages resources into `BOOT-INF/classes/.claude/` (automatic)
3. **Deployment**: Buildpack extracts from `BOOT-INF/classes/.claude/` to `/home/vcap/app/.claude/`
4. **Runtime**: Claude CLI discovers Skills at `/home/vcap/app/.claude/skills/`

## Directory Structure

```
src/main/resources/
├── .claude/
│   └── skills/
│       ├── my-skill/           # Each Skill in its own directory
│       │   ├── SKILL.md        # Required: Skill definition
│       │   ├── reference.md    # Optional: Additional documentation
│       │   └── scripts/        # Optional: Helper scripts
│       │       └── helper.py
│       └── another-skill/
│           └── SKILL.md
├── .claude-code-config.yml
└── application.yml
```

## Creating a Skill

Each Skill needs a `SKILL.md` file with YAML frontmatter:

```markdown
---
name: my-skill-name
description: Brief description of what this Skill does and when to use it
---

# My Skill Name

## Instructions
Provide clear, step-by-step guidance for Claude.

## Examples
Show concrete examples of using this Skill.
```

### Requirements

- **name**: Lowercase letters, numbers, and hyphens only (max 64 chars)
- **description**: What the Skill does and when to use it (max 1024 chars)

## Example: Limericks Skill

See `limericks/SKILL.md` for a complete example that:
- Uses web search to find current news
- Crafts limericks with topical references
- Follows proper limerick structure (AABBA rhyme scheme)

## Maven Configuration

**No special Maven configuration needed!** Spring Boot automatically packages everything from `src/main/resources/` into the JAR.

Just ensure resource files aren't corrupted during filtering:

```xml
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
```

## Verification

After building, verify Skills are included in the JAR:

```bash
mvn clean package
jar tf target/claude-code-demo-1.0.0.jar | grep "BOOT-INF/classes/.claude/"
```

Expected output:
```
BOOT-INF/classes/.claude/
BOOT-INF/classes/.claude/skills/
BOOT-INF/classes/.claude/skills/limericks/
BOOT-INF/classes/.claude/skills/limericks/SKILL.md
```

## Deployment

When you deploy to Cloud Foundry, the buildpack will:

1. Extract the JAR (or detect exploded JAR)
2. Find `.claude/` in `BOOT-INF/classes/.claude/`
3. Copy it to `/home/vcap/app/.claude/`
4. Validate all Skills
5. Report discovered Skills in the staging logs:

```
-----> Configuring Claude Skills
       Found 1 bundled Skill(s)
       Validating Skills...
       Valid Skills: 1
       Installed Skills:
       - limericks
       Total Skills: 1
```

## Troubleshooting

### Skills not detected

**Symptom**: "No Skills found" in staging logs

**Solutions**:
1. Verify `.claude/` directory is in JAR: `jar tf target/*.jar | grep "BOOT-INF/classes/.claude/"`
2. Check Skills are in `src/main/resources/.claude/skills/` (not project root)
3. Rebuild: `mvn clean package`
4. Check SKILL.md has valid YAML frontmatter

### Invalid Skill warnings

**Symptom**: "WARNING: Skill missing SKILL.md" or "WARNING: SKILL.md missing name field"

**Solutions**:
1. Ensure each Skill directory has `SKILL.md`
2. Verify YAML frontmatter starts with `---`
3. Check `name` and `description` fields are present
4. Validate YAML syntax (no tabs, proper indentation)

## Resources

- [SKILLS.md](../../../../SKILLS.md) - Complete Skills guide
- [Claude Skills Documentation](https://code.claude.com/docs/en/skills)
- [DEPLOYMENT.md](../DEPLOYMENT.md) - Spring Boot deployment guide
