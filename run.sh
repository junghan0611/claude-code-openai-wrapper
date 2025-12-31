#!/usr/bin/env bash
# Claude Code OpenAI Wrapper - Local Development Server
# Usage: ./run.sh [--reload] [--port PORT]

set -e

PORT="${PORT:-8000}"
RELOAD=""
DOCKER_CONTAINER="claude-wrapper-container"

# Stop Docker container if running (from docker-based run.sh)
stop_docker_if_running() {
    if command -v docker &> /dev/null; then
        if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${DOCKER_CONTAINER}$"; then
            echo "🐳 Docker 컨테이너 감지 → 중지 중..."
            docker rm -f "$DOCKER_CONTAINER" >/dev/null 2>&1
            echo "   완료. 로컬 서버로 전환합니다."
        fi
    fi
}

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

# Stop Docker container if running
stop_docker_if_running

echo "🚀 Starting Claude Code OpenAI Wrapper on http://localhost:$PORT"
echo "   Press Ctrl+C to stop"
echo ""

# Run the server
exec poetry run uvicorn src.main:app --host 0.0.0.0 --port "$PORT" $RELOAD
