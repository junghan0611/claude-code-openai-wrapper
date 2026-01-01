#!/usr/bin/env bash
# Claude Code OpenAI Wrapper - Local Development Server
# Usage: ./run.sh [--reload] [--port PORT] [--cwd PATH]

set -e

PORT="${PORT:-8000}"
RELOAD=""
DOCKER_CONTAINER="claude-wrapper-container"
# Default workspace: ~/org (same as Docker config)
export CLAUDE_CWD="${CLAUDE_CWD:-$HOME/org}"
# Performance settings
export MAX_TIMEOUT="${MAX_TIMEOUT:-300000}"  # 5분
export DEFAULT_MODEL="${DEFAULT_MODEL:-claude-sonnet-4-5-20250929}"
export RATE_LIMIT_ENABLED="${RATE_LIMIT_ENABLED:-false}"
# Independent mode: disable MCP/plugins for faster startup (6s -> 4s)
export CLAUDE_INDEPENDENT_MODE="${CLAUDE_INDEPENDENT_MODE:-true}"

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
        --cwd|-c)
            export CLAUDE_CWD="$2"
            shift 2
            ;;
        --help|-h)
            echo "Usage: $(basename "$0") [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --reload, -r     Enable auto-reload on file changes"
            echo "  --port, -p PORT  Set server port (default: 8000)"
            echo "  --cwd, -c PATH   Set Claude working directory (default: ~/org)"
            echo "  --help, -h       Show this help"
            echo ""
            echo "Environment Variables:"
            echo "  CLAUDE_CWD              Working directory for Claude (default: ~/org)"
            echo "  MAX_TIMEOUT             Request timeout ms (default: 300000 = 5분)"
            echo "  DEFAULT_MODEL           Default model (default: claude-sonnet-4-5-20250929)"
            echo "  RATE_LIMIT_ENABLED      Rate limiting (default: false)"
            echo "  CLAUDE_INDEPENDENT_MODE Disable MCP/plugins for faster startup (default: true)"
            echo ""
            echo "Examples:"
            echo "  ./run.sh --reload                    # 개발 모드"
            echo "  MAX_TIMEOUT=600000 ./run.sh          # 10분 타임아웃"
            echo "  DEFAULT_MODEL=claude-haiku-4-5-20251001 ./run.sh  # 빠른 모델"
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

echo "🚀 Starting Claude Code OpenAI Wrapper"
echo "   URL: http://localhost:$PORT"
echo "   CWD: $CLAUDE_CWD"
echo "   Timeout: ${MAX_TIMEOUT}ms | Model: $DEFAULT_MODEL"
if [ "$CLAUDE_INDEPENDENT_MODE" = "true" ] || [ "$CLAUDE_INDEPENDENT_MODE" = "1" ]; then
    echo "   ⚡ Independent Mode: ON (MCP/plugins disabled, ~31% faster)"
else
    echo "   🔗 Independent Mode: OFF (MCP/plugins enabled)"
fi
echo "   Press Ctrl+C to stop"
echo ""

# Run the server
exec poetry run uvicorn src.main:app --host 0.0.0.0 --port "$PORT" $RELOAD
