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
    om/IDidMount
    (did-mount [_]
      (let [txtbox (.getElementById js/document "user-name")]
        (.focus txtbox)))
    om/IRenderState
    (render-state [this {:keys [text] :as state}]
      (let [sender-ch (:sender-ch app)]
        (dom/div nil
                 (dom/div #js {:className "alert alert-info col-md-8"}
                          (dom/p nil "Welcome to Tranjlator a chat room that lets you read the conversation translated into a language of your choosing.")
                          (dom/p nil "Choose a screen name to get started."))
                 (dom/div #js {:className "panel panel-default col-md-6 col-md-offset-1"}
                          (dom/div #js {:className "panel-heading"}
                                   (dom/div #js {:className "panel-title"}
                                            (dom/h4 nil
                                                    (dom/span #js {:className "glyphicon glyphicon-user"}) " Screen name")))
                          (dom/div #js {:className "panel-body"}
                                   (dom/div #js {:className "form"}
                                            (dom/div nil
                                                     (dom/input #js {:className "form-control col-md-8" :type "text"
                                                                     :value text :id "user-name"
                                                                     :onKeyUp (fn [e] (check-for-enter e owner state app login-click))
                                                                     :onChange (fn [e] (text-entry e owner state))}))
                                            (dom/div nil
                                                     (dom/button #js {:type "button" :className "btn btn-primary col-md-3 col-md-offset-4"
                                                                      :onClick (fn [e] (login-click sender-ch text owner app))} "Join"))))))))))
