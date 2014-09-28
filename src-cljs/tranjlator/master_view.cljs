(ns tranjlator.master-view
  (:require [cljs.core.async :refer [<! >! put!] :as a]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [tranjlator.actions :refer [check-for-enter post-message clear-text
                                        text-entry]]
            [tranjlator.sockets :refer [make-socket]]
            [tranjlator.langs :refer [+langs+]]
            [tranjlator.messages :as m]
            [tranjlator.slash-commands :refer [command]])
  (:require-macros [cljs.core.async.macros :refer [go-loop go alt!]]))

(defn reading-language-change [e sender-ch app]
  (let [new-lang (.. e -target -value)
        old-lang (:reading-language @app)
        user-name (:user-name @app)]
    (when (not (= new-lang old-lang))
      (do
        (om/update! app :reading-language new-lang)
        (put! sender-ch (m/->language-unsub user-name old-lang))
        (put! sender-ch (m/->language-sub user-name new-lang))))))

(defn writing-language-change [e sender-ch app]
  (let [new-lang (.. e -target -value)]
    (om/update! app :writing-language new-lang)))

(defn send-message-click [sender-ch text owner app]
  (do
    #_(post-message sender-ch
                  {:topic :original
                   :language (:writing-language @app)
                   :content text
                   :content-sha "foo"
                   :original-sha "foo"
                   :user-name (:user-name @app)})

  (post-message sender-ch
                (assoc (command text)
                  :user-name (:user-name @app)))
  (clear-text owner)))


(defn users-view [app owner]
  (reify
    om/IRender
    (render [this]
      (dom/div #js {:className "col-md-2"}
               (dom/div #js {:className "panel panel-info"}
                        (dom/div #js {:className "panel-heading"}
                                 (dom/h4 #js {:className "panel-title"}
                                         (dom/span #js {:className "glyphicon glyphicon-user"})
                                         " Users"))
                        (dom/div #js {:className "panel-body"}
                                 (apply dom/ul nil
                                        (map (fn [item] (dom/li nil item)) (sort app)))))))))

(defn format-chat [{:keys [user-name content]}]
  (str user-name ": " content ))

(defn chat-view [app owner opts]
  (reify
    om/IRenderState
    (render-state [this state]
      (let [label (om/get-state owner :label)
            glyph (om/get-state owner :glyph)
            show-lan (om/get-state owner :show-language)]
        (dom/div #js {:className "col-md-5"}
                 (dom/div #js {:className "panel panel-primary"}
                          (dom/div #js {:className "panel-heading"}
                                   (dom/h4 #js {:className "panel-title"}
                                           (dom/span #js {:className glyph})
                                           label))
                          (dom/div #js {:className "panel-body"}
                                   (apply dom/ul #js {:className "list-group"}
                                          (map (fn [item] (dom/li #js {:className "list-group-item"} (format-chat item)
                                                                 (dom/span #js {:className "badge"} (if show-lan (name (:language item)))))) app)))))))))

(defn master-view [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [listener-ch (:listener-ch app)
            sender-ch (:sender-ch app)]
        (make-socket listener-ch sender-ch (:user-name app))
        (go (loop []
              (when-let [msg (<! listener-ch)]
                (cond
                 (= :original (:topic msg)) (om/transact! app :original (fn [col] (conj col msg)))
                 (= :user-join (:topic msg)) (om/transact! app :users (fn [col] (conj col (:user-name msg []))))
                 :default (println "RECVD:" msg "type: " (keys msg)))
                (recur))))))
    om/IInitState
    (init-state [_]
      {:text ""})
    om/IRenderState
    (render-state [this {:keys [text] :as state}]
      (let [sender-ch (:sender-ch app)]
        (dom/div nil
                 (om/build users-view (:users app))
                 (om/build chat-view (:original app) {:init-state {:label " Original"
                                                                   :glyph "glyphicon glyphicon-globe"
                                                                   :show-language true}})
                 (om/build chat-view (:translated app) {:init-state {:label " Translated"
                                                                     :glyph "glyphicon glyphicon-home"
                                                                     :show-language false}})
                 (dom/div #js {:className "form-group col-md-4 col-md-offset-2"}
                          (dom/input #js {:className "form-control" :type "text"
                                          :value text
                                          :onKeyPress (fn [e] (check-for-enter e owner state app send-message-click))
                                          :onChange #(text-entry % owner state)})
                          (dom/select #js {:className "form-control"
                                           :value (:writing-language app)
                                           :onChange (fn [e] (writing-language-change e sender-ch app))}
                                      (dom/option #js {:value :ar} "Arabic")
                                      (dom/option #js {:value :en :selected :selected} "English")
                                      (dom/option #js {:value :fr} "French")))
                 (dom/div #js {:className "col-xs-offset-2 col-xs-4"}
                          (dom/button #js {:type "button" :className "btn btn-primary"
                                           :onClick (fn [e] (send-message-click sender-ch text owner app))}
                                      (dom/span #js {:className "glyphicon glyphicon-leaf"}) " Enter")
                          (dom/select #js {:className "form-control"
                                           :value (:reading-language app)
                                           :onChange (fn [e] (reading-language-change e sender-ch app))}
                                      (dom/option #js {:value nil :selected :selected} "" )
                                      (dom/option #js {:value :ar} "Arabic")
                                      (dom/option #js {:value :en} "English")
                                      (dom/option #js {:value :fr} "French"))))))))
