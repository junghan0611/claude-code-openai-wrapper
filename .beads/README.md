# Beads Rust (br) - AI-Native Issue Tracking

**Note:** `br` is non-invasive and never executes git commands. After `br sync --flush-only`, you must manually run `git add .beads/ && git commit`.

Welcome to Beads! This repository uses **br** (beads_rust) for issue tracking - a modern, AI-native tool designed to live directly in your codebase alongside your code.

## What is Beads?

Beads is issue tracking that lives in your repo, making it perfect for AI coding agents and developers who want their issues close to their code. No web UI required - everything works through the CLI and integrates seamlessly with git.

**Learn more:** [github.com/steveyegge/beads_rust](https://github.com/steveyegge/beads_rust)

## Quick Start

### Essential Commands

```bash
# Create new issues
br create "Add user authentication"

# View all issues
br list

# View issue details
br show <issue-id>

# Update issue status
br update <issue-id> --status in_progress
br update <issue-id> --status done

# Sync and commit (br never auto-commits)
br sync --flush-only
git add .beads/
git commit -m "sync beads"
```

### Working with Issues

Issues in Beads are:
- **Git-native**: Stored in `.beads/issues.jsonl` and synced like code
- **AI-friendly**: CLI-first design works perfectly with AI coding agents
- **Branch-aware**: Issues can follow your branch workflow
- **Non-invasive**: `br` never executes git commands - you control commits

## Why Beads?

✨ **AI-Native Design**
- Built specifically for AI-assisted development workflows
- CLI-first interface works seamlessly with AI coding agents
- No context switching to web UIs

🚀 **Developer Focused**
- Issues live in your repo, right next to your code
- Works offline, syncs when you push
- Fast, lightweight, and stays out of your way

🔧 **Git Integration**
- Non-invasive: you control all git operations
- Branch-aware issue tracking
- Intelligent JSONL merge resolution

## Get Started with Beads Rust

Try Beads Rust in your own projects:

```bash
# Install Beads Rust
cargo install beads_rust

# Initialize in your repo
br init

# Create your first issue
br create "Try out Beads"
```

## Learn More

- **Documentation**: [github.com/steveyegge/beads_rust/docs](https://github.com/steveyegge/beads_rust/tree/main/docs)
- **Quick Start Guide**: Run `br quickstart`
- **Examples**: [github.com/steveyegge/beads_rust/examples](https://github.com/steveyegge/beads_rust/tree/main/examples)

---

*Beads: Issue tracking that moves at the speed of thought* ⚡
