(ns tranjlator.chat-room-test
  (:use clojure.test tranjlator.chat-room)
  (:require [clojure.core.async :as a :refer [go-loop chan <! >!]]
            [com.stuartsierra.component :as component]
            [tranjlator.messages :as msg]))

(deftest test-user-join
  (testing "When a user joins, it receives the chat history."
    (let [chat-room (-> (->chat-room ["hi" "bye!"]) component/start)
          user (chan)]
      (join chat-room "User!" user)
      (is (= "hi" (a/<!! user)))
      (is (= "bye!" (a/<!! user)))))

  (testing "When a user joins, the other users receive a join message."
    (let [user1 (chan 1)
          user2 (chan 1)
          user3 (chan 1)
          name "User3!"
          chat-room (-> (map->ChatRoom {:initial-users #{user1 user2}}) component/start)
          join-message (msg/->user-join name)]
      (join chat-room name user3)
      (is (= join-message (a/<!! user1)))
      (is (= join-message (a/<!! user2))))))
