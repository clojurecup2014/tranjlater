(ns tranjlator.core
  (:require [org.httpkit.server :refer [run-server with-channel] :as ws]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :refer [resources]]
            [ring.util.response :as resp]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            
            [tranjlator.chat-room :refer [->chat-room]]
            [tranjlator.user :refer [create-user]])
  (:gen-class))

(def +chat-room+ nil)

(defroutes routes
  (GET "/" [] (resp/resource-response "public/html/index.html"))
  (resources "/")
  (GET "/wsapp/" req
       (with-channel req websocket
         (if-not (ws/websocket? websocket)
           (ws/send! websocket "This is not a websocket! Go away!")
           (create-user websocket +chat-room+)))))

(defn -main
  [& [port]]
  (alter-var-root #'+chat-room+ (constantly (-> (->chat-room) component/start)))
  (run-server routes {:port (or port 80)}))
