(ns tranjlator.messages)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Client -> Server

(defn ->chat
  [language content sha user-name]
  {:topic "original"
   :language language
   :content content
   :content-sha sha
   :original-sha sha
   :user-name user-name})

(defn ->user-join
  [user-name]
  {:topic "user-join"
   :user-name user-name})

(defn ->user-leave
  [user-name]
  {:topic "user-leave"
   :user-name user-name})


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
