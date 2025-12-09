# Conversational Sessions Testing Guide

## Overview

This guide shows how to test conversational sessions using the `ConversationDemoController` with HTTPie (or curl/Postman).

**Controller**: `ConversationDemoController` at `/demo/conversation`

**Features**:
- Easy-to-use testing endpoints
- Conversation history tracking
- Multi-turn demo mode
- Session status monitoring
- Active conversation listing

**Why HTTPie?**: HTTPie has cleaner syntax than curl, with automatic JSON formatting, syntax highlighting, and more intuitive parameters. Install with `brew install httpie` or `pip install httpie`.

## Quick Start

### 1. Start the Spring Boot Application

```bash
cd java-wrapper/examples/spring-boot-app
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

### 2. Test Basic Conversation Flow

```bash
# Start a conversation
http POST :8080/demo/conversation/start nickname="My Test Chat"

# Response: {"success":true,"sessionId":"abc-123","nickname":"My Test Chat",...}
# Save the sessionId!

# Ask a question
http POST :8080/demo/conversation/abc-123/ask message="What is the capital of France?"

# Response: {"success":true,"response":"The capital of France is Paris.",...}

# Ask a follow-up (context maintained!)
http POST :8080/demo/conversation/abc-123/ask message="What is its population?"

# Response: {"success":true,"response":"Paris has a population of approximately 2.1 million...",...}

# View conversation history
http GET :8080/demo/conversation/abc-123/history

# End the conversation
http DELETE :8080/demo/conversation/abc-123
```

## Endpoint Reference

### POST /demo/conversation/start

**Purpose**: Start a new conversation

**Request Body** (optional):
```json
{
  "model": "sonnet",
  "timeoutMinutes": 45,
  "nickname": "Code Review Session"
}
```

**Response**:
```json
{
  "success": true,
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "nickname": "Code Review Session",
  "message": "Conversation started! You can now send messages to /demo/conversation/550e8400-.../ask",
  "error": null
}
```

**HTTPie Example**:
```bash
# Default configuration
http POST :8080/demo/conversation/start

# With custom options
http POST :8080/demo/conversation/start \
    model=opus \
    timeoutMinutes:=60 \
    nickname="Long Code Review"
```

**curl Alternative**:
```bash
curl -X POST http://localhost:8080/demo/conversation/start \
  -H "Content-Type: application/json" \
  -d '{"model":"opus","timeoutMinutes":60,"nickname":"Long Code Review"}'
```

### POST /demo/conversation/{sessionId}/ask

**Purpose**: Send a message to a conversation

**Request Body**:
```json
{
  "message": "What is the capital of France?"
}
```

**Response**:
```json
{
  "success": true,
  "response": "The capital of France is Paris.",
  "history": [
    {
      "turn": 1,
      "question": "What is the capital of France?",
      "response": "The capital of France is Paris."
    }
  ],
  "error": null
}
```

**HTTPie Example**:
```bash
SESSION_ID="550e8400-e29b-41d4-a716-446655440000"

http POST :8080/demo/conversation/$SESSION_ID/ask message="Hello Claude!"
```

**curl Alternative**:
```bash
curl -X POST http://localhost:8080/demo/conversation/$SESSION_ID/ask \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello Claude!"}'
```

### GET /demo/conversation/{sessionId}/history

**Purpose**: View complete conversation history

**Response**:
```json
{
  "success": true,
  "sessionId": "550e8400-...",
  "nickname": "My Test Chat",
  "exchanges": [
    {
      "turn": 1,
      "question": "What is the capital of France?",
      "response": "The capital of France is Paris."
    },
    {
      "turn": 2,
      "question": "What is its population?",
      "response": "Paris has a population of approximately 2.1 million people..."
    }
  ],
  "totalMessages": 2,
  "message": "Conversation is active"
}
```

**HTTPie Example**:
```bash
http GET :8080/demo/conversation/$SESSION_ID/history
```

**curl Alternative**:
```bash
curl http://localhost:8080/demo/conversation/$SESSION_ID/history
```

### GET /demo/conversation/{sessionId}/status

**Purpose**: Check if conversation is still active

**Response**:
```json
{
  "active": true,
  "sessionId": "550e8400-...",
  "nickname": "My Test Chat",
  "messageCount": 2,
  "message": "Conversation is active and ready"
}
```

**HTTPie Example**:
```bash
http GET :8080/demo/conversation/$SESSION_ID/status
```

**curl Alternative**:
```bash
curl http://localhost:8080/demo/conversation/$SESSION_ID/status
```

### DELETE /demo/conversation/{sessionId}

**Purpose**: End a conversation

**Response**:
```json
{
  "success": true,
  "message": "Conversation ended successfully",
  "totalMessages": 2,
  "error": null
}
```

**HTTPie Example**:
```bash
http DELETE :8080/demo/conversation/$SESSION_ID
```

**curl Alternative**:
```bash
curl -X DELETE http://localhost:8080/demo/conversation/$SESSION_ID
```

### GET /demo/conversation/active

**Purpose**: List all active conversations

**Response**:
```json
{
  "success": true,
  "conversations": [
    {
      "sessionId": "550e8400-...",
      "nickname": "Code Review",
      "messageCount": 5,
      "active": true
    },
    {
      "sessionId": "660f9511-...",
      "nickname": "Q&A Session",
      "messageCount": 3,
      "active": true
    }
  ],
  "count": 2,
  "error": null
}
```

**HTTPie Example**:
```bash
http GET :8080/demo/conversation/active
```

**curl Alternative**:
```bash
curl http://localhost:8080/demo/conversation/active
```

### POST /demo/conversation/multi-turn

**Purpose**: Run an automated multi-turn demo showing context preservation

**Request Body** (optional):
```json
{
  "topic": "Python programming"
}
```

**Response**:
```json
{
  "success": true,
  "sessionId": "770a0622-...",
  "topic": "Python programming",
  "conversation": [
    {
      "turn": 1,
      "question": "Tell me about Python programming",
      "response": "Python is a high-level programming language..."
    },
    {
      "turn": 2,
      "question": "What are the main advantages of using it?",
      "response": "Python has several key advantages..."
    },
    {
      "turn": 3,
      "question": "Can you show me a simple code example?",
      "response": "Here's a simple Python example:\n\ndef greet(name):\n..."
    },
    {
      "turn": 4,
      "question": "Can you explain the code you just showed me in more detail?",
      "response": "Let me break down the code I showed you..."
    }
  ],
  "message": "Demo completed successfully. Notice how Claude maintained context throughout!",
  "error": null
}
```

**HTTPie Example**:
```bash
# Default topic (Python)
http POST :8080/demo/conversation/multi-turn

# Custom topic
http POST :8080/demo/conversation/multi-turn topic="Java Spring Boot"
```

**curl Alternative**:
```bash
curl -X POST http://localhost:8080/demo/conversation/multi-turn \
  -H "Content-Type: application/json" \
  -d '{"topic": "Java Spring Boot"}'
```

## Testing Scenarios

### Scenario 1: Basic Q&A

Test simple question-answer interaction:

```bash
# 1. Start (HTTPie automatically shows response)
http POST :8080/demo/conversation/start

# Copy the sessionId from response, then:
SESSION="<paste-session-id-here>"

# 2. Ask question
http POST :8080/demo/conversation/$SESSION/ask message="What is 2 + 2?"

# 3. Clean up
http DELETE :8080/demo/conversation/$SESSION
```

**Using curl with jq**:
```bash
SESSION=$(curl -s -X POST http://localhost:8080/demo/conversation/start | jq -r .sessionId)
curl -X POST http://localhost:8080/demo/conversation/$SESSION/ask \
  -H "Content-Type: application/json" \
  -d '{"message": "What is 2 + 2?"}' | jq .response
curl -X DELETE http://localhost:8080/demo/conversation/$SESSION
```

### Scenario 2: Context Preservation

Test that Claude remembers previous messages:

```bash
# Start and save session ID
http POST :8080/demo/conversation/start nickname="Context Test"
SESSION="<paste-session-id-here>"

# Establish context
http POST :8080/demo/conversation/$SESSION/ask message="My name is Alice"

# Test context recall
http POST :8080/demo/conversation/$SESSION/ask message="What is my name?"
# Should respond: "Your name is Alice" or similar

# Clean up
http DELETE :8080/demo/conversation/$SESSION
```

### Scenario 3: Follow-up Questions

Test natural conversation flow:

```bash
# Start conversation
http POST :8080/demo/conversation/start nickname="Follow-up Test"
SESSION="<paste-session-id-here>"

# Introduce topic
http POST :8080/demo/conversation/$SESSION/ask message="Tell me about JavaScript"

# Follow-up with pronoun
http POST :8080/demo/conversation/$SESSION/ask message="What are its key features?"
# "its" should refer to JavaScript - context is maintained!

# Another follow-up
http POST :8080/demo/conversation/$SESSION/ask message="Can you give me a code example?"

# Clean up
http DELETE :8080/demo/conversation/$SESSION
```

### Scenario 4: Session Timeout

Test session expiration:

```bash
# Create session with 10-second timeout (0.17 minutes)
http POST :8080/demo/conversation/start \
    timeoutMinutes:=0.17 \
    nickname="Timeout Test"
SESSION="<paste-session-id-here>"

# Send message (should work)
http POST :8080/demo/conversation/$SESSION/ask message="Hello"

# Wait for timeout
echo "Waiting 15 seconds for timeout..."
sleep 15

# Try to send message (should fail with 404)
http POST :8080/demo/conversation/$SESSION/ask message="Are you still there?"
# Should return: {"success":false, "error":"Conversation not found or has expired"}
```

### Scenario 5: Multiple Concurrent Conversations

Test managing multiple sessions:

```bash
# Start 3 conversations
http POST :8080/demo/conversation/start nickname="Python Session"
http POST :8080/demo/conversation/start nickname="Java Session"
http POST :8080/demo/conversation/start nickname="Rust Session"

# Save the session IDs
SESSION1="<python-session-id>"
SESSION2="<java-session-id>"
SESSION3="<rust-session-id>"

# List active conversations
http GET :8080/demo/conversation/active
# Should show 3 active conversations

# Send different topics to each
http POST :8080/demo/conversation/$SESSION1/ask message="Tell me about Python"
http POST :8080/demo/conversation/$SESSION2/ask message="Tell me about Java"
http POST :8080/demo/conversation/$SESSION3/ask message="Tell me about Rust"

# Each should maintain independent context
http POST :8080/demo/conversation/$SESSION1/ask message="What are its use cases?"
# Should answer about Python (not Java or Rust!)

# Clean up all
http DELETE :8080/demo/conversation/$SESSION1
http DELETE :8080/demo/conversation/$SESSION2
http DELETE :8080/demo/conversation/$SESSION3
```

### Scenario 6: Conversation History

Test history tracking:

```bash
# Start conversation
http POST :8080/demo/conversation/start nickname="History Test"
SESSION="<paste-session-id-here>"

# Send several messages
http POST :8080/demo/conversation/$SESSION/ask message="First question"
http POST :8080/demo/conversation/$SESSION/ask message="Second question"
http POST :8080/demo/conversation/$SESSION/ask message="Third question"
http POST :8080/demo/conversation/$SESSION/ask message="Fourth question"
http POST :8080/demo/conversation/$SESSION/ask message="Fifth question"

# View complete history
http GET :8080/demo/conversation/$SESSION/history
# Should show all 5 exchanges with turn numbers (1, 2, 3, 4, 5)

# Clean up
http DELETE :8080/demo/conversation/$SESSION
```

### Scenario 7: Auto Demo

Run the automated multi-turn demo:

```bash
# Run with default topic (Python)
http POST :8080/demo/conversation/multi-turn

# Run with custom topic
http POST :8080/demo/conversation/multi-turn topic="Rust programming"

# Run another with different topic
http POST :8080/demo/conversation/multi-turn topic="Java Spring Boot"

# Notice in the response how:
# - Turn 2 uses "it" to refer to the topic
# - Turn 4 references "the code you just showed me"
# - Context is perfectly maintained throughout all 4 turns!
```

## HTTPie Tips & Tricks

### Using Port Shorthand

HTTPie supports `:8080` as shorthand for `localhost:8080`:

```bash
# These are equivalent:
http POST :8080/demo/conversation/start
http POST localhost:8080/demo/conversation/start
http POST http://localhost:8080/demo/conversation/start
```

### JSON Parameters

HTTPie automatically handles JSON:

```bash
# String parameter
http POST :8080/demo/conversation/start nickname="My Chat"

# Number parameter (use := for non-string)
http POST :8080/demo/conversation/start timeoutMinutes:=60

# Multiple parameters
http POST :8080/demo/conversation/start \
    nickname="Long Session" \
    model=opus \
    timeoutMinutes:=90
```

### Output Formatting

```bash
# Default: colored output with formatting
http GET :8080/demo/conversation/$SESSION/history

# Body only (no headers)
http --body GET :8080/demo/conversation/$SESSION/history

# Headers only
http --headers GET :8080/demo/conversation/$SESSION/history

# Verbose (show request and response)
http --verbose POST :8080/demo/conversation/start nickname="Test"
```

### Saving Session IDs

```bash
# Save response to file
http POST :8080/demo/conversation/start nickname="Test" > response.json

# Extract sessionId using jq
SESSION=$(http POST :8080/demo/conversation/start nickname="Test" | jq -r .sessionId)

# Or use HTTPie's built-in filtering (requires httpie 3.0+)
http POST :8080/demo/conversation/start nickname="Test" --print=b | jq -r .sessionId
```

## Using with Postman

1. **Import Collection**: Create a new collection "Conversational Sessions"

2. **Create Environment Variable**:
   - Variable: `sessionId`
   - Type: `default`

3. **Add Requests**:

   - **Start Conversation**:
     - Method: POST
     - URL: `http://localhost:8080/demo/conversation/start`
     - Body: `{"nickname": "Postman Test"}`
     - Tests: 
       ```javascript
       pm.environment.set("sessionId", pm.response.json().sessionId);
       ```

   - **Send Message**:
     - Method: POST
     - URL: `http://localhost:8080/demo/conversation/{{sessionId}}/ask`
     - Body: `{"message": "Your question here"}`

   - **View History**:
     - Method: GET
     - URL: `http://localhost:8080/demo/conversation/{{sessionId}}/history`

   - **End Conversation**:
     - Method: DELETE
     - URL: `http://localhost:8080/demo/conversation/{{sessionId}}`

## Key Features to Test

### ✅ Context Preservation

- Ask "Tell me about X"
- Follow up with "What are its benefits?" (pronoun reference)
- Verify Claude knows "its" refers to X

### ✅ Multi-turn Coherence

- Have a 5+ message conversation
- Each message should build on previous ones
- View history to see full conversation flow

### ✅ Independent Sessions

- Create multiple sessions
- Each discusses different topic
- Verify no cross-contamination of context

### ✅ Error Handling

- Try sending to non-existent session → 404
- Try empty message → 400
- Wait for timeout → 404 on next message

### ✅ Session Management

- Create, use, and delete sessions
- List active sessions
- Verify cleanup after delete

## Tips

1. **Install HTTPie**: `brew install httpie` (Mac) or `pip install httpie` (Linux/Windows)
2. **Save Session IDs**: Copy sessionId from response or pipe to jq
3. **Use Port Shorthand**: `:8080` is cleaner than `http://localhost:8080`
4. **Test Timeouts**: Use short timeouts (e.g., 0.17 minutes = ~10 seconds) for testing
5. **Monitor Logs**: Watch application logs to see session lifecycle
6. **Try Multi-turn Demo**: Great way to see context preservation in action
7. **HTTPie Advantages**: Automatic JSON, syntax highlighting, intuitive syntax

## Comparison: Demo vs Production Controller

| Feature | ConversationDemoController | ConversationController |
|---------|---------------------------|------------------------|
| Base Path | `/demo/conversation` | `/api/claude/sessions` |
| Purpose | Testing & demonstration | Production API |
| History Tracking | ✅ In-memory history | ❌ Client manages |
| Nicknames | ✅ Friendly names | ❌ Just UUIDs |
| Multi-turn Demo | ✅ Built-in demo | ❌ N/A |
| Active List | ✅ Lists all active | ❌ N/A |
| Use Case | Manual testing | Production applications |

## Next Steps

After testing with the demo controller, you can:

1. Use the production API at `/api/claude/sessions` for real applications
2. Integrate conversational sessions into your Spring Boot app
3. Build UIs that leverage the conversational context
4. Deploy to Cloud Foundry with confidence

Happy testing! 🚀

