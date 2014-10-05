(ns tranjlator.signin-view
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
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
        (html
         [:div
          (if-let [poor-soul (:dissappointed-user app)]
            [:div {:class "alert alert-danger"}
             [:p  "I am sorry " poor-soul " but that screen name is already in use. Please try another." ]]
            [:div {:class "alert alert-info col-md-8"}
             [:p "Welcome to Tranjlator a chat room that lets you read the conversation translated into a language of your choosing."]
             [:p "Choose a screen name to get started."]])
          [:div {:class "panel panel-default col-md-6 col-md-offset-1"}
           [:div {:class "panel-heading"}
            [:div {:class "panel-title"}
             [:h4
              [:span {:class "glyphicon glyphicon-user"} " Screen name"]]]]
           [:div {:class "panel-body"}
            [:div {:class "form"}
             [:div
              [:input {:class "form-control col-md-8" :type "text"
                       :value text :id "user-name"
                       :onKeyUp (fn [e] (check-for-enter e owner state app login-click))
                       :onChange (fn [e] (text-entry e owner state))}]]
             [:div
              [:button {:type "button" :class "btn btn-primary col-md-3 col-md-offset-4"
                        :onClick (fn [e] (login-click sender-ch text owner app))} "Join"]]]]]])))))
