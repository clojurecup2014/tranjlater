(ns tranjlator.protocols)

(defprotocol MsgSink
  (send-msg [this msg sender]))
