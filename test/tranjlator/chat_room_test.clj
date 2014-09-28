(ns tranjlator.chat-room-test
  (:use clojure.test tranjlator.chat-room)
  (:require [clojure.core.async :as a :refer [go-loop chan <! >!]]
            [com.stuartsierra.component :as component]
            [tranjlator.messages :as msg]
            [tranjlator.protocols :as p]

            [taoensso.timbre :as log]))

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
          user (chan 1)]

      (p/send-msg chat-room (msg/->user-join "User") user)
      (let [messages (set (map (fn [x] (if (map? x) (:user-name x) x))
                               (for [i (range 4)]
                                 (a/<!! user))))]
        (is (= messages #{"User" "clojure" "hi" "bye!"})))))

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

(deftest test-language-sub
  (testing "When a user subscribes to a given language, it starts receiving translations to that language."
    (let [[user] (make-users 1)
          chat-room (-> (map->ChatRoom {:initial-users (->initial-users [user])
                                        :mock-translator? true})
                        component/start)]

      (p/send-msg chat-room (msg/->language-sub (:name user) :de) (:chan user))
      (log/info "SUB-CONF:" (a/<!! (:chan user))) ;; throw away sub confirmation
      (p/send-msg chat-room (test-chat user "hello") (:chan user))

      (let [messages (group-by :topic [(a/<!! (:chan user)) (a/<!! (:chan user))])]

        (is (= "hello" (get-in messages [:original 0 :content])))
        (is (= "This will be translated to: :de!" (get-in messages [:de 0 :content])))))))

(deftest test-language-unsub
  (testing "When a user unsubscribes to a given language, it stops receiving translations to that language."
    (let [[user] (make-users 1)
          chat-room (-> (map->ChatRoom {:initial-users (->initial-users [user])
                                        :mock-translator? true})
                        component/start)]

      (p/send-msg chat-room (msg/->language-sub (:name user) :de) (:chan user))
      (log/info "SUB-CONF:" (a/<!! (:chan user))) ;; throw away sub confirmation
      (p/send-msg chat-room (msg/->language-unsub (:name user) :de) (:chan user))
      (log/info "UNSUB-CONF:" (a/<!! (:chan user))) ;; throw away unsub confirmation
      (p/send-msg chat-room (test-chat user "hello") (:chan user))

      (is (= "hello" (:content (a/<!! (:chan user)))))

      (let [timeout (a/timeout 1000)]
        (a/alt!!
          (:chan user) ([v] (is false (format "user recvd: %s. Should not have recvd anything." (pr-str v))))
          timeout ([_] (is true)))))))

(deftest test-clj-evaluation
  (testing "When a user sends a clojure form all users see the form and the result as chat."
    (let [[user1 user2 user3 :as users] (make-users 3)
          chat-room (-> (map->ChatRoom {:initial-users (->initial-users users)}) component/start)
          text1 "/clojure (+ 1 1)"
          text2 "@clojure (+ 1 1)"
          form "(+ 1 1)"
          result "2"]

      (p/send-msg chat-room (msg/->clojure form text1) (:chan user1))

      (are [x] (= (:content x) text1)
           (a/<!! (:chan user1))
           (a/<!! (:chan user2))
           (a/<!! (:chan user3)))

      (are [x] (= (:content x) (clojure-response result))
           (a/<!! (:chan user1))
           (a/<!! (:chan user2))
           (a/<!! (:chan user3)))

      (p/send-msg chat-room (msg/->clojure form text2) (:chan user1))

      (are [x] (= (:content x) text2)
           (a/<!! (:chan user1))
           (a/<!! (:chan user2))
           (a/<!! (:chan user3)))

      (are [x] (= (:content x) (clojure-response result))
           (a/<!! (:chan user1))
           (a/<!! (:chan user2))
           (a/<!! (:chan user3)))))

  (testing "There is 1 try-clojure session per chat-room."
    (let [[user] (make-users 1)
          chat-room (-> (map->ChatRoom {:initial-users (->initial-users [user])})
                        component/start)
          text1 "/clojure (def foo (+ 1 1))"
          form1 "(def foo (+ 1 1))\n"

          text2 "@clojure foo"
          form2 "foo"
          value "2"]

      (p/send-msg chat-room (msg/->clojure form1 text1) (:chan user))
      (p/send-msg chat-room (msg/->clojure form2 text2) (:chan user))

      (a/<!! (:chan user)) ;; drop broadcast of form1
      (a/<!! (:chan user)) ;; drop result of form1

      (a/<!! (:chan user)) ;; drop broadcast of form2
      (is (= (:content (a/<!! (:chan user))) (clojure-response value))))))

(deftest test-ping
  (testing "When a user sends a clojure form all users see the form and the result as chat."
    (let [[user1 user2 user3 :as users] (make-users 3)
          chat-room (-> (map->ChatRoom {:initial-users (->initial-users users)}) component/start)
          text1 (format "/ping %s" (:name user2))
          chat-msg (msg/->chat (:name user1) :en text1 "ping")
          ping-msg (msg/->ping text1 (:name user2))]

      (p/send-msg chat-room ping-msg (:chan user1))

      (are [x] (= (:content x) text1)
           (a/<!! (:chan user1))
           (a/<!! (:chan user3)))

      (is (= (dissoc (a/<!! (:chan user2)) :sender) ping-msg))
      (is (= (:content (a/<!! (:chan user2))) (:content chat-msg))))))
