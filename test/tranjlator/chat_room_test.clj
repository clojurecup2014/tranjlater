(ns tranjlator.chat-room-test
  (:use clojure.test tranjlator.chat-room)
  (:require [clojure.core.async :as a :refer [go-loop chan <! >!]]
            [com.stuartsierra.component :as component]
            [tranjlator.messages :as msg]))

(defn test-chat
  [user msg]
  (msg/->chat "english" msg "foo" (:name user)))

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
      (join chat-room (msg/->user-join "User!") user)
      (is (= "hi" (a/<!! user)))
      (is (= "bye!" (a/<!! user)))))

  (testing "When a user joins, the other users receive a join message."
    (let [[user1 user2 user3 :as users] (make-users 3)
          chat-room (-> (map->ChatRoom {:initial-users (->initial-users (butlast users))})
                        component/start)
          join-message (msg/->user-join (:name user3))]
      (join chat-room join-message (:chan user3))
      (is (= join-message (dissoc (a/<!! (:chan user1)) :chan)))
      (is (= join-message (dissoc (a/<!! (:chan user2)) :chan))))))

(deftest test-user-part
  (testing "When a user leaves, the other users receive a part message."
    (let [[user1 user2 user3 :as users] (make-users 3)
          chat-room (-> (map->ChatRoom {:initial-users (->initial-users users)}) component/start)
          part-msg (msg/->user-part (:name user1))]

      (part chat-room part-msg (:chan user1))

      (is (= part-msg (a/<!! (:chan user2))))
      (is (= part-msg (a/<!! (:chan user3)))))))

(deftest test-user-chat
  (testing "When a user chats only the other users see it."
    (let [[user1 user2 user3 :as users] (make-users 3)
          chat-room (-> (map->ChatRoom {:initial-users (->initial-users users)}) component/start)
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
           (a/<!! (:chan user2))))))
