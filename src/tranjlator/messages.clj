(ns tranjlator.messages)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Client -> Server

(defn ->chat
  [user-name language content sha]
  {:topic :original
   :language language
   :content content
   :content-sha sha
   :original-sha sha
   :user-name user-name})

(defn ->user-join
  [user-name]
  {:topic :user-join
   :user-name user-name})

(defn ->user-part
  [user-name]
  {:topic :user-part
   :user-name user-name})

(defn ->language-sub
  [user-name language]
  {:topic :language-sub
   :language language
   :user-name user-name})

(defn ->language-unsub
  [user-name language]
  {:topic :language-unsub
   :language language
   :user-name user-name})

(defn ->error-msg
  [msg]
  {:topic :error
   :msg msg})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server -> Client

(defn ->translation
  [language content original-sha content-sha sender-name]
  {:topic language
   :language language
   :content content
   :original-sha original-sha
   :content-sha content-sha
   :translated-sha content-sha
   :user-name sender-name})
