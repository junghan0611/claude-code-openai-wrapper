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
```

## Landing the Plane (Session Completion)

```bash
# 1. Commit all changes (code + issues)
git add -A
git commit -m "your message"

# 2. Push
git pull --rebase origin ko
git push origin ko

# 3. Verify
bd list && git status
```

**Note:** .beads/ 폴더가 ko 브랜치에 직접 포함됩니다. 별도 sync 불필요.

