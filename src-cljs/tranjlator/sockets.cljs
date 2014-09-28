(ns tranjlator.sockets
  (:require [cljs.core.async :refer [<! >!] :as a]
            [chord.client :refer [ws-ch]]
            [goog.events :as evt]
            [cljs.reader :refer [read-string]])
  (:require-macros [cljs.core.async.macros :refer [go-loop go alt!]])
  (:import [goog.net WebSocket]
           [goog.net.WebSocket EventType]))

(def +ws-url+ "/wsapp/")

(defn handle-incoming [listener-ch server-ch]
  (go
   (loop []
     (when-let [msg (<! server-ch)]
       (do (>! listener-ch msg)
           (recur))))))

(defn handle-outgoing [sender-ch server-ch]
  (go
   (loop []
     (when-let [msg (<! sender-ch)]
       (>! server-ch msg)
       (recur)))))

(defn make-socket [listener-ch sender-ch user-name]
  (let [ws (doto (WebSocket.)
             (.open (str "ws://" (.. js/window -location -host) +ws-url+ user-name)))]

    (evt/listen ws (.-CLOSED EventType)  #(do (a/close! listener-ch)
                                              (a/close! sender-ch)))
    (evt/listen ws (.-OPENED EventType)  #(println "WS Connected"))
    (evt/listen ws (.-MESSAGE EventType) #(a/put! listener-ch (read-string (.-message %))))
    (evt/listen ws (.-ERROR EventType )  #(println "WS Error:" %))

    (go-loop []
      (let [timeout (a/timeout 15000)]
        (alt!
          timeout ([_]
                     (println "pinging")
                     (.send ws (pr-str {:topic :ping}))
                     (recur))
          sender-ch ([msg]
                       (if-not (nil? msg)
                         (do (.send ws (pr-str msg))
                             (recur))
                         (do (a/close! listener-ch)
                             (.close ws)
                             (.dispose ws)))))))))
