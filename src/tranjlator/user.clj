(ns tranjlator.user
  (:require [clojure.core.async :as a :refer [go-loop chan <! >!]]
            [taoensso.timbre :as log]
            [org.httpkit.server :as ws]
            [clojure.tools.reader.edn :as edn]
            
            [tranjlator.chat-room :as chat]))

(defn create-user
  [websocket chat-room]
  (let [user-read (chan 10)
        user-write (chan 10)]

    (ws/on-receive websocket #(a/put! user-read %))
    (ws/on-close websocket #(do (a/close! user-read)
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
          (ws/close websocket))))))
