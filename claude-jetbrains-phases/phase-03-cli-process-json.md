Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 3: CLI Process & JSON Protocol

## Prerequisites

Phase 2 complete (CLI detection works).

## Goal

Spawn a Claude Code CLI process with stream-json flags, send prompts via stdin, and parse the NDJSON response events from stdout. This is the communication backbone -- no UI yet, just the protocol layer with unit tests.

## Requirements

1. Create `ClaudeProcess.kt`:
   - Spawn CLI: `claude -p --output-format stream-json --input-format stream-json --include-partial-messages --session-id <uuid>`
   - Set working directory to the project root
   - Write JSON messages to stdin
   - Read NDJSON (newline-delimited JSON) events from stdout line by line
   - Capture stderr for error reporting
   - Support cancellation (kill the process)
   - Handle process exit codes and unexpected termination
   - Run all I/O on background threads (coroutines or IntelliJ's background task API)
2. Create `StreamJsonProtocol.kt`:
   - **IMPORTANT:** The actual stream-json format wraps events in an outer envelope:
     ```json
     {"type":"stream_event","event":{...},"session_id":"...","parent_tool_use_id":null,"uuid":"evt-xxx"}
     ```
   - The `event` field contains the actual Anthropic API event with types like:
     - `content_block_delta` with delta types: `text_delta` (text field), `input_json_delta` (partial_json field), `thinking_delta` (thinking field)
     - `content_block_start` with content_block containing tool_use info (name, id, input)
     - `content_block_stop` with tool results
     - `message_start`, `message_stop` for message boundaries
   - The final result event has a different structure:
     ```json
     {"type":"result","result":{"content":[...],"usage":{"input_tokens":N,"output_tokens":N},"cost":{"input":0.00,"output":0.00,"total":0.00}},"session_id":"..."}
     ```
   - Data classes should model both the outer envelope and inner event types
   - Parser function: `parseEvent(jsonLine: String): StreamEvent`
   - Use kotlinx.serialization or Gson for JSON parsing
   - **Input format** for `--input-format stream-json`: send prompts as:
     ```json
     {"type":"user","message":{"role":"user","content":"Your prompt here"},"session_id":"optional-session-id"}
     ```
3. Create `SessionManager.kt`:
   - Generate and track session UUIDs
   - Store session metadata (ID, start time, project path)
   - List past sessions (for future session history UI)
   - Support resume via `--resume <session-id>` and `--continue`
4. Write unit tests:
   - `StreamJsonProtocolTest.kt`: test parsing of each event type, malformed JSON, empty lines
   - `ClaudeProcessTest.kt`: test with mock process output (at minimum, test the event flow)
   - `SessionManagerTest.kt`: test session creation, listing, UUID generation

## File Structure (new/modified files)

```
src/main/kotlin/com/claudecode/jetbrains/cli/
├── ClaudeProcess.kt
├── StreamJsonProtocol.kt
└── SessionManager.kt

src/test/kotlin/com/claudecode/jetbrains/cli/
├── StreamJsonProtocolTest.kt
├── ClaudeProcessTest.kt
└── SessionManagerTest.kt
```

## Manual Testing

After completing this phase, do the following and tell me the results:

1. Run `./gradlew test` -- tell me how many tests pass/fail and any failure details
2. If Claude CLI is available, write a quick integration test or scratch file that:
   - Spawns a ClaudeProcess with a simple prompt like "What is 2+2? Reply with just the number."
   - Collects all events
   - Tell me the exact sequence of events received (types and subtypes)
   - Tell me if the final result event contains cost/token info
3. Test cancellation: spawn a process with a complex prompt, cancel it after 2 seconds, tell me if the process terminates cleanly
4. Test error handling: spawn a process with an invalid `--session-id` value, tell me what error event or exception occurs
