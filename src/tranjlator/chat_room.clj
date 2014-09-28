(ns tranjlator.chat-room
  (:refer-clojure :exclude [join])
  (:require [clojure.core.async :as a :refer [go go-loop chan <! >!]]
            [tranjlator.messages :as msg]
            [tranjlator.protocols :as p]
            [tranjlator.try-clojure :as try]
            [tranjlator.bing :as xlate]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]))

(defn send-history
  [user history]
  (go (doseq [h history]
        (>! user h))))

(defn send-user-list
  [user user-list]
  (go (doseq [[name _] user-list]
        (>! user (msg/->user-join name)))))

(def ^:const +user-default-topics+ #{:original :user-join :user-part})

(defn remove-own-chats
  [user-name]
  (remove #(= user-name (:user-name %))))

(defn sub-user
  [pub user-name user-chan topics]
  (doseq [t +user-default-topics+]
    (a/sub pub t user-chan)))


(defn mock-translator
  [pub pub-chan language]
  (let [translator-chan (chan 1 (map #(merge % {:topic language
                                                :language language
                                                :content (format "This will be translated to: %s!" (pr-str language) )
                                                :content-sha "sha!"
                                                :original-sha "some other sha!"
                                                :translated-sha "sha!"})))]
    (a/sub pub :original translator-chan)
    (a/pipe translator-chan pub-chan)
    translator-chan))

(def ^:const +clojure-username+ "clojure")

(defn clojure-request
  [command form]
  (str command " " form))

(defn clojure-response
  [result]
  (str ";; => " result))

(defn create-translator
  [pub pub-chan language]
  (assert (keyword? language) "Invalid language specifier")
  (log/infof "Creating translator for lang: %s" (pr-str language))
  (let [translation-msg-chan (chan 1 (map #(do (log/info "XLATED:" (pr-str %))
                                               %)))
        translator (-> (xlate/->translator language translation-msg-chan)
                       component/start)]
    (a/sub pub :original (:ctrl-chan translator))
    (a/pipe translation-msg-chan pub-chan)
    translator))

(defrecord ChatRoom
    [initial-users initial-history pub-chan process-chan mock-translator?]

  component/Lifecycle
  (start [this]
    (let [pub-chan (chan 1000)
          pub (a/pub pub-chan :topic)
          user-part (chan 10)
          user-join (chan 10)
          exists (chan 10)
          chat (chan 10)
          clojure (chan 10)
          language-sub (chan 10)
          language-unsub (chan 10)
          initial-history (or initial-history [])]

      (a/sub pub :user-join user-join)
      (a/sub pub :user-part user-part)
      (a/sub pub :original chat)
      (a/sub pub :exists? exists)
      (a/sub pub :clojure clojure)
      
      (a/sub pub :language-sub language-sub)
      (a/sub pub :language-unsub language-unsub)

      (doseq [[name chan] initial-users]
        (sub-user pub name chan +user-default-topics+))

      (assoc this
        :pub-chan pub-chan
        :initial-history initial-history
        :initial-users initial-users
        :process-chan
        (go-loop [users (assoc initial-users +clojure-username+ (chan 1 (remove (constantly true))))
                  history initial-history,
                  translators {},
                  try-clj-cookie nil]
          (a/alt!
            user-join ([{:keys [sender user-name] :as msg}]
                         (log/infof "JOIN: %s" (pr-str msg))
                         (if-not (nil? msg)
                           (do (>! sender msg)
                               (send-user-list sender users)
                               (send-history sender history)
                               (sub-user pub user-name sender +user-default-topics+)
                               (recur (assoc users user-name sender) history translators
                                      try-clj-cookie))
                           (log/warn "ChatRoom shutting down due to \"user-join\" channel closing")))

            user-part ([{:keys [user-name] :as msg}]
                         (log/infof "PART: %s" (pr-str msg))
                         (if-not (nil? msg)
                           (do (when-let [chan (get users user-name)]
                                 (doseq [t +user-default-topics+]
                                   (a/unsub pub t chan)))
                               (recur (dissoc users user-name) history translators
                                      try-clj-cookie))
                           (log/warn "ChatRoom shutting down due to \"user-leave\" channel closing")))

            chat ([msg]
                    (log/infof "CHAT: %s" (pr-str msg))
                    (if-not (nil? msg)
                      (recur users (conj history msg) translators try-clj-cookie)
                      (log/warn "ChatRoom shutting down due to \"chat\" channel closing")))

            language-sub ([{:keys [language user-name] :as msg}]
                            (log/infof "LANG-SUB: %s users: %s" (pr-str msg) (pr-str users))
                            (if-not (nil? msg)
                              (when-let [chan (get users user-name)]
                                (a/sub pub language chan)
                                (>! chan msg)
                                (recur users history
                                       (if-not (contains? translators language)
                                         (try (assoc translators language (if mock-translator?
                                                                            (mock-translator pub pub-chan language)
                                                                            (create-translator pub pub-chan language)))
                                              (catch Throwable _
                                                (>! chan (msg/->error-msg (format "Could not create translator for %s" (pr-str language))))
                                                translators))
                                         translators)
                                       try-clj-cookie))
                              (log/warn "ChatRoom shutting down due to \"language-sub\" channel closing")))

            language-unsub ([{:keys [language user-name] :as msg}]
                              (log/infof "LANG-SUB: %s" (pr-str msg))
                              (if-not (nil? msg)
                                (when-let [chan (get users user-name)]
                                  (a/unsub pub language chan)
                                  (>! chan msg)
                                  (recur users history translators try-clj-cookie))
                                (log/warn "ChatRoom shutting down due to \"language-unsub\" channel closing")))

            exists ([{:keys [user-name response-chan] :as msg}]
                      (if-not (nil? msg)
                        (do (log/info "users:" (pr-str users))
                            (>! response-chan (contains? users user-name))
                            (recur users history translators try-clj-cookie))
                        (log/warn "ChatRoom shutting down due to \"exists\" channel closing")))

            clojure ([{:keys [text body user-name] :as msg}]
                       (if-not (nil? msg)
                         (let [expr-msg (msg/->chat user-name "clojure"
                                                    text
                                                    "foo")
                               query-result (try/query body try-clj-cookie)]
                           (>! pub-chan expr-msg)
                           (let [{:keys [result cookie]} (<! query-result)
                                 result-msg (msg/->chat +clojure-username+
                                                         "clojure"
                                                         (clojure-response (:result result))
                                                        "foo")]
                             (>! pub-chan result-msg)
                             (recur users history translators cookie)))
                         (log/warn "ChatRoom shutting down due to \"clojure\" channel closing"))))))))
  
  (stop [this]
    (a/close! pub-chan)
    (a/<!! process-chan)
    (dissoc this :pub-chan :process-chan :initial-users :initial-history))

  p/MsgSink
  (send-msg [this msg sender]
    (a/put! pub-chan (assoc msg :sender sender)))

  p/Exists?
  (exists? [this user]
    (log/info "exists check:" user)
    (let [response-chan (chan 1)]
      (a/put! pub-chan {:topic :exists? :user-name user :response-chan response-chan})
      response-chan)))

(defn ->chat-room
  ([]
     (->chat-room []))
  ([history]
     (->ChatRoom nil history nil nil nil)))

(comment
  (require '[tranjlator.messages :refer :all])
  (def brian (chan))
  (def bob (chan))

  (def chat-room
    (-> (->ChatRoom nil nil) component/start))

  (chat chat-room (->chat "foo" "foo" "foo" "user-1"))

  (join chat-room "brian" brian)
  (join chat-room "bob" bob)

  
  
  )
