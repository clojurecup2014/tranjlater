(ns tranjlator.bing
  (:require [cheshire.core :as json]
            [org.httpkit.client :as http]
            [clojure.set :refer [rename-keys]]
            [clojure.data.xml :as xml]
            [taoensso.timbre  :as log]
            [com.stuartsierra.component :as component]
            [clojure.core.async :as a :refer [<! go go-loop chan >!]]
            [clojure.core.async.impl.protocols :as impl]))

(def +creds+
  {:client-id "clojure-cup-tranjlator"
   :client-secret "yEyqkowDUzg2mRNDky0PsTIaby3iatg1XILEeKYKB9Y="})

;; Util
(defn now [] (System/currentTimeMillis))
(def sec->msec (partial * 1000))

(defn expired?
  [{:keys [expires] :as token}]
  (> (now) expires))

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

(defn translate!
  ([text from to]
     (translate! (access-token) text from to))
  ([access-token text from to]
     (log/info "here")
     (let [result-chan (chan 1 (map (fn [{:keys [status body] :as res}]
                                      (case status
                                        400 res
                                        200 (-> body xml/parse-str :content first)))))]
       (go (http/get "http://api.microsofttranslator.com/v2/Http.svc/Translate"
                     {:headers {"Authorization" (str "Bearer " (if (extends? impl/ReadPort (type access-token))
                                                                 (:token (<! access-token))
                                                                 access-token))}
                      :query-params {:text text
                                     :from (name from)
                                     :to   (name to)}}
                     #(a/put! result-chan %)))
       result-chan)))



(defrecord Translator
    [api-creds language ctrl-chan work-chan out-chan]

  component/Lifecycle
  (start [this]
    (let [ctrl-chan (chan 1000)]
      (assoc this
        :ctrl-chan ctrl-chan
        :work-chan (go-loop [token (<! (access-token api-creds))]
                     (when-some [msg (<! ctrl-chan)]
                       (let [token (if (expired? token) (<! (access-token api-creds)) token)]
                         (>! out-chan (<! (translate! (:token token)
                                                      (:content msg)
                                                      (:language msg)
                                                      (:language this))))
                         (recur token)))))))

  (stop [this]
    (a/close! ctrl-chan)
    (a/close! out-chan)
    (a/<!!    work-chan)
    (dissoc this :ctrl-chan :work-chan :out-chan)))

(defn ->translator
  ([language out-chan]
     (->translator +creds+ language out-chan))
  ([api-creds language out-chan]
     (->Translator api-creds (name language) nil nil out-chan)))

(comment
  (a/<!! (access-token))
  (a/<!! (translate! "Hello, World." :en :de))

  (let [out-chan (chan 1)]
    (def translator (-> (->translator :de out-chan) component/start))
    (a/>!! (:ctrl-chan translator) {:topic "original"
                                    :language :en
                                    :content "Hello, World!"
                                    :content-sha "foo"
                                    :original-sha "foo"
                                    :user-name "bar"})
    (a/<!! out-chan))

  )

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





