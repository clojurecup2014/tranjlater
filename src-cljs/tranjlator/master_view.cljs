(ns tranjlator.master-view
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [tranjlator.actions :refer [check-for-enter post-message clear-text
                                         text-entry]]))

(defn send-message-click [sender-ch text owner app]
  (do
    (post-message sender-ch
                  {:topic :original
                   :language "foo"
                   :content text
                   :content-sha "foo"
                   :original-sha "foo"
                   :user-name (:user-name @app)})
    (clear-text owner)))

(defn users-view [app owner]
  (reify
    om/IRender
    (render [this]
      (apply dom/ul nil
             (map (fn [item] (dom/li nil item)) app)))))

(defn chat-view [app owner]
  (reify
    om/IRender
    (render [this]
      (apply dom/ul nil
             (map (fn [item] (dom/li nil (:content item))) app)))))

(defn master-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:text ""})
    om/IRenderState
    (render-state [this {:keys [text] :as state}]
      (let [sender-ch (:sender-ch app)]
        (dom/div #js {:className "container"}
                 (dom/h3 nil "Enter Chat stuff")
                 (dom/div #js {:className "form-horizontal"}
                          (dom/div nil
                                   (dom/div nil
                                            (om/build users-view (:users app)))
                                   (dom/div nil
                                            (om/build chat-view (:original app)))
                                   (dom/div nil
                                            (om/build chat-view (:translated app))))
                          (dom/div #js {:className "form-group"}
                                   (dom/input #js {:className "form-control" :type "text"
                                                   :value text
                                                   :onKeyPress (fn [e] (check-for-enter e owner state app send-message-click))
                                                   :onChange #(text-entry % owner state)}))
                          (dom/div #js {:className "col-xs-offset-2 col-xs-10"}
                                   (dom/button #js {:type "button" :className "button"
                                                    :onClick (fn [e] (send-message-click sender-ch text owner app))}
                                               "click me"))))))))
