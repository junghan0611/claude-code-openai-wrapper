# CHANGELOG

All notable changes to this project will be documented in this file.

## [Unreleased]

## [2026-02-18] - Claude Sonnet 4.6 Support & Adaptive Thinking

### Added

- **Claude Sonnet 4.6** (`claude-sonnet-4-6`) support - fast + intelligent with agentic search ($3/$15 per 1M tokens)
- **Adaptive thinking** for Claude 4.6 models (`thinking: {type: "adaptive"}`)
- **`X-Claude-Thinking-Type` header** - explicitly control thinking mode (`adaptive` or `enabled`)
- **New stop reasons**: `refusal` and `model_context_window_exceeded` for Claude 4.6 compatibility
- **`CLAUDE_4_6_MODELS` constant** for feature detection (adaptive thinking, no prefill, etc.)

### Changed

- **Default model** changed from `claude-opus-4-6` to `claude-sonnet-4-6` (better cost/performance ratio)
- **Fast model** changed from `claude-haiku-4-5-20251001` to `claude-sonnet-4-6`
- Claude 4.6 models automatically use adaptive thinking instead of budget_tokens (deprecated)
- `max_tokens`/`max_thinking_tokens` ignored for 4.6 models (adaptive thinking takes over)

### Migration Notes

- **Prefill removal**: Claude 4.6 models return 400 error on assistant message prefills
- **budget_tokens deprecated**: Use `thinking: {type: "adaptive"}` with effort parameter instead
- **JSON escaping**: Standard JSON parsers handle differences automatically
- See [Anthropic Migration Guide](https://docs.anthropic.com/en/docs/migration-guide) for full details

### Environment Variables

```bash
DEFAULT_MODEL=claude-sonnet-4-6   # New default (was claude-opus-4-6)
```

## [2026-02-14] - SDK Upgrade & Claude Opus 4.6 Support

### Added

- **Claude Opus 4.6** (`claude-opus-4-6`) support - most intelligent model with 200K/1M context
- **Model selection CLI**: `./run.sh -m opus|sonnet|haiku` for easy model switching
- **Effort control**: `X-Claude-Effort` header (low/medium/high/max) for thinking depth
- **Backward-compatible thinking API**: `X-Claude-Max-Thinking-Tokens` header now maps to new ThinkingConfig

### Changed

- **Claude Agent SDK** upgraded from v0.1.18 to v0.1.36 (bundled CLI 2.1.42)
- **Default model** changed from `claude-sonnet-4-5-20250929` to `claude-opus-4-6`
- `max_thinking_tokens` internally mapped to new `thinking` config (SDK deprecation)
- Opus 4.5 moved to legacy model status

### Environment Variables

```bash
DEFAULT_MODEL=claude-opus-4-6     # New default (was claude-sonnet-4-5-20250929)
```

## [2026-01-01] - Performance Optimization

### Added

- **Independent Mode** (`CLAUDE_INDEPENDENT_MODE=true`)
  - Disables MCP servers, plugins, and slash commands
  - Reduces startup overhead by ~31%
  - Default: `true`

- **Minimal Tools Mode** (`CLAUDE_MINIMAL_TOOLS=true`)
  - Reduces tool set from 18 to 8 core tools
  - Improves response time by ~39%
  - Default: `true`
  - Core tools: `Bash`, `Glob`, `Grep`, `Read`, `Edit`, `Write`, `WebFetch`, `WebSearch`
  - Disabled tools: `Task`, `TaskOutput`, `LSP`, `AskUserQuestion`, `TodoWrite`, `NotebookEdit`, `EnterPlanMode`, `ExitPlanMode`, `Skill`, `KillShell`

### Changed

- Startup banner now uses clean `print()` output instead of logger prefix
- Combined performance improvement: **~57% faster** (10s → 4.3s)

### Performance Summary

| Mode | Time | Tools | Improvement |
|------|------|-------|-------------|
| Default (Full) | ~10s | 18 | - |
| Independent Mode | 6.1s | 18 | -31% |
| + Minimal Tools | 4.3s | 8 | -57% (cumulative) |

### Environment Variables

```bash
# Performance (both default to true)
CLAUDE_INDEPENDENT_MODE=true   # Disable MCP/plugins
CLAUDE_MINIMAL_TOOLS=true      # Use 8 core tools only

# Other settings
CLAUDE_CWD=/path/to/workspace  # Working directory
DEFAULT_MODEL=claude-sonnet-4-5-20250929
MAX_TIMEOUT=300000             # 5 minutes
```
