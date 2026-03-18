(ns ccow.claude-test
  (:require [clojure.test :refer [deftest is testing]]
            [ccow.claude :as claude]))

(deftest find-cli-test
  (testing "claude CLI를 찾을 수 있다"
    (is (string? (claude/find-cli)))))

(deftest models-test
  (testing "모델 목록이 비어있지 않다"
    (let [models (claude/models)]
      (is (seq models))
      (is (some #(= "claude-sonnet-4-6" %) models)))))

(deftest parse-stream-line-test
  (testing "assistant 메시지를 파싱한다"
    (let [line "{\"type\":\"assistant\",\"message\":{\"model\":\"claude-sonnet-4-6\",\"content\":[{\"type\":\"text\",\"text\":\"Hello!\"}]}}"
          result (#'claude/parse-stream-line line)]
      (is (= :text (:type result)))
      (is (= "Hello!" (:text result)))))

  (testing "result 메시지를 파싱한다"
    (let [line "{\"type\":\"result\",\"subtype\":\"success\",\"session_id\":\"abc-123\",\"total_cost_usd\":0.01,\"duration_ms\":500,\"num_turns\":1,\"is_error\":false}"
          result (#'claude/parse-stream-line line)]
      (is (= :result (:type result)))
      (is (= "abc-123" (:session-id result)))))

  (testing "빈 줄은 nil 반환"
    (is (nil? (#'claude/parse-stream-line "")))
    (is (nil? (#'claude/parse-stream-line nil))))

  (testing "미지원 타입은 nil (forward-compatible)"
    (let [line "{\"type\":\"unknown_future_type\",\"data\":{}}"
          result (#'claude/parse-stream-line line)]
      (is (nil? result)))))
