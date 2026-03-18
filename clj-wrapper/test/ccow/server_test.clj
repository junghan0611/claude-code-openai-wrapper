(ns ccow.server-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.data.json :as json]
            [ccow.server :as server]))

(deftest messages->prompt-test
  (testing "user 메시지 변환"
    (let [[prompt sys] (#'server/messages->prompt
                        [{:role "user" :content "Hello"}])]
      (is (= "Human: Hello" prompt))
      (is (nil? sys))))

  (testing "system + user 메시지 변환"
    (let [[prompt sys] (#'server/messages->prompt
                        [{:role "system" :content "You are helpful"}
                         {:role "user" :content "Hi"}])]
      (is (= "Human: Hi" prompt))
      (is (= "You are helpful" sys))))

  (testing "multi-turn 대화 변환"
    (let [[prompt _] (#'server/messages->prompt
                      [{:role "user" :content "Hello"}
                       {:role "assistant" :content "Hi there"}
                       {:role "user" :content "How are you?"}])]
      (is (re-find #"Human: Hello" prompt))
      (is (re-find #"Assistant: Hi there" prompt))
      (is (re-find #"Human: How are you\?" prompt)))))

(deftest handler-models-test
  (testing "GET /v1/models 응답"
    (let [handler (server/make-handler "/tmp")
          resp    (handler {:request-method :get :uri "/v1/models"})
          body    (json/read-str (:body resp) :key-fn keyword)]
      (is (= 200 (:status resp)))
      (is (= "list" (:object body)))
      (is (seq (:data body)))
      (is (some #(= "claude-sonnet-4-6" (:id %)) (:data body))))))

(deftest handler-health-test
  (testing "GET /health 응답"
    (let [handler (server/make-handler "/tmp")
          resp    (handler {:request-method :get :uri "/health"})
          body    (json/read-str (:body resp) :key-fn keyword)]
      (is (= 200 (:status resp)))
      (is (= "healthy" (:status body))))))

(deftest handler-404-test
  (testing "존재하지 않는 경로는 404"
    (let [handler (server/make-handler "/tmp")
          resp    (handler {:request-method :get :uri "/nonexistent"})]
      (is (= 404 (:status resp))))))

(deftest cors-test
  (testing "CORS 헤더가 포함된다"
    (let [handler (server/make-handler "/tmp")
          resp    (handler {:request-method :get :uri "/health"})]
      (is (= "*" (get-in resp [:headers "Access-Control-Allow-Origin"]))))))
