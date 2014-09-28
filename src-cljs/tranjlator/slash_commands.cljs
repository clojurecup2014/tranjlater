(ns tranjlator.slash-commands)

(def slash-command #"[@/]([^\s]+)")
(def slash-body #"[@/][^\s]+\s+(.*?)\s*$")

(defn command-word
  [text]
  (second (re-find slash-command text)))

(defn command-body
  [text]
  (second (re-find slash-body text)))

(defmulti command
  #(-> % command-word keyword))

(defmethod command :clojure
  [text]
  {:topic :clojure
   :text text
   :body (command-body text)})

(defmethod command :default
  [text]
  {:topic :original
   :language "foo"
   :content text
   :content-sha "foo"
   :original-sha "foo"})
