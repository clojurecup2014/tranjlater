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
             {:content "Hello" :language "en"})
      (is (= "Hallo"
             (:content (a/<!! (:out-chan translator))))))))

(deftest t-store-translations

  (testing "check datomic for translation"
    )

  (testing ""))
