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
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --status in_progress  # Claim work
bd close <id>         # Complete work
bd sync               # Sync with git
```

## Landing the Plane (Session Completion)

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   bd sync
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds

