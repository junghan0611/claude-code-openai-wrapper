# Claude Code OpenAI Wrapper 아키텍처 분석

> 분석일: 2026-01-01
> 관련 이슈: ccow-ayl (perf: Reduce Claude CLI warm-up overhead)

## 1. 시스템 구조

```
┌─────────────────────────────────────────────────────────────────┐
│                        Doom Emacs                               │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ gptel                                                    │   │
│  │  - gptel-make-openai "Claude-Code"                       │   │
│  │  - :host "localhost:8000"                                │   │
│  │  - Advice: gptel--claude-code-add-enable-tools           │   │
│  │  - 자동 서버 감지: gptel--claude-code-server-available-p │   │
│  └────────────────────────┬────────────────────────────────┘   │
└───────────────────────────┼─────────────────────────────────────┘
                            │ HTTP (OpenAI 형식)
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│               claude-code-openai-wrapper                        │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ main.py (FastAPI)                                        │   │
│  │  - /v1/chat/completions                                  │   │
│  │  - session_manager.process_messages()                    │   │
│  └────────────────────────┬────────────────────────────────┘   │
│  ┌────────────────────────▼────────────────────────────────┐   │
│  │ claude_cli.py                                            │   │
│  │  - run_completion(session_id, continue_session)          │   │
│  │  - claude-agent-sdk의 query() 함수 사용                  │   │
│  └────────────────────────┬────────────────────────────────┘   │
└───────────────────────────┼─────────────────────────────────────┘
                            │ claude-agent-sdk
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│               claude-agent-sdk-python                           │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ query() - 단발성 쿼리 (현재 사용)                        │   │
│  │ ClaudeSDKClient - 양방향 대화 (미사용)                   │   │
│  │                                                          │   │
│  │ ClaudeAgentOptions:                                      │   │
│  │  - resume: str (세션 재개)                               │   │
│  │  - continue_conversation: bool                           │   │
│  │  - hooks, mcp_servers, fork_session, output_format ...   │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## 2. 현재 문제점 (ccow-ayl)

### 2.1 성능 이슈

| 항목 | 현재 | 예상 | 차이 |
|------|------|------|------|
| 응답 시간 (도구 미사용) | 10초 | 2-3초 | 7-8초 오버헤드 |

### 2.2 근본 원인

**SDK `query()` 함수의 stateless 설계**

SDK의 `query()` 함수는 의도적으로 stateless:
- 매 호출마다 새 CLI 프로세스 시작 (설계 의도)
- session_id 미전달은 **버그가 아님** - 의도된 설계

```
# query.py 문서:
# - Stateless: Each query is independent, no conversation state
# - No interrupts: Cannot interrupt or send follow-up messages
```

### 2.3 시간 분해

```
전체 10s =
  CLI 프로세스 시작 (3-5s) +
  SDK 초기화 (1s) +
  API 네트워크 (2-3s) +
  모델 생성 (2-3s)
```

### 2.4 두 가지 세션 개념 (혼동 주의)

| 구분 | wrapper의 session_id | Claude CLI의 resume |
|------|---------------------|---------------------|
| 위치 | `session_manager.py` | `claude-agent-sdk` |
| 용도 | 대화 히스토리 저장 | CLI 프로세스 재사용 |
| 방식 | messages 배열 누적 | CLI 내부 컨텍스트 유지 |

**wrapper는 이미 전체 대화 히스토리를 messages로 매번 전달** → CLI resume 사용 시 컨텍스트 중복 위험

## 3. 해결 방안

### 3.1 방안 비교

| 방안 | 설명 | 복잡도 | 효과 |
|------|------|--------|------|
| 환경변수 최적화 | 버전 체크 건너뛰기 등 | 낮음 | 낮음 |
| CLI 옵션 추가 | --no-session-persistence 등 | 낮음 | 낮음~중간 |
| ClaudeSDKClient | 프로세스 유지, 양방향 통신 | 높음 | 높음 |
| 프로세스 풀링 | warm 프로세스 미리 유지 | 중간 | 높음 |

### 3.2 독립 모드 (CLAUDE_INDEPENDENT_MODE) ✅ 구현됨

**성능 측정 결과:**
| 모드 | 시간 | 개선 |
|------|------|------|
| 기본 (MCP 포함) | 6.1s | - |
| 독립 모드 | 4.2s | **-31%** |

**환경변수:**
```bash
CLAUDE_INDEPENDENT_MODE=true  # 기본값: true
```

**적용되는 CLI 옵션:**
```python
extra_args = {
    "strict-mcp-config": None,       # MCP 서버 연결 안함
    "mcp-config": "empty-mcp.json",  # 빈 MCP 설정
    "disable-slash-commands": None,   # 슬래시 명령 로드 안함
    "setting-sources": "",            # 외부 설정 로드 안함
}
```

**유지되는 기능:**
- ✅ 작업 디렉토리 (cwd)
- ✅ 내장 도구: WebSearch, WebFetch, Bash, Read, Edit, Write 등
- ✅ 모델 선택, 시스템 프롬프트

**비활성화되는 기능:**
- ❌ MCP 서버 (context7, github 등)
- ❌ 플러그인/스킬
- ❌ 슬래시 명령

### 3.3 장기: ClaudeSDKClient 도입 검토

```python
# 현재: query() - stateless, 매번 새 프로세스
async for message in query(prompt=prompt, options=options):
    yield message

# 개선: ClaudeSDKClient - 프로세스 유지
async with ClaudeSDKClient(options=options) as client:
    await client.query(prompt)
    async for msg in client.receive_response():
        yield msg
```

**장점:**
- 프로세스 재사용으로 warm-up 제거
- 인터럽트, 권한 모드 변경 등 추가 기능

**단점:**
- 아키텍처 변경 필요
- 연결 관리 복잡성 증가

## 4. claude-agent-sdk 기능 분석

### 4.1 현재 사용 중

| 기능 | 위치 | 상태 |
|------|------|------|
| `query()` | claude_cli.py:145 | 사용 중 |
| `ClaudeAgentOptions` | claude_cli.py:119 | 부분 사용 |
| `resume` (session_id) | claude_cli.py:142 | 구현됨, 미전달 |

### 4.2 미사용 기능

| 기능 | SDK 지원 | 용도 |
|------|----------|------|
| `ClaudeSDKClient` | client.py | 양방향 대화, 인터럽트 |
| `hooks` | PreToolUse, PostToolUse | 도구 실행 전/후 개입 |
| `mcp_servers` | in-process MCP | 커스텀 도구 정의 |
| `fork_session` | 세션 포크 | 대화 분기 |
| `output_format` | 구조화 출력 | JSON 스키마 응답 |
| `enable_file_checkpointing` | 파일 체크포인트 | 되돌리기 |
| `can_use_tool` | 권한 콜백 | 동적 권한 제어 |
| `set_permission_mode` | 권한 모드 변경 | 런타임 권한 전환 |

### 4.3 ResultMessage 정보

```python
@dataclass
class ResultMessage:
    session_id: str           # 세션 ID 반환
    total_cost_usd: float     # 비용 정보
    usage: dict               # 토큰 사용량
    duration_ms: int          # 응답 시간
    num_turns: int            # 턴 수
```

## 5. gptel 연동 분석

### 5.1 현재 구현 (ai-gptel.el)

```elisp
;; 백엔드 정의
(setq gptel-claude-code-backend
      (gptel-make-openai "Claude-Code"
        :host "localhost:8000"
        :endpoint "/v1/chat/completions"
        :protocol "http"
        :stream t
        :key "not-needed"
        :models '((claude-sonnet-4-5-20250929 ...)
                  (claude-opus-4-5-20251101 ...)
                  (claude-haiku-4-5-20251001 ...))))

;; enable_tools 자동 추가 (Advice)
(advice-add 'gptel--request-data :around #'gptel--claude-code-add-enable-tools)

;; 서버 상태 확인
(defun gptel--claude-code-server-available-p ()
  "Check if Claude-Code wrapper server is running.")
```

### 5.2 확장 가능한 연동

| 기능 | 구현 방법 | 우선순위 |
|------|-----------|----------|
| 세션 ID 전달 | `:request-params` 또는 헤더 | P1 |
| 비용/토큰 표시 | 응답 헤더 → gptel 후처리 훅 | P2 |
| 도구 결과 포맷 | wrapper에서 마크다운 변환 | P2 |
| 인터럽트 | SSE cancel 지원 | P3 |

## 6. 확장 로드맵

### Phase 1: 성능 개선 (ccow-ayl)
- [ ] CLI 로딩 최적화 방안 조사
- [ ] ClaudeSDKClient 도입 검토
- [ ] 프로세스 풀링 가능성 평가

### Phase 2: SDK 기능 활용
- [ ] 비용/토큰 정보 응답에 포함
- [ ] 실제 usage 데이터 반환 (추정치 대체)
- [ ] 세션 관리 API 추가 (`/v1/sessions`)

### Phase 3: 고급 기능
- [ ] ClaudeSDKClient 도입 (양방향 대화)
- [ ] 훅 시스템 활용 (도구 필터링)
- [ ] 커스텀 MCP 도구 지원

### Phase 4: gptel 강화
- [ ] 비용 표시 gptel 훅
- [ ] 세션 상태 모드라인 표시
- [ ] 도구 결과 org-mode 포맷팅

## 7. 관련 파일

| 파일 | 역할 |
|------|------|
| `src/main.py` | FastAPI 엔드포인트 (수정 필요: 407, 729) |
| `src/claude_cli.py` | SDK 래퍼 (수정 필요: 139) |
| `src/session.py` | 세션 관리 |
| `~/sync/emacs/doomemacs-config/lisp/ai-gptel.el` | gptel 설정 |
| `~/repos/3rd/claude-agent-sdk-python/` | SDK 소스 참조 |

## 8. 참고 자료

- [Claude Agent SDK Python](https://docs.anthropic.com/en/docs/claude-code/sdk/sdk-python)
- [gptel 소스](https://github.com/karthink/gptel)
- bd 이슈: `bd show ccow-ayl`
