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
              :content-sha "9B71D224BD62F3785D96D46AD3EA3D73319BFBC2890CAADAE2DFF72519673CA72323C3D99BA5C11D7C7ACC6E14B8C5DA0C4663475C2E5C3ADEF46F73BCDEC043"})
      (is (= "Hallo"
             (:content (a/<!! (:out-chan translator))))))))

