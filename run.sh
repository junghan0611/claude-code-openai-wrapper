#!/usr/bin/env bash
# Claude Code OpenAI Wrapper - Local Development Server
# Usage: ./run.sh [--reload] [--port PORT]

set -e

PORT="${PORT:-8000}"
RELOAD=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --reload|-r)
            RELOAD="--reload"
            shift
            ;;
        --port|-p)
            PORT="$2"
            shift 2
            ;;
        --help|-h)
            echo "Usage: $(basename "$0") [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --reload, -r     Enable auto-reload on file changes"
            echo "  --port, -p PORT  Set server port (default: 8000)"
            echo "  --help, -h       Show this help"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Check if we're in a nix/direnv environment
if [ -z "$IN_NIX_SHELL" ] && [ ! -d ".direnv" ]; then
    echo "⚠️  Not in Nix shell. Run 'direnv allow' or 'nix develop' first."
    echo "   Or install dependencies manually: poetry install"
fi

# Check if poetry is available
if ! command -v poetry &> /dev/null; then
    echo "❌ Poetry not found. Install via Nix or manually."
    exit 1
fi

# Install dependencies if .venv doesn't exist
if [ ! -d ".venv" ]; then
    echo "📦 Installing dependencies..."
    poetry install --no-interaction
fi

echo "🚀 Starting Claude Code OpenAI Wrapper on http://localhost:$PORT"
echo "   Press Ctrl+C to stop"
echo ""

# Run the server
exec poetry run uvicorn src.main:app --host 0.0.0.0 --port "$PORT" $RELOAD
