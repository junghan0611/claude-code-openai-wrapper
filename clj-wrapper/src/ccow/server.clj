(ns ccow.server
  "Ring HTTP 서버 — OpenAI API 호환 엔드포인트.
   gptel이 실제로 호출하는 2개 엔드포인트만 구현:
     POST /v1/chat/completions  (SSE 스트리밍)
     GET  /v1/models"
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [ring.adapter.jetty :as jetty]
            [ccow.claude :as claude]))

;; ── 메시지 변환 ────────────────────────────────────────────────

(defn- messages->prompt
  "OpenAI messages 배열을 Claude 프롬프트 문자열과 system-prompt로 변환."
  [messages]
  (let [system-msgs  (filter #(= "system" (:role %)) messages)
        conv-msgs    (remove #(= "system" (:role %)) messages)
        system-prompt (when (seq system-msgs)
                        (:content (last system-msgs)))
        prompt-parts (map (fn [{:keys [role content]}]
                            (case role
                              "user"      (str "Human: " content)
                              "assistant" (str "Assistant: " content)
                              content))
                          conv-msgs)
        prompt       (str/join "\n\n" prompt-parts)]
    [prompt system-prompt]))

;; ── SSE 스트리밍 ───────────────────────────────────────────────

(defn- sse-chunk
  "OpenAI chat.completion.chunk SSE 포맷."
  [request-id model delta & {:keys [finish-reason]}]
  (let [chunk {:id      request-id
               :object  "chat.completion.chunk"
               :created (quot (System/currentTimeMillis) 1000)
               :model   model
               :choices [{:index         0
                          :delta         delta
                          :finish_reason finish-reason}]}]
    (str "data: " (json/write-str chunk) "\n\n")))

(defn- generate-request-id []
  (str "chatcmpl-" (subs (str (java.util.UUID/randomUUID)) 0 8)))

;; ── Claude 호출 옵션 빌드 ──────────────────────────────────────

(defn- build-claude-opts
  "요청 파라미터로부터 Claude CLI 옵션을 조립한다."
  [prompt system-prompt model enable-tools cwd]
  (cond-> {:prompt  prompt
           :model   model
           :cwd     cwd}
    system-prompt (assoc :system-prompt system-prompt)
    enable-tools  (assoc :max-turns       10
                         :allowed-tools   claude/core-tools
                         :permission-mode "bypassPermissions")
    (not enable-tools) (assoc :max-turns 1)))

;; ── 핸들러 ─────────────────────────────────────────────────────

(defn- handle-chat-completions
  "POST /v1/chat/completions — SSE 스트리밍 & 논스트리밍 응답."
  [body cwd default-model]
  (let [{:keys [messages stream]
         :or   {stream true}} body
        model        (or (:model body) default-model)
        enable-tools (:enable_tools body false)
        [prompt system-prompt] (messages->prompt messages)
        request-id   (generate-request-id)
        claude-opts  (build-claude-opts prompt system-prompt model enable-tools cwd)]

    (if stream
      ;; ── 스트리밍 응답 ──
      (let [out (java.io.PipedOutputStream.)
            in  (java.io.PipedInputStream. out 65536)]
        (future
          (try
            (let [writer (java.io.OutputStreamWriter. out "UTF-8")]
              ;; role 청크
              (.write writer (sse-chunk request-id model
                                        {:role "assistant" :content ""}))
              (.flush writer)

              ;; Claude 실행 + 스트리밍
              (claude/query!
               (assoc claude-opts
                      :callback-fn
                      (fn [{:keys [type text]}]
                        (when (and (= type :text) (not (str/blank? text)))
                          (.write writer (sse-chunk request-id model {:content text}))
                          (.flush writer))
                        (when (and (= type :tool-result) text (not (str/blank? text)))
                          (let [formatted (str "\n```\n" text "\n```\n")]
                            (.write writer (sse-chunk request-id model {:content formatted}))
                            (.flush writer))))))

              ;; 종료 청크
              (.write writer (sse-chunk request-id model {} :finish-reason "stop"))
              (.write writer "data: [DONE]\n\n")
              (.flush writer))
            (catch Exception e
              (let [w (java.io.OutputStreamWriter. out "UTF-8")]
                (.write w (str "data: " (json/write-str {:error {:message (.getMessage e)}}) "\n\n"))
                (.flush w)))
            (finally
              (.close out))))
        {:status  200
         :headers {"Content-Type"  "text/event-stream"
                   "Cache-Control" "no-cache"
                   "Connection"    "keep-alive"}
         :body    in})

      ;; ── 논스트리밍 응답 ──
      (let [messages-out (claude/query! claude-opts)
            text-parts   (->> messages-out
                              (filter #(= :text (:type %)))
                              (map :text))
            result-text  (or (->> messages-out
                                  (filter #(= :result (:type %)))
                                  first
                                  :result-text)
                             (str/join "" text-parts)
                             "")]
        {:status  200
         :headers {"Content-Type" "application/json"}
         :body    (json/write-str
                   {:id      request-id
                    :object  "chat.completion"
                    :created (quot (System/currentTimeMillis) 1000)
                    :model   model
                    :choices [{:index         0
                               :message       {:role "assistant" :content result-text}
                               :finish_reason "stop"}]
                    :usage   {:prompt_tokens     (max 1 (quot (count prompt) 4))
                              :completion_tokens (max 1 (quot (count result-text) 4))
                              :total_tokens      (max 2 (quot (+ (count prompt)
                                                                  (count result-text)) 4))}})}))))

(defn- handle-models []
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (json/write-str
             {:object "list"
              :data   (mapv (fn [id] {:id id :object "model" :owned_by "anthropic"})
                            (claude/models))})})

(defn- handle-health []
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (json/write-str {:status "healthy" :service "ccow"})})

;; ── Ring 핸들러 ────────────────────────────────────────────────

(defn- read-body [request]
  (when-let [body (:body request)]
    (json/read-str (slurp body) :key-fn keyword)))

(defn make-handler
  "Ring 핸들러를 생성한다."
  [cwd default-model]
  (fn [request]
    (let [method (:request-method request)
          uri    (:uri request)
          response
          (cond
            (= method :options)
            {:status 204 :headers {} :body ""}

            (and (= method :post) (= uri "/v1/chat/completions"))
            (handle-chat-completions (read-body request) cwd default-model)

            (and (= method :get) (= uri "/v1/models"))
            (handle-models)

            (and (= method :get) (= uri "/health"))
            (handle-health)

            :else
            {:status 404
             :headers {"Content-Type" "application/json"}
             :body (json/write-str {:error {:message "Not found"}})})

          cors-headers {"Access-Control-Allow-Origin"  "*"
                        "Access-Control-Allow-Methods" "GET, POST, OPTIONS"
                        "Access-Control-Allow-Headers" "*"}]
      (update response :headers merge cors-headers))))

;; ── 서버 기동 ──────────────────────────────────────────────────

(defn start!
  "HTTP 서버를 시작한다."
  [port cwd default-model]
  (let [handler (make-handler cwd default-model)]
    (println (str "🚀 ccow listening on http://localhost:" port))
    (println "   POST /v1/chat/completions")
    (println "   GET  /v1/models")
    (println "   GET  /health")
    (println "   Press Ctrl+C to stop")
    (jetty/run-jetty handler {:port port :join? true})))
