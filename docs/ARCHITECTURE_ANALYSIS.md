# Claude Code OpenAI Wrapper Architecture Analysis

> Last Updated: 2026-01-01
> Related Issues: ccow-ayl (closed), ccow-bhb (closed)

## 1. System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Doom Emacs                               │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ gptel                                                    │   │
│  │  - gptel-make-openai "Claude-Code"                       │   │
│  │  - :host "localhost:8000"                                │   │
│  │  - Advice: gptel--claude-code-add-enable-tools           │   │
│  └────────────────────────┬────────────────────────────────┘   │
└───────────────────────────┼─────────────────────────────────────┘
                            │ HTTP (OpenAI format)
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│               claude-code-openai-wrapper                        │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ main.py (FastAPI)                                        │   │
│  │  - /v1/chat/completions                                  │   │
│  │  - session_manager.process_messages()                    │   │
│  └────────────────────────┬────────────────────────────────┘   │
│  ┌────────────────────────▼────────────────────────────────┐   │
│  │ claude_cli.py                                            │   │
│  │  - run_completion(session_id, continue_session)          │   │
│  │  - Uses claude-agent-sdk query() function                │   │
│  └────────────────────────┬────────────────────────────────┘   │
└───────────────────────────┼─────────────────────────────────────┘
                            │ claude-agent-sdk
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│               claude-agent-sdk-python                           │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ query() - Stateless queries (currently used)             │   │
│  │ ClaudeSDKClient - Bidirectional conversation (unused)    │   │
│  │                                                          │   │
│  │ ClaudeAgentOptions:                                      │   │
│  │  - resume: str (session resume)                          │   │
│  │  - continue_conversation: bool                           │   │
│  │  - hooks, mcp_servers, fork_session, output_format ...   │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## 2. Performance Problem (Resolved)

### 2.1 Original Issue

| Metric | Before | Expected | Gap |
|--------|--------|----------|-----|
| Response time (no tools) | 10s | 2-3s | 7-8s overhead |

### 2.2 Root Cause

**SDK `query()` function is intentionally stateless:**
- Starts new CLI process per call (by design)
- No session_id passing is NOT a bug - it's intended

```
# query.py documentation:
# - Stateless: Each query is independent, no conversation state
# - No interrupts: Cannot interrupt or send follow-up messages
```

### 2.3 Time Breakdown

```
Total 10s =
  CLI process start (3-5s) +
  SDK initialization (1s) +
  API network (2-3s) +
  Model generation (2-3s)
```

### 2.4 Two Session Concepts (Caution)

| Aspect | Wrapper session_id | Claude CLI resume |
|--------|-------------------|-------------------|
| Location | `session_manager.py` | `claude-agent-sdk` |
| Purpose | Store conversation history | Reuse CLI process |
| Method | Accumulate messages array | Maintain CLI internal context |

**Wrapper already sends full conversation history via messages** → Using CLI resume risks context duplication

## 3. Solutions Implemented

### 3.1 Approach Comparison

| Approach | Description | Complexity | Effect |
|----------|-------------|------------|--------|
| Environment optimization | Skip version check, etc. | Low | Low |
| CLI options | --no-session-persistence, etc. | Low | Low-Medium |
| ClaudeSDKClient | Maintain process, bidirectional | High | High |
| Process pooling | Keep warm processes ready | Medium | High |

### 3.2 Independent Mode (CLAUDE_INDEPENDENT_MODE) ✅ Implemented

**Performance Results:**
| Mode | Time | Improvement |
|------|------|-------------|
| Default (with MCP) | 6.1s | - |
| Independent mode | 4.2s | **-31%** |

**Environment Variable:**
```bash
CLAUDE_INDEPENDENT_MODE=true  # Default: true
```

**Applied CLI Options:**
```python
extra_args = {
    "strict-mcp-config": None,       # No MCP server connections
    "mcp-config": "empty-mcp.json",  # Empty MCP config
    "disable-slash-commands": None,   # No slash command loading
    "setting-sources": "",            # No external settings
}
```

**Preserved Features:**
- ✅ Working directory (cwd)
- ✅ Built-in tools: WebSearch, WebFetch, Bash, Read, Edit, Write, etc.
- ✅ Model selection, system prompt

**Disabled Features:**
- ❌ MCP servers (context7, github, etc.)
- ❌ Plugins/skills
- ❌ Slash commands

### 3.3 Minimal Tools Mode (CLAUDE_MINIMAL_TOOLS) ✅ Implemented

**Performance Results:**
| Mode | Time | Tools | Improvement |
|------|------|-------|-------------|
| Independent mode (baseline) | 7.1s | 18 | - |
| + Minimal tools | 4.3s | 8 | **-39%** |

**Environment Variable:**
```bash
CLAUDE_MINIMAL_TOOLS=true  # Default: true
```

**Preserved Tools (8):**
- Bash, Glob, Grep (shell/search)
- Read, Edit, Write (file operations)
- WebFetch, WebSearch (web search)

**Disabled Tools (10):**
- Task, TaskOutput (agent spawning)
- LSP (IDE integration)
- AskUserQuestion, TodoWrite (interactive)
- NotebookEdit (Jupyter)
- EnterPlanMode, ExitPlanMode (planning mode)
- Skill, KillShell (misc)

**How It Works:**
Tool definitions are included in the system prompt, so fewer tools = fewer tokens = faster response

### 3.4 Cumulative Performance Improvement

| Stage | Time | Improvement | Cumulative |
|-------|------|-------------|------------|
| Default (Full) | ~10s | - | - |
| Independent Mode | 6.1s | -31% | -31% |
| + Minimal Tools | 4.3s | -39% | **-57%** |

### 3.5 Future: ClaudeSDKClient Consideration

```python
# Current: query() - stateless, new process each time
async for message in query(prompt=prompt, options=options):
    yield message

# Future: ClaudeSDKClient - persistent process
async with ClaudeSDKClient(options=options) as client:
    await client.query(prompt)
    async for msg in client.receive_response():
        yield msg
```

**Pros:**
- Process reuse eliminates warm-up
- Additional features: interrupts, permission mode changes

**Cons:**
- Requires architecture changes
- Increased connection management complexity

## 4. claude-agent-sdk Feature Analysis

### 4.1 Currently Used

| Feature | Location | Status |
|---------|----------|--------|
| `query()` | claude_cli.py | In use |
| `ClaudeAgentOptions` | claude_cli.py | Partially used |
| `extra_args` | claude_cli.py | In use (independent/minimal mode) |

### 4.2 Unused Features

| Feature | SDK Support | Purpose |
|---------|-------------|---------|
| `ClaudeSDKClient` | client.py | Bidirectional conversation, interrupts |
| `hooks` | PreToolUse, PostToolUse | Intercept before/after tool execution |
| `mcp_servers` | in-process MCP | Custom tool definitions |
| `fork_session` | Session fork | Conversation branching |
| `output_format` | Structured output | JSON schema responses |
| `enable_file_checkpointing` | File checkpoint | Undo capability |
| `can_use_tool` | Permission callback | Dynamic permission control |
| `set_permission_mode` | Permission mode change | Runtime permission switching |

### 4.3 ResultMessage Information

```python
@dataclass
class ResultMessage:
    session_id: str           # Session ID returned
    total_cost_usd: float     # Cost information
    usage: dict               # Token usage
    duration_ms: int          # Response time
    num_turns: int            # Number of turns
```

## 5. gptel Integration Analysis

### 5.1 Current Implementation (ai-gptel.el)

```elisp
;; Backend definition
(setq gptel-claude-code-backend
      (gptel-make-openai "Claude-Code"
        :host "localhost:8000"
        :endpoint "/v1/chat/completions"
        :protocol "http"
        :stream t
        :key "not-needed"
        :models '((claude-sonnet-4-5-20250929 ...)
                  (claude-opus-4-5-20251101 ...)
                  (claude-haiku-4-5-20251001 ...))))

;; Auto-add enable_tools (Advice)
(advice-add 'gptel--request-data :around #'gptel--claude-code-add-enable-tools)

;; Server status check
(defun gptel--claude-code-server-available-p ()
  "Check if Claude-Code wrapper server is running.")
```

### 5.2 Potential Enhancements

| Feature | Implementation | Priority |
|---------|----------------|----------|
| Session ID passing | `:request-params` or headers | P1 |
| Cost/token display | Response headers → gptel post-processing hook | P2 |
| Tool result formatting | Markdown conversion in wrapper | P2 |
| Interrupt | SSE cancel support | P3 |

## 6. Roadmap

### Phase 1: Performance Optimization ✅ Complete
- [x] CLI loading optimization research → Independent mode
- [x] Tool minimization → 8 core tools only
- [ ] ClaudeSDKClient adoption (long-term)
- [ ] Process pooling evaluation (long-term)

### Phase 2: SDK Feature Utilization
- [ ] Include cost/token info in responses
- [ ] Return actual usage data (replace estimates)
- [ ] Add session management API (`/v1/sessions`)

### Phase 3: Advanced Features
- [ ] ClaudeSDKClient adoption (bidirectional)
- [ ] Hook system utilization (tool filtering)
- [ ] Custom MCP tool support

### Phase 4: gptel Enhancement
- [ ] Cost display gptel hook
- [ ] Session status modeline display
- [ ] Tool result org-mode formatting

## 7. Related Files

| File | Role |
|------|------|
| `src/main.py` | FastAPI endpoints |
| `src/claude_cli.py` | SDK wrapper |
| `src/session.py` | Session management |
| `src/empty-mcp.json` | Empty MCP config for independent mode |
| `~/sync/emacs/doomemacs-config/lisp/ai-gptel.el` | gptel configuration |

## 8. References

- [Claude Agent SDK Python](https://docs.anthropic.com/en/docs/claude-code/sdk/sdk-python)
- [gptel source](https://github.com/karthink/gptel)
- See CHANGELOG.md for version history
