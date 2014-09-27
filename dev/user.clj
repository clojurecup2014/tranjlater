(ns user
  (:require [tranjlator.core :refer [-main]]
            [clojure.tools.namespace.repl :refer [refresh]]))

(def system nil)

(defn go
  ([] (go 8080))
  ([port]
     (alter-var-root #'system (constantly  {:server (-main port)}))))

(defn stop
  ([] (stop system))
  ([system]
     ((:server system))
     (alter-var-root #'system (constantly nil))))

(defn reset
  []
  (stop)
  (refresh :after 'user/go))
