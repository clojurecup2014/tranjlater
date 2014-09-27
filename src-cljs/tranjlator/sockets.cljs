(ns tranjlator.sockets
  (:require [cljs.core.async :refer [<! >!] :as a]
            [chord.client :refer [ws-ch]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

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
  (go
   (let [{:keys [ws-channel error]} (<! (ws-ch (str "ws://" (.. js/window -location -host) +ws-url+)))]
     (when ws-channel
       (do
         (handle-incoming listener-ch ws-channel)
         (handle-outgoing sender-ch ws-channel))))))
