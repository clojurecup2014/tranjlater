(ns tranjlator.chat-room
  (:refer-clojure :exclude [join])
  (:require [clojure.core.async :as a :refer [go go-loop chan <! >!]]
            [tranjlator.messages :as msg]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]))

(defprotocol UserChat
  (chat [this msg]))

(defprotocol UserJoin
  (join [this user-name channel]))

(defprotocol UserPart
  (part [this user-name channel]))

(defn send-history
  [user history]
  (go (doseq [h history]
        (>! user h))))

(defn send-join
  [users name]
  (let [join-message (msg/->user-join name)]
    (go (doseq [[name channel] users]
          (>! channel join-message)))))

(defn send-chat
  [users chat-msg]
  (go (doseq [[name channel] users]
        (log/infof "sending %s to user %s" chat-msg name)
        (>! channel chat-msg))))

(defrecord ChatRoom
    [initial-users initial-history ctrl-chan process-chan]

  component/Lifecycle
  (start [this]
    (let [ctrl-chan (chan 1000)
          pub-chan (chan)
          pub (a/pub pub-chan :topic 100)]

      (assoc this
        :ctrl-chan ctrl-chan
        :process-chan
        (go-loop [users (or initial-users {}) history (or initial-history [])]
          (let [{:keys [payload] :as msg} (<! ctrl-chan)]
            (when-not (nil? msg)
              (log/info "msg:" msg)
              (case (:op msg)
                :join (do (send-history (:channel msg) history)
                          (send-join users (:name msg))
                          (recur (assoc users (:name msg) (:channel msg)) history))
                :chat (do (send-chat (dissoc users (:user-name payload)) payload)
                          (recur users (conj history payload)))
                (recur users history))))))))
  (stop [this]
    (a/close! ctrl-chan)
    (a/<!! process-chan)
    (dissoc this :ctrl-chan :process-chan))

  UserChat
  (chat [this msg]
    (a/put! ctrl-chan {:op :chat :payload msg}))

  UserJoin
  (join [this user-name chan]
    (a/put! ctrl-chan {:op :join :name user-name :channel chan}))

  UserPart
  (part [this user-name chan]
    (a/put! ctrl-chan {:op :part :name user-name :channel chan}))
  )

(defn ->chat-room
  ([]
     (->chat-room []))
  ([history]
     (->ChatRoom nil history nil nil)))

(comment
  (require '[tranjlator.messages :refer :all])
  (def brian (chan))
  (def bob (chan))

  (def chat-room
    (-> (->ChatRoom nil nil) component/start))

  (chat chat-room (->chat "foo" "foo" "foo" "user-1"))

  (join chat-room "brian" brian)
  (join chat-room "bob" bob)

  
  
  )
