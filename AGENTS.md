# Agent Instructions

## Documentation & Commit Language

**IMPORTANT:** All documentation, commit messages, and code comments must be written in **English**.
- Commit messages: English only
- Documentation (*.md): English only
- Code comments: English only
- Conversation with user: Korean is OK

## Project Purpose (ko branch)

This fork is a wrapper for using Claude Code flat-rate subscription in **Doom Emacs + gptel** environment.

### Key Features
- **OpenAI-compatible API**: Provides Claude Code as OpenAI API format at `localhost:8000`
- **gptel Integration**: Direct use as Emacs gptel backend
- **Claude Tools**: Use Read, Write, Bash, WebSearch, etc. from within Emacs

### Architecture
```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────┐
│  Doom Emacs     │────▶│  Claude Wrapper  │────▶│ Claude Code │
│  (gptel)        │     │  (localhost:8000)│     │ (Anthropic) │
└─────────────────┘     └──────────────────┘     └─────────────┘
```

### Related Configuration
- gptel config: `~/sync/emacs/doomemacs-config/lisp/ai-gptel.el`
- Docker config: `~/sync/emacs/doomemacs-config/docker/claude-wrapper/`

---

## Performance Modes

Two environment variables control performance optimization:

| Variable | Default | Effect |
|----------|---------|--------|
| `CLAUDE_INDEPENDENT_MODE` | `true` | Disable MCP/plugins (-31%) |
| `CLAUDE_MINIMAL_TOOLS` | `true` | Use 8 core tools only (-39%) |

Combined improvement: **~57% faster** (10s → 4.3s)

See `docs/ARCHITECTURE_ANALYSIS.md` for details.

---

## Issue Tracking with br (beads_rust)

**Note:** `br` is non-invasive and never executes git commands. After `br sync --flush-only`, you must manually run `git add .beads/ && git commit`.

This project uses **br** (beads_rust) for issue tracking with prefix `ccow-`. Run `br onboard` to get started.

## Quick Reference

```bash
# Issue workflow
br ready                          # Find available work
br show <id>                      # View issue details
br update <id> --status in_progress  # Claim work
br close <id>                     # Complete work
```

## Landing the Plane (Session Completion)

```bash
# 1. Commit all changes (code + issues)
git add -A
git commit -m "your message"

# 2. Sync and commit beads
br sync --flush-only
git add .beads/
git commit -m "sync beads"

# 3. Push
git pull --rebase origin ko
git push origin ko

# 4. Verify
br list && git status
```

**Note:** `.beads/` folder is included directly in ko branch. No separate sync needed.
