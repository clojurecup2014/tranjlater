(ns tranjlator.core
  (:require [org.httpkit.server :refer [run-server]]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :refer [resources]]
            [ring.util.response :as resp])
  (:gen-class))

(defroutes routes
  (GET "/" [] (resp/resource-response "public/html/index.html"))
  (resources "/")
  )

(defn -main
  "I don't do a whole lot."
  [& [port]]
  (run-server routes {:port (or port 80)}))
