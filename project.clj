(defproject tranjlator "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[com.palletops/uberimage "0.3.0"]
            [lein-cljsbuild "1.0.3"]]
  :dependencies [[org.clojure/clojure "1.7.0-alpha2"]
                 [org.clojure/clojurescript "0.0-2322"]
                 [enlive "1.1.5"]
                 [sablono "0.2.18"]
                 [clj-time "0.8.0"]
                 [compojure "1.1.8"]
                 [jarohen/chord "0.4.2"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [om "0.7.3"]
                 [http-kit "2.1.16"]
                 [ring-cors "0.1.0"]]
  :cljsbuild {:source-paths ["src-cljs"]
              :compiler {
                         :output-to "main.js"
                         :optimizations :whitespace
                         :pretty-print true}})
