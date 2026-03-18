#!/usr/bin/env bash
# ccow — Claude Code OpenAI Wrapper (Clojure)
set -euo pipefail
cd "$(dirname "$0")"

CMD="${1:-help}"
shift 2>/dev/null || true

ARCH=$(uname -m)
BINARY="target/ccow-${ARCH}"

case "$CMD" in
  ## --- 서버 실행 ---
  start)
    echo "=== ccow 서버 시작 (JVM) ==="
    clj -M:run "$@"
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

    # --output 지정 시 복사
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

  ## --- 테스트 ---
  test)
    echo "=== 테스트 ==="
    clj -M:test
    ;;

  ## --- 개발 ---
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
    echo "Usage: ./run.sh <command> [args]"
    echo ""
    echo "Server:"
    echo "  start [port]                JVM으로 서버 시작 (기본 8000)"
    echo ""
    echo "Build:"
    echo "  build [--output PATH]       GraalVM native binary"
    echo "  jar-build                   JVM uberjar"
    echo ""
    echo "Development:"
    echo "  test    테스트"
    echo "  repl    Clojure REPL"
    echo "  clean   빌드 파일 정리"
    echo ""
    echo "환경변수:"
    echo "  PORT              서버 포트 (기본 8000)"
    echo "  CLAUDE_CWD        Claude 작업 디렉토리"
    echo "  CLAUDE_CLI_PATH   claude 바이너리 경로"
    ;;
esac
