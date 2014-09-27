(ns tranjlator.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [chord.client :refer [ws-ch]]
            [cljs.core.async :refer [chan <! >! put!] :as a]
            [sablono.core :as html :refer-macros [html]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(def +ws-url+ "ws://....")

(def sample-source {:topic "Original" :language "English" :content "Hi" :original-sha "XYZ"
                    :content-sha "XYZ" :user-name "bob"})

(def sample-translation {:topic "German" :language "German" :content "Guten Tag"
                         :original-sha "XYZ" :content-sha "ABC" :user-name "bob"})

(def sample-users ["Eric" "Colin" "Brian" "Rick"])

(def app-state (atom {:original [sample-source]
                      :translated [sample-translation]
                      :users sample-users
                      :user-name nil
                      :listener-ch (chan)
                      :sender-ch (chan)}))

(defn handle-incoming [listener-ch server-ch]
  (go
   (loop []
     (when-let [msg (<! server-ch)]
       (do (>! listener-ch msg)
           (recur))))))

(defn handle-outgoing [sender-ch server-ch]
  (go
   (loop []
     (when-let [msg (<! sender-ch)]
       (>! server-ch msg)
       (recur)))))

(defn make-socket [listener-ch sender-ch]
  (go
   (let [{:keys [ws-channel error]} (<! (ws-ch +ws-url+))]
     (when ws-channel
       (do
         (handle-incoming listener-ch ws-channel)
         (handle-outgoing sender-ch ws-channel))))))

(defn clear-text [owner]
  (om/set-state! owner :text ""))

(defn post-message [target text]
  (println text)
  #_(put! target text)
  false)

(defn send-message-click [sender-ch text owner app]
  (do
    (post-message sender-ch text)
    (clear-text owner)))

(defn login-click [sender-ch text owner app]
  (post-message sender-ch text)
  (om/update! app :user-name text)
  (clear-text owner))

(defn check-for-enter [e owner {:keys [text sender-ch]} app click-fn]
  (when (or (= 13 (.-keyCode e))
            (= 13 (.-which e)))
    (click-fn sender-ch text owner app)))

(defn text-entry [e owner {:keys [text]}]
  (om/set-state! owner :text (.. e -target -value)))

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
                                                   :onClick (fn [e] (login-click sender-ch text owner app))}))))))))

(defn view-picker [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [listener-ch (:listener-ch app)
            sender-ch (:sender-ch app)]
        (make-socket listener-ch sender-ch)
        (go (loop []
              (let [msg (<! listener-ch)]
                ;; do something cool with the message
                (recur))))))
    om/IRender
    (render [_]
      (if (empty? (:user-name app))
        (om/build signin-view app)
        (om/build master-view app)))))

(om/root
 view-picker
 app-state
 {:target (. js/document (getElementById "app"))})
