(ns tranjlator.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [chord.client :refer [ws-ch]]
            [cljs.core.async :refer [chan <! >! put!] :as a]
            [sablono.core :as html :refer-macros [html]]
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
                      :translated []
                      :users []
                      :chat-hisory []
                      :user-name nil
                      :dissappointed-user nil
                      :listener-ch (chan)
                      :sender-ch (chan)
                      :socket-ctrl (chan)
                      :reading-language nil
                      :writing-language "en"
                      :connection-good true}))

(defn view-picker [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div {:class "container"}
        [:div  {:class "row"}
         [:h2 "Tranjlator"]]
        (if (empty? (:user-name app))
          (om/build signin-view app)
          (om/build master-view app))]))))

(om/root
 view-picker
 app-state
 {:target (. js/document (getElementById "app"))})
