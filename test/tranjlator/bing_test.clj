(ns tranjlator.bing-test
  (:use clojure.test tranjlator.bing)
  (:require [taoensso.timbre  :as log]
            [com.stuartsierra.component :as component]
            [clojure.core.async :as a :refer [<! go go-loop chan >!]]
            [tranjlator.datomic :refer [->db]]))

(deftest t-translate
  (testing "Hello -> Hallo"
    (let [system (component/start
                  (component/system-map
                   :db (->db)
                   :translator (->translator :de (chan 1))))
          translator (get-in system [:translator])]
      (a/>!! (:ctrl-chan translator)
             {:content "Hello" :language "en"
              :content-sha "9b71d224bd62f3785d96d46ad3ea3d73319bfbc2890caadae2dff72519673ca72323c3d99ba5c11d7c7acc6e14b8c5da0c4663475c2e5c3adef46f73bcdec043"})
      (is (= "Hallo"
             (:content (a/<!! (:out-chan translator))))))))

