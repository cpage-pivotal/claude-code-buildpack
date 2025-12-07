#!/usr/bin/env bash
# Debug script for Claude Code settings configuration
# This script helps troubleshoot marketplace and MCP configuration issues

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║        Claude Code Settings Debug Tool                        ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

echo "1. Environment Variables"
echo "========================"
echo "   HOME = $HOME"
echo "   CLAUDE_CLI_PATH = $CLAUDE_CLI_PATH"
echo "   ANTHROPIC_API_KEY = ${ANTHROPIC_API_KEY:+[SET - length: ${#ANTHROPIC_API_KEY}]}"
echo "   ANTHROPIC_API_KEY = ${ANTHROPIC_API_KEY:-[NOT SET]}"
echo ""

echo "2. Source Files (App Directory)"
echo "================================"
if [ -f "/home/vcap/app/.claude/settings.json" ]; then
    echo "   ✓ /home/vcap/app/.claude/settings.json EXISTS"
    echo ""
    echo "   Contents:"
    cat /home/vcap/app/.claude/settings.json | sed 's/^/   │ /'
else
    echo "   ✗ /home/vcap/app/.claude/settings.json DOES NOT EXIST"
fi
echo ""

if [ -f "/home/vcap/app/.claude.json" ]; then
    echo "   ✓ /home/vcap/app/.claude.json EXISTS (MCP config)"
else
    echo "   ✗ /home/vcap/app/.claude.json DOES NOT EXIST"
fi
echo ""

echo "3. Profile.d Script"
echo "==================="
if [ -f "/home/vcap/app/.profile.d/claude-code-env.sh" ]; then
    echo "   ✓ /home/vcap/app/.profile.d/claude-code-env.sh EXISTS"
    echo ""
    if grep -q "Copy Claude settings" /home/vcap/app/.profile.d/claude-code-env.sh; then
        echo "   ✓ Copy logic found in script"
        echo ""
        echo "   Copy logic section:"
        grep -A 12 "Copy Claude settings" /home/vcap/app/.profile.d/claude-code-env.sh | sed 's/^/   │ /'
    else
        echo "   ✗ Copy logic NOT found in script"
        echo "   → This means you're using an older version of the buildpack"
    fi
else
    echo "   ✗ /home/vcap/app/.profile.d/claude-code-env.sh DOES NOT EXIST"
fi
echo ""

echo "4. Target Files (HOME Directory)"
echo "================================="
if [ -d "$HOME/.claude" ]; then
    echo "   ✓ $HOME/.claude directory EXISTS"
    echo ""
    echo "   Directory contents:"
    ls -lah $HOME/.claude/ 2>/dev/null | sed 's/^/   │ /' || echo "   │ (empty or unreadable)"
else
    echo "   ✗ $HOME/.claude directory DOES NOT EXIST"
fi
echo ""

if [ -f "$HOME/.claude/settings.json" ]; then
    echo "   ✓ $HOME/.claude/settings.json EXISTS"
    echo ""
    echo "   Contents:"
    cat $HOME/.claude/settings.json | sed 's/^/   │ /'
else
    echo "   ✗ $HOME/.claude/settings.json DOES NOT EXIST"
    echo "   → This is why 'claude plugin marketplace list' shows no marketplaces"
fi
echo ""

echo "5. Manual Copy Test"
echo "==================="
if [ -f "/home/vcap/app/.claude/settings.json" ]; then
    echo "   Attempting manual copy..."
    mkdir -p $HOME/.claude 2>/dev/null

    if cp /home/vcap/app/.claude/settings.json $HOME/.claude/settings.json 2>/dev/null; then
        echo "   ✓ Manual copy SUCCEEDED"

        if [ -f "/home/vcap/app/.claude.json" ]; then
            cp /home/vcap/app/.claude.json $HOME/.claude.json 2>/dev/null
            echo "   ✓ Also copied .claude.json (MCP config)"
        fi
    else
        echo "   ✗ Manual copy FAILED"
        echo "   → Check permissions"
    fi
else
    echo "   ✗ Source file doesn't exist, cannot test copy"
fi
echo ""

echo "6. Claude CLI Test"
echo "=================="
if command -v claude > /dev/null 2>&1; then
    echo "   ✓ Claude CLI is available"
    echo ""
    echo "   Testing marketplace list:"
    echo "   ─────────────────────────"
    claude plugin marketplace list 2>&1 | sed 's/^/   │ /'
else
    echo "   ✗ Claude CLI is NOT available"
    echo "   → PATH = $PATH"
fi
echo ""

echo "7. Recommendations"
echo "=================="
if [ ! -f "$HOME/.claude/settings.json" ]; then
    if [ -f "/home/vcap/app/.claude/settings.json" ]; then
        echo "   → Settings file exists in app directory but not in HOME"
        echo "   → The .profile.d script should copy it at startup"
        echo "   → Possible causes:"
        echo "      • Buildpack version doesn't have copy logic (update buildpack)"
        echo "      • .profile.d script hasn't run yet (restart app)"
        echo "      • Script condition failed (check if HOME != /home/vcap/app)"
        echo ""
        echo "   Quick fix: Run this command to copy manually:"
        echo "      mkdir -p $HOME/.claude && cp /home/vcap/app/.claude/settings.json $HOME/.claude/settings.json"
    else
        echo "   → Settings file doesn't exist in app directory either"
        echo "   → The buildpack didn't generate settings during staging"
        echo "   → Check your .claude-code-config.yml file"
    fi
else
    echo "   ✓ Everything looks good!"
    echo "   → Settings file exists in HOME directory"
    echo "   → Marketplaces should be working"
fi
echo ""

echo "╚════════════════════════════════════════════════════════════════╝"
