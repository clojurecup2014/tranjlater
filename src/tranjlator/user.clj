(ns tranjlator.user
  (:require [clojure.core.async :as a :refer [go go-loop chan <! >!]]
            [taoensso.timbre :as log]
            [org.httpkit.server :as ws]
            [clojure.tools.reader.edn :as edn]
            
            [tranjlator.protocols :as p]
            [tranjlator.messages :as msg]))

(defn create-user
  [user-name websocket chat-room]
  (go (let [user-read (chan 10)
            user-write (chan 10)]

        (if (<! (p/exists? chat-room user-name))
          (do (log/info "disconnecting websocket: username taken:" user-name)
              (ws/send! websocket (pr-str (msg/->error-msg "Username unavailable!")))
              (ws/close websocket))
          (do (log/info "new websocket:" user-name)
              (ws/on-receive websocket #(a/put! user-read %))
              (ws/on-close websocket (fn [& _]
                                       (p/send-msg chat-room (msg/->user-part user-name) user-write)
                                       (a/close! user-read)
                                       (a/close! user-write)))

              (go-loop []
                (let [msg (edn/read-string (<! user-read))]
                  (if-not (nil? msg)
                    (do (p/send-msg chat-room msg user-write)
                        (recur))
                    (ws/close websocket))))

              (go-loop []
                (let [msg (<! user-write)]
                  (if-not (nil? msg)
                    (do (ws/send! websocket (pr-str (dissoc msg :sender)))
                        (recur))
                    (ws/close websocket))))

              (p/send-msg chat-room (msg/->user-join user-name) user-write))))))
