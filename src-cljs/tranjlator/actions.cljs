(ns tranjlator.actions
  (:require [om.core :as om :include-macros true]
   ))

(defn check-for-enter [e owner {:keys [text sender-ch]} app click-fn]
  (when (or (= 13 (.-keyCode e))
            (= 13 (.-which e)))
    (click-fn sender-ch text owner app)))

(defn clear-text [owner]
  (om/set-state! owner :text ""))

(defn post-message [target msg]
  (println (pr-str msg))
  #_(put! target msg))

(defn text-entry [e owner {:keys [text]}]
  (om/set-state! owner :text (.. e -target -value)))
