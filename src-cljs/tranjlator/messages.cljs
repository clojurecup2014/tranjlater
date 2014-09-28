(ns tranjlator.messages
  (:require [cljs.reader :refer [read-string]]))

(defn login-message [user-name]
  {:topic :user-join
   :user-name user-name})

(defn ->language-sub
  [user-name language]
  {:topic :language-sub
   :language (keyword language)
   :user-name user-name})

(defn ->language-unsub
  [user-name language]
  {:topic :language-unsub
   :language (keyword language)
   :user-name user-name})
