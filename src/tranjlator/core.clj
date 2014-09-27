(ns tranjlator.core
  (:require [org.httpkit.server :refer [run-server]]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :refer [resources]]
            [ring.util.response :as resp]
            [taoensso.timbre :as log])
  (:gen-class))



(defroutes routes
  (GET "/" [] (resp/resource-response "public/html/index.html"))
  (resources "/")
  )

(defn -main
  [& [port]]
  (run-server routes {:port (or port 80)}))
