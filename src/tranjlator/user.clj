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
    (log/info "new websocket")
    (ws/on-receive websocket #(do (log/info "recv:" %)
                                  (a/put! user-read %)))
    (ws/on-close websocket #(do (log/info "closed: %" %)
                                (a/close! user-read)
                                (a/close! user-write)))

    (go-loop []
      (let [msg (edn/read-string (<! user-read))]
        (chat/send-msg chat-room msg user-write)
        (recur)))

    (go-loop []
      (let [msg (<! user-write)]
        (ws/send! websocket (pr-str (dissoc msg :sender)))))))
