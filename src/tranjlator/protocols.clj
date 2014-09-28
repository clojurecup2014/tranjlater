(ns tranjlator.protocols)

(defprotocol MsgSink
  (send-msg [this msg sender]))

(defprotocol Exists?
  (exists? [this token]
    "Returns a channel which will receive the result of the existence check."))

