(ns ccow.claude
  "Claude CLI 서브프로세스 실행 및 stream-json 파싱.
   Python SDK 4,828줄을 ~160줄로 대체."
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

;; ── 설정 ───────────────────────────────────────────────────────

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
  "빈 MCP config 파일 경로 (한번만 생성)."
  (memoize
   (fn []
     (let [f (java.io.File/createTempFile "ccow-empty-mcp" ".json")]
       (.deleteOnExit f)
       (spit f "{\"mcpServers\": {}}")
       (.getAbsolutePath f)))))

;; ── 스킬 로딩 ──────────────────────────────────────────────────

(defn- load-skill
  "~/.claude/skills/<name>/SKILL.md 내용을 읽는다."
  [skill-name]
  (let [path (str (System/getProperty "user.home")
                  "/.claude/skills/" skill-name "/SKILL.md")]
    (when (.exists (io/file path))
      (slurp path))))

(defn- build-skills-prompt
  "CCOW_SKILLS 환경변수에 지정된 스킬만 읽어 system prompt 조각으로 만든다.
   예: CCOW_SKILLS=botlog,denotecli,bibcli"
  []
  (when-let [skills-str (System/getenv "CCOW_SKILLS")]
    (let [names   (str/split skills-str #"[,;:\s]+")
          loaded  (keep (fn [n]
                          (when-let [content (load-skill n)]
                            (str "## Skill: " n "\n" content)))
                        names)]
      (when (seq loaded)
        (str "# Available Skills\n\n" (str/join "\n\n" loaded))))))

;; ── 시간 정보 ──────────────────────────────────────────────────

(defn- current-time-prompt
  "현재 KST 시간 정보. Denote ID 생성에 사용."
  []
  (let [kst    (java.time.ZonedDateTime/now (java.time.ZoneId/of "Asia/Seoul"))
        fmt-ts (.format kst (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd'T'HHmmss"))
        fmt-dt (.format kst (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd EEE HH:mm"))]
    (str "Current time (KST): " fmt-dt " | Denote timestamp: " fmt-ts)))

;; ── CLI 명령 빌드 ──────────────────────────────────────────────

(defn- build-command
  "Claude CLI 명령어를 조립한다.
   클린 모드: MCP/플러그인/스킬 전부 차단 → 13.5K 토큰.
   필요한 스킬만 CCOW_SKILLS로 system prompt에 주입."
  [{:keys [prompt model system-prompt max-turns
           allowed-tools permission-mode]}]
  (let [cli            (find-cli)
        minimal-tools? (env-truthy? "CLAUDE_MINIMAL_TOOLS")
        ;; system prompt 조립: 시간 + 스킬 + 사용자 지정
        parts          (filterv some?
                                [(current-time-prompt)
                                 (build-skills-prompt)
                                 system-prompt])
        full-sys       (str/join "\n\n" parts)]
    (cond-> [cli "--output-format" "stream-json" "--verbose"
             "--max-turns" (str (or max-turns 10))
             "--print" prompt
             ;; 클린 모드: MCP/플러그인/슬래시 전부 차단
             "--strict-mcp-config" "--mcp-config" (empty-mcp-path)
             "--disable-slash-commands" "--setting-sources" ""]
      ;; Minimal tools
      (and minimal-tools?
           (not allowed-tools))
                       (into ["--tools" (str/join "," core-tools)])
      ;; 시간+스킬 주입
      true             (into ["--append-system-prompt" full-sys])
      ;; 명시적 옵션
      model            (into ["--model" model])
      allowed-tools    (into ["--allowedTools" (str/join "," allowed-tools)])
      permission-mode  (into ["--permission-mode" permission-mode]))))

;; ── stream-json 파싱 ───────────────────────────────────────────

(defn- parse-text-blocks [content]
  (when (sequential? content)
    (->> content
         (keep (fn [block]
                 (when (= "text" (:type block))
                   (:text block))))
         (str/join ""))))

(defn- parse-stream-line
  "stream-json 한 줄을 파싱. 미지원 타입은 nil (forward-compatible)."
  [^String line]
  (when-not (str/blank? line)
    (try
      (let [msg (json/read-str line :key-fn keyword)]
        (case (:type msg)
          "assistant"
          (let [text (parse-text-blocks (get-in msg [:message :content]))]
            (when (and text (not (str/blank? text)))
              {:type :text :text text :model (get-in msg [:message :model])}))
          "tool_use"    {:type :tool :name (:name msg)}
          "tool_result" {:type :tool-result
                         :text (let [c (:content msg)]
                                 (cond
                                   (string? c) c
                                   (sequential? c)
                                   (->> c (keep #(when (= "text" (:type %)) (:text %))) (str/join ""))
                                   :else nil))}
          "result"      {:type :result
                         :session-id (:session_id msg)
                         :cost       (:total_cost_usd msg)
                         :duration   (:duration_ms msg)
                         :num-turns  (:num_turns msg)
                         :is-error   (:is_error msg)
                         :result-text (:result msg)}
          "system"      {:type :system :subtype (:subtype msg) :data msg}
          nil))
      (catch Exception _ nil))))

;; ── 쿼리 실행 ──────────────────────────────────────────────────

(defn query!
  "Claude CLI를 실행하고 stream-json 메시지를 반환.
   callback-fn이 주어지면 각 메시지마다 호출 (스트리밍)."
  [{:keys [cwd callback-fn] :as opts}]
  (let [cmd  (build-command opts)
        pb   (doto (ProcessBuilder. ^java.util.List cmd)
               (.redirectErrorStream false))
        _    (when cwd (.directory pb (io/file cwd)))
        proc (.start pb)]
    (.close (.getOutputStream proc))
    (let [reader (BufferedReader. (InputStreamReader. (.getInputStream proc) "UTF-8"))]
      (try
        (loop [messages []]
          (if-let [line (.readLine reader)]
            (let [parsed (parse-stream-line line)]
              (when (and parsed callback-fn) (callback-fn parsed))
              (recur (if parsed (conj messages parsed) messages)))
            (do (.waitFor proc) messages)))
        (finally
          (.close reader)
          (.destroy proc))))))

(defn models [] default-models)
