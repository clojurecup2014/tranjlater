(ns tranjlator.messages)

(defn login-message [user-name]
  {:topic :user-join
   :user-name user-name})
