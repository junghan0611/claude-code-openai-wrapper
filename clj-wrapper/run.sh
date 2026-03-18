#!/usr/bin/env bash
# ccow — Claude Code OpenAI Wrapper (Clojure)
# Python run.sh와 동일한 환경변수/인터페이스
set -euo pipefail
cd "$(dirname "$0")"

CMD="${1:-start}"
shift 2>/dev/null || true

ARCH=$(uname -m)
BINARY="target/ccow-${ARCH}"

# Python 래퍼와 동일한 기본값
export PORT="${PORT:-8000}"
export CLAUDE_CWD="${CLAUDE_CWD:-$HOME/org}"
export DEFAULT_MODEL="${DEFAULT_MODEL:-claude-sonnet-4-6}"
export CLAUDE_INDEPENDENT_MODE="${CLAUDE_INDEPENDENT_MODE:-true}"
export CLAUDE_MINIMAL_TOOLS="${CLAUDE_MINIMAL_TOOLS:-true}"

# 모델 별칭
resolve_model() {
  case "$1" in
    opus)   echo "claude-opus-4-6" ;;
    sonnet) echo "claude-sonnet-4-6" ;;
    haiku)  echo "claude-haiku-4-5-20251001" ;;
    *)      echo "$1" ;;
  esac
}

case "$CMD" in
  ## --- 서버 실행 ---
  start)
    # 인자 파싱
    while [[ $# -gt 0 ]]; do
      case $1 in
        --model|-m) export DEFAULT_MODEL="$(resolve_model "$2")"; shift 2 ;;
        --port|-p)  export PORT="$2"; shift 2 ;;
        --cwd|-c)   export CLAUDE_CWD="$2"; shift 2 ;;
        *) shift ;;
      esac
    done

    echo "🚀 Starting ccow (Clojure)"
    echo "   URL: http://localhost:$PORT"
    echo "   CWD: $CLAUDE_CWD"
    echo "   Model: $DEFAULT_MODEL"
    if [ "$CLAUDE_INDEPENDENT_MODE" = "true" ]; then
      echo "   ⚡ Independent Mode: ON"
    fi
    if [ "$CLAUDE_MINIMAL_TOOLS" = "true" ]; then
      echo "   🔧 Minimal Tools: ON (8 core tools)"
    fi
    echo ""

    # native binary가 있으면 그걸 사용, 없으면 JVM
    if [ -f "${BINARY}" ]; then
      echo "→ native binary: ${BINARY}"
      exec "${BINARY}"
    else
      exec clj -M:run
    fi
    ;;

  ## --- 빌드 ---
  build)
    OUTPUT=""
    FORCE=false
    ARGS=("$@")
    i=0
    while [ $i -lt ${#ARGS[@]} ]; do
      case "${ARGS[$i]}" in
        --output) i=$((i+1)); OUTPUT="${ARGS[$i]:-}" ;;
        --force)  FORCE=true ;;
        *)        [ -z "$OUTPUT" ] && OUTPUT="${ARGS[$i]}" ;;
      esac
      i=$((i+1))
    done

    if [ "$FORCE" = false ] && [ -f "${BINARY}" ]; then
      echo "✅ 캐시 사용: ${BINARY}"
    else
      NI_ARGS="--initialize-at-build-time --no-fallback -H:+ReportExceptionStackTraces"
      NI_ARGS="$NI_ARGS -H:Name=ccow-${ARCH} -jar target/ccow.jar -o ${BINARY}"

      if command -v native-image &>/dev/null; then
        echo "=== GraalVM native-image 빌드 (${ARCH}) ==="
        clj -T:build uber
        # shellcheck disable=SC2086
        native-image $NI_ARGS
      else
        FHS_BIN="$(nix build .#fhs --no-link --print-out-paths 2>/dev/null)/bin/ccow-build"
        if [ -x "$FHS_BIN" ]; then
          echo "=== FHS → native-image 빌드 (${ARCH}) ==="
          "$FHS_BIN" -c "cd $(pwd) && clj -T:build uber && native-image $NI_ARGS"
        else
          echo "=== nix develop → native-image 빌드 (${ARCH}) ==="
          nix develop --command bash -c "cd $(pwd) && clj -T:build uber && native-image $NI_ARGS"
        fi
      fi

      if command -v patchelf &>/dev/null; then
        INTERP="/lib64/ld-linux-x86-64.so.2"
        [ "$ARCH" = "aarch64" ] && INTERP="/lib/ld-linux-aarch64.so.1"
        patchelf --set-interpreter "$INTERP" "${BINARY}" 2>/dev/null || true
        patchelf --remove-rpath "${BINARY}" 2>/dev/null || true
      fi
      echo "  ✅ ${BINARY} ($(du -h "${BINARY}" | cut -f1))"
    fi

    if [ -n "$OUTPUT" ]; then
      cp "${BINARY}" "$OUTPUT"
      echo "→ $OUTPUT"
    fi
    ;;

  jar-build)
    echo "=== JVM uberjar 빌드 ==="
    clj -T:build uber
    echo "✅ target/ccow.jar"
    echo "실행: java -jar target/ccow.jar"
    ;;

  test)
    echo "=== 테스트 ==="
    clj -M:test
    ;;
  repl)
    clj
    ;;
  clean)
    rm -rf .cpcache/ target/
    echo "✅ cleaned"
    ;;
  help|*)
    echo "ccow — Claude Code OpenAI Wrapper (Clojure)"
    echo ""
    echo "Usage: ./run.sh [command] [options]"
    echo ""
    echo "Commands:"
    echo "  start [options]       서버 시작 (기본)"
    echo "  build [--output PATH] GraalVM native binary"
    echo "  jar-build             JVM uberjar"
    echo "  test                  테스트"
    echo "  repl / clean / help"
    echo ""
    echo "Start Options:"
    echo "  --model, -m MODEL  opus, sonnet, haiku (기본: sonnet)"
    echo "  --port, -p PORT    포트 (기본: 8000)"
    echo "  --cwd, -c PATH     작업 디렉토리 (기본: ~/org)"
    echo ""
    echo "Environment (Python 래퍼와 동일):"
    echo "  PORT, CLAUDE_CWD, DEFAULT_MODEL"
    echo "  CLAUDE_INDEPENDENT_MODE  MCP 비활성화 (기본: true)"
    echo "  CLAUDE_MINIMAL_TOOLS     8개 도구만 (기본: true)"
    ;;
esac
