(ns tranjlator.core
  (:require [org.httpkit.server :refer [run-server]]
            [compojure.core :refer [defroutes GET]])
  (:gen-class))

(defroutes routes
  (GET "/" [] "Hello World!"))

(defn -main
  "I don't do a whole lot."
  [& [port]]
  (run-server routes {:port (or port 80)}))
