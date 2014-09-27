(ns tranjlator.messages)

(defn login-message [user-name]
  (.log js/console "here")
  {:user-name user-name})
