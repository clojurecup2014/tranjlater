(ns tranjlator.user
  (:require [clojure.core.async :as a :refer [go-loop chan <! >!]]
            [taoensso.timbre :as log]
            [org.httpkit.server :as ws]
            [clojure.tools.reader.edn :as edn]
            
            [tranjlator.chat-room :as chat]
            [tranjlator.messages :as msg]))

(defn create-user
  [user-name websocket chat-room]
  (let [user-read (chan 10)
        user-write (chan 10)]
    (log/info "new websocket:" user-name)
    (ws/on-receive websocket #(a/put! user-read %))
    (ws/on-close websocket (fn [& _]
                             (chat/send-msg chat-room (msg/->user-part user-name) user-write)
                             (a/close! user-read)
                             (a/close! user-write)))

    (go-loop []
      (let [msg (edn/read-string (<! user-read))]
        (if-not (nil? msg)
          (do (chat/send-msg chat-room msg user-write)
              (recur))
          (ws/close websocket))))

    (go-loop []
      (let [msg (<! user-write)]
        (if-not (nil? msg)
          (do (ws/send! websocket (pr-str (dissoc msg :sender)))
              (recur))
          (ws/close websocket))))

    (chat/send-msg chat-room (msg/->user-join user-name) user-write)))
