# Agent Instructions

## Project Purpose (ko branch)

이 포크는 **Doom Emacs + gptel** 환경에서 Claude Code 정액제를 활용하기 위한 래퍼입니다.

### 핵심 기능
- **OpenAI 호환 API**: Claude Code를 `localhost:8000`에서 OpenAI API 형식으로 제공
- **gptel 통합**: Emacs gptel 백엔드로 직접 사용 가능
- **Claude 도구 활용**: Read, Write, Bash 등 Claude Code 도구를 Emacs 내에서 사용

### 사용 환경
```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────┐
│  Doom Emacs     │────▶│  Claude Wrapper  │────▶│ Claude Code │
│  (gptel)        │     │  (localhost:8000)│     │ (Anthropic) │
└─────────────────┘     └──────────────────┘     └─────────────┘
```

### 관련 설정
- gptel 설정: `~/sync/emacs/doomemacs-config/lisp/ai-gptel.el`
- Docker 설정: `~/sync/emacs/doomemacs-config/docker/claude-wrapper/`

---

## Issue Tracking

This project uses **bd** (beads) for issue tracking with prefix `ccow-`. Run `bd onboard` to get started.

## Quick Reference

```bash
# Issue workflow
bd ready                          # Find available work
bd show <id>                      # View issue details
bd update <id> --status in_progress  # Claim work
bd close <id>                     # Complete work

# Sync workflow (session end)
git push origin ko                # Push code first
bd sync                           # Sync issues (auto push beads-sync)
```

## Landing the Plane (Session Completion)

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until both branches are pushed.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - `nix flake check` or `poetry run pytest`
3. **Update issue status** - Close finished work (`bd close <id>`)
4. **PUSH TO REMOTE** - Two branches to sync:
   ```bash
   # 1. Code changes (ko branch)
   git pull --rebase origin ko
   git push origin ko

   # 2. Issue changes (beads-sync branch)
   bd sync                        # Export, commit, pull, push
   git status                     # Verify clean state
   ```
5. **Verify** - Both branches pushed:
   ```bash
   git log --oneline -1 origin/ko        # Code synced
   git log --oneline -1 origin/beads-sync # Issues synced
   ```
6. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until BOTH `ko` and `beads-sync` are pushed
- `bd sync` handles beads-sync automatically, but verify with `git status`
- If `bd sync` fails with pull error, use `bd sync --no-pull` first, then `git push origin beads-sync`

