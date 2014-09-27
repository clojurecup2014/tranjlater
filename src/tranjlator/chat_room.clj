(ns tranjlator.chat-room
  (:refer-clojure :exclude [join])
  (:require [clojure.core.async :as a :refer [go go-loop chan <! >!]]
            [tranjlator.messages :as msg]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]))

(defprotocol MsgSink
  (send-msg [this msg sender]))

(defn send-history
  [user history]
  (go (doseq [h history]
        (>! user h))))

(def ^:const +user-default-topics+ #{:original :user-join :user-part})

(defn remove-own-chats
  [user-name]
  (remove #(= user-name (:user-name %))))

(defn sub-user
  [pub user-name user-chan topics]
  (doseq [t +user-default-topics+]
    (a/sub pub t user-chan))
  user-chan

  ;; (let [removing-chan (chan 10 (remove-own-chats user-name))]
  ;; (a/pipe removing-chan user-chan)
  ;; (a/sub pub t removing-chan)
  ;; removing-chan)
  )

(defrecord ChatRoom
    [initial-users initial-history pub-chan process-chan]

  component/Lifecycle
  (start [this]
    (let [pub-chan (chan 1000)
          pub (a/pub pub-chan :topic)
          user-part (chan 10)
          user-join (chan 10)
          chat (chan 10)
          initial-users (reduce (fn [acc [name chan]] (assoc acc name (sub-user pub name chan +user-default-topics+))) {} initial-users)
          initial-history (or initial-history [])]

      (a/sub pub :user-join user-join)
      (a/sub pub :user-part user-part)
      (a/sub pub :original chat)
      
      (assoc this
        :pub-chan pub-chan
        :initial-history initial-history
        :initial-users initial-users
        :process-chan
        (go-loop [users initial-users
                  history initial-history]
          (a/alt!
            user-join ([{:keys [sender user-name] :as msg}]
                         (log/infof "JOIN: %s" (pr-str msg))
                         (if-not (nil? msg)
                           (do (>! sender msg)
                               (doseq [[name _] users]
                                 (>! sender (msg/->user-join name)))
                               (send-history sender history)
                               (recur (assoc users user-name (sub-user pub user-name sender +user-default-topics+)) history))
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
                    (log/infof "CHAT: %s" (pr-str msg))
                    (if-not (nil? msg)
                      (recur users (conj history msg))
                      (log/warn "ChatRoom shutting down due to \"chat\" channel closing"))))))))
  (stop [this]
    (a/close! pub-chan)
    (a/<!! process-chan)
    (dissoc this :pub-chan :process-chan))

  MsgSink
  (send-msg [this msg sender]
    (a/put! pub-chan (assoc msg :sender sender))))

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
