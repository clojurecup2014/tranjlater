(ns tranjlator.try-clojure
  (:require [org.httpkit.client :as http]
            [cheshire.core :as json]
            [clojure.core.async :as a :refer [<! go go-loop chan >!]]))

(def ^:const +http-route+ "http://tryclj.com/eval.json")

(defn read-response
  [resp]
  {:result (-> resp :body (json/parse-string true))
   :cookie (get-in resp [:headers :set-cookie]
                   (get-in resp [:opts :headers "Cookie"]))})

(defn query
  ([expr]
     (query expr nil))
  ([expr cookie]
     (let [resp-chan (chan 1 (map read-response))]
       (http/get +http-route+
                 (merge {:query-params {:expr expr}}
                        (when cookie
                          {:headers {"Cookie" cookie}}))
                 #(a/put! resp-chan %))
       resp-chan)))

(comment
  (def result (a/<!! (query "(def foo (+ 1 1))")))

  (a/<!! (query "foo" (:cookie result)))

  )
