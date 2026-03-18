(ns ccow.claude
  "Claude CLI 서브프로세스 실행 및 stream-json 파싱.
   Python SDK가 하는 일을 ~100줄로 대체."
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

(defn- build-command
  "Claude CLI 명령어를 조립한다."
  [{:keys [prompt model system-prompt max-turns cwd
           allowed-tools permission-mode]}]
  (let [cli (find-cli)]
    (cond-> [cli "--output-format" "stream-json" "--verbose"
             "--max-turns" (str (or max-turns 10))
             "--print" prompt]
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
  "Claude CLI를 실행하고 stream-json 메시지를 lazy seq으로 반환.
   callback-fn이 주어지면 각 메시지마다 호출한다 (스트리밍).

   opts:
     :prompt          사용자 프롬프트 (필수)
     :model           모델 이름
     :system-prompt   시스템 프롬프트
     :max-turns       최대 턴 수 (기본 10)
     :cwd             작업 디렉토리
     :allowed-tools   허용 도구 목록
     :permission-mode 권한 모드
     :callback-fn     (fn [parsed-msg] ...) 스트리밍 콜백"
  [{:keys [cwd callback-fn] :as opts}]
  (let [cmd    (build-command opts)
        pb     (doto (ProcessBuilder. ^java.util.List cmd)
                 (.redirectErrorStream false))
        _      (when cwd (.directory pb (io/file cwd)))
        proc   (.start pb)
        reader (BufferedReader. (InputStreamReader. (.getInputStream proc) "UTF-8"))]
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
        (.destroy proc)))))

(defn models
  "사용 가능한 모델 목록을 반환."
  []
  default-models)
