(ns ccow.core
  "ccow — Claude Code OpenAI Wrapper (Clojure)
   Claude CLI를 서브프로세스로 실행, OpenAI API 형식으로 래핑."
  (:gen-class)
  (:require [ccow.claude :as claude]
            [ccow.server :as server]))

(defn -main [& args]
  (let [port (or (some-> (System/getenv "PORT") parse-long)
                 (some-> (first args) parse-long)
                 8000)
        cwd  (or (System/getenv "CLAUDE_CWD") (System/getProperty "user.dir"))]
    (println (str "ccow — Claude Code OpenAI Wrapper (Clojure)"))
    (println (str "  Port: " port))
    (println (str "  CWD:  " cwd))
    (println (str "  CLI:  " (claude/find-cli)))
    (println "")
    (server/start! port cwd)))
