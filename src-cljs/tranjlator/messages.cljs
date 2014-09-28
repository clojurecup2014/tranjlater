(ns tranjlator.messages)

(defn login-message [user-name]
  {:topic :user-join
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
