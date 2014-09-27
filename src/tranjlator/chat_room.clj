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

(def ^:const +user-default-topics+ #{:original :user-join :user-part})

(defn filter-own-chats
  [user-name]
  (remove #(= user-name (:user-name %))))

(defn sub-user
  [pub user-name user-chan topics]
  (let [filtering-chan (chan 10 (filter-own-chats user-name))]
    (a/pipe filtering-chan user-chan)
    (doseq [t +user-default-topics+]
      (a/sub pub t filtering-chan))))

(defrecord ChatRoom
    [initial-users initial-history pub-chan process-chan]

  component/Lifecycle
  (start [this]
    (let [pub-chan (chan 1000)
          pub (a/pub pub-chan :topic)
          user-part (chan 10)
          user-join (chan 10)
          chat (chan 10)]

      (a/sub pub :user-join user-join)
      (a/sub pub :user-part user-part)
      (a/sub pub :original chat)
      
      (doseq [[name chan] initial-users]
        (sub-user pub name chan +user-default-topics+))
      
      (assoc this
        :pub-chan pub-chan
        :process-chan
        (go-loop [users (or initial-users {}) history (or initial-history [])]
          (a/alt!
            user-join ([{:keys [chan user-name] :as msg}]
                         (log/infof "JOIN: %s" (pr-str msg))
                         (if-not (nil? msg)
                           (do (send-history chan history)
                               (sub-user pub user-name chan +user-default-topics+)
                               (recur (assoc users user-name chan) history))
                           (log/warn "ChatRoom shutting down due to \"user-join\" channel closing")))

            user-part ([{:keys [user-name] :as msg}]
                         (log/infof "PART: %s" (pr-str msg))
                         (if-not (nil? msg)
                           (do (when-let [chan (get users user-name)]
                                 (doseq [t +user-default-topics+]
                                   (a/unsub pub t chan)))
                               (recur (dissoc users user-name) history))
                           (log/warn "ChatRoom shutting down due to \"user-leave\" channel closing")))

            chat ([msg]
                    (log/info "CHAT: %s" (pr-str msg))
                    (if-not (nil? msg)
                      (recur users (conj history msg))
                      (log/warn "ChatRoom shutting down due to \"chat\" channel closing"))))))))
  (stop [this]
    (a/close! pub-chan)
    (a/<!! process-chan)
    (dissoc this :ctrl-chan :process-chan))

  UserChat
  (chat [this msg]
    (a/put! pub-chan msg))

  UserJoin
  (join [this join-msg chan]
    (log/info "pubbing:" join-msg)
    (a/put! pub-chan (assoc join-msg :chan chan)))

  UserPart
  (part [this join-msg chan]
    (a/put! pub-chan join-msg))
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
