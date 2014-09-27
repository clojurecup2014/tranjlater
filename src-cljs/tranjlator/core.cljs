(ns tranjlator.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [chord.client :refer [ws-ch]]
            [cljs.core.async :refer [chan <! >! put!] :as a]
            [tranjlator.master-view :refer [master-view]]
            [tranjlator.signin-view :refer [signin-view]]
            [tranjlator.sockets :as s :refer
             [make-socket]]
            [tranjlator.sample-data :refer
             [sample-source sample-translation sample-users]]
            [tranjlator.actions :refer [check-for-enter post-message clear-text
                                        text-entry]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(def app-state (atom {:original []
                      :translated [sample-translation]
                      :users []
                      :user-name nil
                      :listener-ch (chan)
                      :sender-ch (chan)}))

(defn view-picker [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [listener-ch (:listener-ch app)
            sender-ch (:sender-ch app)]
        (make-socket listener-ch sender-ch)
        (go (loop []
              (let [msg (<! listener-ch)
                    topic (get-in msg [:message :topic])]
                (cond
                 (= :original topic) (om/transact! app :original (fn [col] (conj col (:message msg))))
                 (= :user-join topic) (om/transact! app :users (fn [col] (conj col (get-in msg [:message :user-name]))))
                 :default (println "RECVD:" msg))
                (recur))))))
    om/IRender
    (render [_]
      (dom/div #js {:className "container"}
               (dom/div {:className "row"}
                        (dom/h2 nil "Tranjlator"))
               (if (empty? (:user-name app))
                 (om/build signin-view app)
                 (om/build master-view app))))))

(om/root
 view-picker
 app-state
 {:target (. js/document (getElementById "app"))})
