(ns user
  (:require [tranjlator.core :refer [-main]]))

(def system nil)

(defn go
  ([] (go 8080))
  ([port]
     (alter-var-root #'system (constantly (-main port)))))
