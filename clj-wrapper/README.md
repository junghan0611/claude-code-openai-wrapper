# ccow — Claude Code OpenAI Wrapper (Clojure)

Claude Code CLI를 OpenAI API 형식으로 래핑하는 **Clojure 네이티브 바이너리**.

## Why Clojure?

| | Python (원본) | Clojure (ccow) |
|---|---:|---:|
| **소스 코드** | 4,828줄 (12파일) | **375줄** (3파일) |
| **비율** | 100% | **7.8%** |
| **의존성** | poetry + venv + SDK + FastAPI + 10개+ | deps.edn + ring + data.json |
| **배포** | Python 환경 필요 | **단일 바이너리** (GraalVM) |
| **기동 시간** | 2-3초 | **즉시** (native) |

### 왜 이게 가능한가?

Python 원본의 4,828줄 중 **gptel이 실제로 사용하는 건 2개 엔드포인트**뿐:
- `POST /v1/chat/completions` (SSE 스트리밍)
- `GET /v1/models`

나머지 3,000줄+ (MCP 관리, 세션 관리, 도구 관리 API, 파라미터 검증, HTML 랜딩페이지)은 전부 불필요.

또한 Python SDK(`claude-agent-sdk`)가 하는 일의 본질은:
```bash
claude --output-format stream-json --verbose --max-turns 10 --print "프롬프트"
```
서브프로세스 하나 실행하고 stdout JSON 스트림 파싱. SDK 없이 직접 호출하면 됨.

## Architecture

```
Emacs gptel  →  ccow (localhost:8000)  →  claude CLI
 (OpenAI API)    (375줄 Clojure)          (Read/Write/Edit/Bash)
```

## Quick Start

```bash
# JVM으로 실행
./run.sh start

# 또는 포트 지정
PORT=9000 ./run.sh start

# GraalVM native binary 빌드
./run.sh build

# native binary로 실행
./target/ccow-x86_64
```

## gptel 설정 (Doom Emacs)

```elisp
(setq gptel-claude-code-backend
      (gptel-make-openai "Claude-Code"
        :host "localhost:8000"
        :endpoint "/v1/chat/completions"
        :protocol "http"
        :stream t
        :key "not-needed"
        :models '((claude-sonnet-4-6
                   :description "Sonnet 4.6 + tool-use"
                   :capabilities (media tool-use)))))
```

## Tool 사용

기본적으로 도구 없이 Q&A 모드로 동작. 파일시스템 접근이 필요하면:

```json
{
  "model": "claude-sonnet-4-6",
  "messages": [{"role": "user", "content": "README.md 파일을 읽어줘"}],
  "enable_tools": true,
  "stream": true
}
```

`enable_tools: true` → Read, Write, Edit, Bash, Glob, Grep, WebSearch, WebFetch 활성화.

## Build

```bash
# 필요: Clojure, GraalVM (nix develop 권장)
nix develop              # GraalVM 환경 진입
./run.sh build           # native binary 생성
ls -lh target/ccow-*     # ~30MB 바이너리
```

## NixOS Integration

```nix
# flake.nix의 devShell에 GraalVM 포함
nix develop              # default shell (GraalVM)
nix develop .#jvm        # JVM만 (가벼운 개발)
```

## Project Structure

```
clj-wrapper/
├── deps.edn          # 의존성 (3개: clojure, data.json, ring)
├── build.clj         # uberjar 빌드 설정
├── flake.nix         # NixOS + GraalVM
├── run.sh            # 통합 CLI
├── src/ccow/
│   ├── core.clj      # 진입점 (18줄)
│   ├── claude.clj    # CLI 실행 + stream-json 파싱 (147줄)
│   └── server.clj    # Ring HTTP 서버 (210줄)
└── test/ccow/
    ├── claude_test.clj
    └── server_test.clj
```

## License

MIT
