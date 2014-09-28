(ns tranjlator.bing ;translator
  (:require [cheshire.core :as json]
            [org.httpkit.client :as http]
            [clojure.set :refer [rename-keys]]
            [clojure.data.xml :as xml]
            [taoensso.timbre  :as log]
            [com.stuartsierra.component :as component]
            [clojure.core.async :as a :refer [<! go go-loop chan >!]]
            [clojure.core.async.impl.protocols :as impl]
            [tranjlator.datomic :refer [->db]]
            [datomic.api :as d :refer [db q]]
            [pandect.core :refer [sha512-bytes sha512]]
            [clojure.string :refer [lower-case]]
            [tranjlator.messages :refer [->translation]]
            [tranjlator.protocols :as p]))
                                                          
(def +creds+
  {:client-id "clojure-cup-tranjlator"
   :client-secret "yEyqkowDUzg2mRNDky0PsTIaby3iatg1XILEeKYKB9Y="})

;; Util
(defn now [] (System/currentTimeMillis))
(def sec->msec (partial * 1000))

(defn sha-bytes
  [s]
  (sha512-bytes ((fnil lower-case "") s)))

(defn bin->hex
  [^bytes b]
  (javax.xml.bind.DatatypeConverter/printHexBinary b))

(defn hex->bin
  [^String s]
  (javax.xml.bind.DatatypeConverter/parseHexBinary s))

(defn expired?
  [{:keys [expires] :as token}]
  (> (now) expires))

(defn db-lookup-translation
  [conn sha from to]
  (go
    (log/info "querying datomic" [sha from to])
    (let [res (q '[:find ?text ?org-sha ?trans-sha
                   :in $ ?sha ?from ?to
                   :where
                   [?o :original/sha    ?org-sha]
                   [?o :original/language  ?from]
                   [?o :original/translation  ?e]
                   [?e :translation/language ?to]
                   [?e :translation/text   ?text]
                   [?e :translation/sha ?trans-sha]
                   [(java.util.Arrays/equals ^bytes ?sha ^bytes ?org-sha)]]
                 (db conn) sha from to)]
      (log/info "Datomic query response:" res)
      (first res))))

;; Bing API
(defn request-access-token
  "creds contains keys: [:client-id :client-secret]"
  ([] (request-access-token +creds+))
  ([creds]
     (let [result-chan (chan 1 (map (fn [{:keys [status body] :as res}]
                                      (json/parse-string body true))))]
       (http/post "https://datamarket.accesscontrol.windows.net/v2/OAuth2-13"
                  {:headers {"content-type" "application/x-www-form-urlencoded"}
                   :form-params {:scope "http://api.microsofttranslator.com"
                                 :grant_type "client_credentials"
                                 :client_id     (:client-id     creds)
                                 :client_secret (:client-secret creds)}}
                  #(a/put! result-chan %))
       result-chan)))

(defn access-token
  ([] (access-token +creds+))
  ([creds]
     (go (let [{:keys [access_token expires_in] :as token} (<! (request-access-token creds))]
           {:token access_token
            :expires (+ (now) (sec->msec (Long/parseLong expires_in)))}))))



(defn translation-tx
  "assertion of facts regarding text's translation"
  [org-text org-sha from to trans-text trans-sha]
  [[:db/add (d/tempid :db.part/user -1) :original/language from]
   [:db/add (d/tempid :db.part/user -1) :original/text org-text]
   [:db/add (d/tempid :db.part/user -1) :original/sha   org-sha]
   [:db/add (d/tempid :db.part/user -1) :original/translation (d/tempid :db.part/user -2)]
                                                                                                       
   [:db/add (d/tempid :db.part/user -2) :translation/language to]
   [:db/add (d/tempid :db.part/user -2) :translation/text trans-text]
   [:db/add (d/tempid :db.part/user -2) :translation/sha  trans-sha]])

(defn parse-translation
  [{:keys [status body] :as res}]
  (case status
    400 res
    200 (-> body xml/parse-str :content first)
    res)) ; datomic lookups won't have a status key -- return res

(defn translate!
  ([text from to db]
     (translate! (access-token) text from to db))
  ([access-token text from to db]
     (log/info "entering translate!")
     (let [result-chan (chan 1)
           sha (sha-bytes text)]
       (go
         (if-let [[trans org-sha trans-sha :as resp] (<! (db-lookup-translation (:conn db) sha from to))]
           (do (log/info "Datomic translation:" resp)
               (>! result-chan  resp))
           (do
             (log/info "Querying Microsoft translation service..")
             (http/get "http://api.microsofttranslator.com/v2/Http.svc/Translate"
                       {:headers {"Authorization" (str "Bearer " (if (extends? impl/ReadPort (type access-token))
                                                                   (:token (<! access-token))
                                                                   access-token))}
                        :query-params {:text text
                                       :from (name from)
                                       :to   (name to)}}
                       #(let [trans (parse-translation %)
                              trans-sha (sha-bytes trans)
                              _ (log/info "Bing translation:" [trans trans-sha])
                              tx-data (translation-tx text sha from to trans trans-sha)]
                          (when trans
                            (log/info "transacting:" tx-data)
                            @(d/transact (:conn db)  tx-data))
                          (a/put! result-chan [trans sha trans-sha]))))))
       result-chan)))



(defrecord Translator
    [api-creds language ctrl-chan work-chan out-chan db]

  component/Lifecycle
  (start [this]
    (let [ctrl-chan (chan 1000)]
      (assoc this
        :ctrl-chan ctrl-chan
        :work-chan (go-loop [token (<! (access-token api-creds))]
                     (when-some [msg (<! ctrl-chan)]
                       (let [token (if (expired? token) (<! (access-token api-creds)) token)
                             [trans org-sha trans-sha]  (<! (translate! (:token token)
                                                                        (:content msg)
                                                                        (keyword (:language msg))
                                                                        (:language this)
                                                                        db))]
                         (>! out-chan (->translation language
                                                     trans
                                                     (doto (bin->hex  org-sha) (->> pr-str (log/info "Origin-SHA:")))
                                                     (doto (bin->hex trans-sha) (->> pr-str (log/info "trans-SHA:")))
                                                     (:user-name msg)))
                         (recur token)))))))

  (stop [this]
    (a/close! ctrl-chan)
    (a/close! out-chan)
    (a/<!!    work-chan)
    (dissoc this :ctrl-chan :work-chan :out-chan))

  p/Token
  (token [this]
    (access-token api-creds))

  p/Translate
  (translate [this text src-lang token]
    (translate! (:token token) text src-lang (:language this) db)))

(defn ->translator
  ([language out-chan]
     (->translator +creds+ language out-chan))
  ([api-creds language out-chan]
     (component/using (->Translator api-creds language nil nil out-chan nil)
                      [:db])))


;; unused
(def +langs+
  {:ar {:code "ar" :name "Arabic"}
   :bg {:code "bg" :name "Bulgarian"}
   :ca {:code "ca" :name "Catalan"}
   :zh-CHS {:code "zh-CHS" :name "Chinese Simplified"}
   :zh-CHT {:code "zh-CHT" :name "Chinese Traditional"}
   :cs {:code "cs" :name "Czech"}
   :da {:code "da" :name "Danish"}
   :nl {:code "nl" :name "Dutch"}
   :en {:code "en" :name "English"}
   :et {:code "et" :name "Estonian"}
   :fi {:code "fi" :name "Finnish"}
   :fr {:code "fr" :name "French"}
   :de {:code "de" :name "German"}
   :el {:code "el" :name "Greek"}
   :ht {:code "ht" :name "Haitian Creole"}
   :he {:code "he" :name "Hebrew"}
   :hi {:code "hi" :name "Hindi"}
   :mww {:code "mww" :name "Hmong Daw"}
   :hu {:code "hu" :name "Hungarian"}
   :id {:code "id" :name "Indonesian"}
   :it {:code "it" :name "Italian"}
   :ja {:code "ja" :name "Japanese"}
   :tlh {:code "tlh" :name "Klingon"}
   :tlh-Qaak {:code "tlh-Qaak" :name "Klingon (pIqaD)"}
   :ko {:code "ko" :name "Korean"}
   :lv {:code "lv" :name "Latvian"}
   :lt {:code "lt" :name "Lithuanian"}
   :ms {:code "ms" :name "Malay"}
   :mt {:code "mt" :name "Maltese"}
   :no {:code "no" :name "Norwegian"}
   :fa {:code "fa" :name "Persian"}
   :pl {:code "pl" :name "Polish"}
   :pt {:code "pt" :name "Portuguese"}
   :ro {:code "ro" :name "Romanian"}
   :ru {:code "ru" :name "Russian"}
   :sk {:code "sk" :name "Slovak"}
   :sl {:code "sl" :name "Slovenian"}
   :es {:code "es" :name "Spanish"}
   :sv {:code "sv" :name "Swedish"}
   :th {:code "th" :name "Thai"}
   :tr {:code "tr" :name "Turkish"}
   :uk {:code "uk" :name "Ukrainian"}
   :ur {:code "ur" :name "Urdu"}
   :vi {:code "vi" :name "Vietnamese"}
   :cy {:code "cy" :name "Welsh"}})


(comment
  (a/<!! (access-token))
  (a/<!! (translate! "Hello, World." :en :de))

  (def sys
    (component/start
     (component/system-map
      :db (->db)
      :translator (->translator :de (chan 1)))))

  (a/>!! (:ctrl-chan (:translator sys))
         {:language :en :content "Hello"}))
