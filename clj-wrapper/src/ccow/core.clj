(ns ccow.core
  "ccow — Claude Code OpenAI Wrapper (Clojure)
   Claude CLI를 서브프로세스로 실행, OpenAI API 형식으로 래핑."
  (:gen-class)
  (:require [ccow.claude :as claude]
            [ccow.server :as server]
            [clojure.string :as str]))

(defn- env-truthy? [k]
  (contains? #{"1" "true" "yes"} (some-> (System/getenv k) str/lower-case)))

(defn -main [& args]
  (let [port  (or (some-> (System/getenv "PORT") parse-long)
                  (some-> (first args) parse-long)
                  8000)
        cwd   (or (System/getenv "CLAUDE_CWD")
                  (str (System/getProperty "user.home") "/org"))
        model (or (System/getenv "DEFAULT_MODEL") "claude-sonnet-4-6")
        independent? (env-truthy? "CLAUDE_INDEPENDENT_MODE")
        minimal?     (env-truthy? "CLAUDE_MINIMAL_TOOLS")]
    (println "")
    (println "==================================================")
    (println "  ccow — Claude Code OpenAI Wrapper (Clojure)")
    (println (str "  Mode:  " (if independent?
                                 "⚡ Independent (MCP off)"
                                 "🔗 Full (MCP on)")))
    (println (str "  Tools: " (if minimal?
                                 "🔧 Minimal (8 tools)"
                                 "🛠️  Full (all tools)")))
    (println (str "  CWD:   " cwd))
    (println (str "  Model: " model))
    (println (str "  CLI:   " (claude/find-cli)))
    (println "==================================================")
    (println "")
    (server/start! port cwd model)))
