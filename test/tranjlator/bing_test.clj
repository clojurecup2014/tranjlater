(ns tranjlator.bing-test
  (:use clojure.test tranjlator.bing)
  (:require [cheshire.core :as json]
            [org.httpkit.client :as http]
            [clojure.set :refer [rename-keys]]
            [clojure.data.xml :as xml]
            [taoensso.timbre  :as log]
            [com.stuartsierra.component :as component]
            [clojure.core.async :as a :refer [<! go go-loop chan >!]]))

(deftest t-translate
  (testing "translate"
    (let [de (-> (->translator :de (chan 1)) component/start)]
      (a/>!! (:ctrl-chan de) {:content "Hello" :language "en"})
      (is (= "Hallo" (a/<!! (:out-chan de)))))))
