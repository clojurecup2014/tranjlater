(ns tranjlator.sockets
  (:require [cljs.core.async :refer [<! >!] :as a]
            [chord.client :refer [ws-ch]]
            [goog.events :as evt]
            [cljs.reader :refer [read-string]])
  (:require-macros [cljs.core.async.macros :refer [go-loop go]])
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

(defn make-socket [listener-ch sender-ch]
  (let [ws (doto (WebSocket.)
             (.open (str "ws://" (.. js/window -location -host) +ws-url+)))]

    (.dir js/console ws)
    (.dir js/console EventType)
    (evt/listen ws (.-CLOSED EventType)  #(do (a/close! listener-ch)
                                              (a/close! sender-ch)))
    (evt/listen ws (.-OPENED EventType)  #(println "WS Connected"))
    (evt/listen ws (.-MESSAGE EventType) #(a/put! listener-ch (read-string (.-message %))))
    (evt/listen ws (.-ERROR EventType )  #(println "WS Error:" %))

    (go-loop []
      (when-some [msg (<! sender-ch)]
        (.send ws (pr-str msg))
        (recur))
      (a/close! listener-ch)
      (.close ws)
      (.disposeInternal ws))

    ;; (when ws-channel
    ;;   (do
    ;;     (handle-incoming listener-ch ws-channel)
    ;;     (handle-outgoing sender-ch ws-channel)))
    ))
