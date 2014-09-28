(ns tranjlator.signin-view
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [tranjlator.messages :as m]
            [tranjlator.actions :refer [check-for-enter text-entry post-message
                                        clear-text]]))

(defn login-click [sender-ch text owner app]
  (do
    (om/update! app :user-name text)
    (clear-text owner)))

(defn signin-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:text ""})
    om/IRenderState
    (render-state [this {:keys [text] :as state}]
      (let [sender-ch (:sender-ch app)]
        (dom/h3 #js {:className "container"}
                (dom/div #js {:className "form-horizontal"}
                         (dom/div #js {:className "form-group"}
                                  (dom/input #js {:className "form-control" :type "text"
                                                  :value text
                                                  :onKeyPress (fn [e] (check-for-enter e owner state app login-click))
                                                  :onChange (fn [e] (text-entry e owner state))}))
                         (dom/div #js {:className "col-xs-offset-2 col-xs-10"}
                                  (dom/button #js {:type "button" :className "button"
                                                   :onClick (fn [e] (login-click sender-ch text owner app))} "Join"))))))))
