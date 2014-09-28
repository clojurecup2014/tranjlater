(ns tranjlator.datomic
  (:require [clojure.java.io  :as io]
            [taoensso.timbre  :as log]
            [com.stuartsierra.component :as component]
            [datomic.api :as d :refer [db q]]))

(def +schema+ "schema.dtm")
(def +uri+ "datomic:mem://datomic:4334")

(defn read-file
  [f]
  (-> f
      io/resource
      slurp
      read-string))

(defrecord Datomic [conn uri schema]
  
  component/Lifecycle
  (start [this]
    (let [created? (d/create-database uri)
          _log     (when created? (log/info "created datomic db at:" uri))
          conn     (d/connect uri)]
      @(d/transact conn schema)
      (assoc this :conn conn)))

  (stop [this]
    (let []
      (assoc this :conn (d/shutdown false)))))

(defn ->db
  ([] (->db +uri+ +schema+))
  ([uri schema-file]
   (->Datomic nil uri (read-file schema-file))))


(comment
  
  ;; start datomic component & load schema
  (def DB (component/start (->db +uri+ +schema+)))

  ;; assert
  @(d/transact (:conn DB)
               [[:db/add (d/tempid :db.part/user -1) :original/text "hello"]])

  ;; query
  (d/q '[:find ?text
         :where [?e :original/text ?text]]
       (db (:conn DB))) 
                        ; -> #{["hello"]}

  ;; shutdown
  (d/shutdown false)

  ;; maybe delete-database
  (d/delete-database +uri+))
