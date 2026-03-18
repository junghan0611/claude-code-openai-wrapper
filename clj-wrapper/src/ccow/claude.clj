(ns ccow.claude
  "Claude CLI 서브프로세스 실행 및 stream-json 파싱.
   Python SDK가 하는 일을 ~120줄로 대체."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io BufferedReader InputStreamReader]))

;; ── CLI 경로 탐색 ──────────────────────────────────────────────

(defn find-cli
  "claude CLI 바이너리 경로를 찾는다."
  []
  (or (System/getenv "CLAUDE_CLI_PATH")
      (let [which (try
                    (-> (ProcessBuilder. ["which" "claude"])
                        (.redirectErrorStream true)
                        .start)
                    (catch Exception _ nil))]
        (when which
          (let [path (str/trim (slurp (.getInputStream which)))]
            (when (and (.waitFor which) (not (str/blank? path)))
              path))))
      "claude"))

;; ── CLI 명령 빌드 ──────────────────────────────────────────────

(def ^:private default-models
  ["claude-sonnet-4-6" "claude-opus-4-6"
   "claude-sonnet-4-5-20250929" "claude-haiku-4-5-20251001"
   "claude-opus-4-5-20251101" "claude-opus-4-5-20250929"
   "claude-opus-4-1-20250805" "claude-opus-4-20250514"
   "claude-sonnet-4-20250514"])

(def core-tools
  "gptel에서 사용하는 핵심 도구 목록"
  ["Read" "Write" "Edit" "Bash" "Glob" "Grep" "WebSearch" "WebFetch"])

(defn- env-truthy? [k]
  (contains? #{"1" "true" "yes"} (some-> (System/getenv k) str/lower-case)))

(def ^:private empty-mcp-path
  "빈 MCP config 파일 경로 (independent mode용). 한번만 생성."
  (memoize
   (fn []
     (let [f (java.io.File/createTempFile "ccow-empty-mcp" ".json")]
       (.deleteOnExit f)
       (spit f "{\"mcpServers\": {}}")
       (.getAbsolutePath f)))))

(defn- build-command
  "Claude CLI 명령어를 조립한다.
   Independent mode: MCP/플러그인 비활성화로 ~4초 빠른 시작.
   Minimal tools: 8개 핵심 도구만 사용."
  [{:keys [prompt model system-prompt max-turns
           allowed-tools permission-mode]}]
  (let [cli             (find-cli)
        independent?    (env-truthy? "CLAUDE_INDEPENDENT_MODE")
        minimal-tools?  (env-truthy? "CLAUDE_MINIMAL_TOOLS")]
    (cond-> [cli "--output-format" "stream-json" "--verbose"
             "--max-turns" (str (or max-turns 10))
             "--print" prompt]
      ;; Independent mode — MCP/플러그인/슬래시 커맨드 비활성화
      independent?    (into ["--strict-mcp-config"
                             "--mcp-config" (empty-mcp-path)
                             "--disable-slash-commands"
                             "--setting-sources" ""])
      ;; Minimal tools — 8개 핵심 도구만
      (and minimal-tools?
           (not allowed-tools))
                        (into ["--tools" (str/join "," core-tools)])
      ;; 명시적 옵션
      model           (into ["--model" model])
      system-prompt   (into ["--append-system-prompt" system-prompt])
      allowed-tools   (into ["--allowedTools" (str/join "," allowed-tools)])
      permission-mode (into ["--permission-mode" permission-mode]))))

;; ── stream-json 파싱 ───────────────────────────────────────────

(defn- parse-text-blocks
  "AssistantMessage content에서 텍스트를 추출한다."
  [content]
  (when (sequential? content)
    (->> content
         (keep (fn [block]
                 (when (= "text" (:type block))
                   (:text block))))
         (str/join ""))))

(defn- parse-stream-line
  "stream-json 한 줄을 파싱하여 {:type :text/:tool/:result/:system :data ...} 반환.
   파싱 불가 시 nil."
  [^String line]
  (when-not (str/blank? line)
    (try
      (let [msg (json/read-str line :key-fn keyword)]
        (case (:type msg)
          "assistant"
          (let [content (get-in msg [:message :content])
                text    (parse-text-blocks content)]
            (when (and text (not (str/blank? text)))
              {:type :text :text text :model (get-in msg [:message :model])}))

          "tool_use"
          {:type :tool :name (:name msg)}

          "tool_result"
          (let [content (:content msg)]
            {:type :tool-result
             :text (cond
                     (string? content) content
                     (sequential? content)
                     (->> content
                          (keep #(when (= "text" (:type %)) (:text %)))
                          (str/join ""))
                     :else nil)})

          "result"
          {:type       :result
           :session-id (:session_id msg)
           :cost       (:total_cost_usd msg)
           :duration   (:duration_ms msg)
           :num-turns  (:num_turns msg)
           :is-error   (:is_error msg)
           :result-text (:result msg)}

          "system"
          {:type :system :subtype (:subtype msg) :data msg}

          ;; 미지원 타입은 무시 (forward-compatible)
          nil))
      (catch Exception _e nil))))

;; ── 쿼리 실행 ──────────────────────────────────────────────────

(defn query!
  "Claude CLI를 실행하고 stream-json 메시지를 반환.
   callback-fn이 주어지면 각 메시지마다 호출한다 (스트리밍)."
  [{:keys [cwd callback-fn] :as opts}]
  (let [cmd    (build-command opts)
        pb     (doto (ProcessBuilder. ^java.util.List cmd)
                 (.redirectErrorStream false))
        _      (when cwd (.directory pb (io/file cwd)))
        proc   (.start pb)]
    ;; stdin을 즉시 닫아야 CLI가 --print 모드로 동작 후 종료
    (.close (.getOutputStream proc))
    (let [reader (BufferedReader. (InputStreamReader. (.getInputStream proc) "UTF-8"))]
      (try
        (loop [messages []]
          (if-let [line (.readLine reader)]
            (let [parsed (parse-stream-line line)]
              (when (and parsed callback-fn)
                (callback-fn parsed))
              (recur (if parsed (conj messages parsed) messages)))
            (do
              (.waitFor proc)
              messages)))
        (finally
          (.close reader)
          (.destroy proc))))))

(defn models
  "사용 가능한 모델 목록을 반환."
  []
  default-models)
