# CHANGELOG

All notable changes to this project will be documented in this file.

## [Unreleased]

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
