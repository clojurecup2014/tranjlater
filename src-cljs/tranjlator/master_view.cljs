(ns tranjlator.master-view
  (:require [cljs.core.async :refer [<! >! put! timeout chan] :as a]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
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
    (let [sha (-> text hash hex-string)]
      (post-message sender-ch
                    (assoc (command text)
                      :user-name (:user-name @app)
                      :language (keyword (:writing-language @app))
                      :original-sha sha
                      :content-sha sha)))
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

(defn original-view [app owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (dom/div #js {:className "col-md-5"}
               (dom/div #js {:className "panel panel-primary"}
                        (dom/div #js {:className "panel-heading"}
                                 (dom/h4 #js {:className "panel-title"}
                                         (dom/span #js {:className "glyphicon glyphicon-globe"})
                                         " Original"))
                        (dom/div #js {:className "panel-body chat" :id "original-panel"}
                                 (apply dom/ul #js {:className "list-group"}
                                        (map (fn [item] (dom/li #js {:className "list-group-item"} (format-chat item)
                                                               (dom/span #js {:className "badge"} (name (:language item))))) app))))))
    om/IDidUpdate
    (did-update [_ _ _]
      (let [panel (.getElementById js/document "original-panel")]
        (set! (.-scrollTop panel) (.-scrollHeight panel))))))

(defn translated-view [app owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (dom/div #js {:className "col-md-5"}
               (dom/div #js {:className "panel panel-primary"}
                        (dom/div #js {:className "panel-heading"}
                                 (dom/h4 #js {:className "panel-title"}
                                         (dom/span #js {:className "glyphicon glyphicon-home"})
                                         " Translated"))
                        (dom/div #js {:className "panel-body chat" :id "translated-panel"}
                                 (apply dom/ul #js {:className "list-group"}
                                        (map (fn [item] (dom/li #js {:className "list-group-item"} (format-chat item))) app))))))
    om/IDidUpdate
    (did-update [_ _ _]
      (let [panel (.getElementById js/document "translated-panel")]
        (set! (.-scrollTop panel) (.-scrollHeight panel)))))
  )
(defn master-view [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [listener-ch (:listener-ch app)
            sender-ch (:sender-ch app)
            socket-ctrl (:socket-ctrl app)]
        (make-socket listener-ch sender-ch (:user-name app) socket-ctrl)
        (go (loop []
              (when-let [msg (<! listener-ch)]
                (let [topic (:topic msg)
                      lang (:reading-language @app)]
                  (cond
                   (= :original topic) (om/transact! app :original (fn [col] (conj col msg)))
                   (= :user-join topic) (om/transact! app :users (fn [col] (conj col (:user-name msg []))))
                   (= :user-part topic) (om/transact! app :users (fn [col] (remove (fn [x] (= x (:user-name msg))) col)))
                   (= (keyword lang) topic) (om/transact! app :translated (fn [col] (conj col msg)))
                   (= :ping topic) (when (= (:target msg) (:user-name @app)) (ping))
                   (= :error topic) (do (let [name (:user-name @app)]
                                          (put! socket-ctrl "Doh")
                                          (om/update! app :sender-ch (chan))
                                          (om/update! app :listener-ch (chan))
                                          (om/update! app :dissappointed-user name)
                                          (om/update! app :user-name nil)))
                   :default (println "RECVD:" msg "type: " (keys msg))))
                (recur))))))
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
        (dom/div {:className "col-md-12"}
                 (dom/div #js {:className "row"}
                          (when-not (:reading-language app)
                            (dom/div #js {:className "alert alert-success"} "Select a language to view translated messages."))
                          (dom/div #js {:className "col-md-5 col-xs-offset-7"}
                                   (apply dom/select #js {:className "form-control"
                                                    :value (:reading-language app)
                                                          :onChange (fn [e] (reading-language-change e sender-ch app))}
                                          (map (fn [lang] (dom/option #js {:value (:code lang)} (:name lang))) (cons {:code nil :name ""} +langs+))))
                          (om/build users-view (:users app))
                          (om/build original-view (:original app))
                          (om/build translated-view (:translated app) {:init-state {:label " Translated"
                                                                                    :glyph "glyphicon glyphicon-home"
                                                                                    :show-language false}})
                          (dom/div #js {:className "col-md-12 table"}
                                   (dom/div #js {:className "col-md-3"}
                                            (apply dom/select #js {:className "form-control"
                                                             :value (:writing-language app)
                                                             :onChange (fn [e] (writing-language-change e sender-ch app))}
                                                        (map (fn [lang] (dom/option #js {:value (:code lang)} (:name lang))) +langs+)))
                                   (dom/div #js {:className "col-md-8"}
                                            (dom/input #js {:className "form-control" :type "text"
                                                            :value text :id "text-entry"
                                                            :onKeyUp (fn [e] (check-for-enter e owner state app send-message-click))
                                                            :onChange #(text-entry % owner state)}))
                                   (dom/div #js {:className "col-md-1"}
                                            (dom/button #js {:type "button" :className "btn btn-primary"
                                                             :onClick (fn [e] (send-message-click sender-ch text owner app))}
                                                        (dom/span #js {:className "glyphicon glyphicon-leaf"}) " Enter"))))
                 (dom/div #js {:className "row"}
                          (dom/div #js {:className "alert alert-warning" :id "timeout-alert"}
                                   "Want to type in a different language? Use the dropdown to tell us which one. Rather code? Use /clojure or @clojure to evaluate forms.")))))))
