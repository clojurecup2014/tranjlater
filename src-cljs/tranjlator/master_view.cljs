(ns tranjlator.master-view
  (:require [cljs.core.async :refer [<! >! put! timeout chan] :as a]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [tranjlator.actions :refer [check-for-enter post-message clear-text
                                        text-entry]]
            [tranjlator.sockets :refer [make-socket]]
            [tranjlator.langs :refer [+langs+]]
            [tranjlator.messages :as m]
            [tranjlator.hashing :refer [hash hex-string]]
            [tranjlator.slash-commands :refer [command]])
  (:require-macros [cljs.core.async.macros :refer [go-loop go alt!]]))

(defn ping []
  (let [clip (.getElementById js/document "ping-sound")]
    (.play clip)))

(defn reading-language-change [e sender-ch app]
  (let [new-lang (.. e -target -value)
        old-lang (:reading-language @app)
        user-name (:user-name @app)]
    (when (not (= new-lang old-lang))
      (do
        (when (empty? (:translated @app))
          (om/update! app :translated (into [] (:original @app))))
        (om/update! app :reading-language new-lang)
        (when-not (nil? old-lang)
          (put! sender-ch (m/->language-unsub user-name old-lang)))
        (when-not (nil? new-lang)
          (put! sender-ch (m/->language-sub user-name new-lang)))))))

(defn writing-language-change [e sender-ch app]
  (let [new-lang (.. e -target -value)]
    (om/update! app :writing-language new-lang)))

(defn send-message-click [sender-ch text owner app]
  (do
    (let [sha (-> text .toLowerCase hash hex-string)]
      (do
        (om/transact! app :chat-hisory  (fn [col] (cons col text)))
        (post-message sender-ch
                      (assoc (command text)
                        :user-name (:user-name @app)
                        :language (keyword (:writing-language @app))
                        :original-sha sha
                        :content-sha sha))))
    (clear-text owner)))

(defn replacer [col key val new-val ex-key ex-val]
  (loop [src col acc []]
    (cond (empty? src) (conj acc new-val)
          (and (= (key (first src)) val)
               (not (= (ex-key (first src)) ex-val))) (concat acc [new-val] (rest src))
          :default (recur (rest src) (conj acc (first src))))))

(defn users-view [app owner]
  (reify
    om/IRender
    (render [this]
      (html
       [:div {:class "col-md-2"}
        [:div {:class "panel panel-info"}
         [:div {:class "panel-heading"}
          [:h4 {:class "panel-title"}
           [:span {:class "glyphicon glyphicon-user"}]
           " Users"]]
         [:div {:class "panel-body"}
          [:ul
           (map (fn [item] [:li item]) (sort app))]]]]))))

(defn format-chat [{:keys [user-name content]}]
  (str user-name ": " content ))

(defn original-view [app owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (html
       [:div {:class "col-md-5"}
        [:div {:class "panel panel-primary"}
         [:div {:class "panel-heading"}
          [:h4 {:class "panel-title"}
           [:span {:class "glyphicon glyphicon-globe"}] " Original"]]
         [:div {:class "panel-body chat" :id "original-panel"}
          [:ul {:class "list-group"}
           (map (fn [item] [:li {:class "list-group-item"} (format-chat item)
                           [:span {:class "badge"} (name (:language item))]]) app)]]]]))
    om/IDidUpdate
    (did-update [_ _ _]
      (let [panel (.getElementById js/document "original-panel")]
        (set! (.-scrollTop panel) (.-scrollHeight panel))))))

(defn translated-view [app owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (html
       [:div {:class "col-md-5"}
        [:div  {:class "panel panel-primary"}
         [:div {:class "panel-heading"}
          [:h4 {:class "panel-title"}
           [:span {:class "glyphicon glyphicon-home"}]
           " Translated"]]
         [:div {:class "panel-body chat" :id "translated-panel"}
          [:ul {:class "list-group"}
                 (map (fn [item]
                        (let [new-lang (:reading-language app)
                              valid? (fn [msg]
                                       (= (:topic msg) (keyword new-lang)))]
                          [:li {:class
                                (if (valid? item) "list-group-item" "out-of-date list-group-item")}
                           (format-chat item)])) (:translated app))]]]]))
    om/IDidUpdate
    (did-update [_ _ _]
      (let [panel (.getElementById js/document "translated-panel")]
        (set! (.-scrollTop panel) (.-scrollHeight panel))))))

(defn master-view [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [listener-ch (:listener-ch app)
            sender-ch (:sender-ch app)
            socket-ctrl (:socket-ctrl app)]
        (make-socket listener-ch sender-ch (:user-name app) socket-ctrl)
        (go (loop []
              (if-let [msg (<! listener-ch)]
                (let [topic (:topic msg)
                      lang (:reading-language @app)]
                  (cond
                   (= :original topic) (om/transact! app :original (fn [col] (conj col msg)))
                   (= :user-join topic) (om/transact! app :users (fn [col] (conj col (:user-name msg []))))
                   (= :user-part topic) (om/transact! app :users (fn [col] (remove (fn [x] (= x (:user-name msg))) col)))
                   (= (keyword lang) topic) (om/transact! app :translated
                                                          (fn [col] (replacer col :original-sha
                                                                             (:original-sha msg) msg :topic topic)))
                   (= :ping topic) (when (= (:target msg) (:user-name @app)) (ping))
                   (= :error topic) (do (let [name (:user-name @app)]
                                          (put! socket-ctrl "Doh")
                                          (om/update! app :sender-ch (chan))
                                          (om/update! app :listener-ch (chan))
                                          (om/update! app :dissappointed-user name)
                                          (om/update! app :user-name nil)))
                   :default (println "RECVD:" msg "type: " (keys msg)))
                  (recur))
                (om/update! app :connection-good false))))))
    om/IDidMount
    (did-mount [_]
      (let [txtbox (.getElementById js/document "text-entry")
            msg (.getElementById js/document "timeout-alert")]
        (.focus txtbox)
        (go
         (<! (timeout (* 30 1000)))
         (set! (.-className msg) (+ (.-className msg) " hidden")))))
    om/IInitState
    (init-state [_]
      {:text ""})
    om/IRenderState
    (render-state [this {:keys [text] :as state}]
      (let [sender-ch (:sender-ch app)]
        (html
         [:div
          [:div {:class "row"}
           (if (not (:connection-good app))
             [:div {:class "alert alert-danger"} "Connection lost, please refresh"]
             (when-not (:reading-language app)
               [:div {:class "alert alert-success"} "Select a language to view translated messages."]))
           [:div {:class "col-md-5 col-xs-offset-7"}
            [:select {:class "form-control"
                                    :value (:reading-language app)
                      :onChange (fn [e] (reading-language-change e sender-ch app))}
             (map (fn [lang] [:option {:value (:code lang)} (:name lang)]) (cons {:code nil :name ""} +langs+))]]
           (om/build users-view (:users app))
           (om/build original-view (:original app))
           (om/build translated-view app)
           [:div {:class "col-md-12 table"}
            [:div {:class "col-md-3"}
             [:select {:class "form-control"
                       :value (:writing-language app)
                       :onChange (fn [e] (writing-language-change e sender-ch app))}
              (map (fn [lang] [:option [:option  {:value (:code lang)} (:name lang)]]) +langs+)]]
            [:div {:class "col-md-8"}
             [:input {:class "form-control" :type "text"
                      :value text :id "text-entry"
                      :onKeyUp #(when (= (.-key %) "Enter")
                                  (send-message-click sender-ch text owner app))
                      :onChange #(text-entry % owner state)}]]
            [:div {:class "col-md-1"}
             [:button {:type "button" :class "btn btn-primary"
                       :onClick (fn [e] (send-message-click sender-ch text owner app))}
              [:span {:class "glyphicon glyphicon-leaf"}] " Enter"]]]]
          [:div {:class "row"}
           [:div {:class "alert alert-warning" :id "timeout-alert"}
            "Want to type in a different language? Use the dropdown to tell us which one. Rather code? Use /clojure or @clojure to evaluate forms."]]])))))
