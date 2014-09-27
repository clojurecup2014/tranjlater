(ns tranjlator.chat-room-test
  (:use clojure.test tranjlator.chat-room)
  (:require [clojure.core.async :as a :refer [go-loop chan <! >!]]
            [com.stuartsierra.component :as component]
            [tranjlator.messages :as msg]))

(defn test-chat
  [user msg]
  (msg/->chat "english" msg "foo" (:name user)))

(deftest test-user-join
  (testing "When a user joins, it receives the chat history."
    (let [chat-room (-> (->chat-room ["hi" "bye!"]) component/start)
          user (chan)]
      (join chat-room "User!" user)
      (is (= "hi" (a/<!! user)))
      (is (= "bye!" (a/<!! user)))))

  (testing "When a user joins, the other users receive a join message."
    (let [[user1 user2 user3 :as users] (for [i (range 3)] {:name (format "User%d!" i) :chan (chan 10)})
          chat-room (-> (map->ChatRoom {:initial-users (reduce (fn [acc {:keys [name chan]}] (assoc acc name chan)) {} (butlast users))})
                        component/start)
          join-message (msg/->user-join (:name user3))]
      (join chat-room (:name user3) (:chan user3))
      (is (= join-message (a/<!! (:chan user1))))
      (is (= join-message (a/<!! (:chan user2))))))

  (testing "When a user chats the other users see it."
    (let [[user1 user2 user3 :as users] (for [i (range 3)] {:name (format "User%d!" i) :chan (chan 10)})
          chat-room (-> (map->ChatRoom {:initial-users (reduce (fn [acc {:keys [name chan]}] (assoc acc name chan)) {} users)}) component/start)
          chats ["hi!" "what?!?" "I love lamp!"]]

      (chat chat-room (test-chat user1 (first chats)))
      (chat chat-room (test-chat user2 (second chats)))
      (chat chat-room (test-chat user3 (nth chats 2)))

      (are [x] (and (= (:content x) (first chats)))
           (a/<!! (:chan user2))
           (a/<!! (:chan user3)))

      (are [x] (= (:content x) (second chats))
           (a/<!! (:chan user1))
           (a/<!! (:chan user3)))

      (are [x] (= (:content x) (nth chats 2))
           (a/<!! (:chan user1))
           (a/<!! (:chan user2)))))
  )
