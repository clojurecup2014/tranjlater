(ns tranjlator.chat-room-test
  (:use clojure.test tranjlator.chat-room)
  (:require [clojure.core.async :as a :refer [go-loop chan <! >!]]
            [com.stuartsierra.component :as component]
            [tranjlator.messages :as msg]
            [tranjlator.protocols :as p]))

(defn test-chat
  [user msg]
  (msg/->chat (:name user) "english" msg "foo"))

(defn make-users
  [num-users]
  (for [i (range num-users)] {:name (format "User%d!" i) :chan (chan 10)}))

(defn ->initial-users
  [users-vector]
  (reduce (fn [acc {:keys [name chan]}] (assoc acc name chan)) {} users-vector))

(deftest test-user-join
  (testing "When a user joins, it receives the chat history."
    (let [chat-room (-> (->chat-room ["hi" "bye!"]) component/start)
          user (chan)]

      (p/send-msg chat-room (msg/->user-join "User") user)
      (is (= "User" (:user-name (a/<!! user))))
      (is (= "hi" (a/<!! user)))
      (is (= "bye!" (a/<!! user)))))

  (testing "When a user joins, the other users receive a join message."
    (let [[user1 user2 user3 :as users] (make-users 3)
          chat-room (-> (map->ChatRoom {:initial-users (->initial-users (butlast users))})
                        component/start)
          join-message (msg/->user-join (:name user3))]

      (p/send-msg chat-room join-message (:chan user3))

      (is (= join-message (dissoc (a/<!! (:chan user1)) :sender)))
      (is (= join-message (dissoc (a/<!! (:chan user2)) :sender))))))

(deftest test-user-part
  (testing "When a user leaves, the other users receive a part message."
    (let [[user1 user2 user3 :as users] (make-users 3)
          chat-room (-> (map->ChatRoom {:initial-users (->initial-users users)}) component/start)
          part-msg (msg/->user-part (:name user1))]

      (p/send-msg chat-room part-msg (:chan user1))

      (is (= part-msg (dissoc (a/<!! (:chan user2)) :sender)))
      (is (= part-msg (dissoc (a/<!! (:chan user3)) :sender))))))

(deftest test-user-chat
  (testing "When a user chats all the users see it."
    (let [[user1 user2 user3 :as users] (make-users 3)
          chat-room (-> (map->ChatRoom {:initial-users (->initial-users users)}) component/start)
          chats ["hi!" "what?!?" "I love lamp!"]]

      (doseq [[c u] (map vector chats users)]
        (p/send-msg chat-room (test-chat u c) (:chan u)))

      (are [x] (= (:content x) (first chats))
           (a/<!! (:chan user1))
           (a/<!! (:chan user2))
           (a/<!! (:chan user3)))

      (are [x] (= (:content x) (second chats))
           (a/<!! (:chan user1))
           (a/<!! (:chan user2))
           (a/<!! (:chan user3)))

      (are [x] (= (:content x) (nth chats 2))
           (a/<!! (:chan user1))
           (a/<!! (:chan user2))
           (a/<!! (:chan user3))))))
