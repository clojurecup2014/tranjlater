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
      (dom/div #js {:className "col-md-2"}
               (dom/h4 nil "Users")
               (apply dom/ul nil
                      (map (fn [item] (dom/li nil item)) (sort app)))))))

(defn format-chat [{:keys [user-name content]}]
  (str user-name ": " content ))

(defn chat-view [app owner opts]
  (reify
    om/IRenderState
    (render-state [this state]
      (let [label (om/get-state owner :label)
            glyph (om/get-state owner :glyph)]
        (dom/div #js {:className "col-md-5"}
                 (dom/h4 nil
                         (dom/span #js {:className glyph})
                         label)
                 (apply dom/ul nil
                        (map (fn [item] (dom/li nil (format-chat item))) app)))))))

(defn master-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:text ""})
    om/IRenderState
    (render-state [this {:keys [text] :as state}]
      (let [sender-ch (:sender-ch app)]
        (dom/div nil
                 (om/build users-view (:users app))
                 (om/build chat-view (:original app) {:init-state {:label " Original"
                                                                   :glyph "glyphicon glyphicon-globe"}})
                 (om/build chat-view (:translated app) {:init-state {:label " Translated"
                                                                     :glyph "glyphicon glyphicon-home"}})
                 (dom/div #js {:className "form-group col-md-8 col-md-offset-2"}
                          (dom/input #js {:className "form-control" :type "text"
                                          :value text
                                          :onKeyPress (fn [e] (check-for-enter e owner state app send-message-click))
                                          :onChange #(text-entry % owner state)}))
                 (dom/div #js {:className "col-xs-offset-2 col-xs-10"}
                          (dom/button #js {:type "button" :className "button"
                                           :onClick (fn [e] (send-message-click sender-ch text owner app))}
                                      (dom/span #js {:className "glyphicon glyphicon-leaf"}) " Enter")))))))
