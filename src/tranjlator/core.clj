(ns tranjlator.core
  (:require [org.httpkit.server :refer [run-server with-channel] :as ws]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :refer [resources]]
            [ring.util.response :as resp]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            
            [tranjlator.chat-room :refer [->chat-room]]
            [tranjlator.user :refer [create-user]]
            [tranjlator.datomic :refer [->db]])
  (:gen-class))

(def +system+ nil)

(defroutes routes
  (GET "/" [] (resp/resource-response "public/html/index.html"))
  (resources "/")
  (GET "/wsapp/:user-name" [user-name :as req]
       (with-channel req websocket
         (if-not (ws/websocket? websocket)
           (ws/send! websocket "This is not a websocket! Go away!")
           (create-user user-name websocket (:chat-room +system+))))))

(defn system
  [db-uri bing-creds]
  (component/system-map
   :db (->db)
   :chat-room (->chat-room)))

(defn -main
  [& [port]]
  (alter-var-root #'+system+ (constantly (-> (system nil nil) component/start-system)))
  (run-server routes {:port (or port 80)}))
