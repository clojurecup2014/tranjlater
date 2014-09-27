(ns tranjlator.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [chord.client :refer [ws-ch]]
            [cljs.core.async :as a]
            [sablono.core :as html :refer-macros [html]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(def +ws-url+ "ws://....")

(def sample-source {:topic "Original" :language "English" :content "Hi" :original-sha "XYZ"
                    :content-sha "XYZ" :user-name "bob"})

(def sample-translation {:topic "German" :language "German" :content "Guten Tag"
                         :original-sha "XYZ" :content-sha "ABC" :user-name "bob"})

(def app-state (atom {:original [sample-source]
                      :translated [sample-translation]}))

(defn chat-view [app owner]
  (reify
    om/IRender
    (render [this]
      (apply dom/ul nil
             (map (fn [item] (dom/li nil (:content item))) app)))))

(defn master-view [app owner]
  (reify
    om/IRender
    (render [this]
      (dom/div #js {:className "container"}
               (dom/h3 nil "Enter Chat stuff")
               (dom/form #js {:className "form-horizontal"}
                         (dom/div nil
                                  (dom/div nil
                                           (om/build chat-view (:original app)))
                                  (dom/div nil
                                           (om/build chat-view (:translated app))))
                         (dom/div #js {:className "form-group"}
                                  (dom/input #js {:className "form-control" :type "text"}))
                         (dom/div #js {:className "col-xs-offset-2 col-xs-10"}
                                  (dom/button #js {:type "button" :className "button"}
                                              "click me")))))))

(om/root
 master-view
 app-state
 {:target (. js/document (getElementById "app"))})
